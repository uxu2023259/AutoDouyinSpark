package com.douyin.autospark;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AutoRunner implements AutoCloseable {
  private static final DateTimeFormatter FIXED_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final ConfigStore store;
  private final ChatAutomator automator;
  private final ChineseLogger logger;
  private final SendPolicy sendPolicy = new SendPolicy();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "抖音自动续火花-调度器");
    thread.setDaemon(true);
    return thread;
  });
  private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
    Thread thread = new Thread(r, "抖音自动续火花-执行器");
    thread.setDaemon(true);
    return thread;
  });
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final Deque<TaskResult> recentResults = new ArrayDeque<>();

  public AutoRunner(ConfigStore store, ChatAutomator automator, ChineseLogger logger) {
    this.store = store;
    this.automator = automator;
    this.logger = logger;
  }

  public void start() {
    scheduler.scheduleWithFixedDelay(this::scheduledTick, 2, 30, TimeUnit.SECONDS);
    logger.info("自动调度器已启动，会根据配置执行巡检任务。");
  }

  public void runNow() {
    worker.submit(() -> runOnce("手动执行"));
  }

  public void openBrowserForLogin() {
    logger.info("已收到打开登录/聊天页请求，正在启动内置浏览器，请稍候。");
    worker.submit(() -> {
      try {
        if (!DesktopSession.isInteractiveDesktopAvailable()) {
          logger.warn("当前没有可交互桌面会话，暂不启动浏览器，等待用户登录 Windows 桌面。");
          return;
        }
        automator.openBrowserForLogin();
      } catch (RuntimeException error) {
        logger.exception("打开浏览器失败", error);
      }
    });
  }

  public List<TaskResult> recentResults() {
    synchronized (recentResults) {
      return new ArrayList<>(recentResults);
    }
  }

  private void scheduledTick() {
    try {
      AppConfig config = store.loadConfig();
      if (!config.isEnabled()) {
        return;
      }
      if (!DesktopSession.isInteractiveDesktopAvailable()) {
        logger.warn("未检测到可交互桌面会话，后台守护只记录状态，不启动浏览器。");
        return;
      }
      SendState state = store.loadState();
      LocalDateTime now = LocalDateTime.now();
      boolean intervalDue = state.getNextIntervalRunAt() == null || !now.isBefore(state.getNextIntervalRunAt());
      boolean fixedDueNow = shouldRunFixedTime(state, config, now);
      if (intervalDue || fixedDueNow) {
        if (intervalDue) {
          state.setNextIntervalRunAt(now.plusMinutes(config.getIntervalMinutes()));
          store.saveState(state);
        }
        String triggerName = fixedDueNow ? "固定时间" : "定时巡检";
        worker.submit(() -> runOnce(triggerName));
      }
    } catch (Exception error) {
      logger.exception("调度器检查失败", error);
    }
  }

  private boolean shouldRunFixedTime(SendState state, AppConfig config, LocalDateTime now) throws IOException {
    String fixedTime = config.getFixedTime();
    if (fixedTime == null || fixedTime.isBlank()) {
      return false;
    }
    String currentMinute = now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
    if (!currentMinute.equals(fixedTime)) {
      return false;
    }
    String key = now.format(FIXED_KEY_FORMATTER);
    if (Boolean.TRUE.equals(state.getFixedRuns().get(key))) {
      return false;
    }
    state.getFixedRuns().put(key, true);
    store.saveState(state);
    return true;
  }

  private void runOnce(String triggerName) {
    if (!running.compareAndSet(false, true)) {
      logger.warn("已有任务正在执行，本次请求已跳过。");
      return;
    }

    String taskId = triggerName + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    boolean allGood = false;
    try {
      AppConfig config = store.loadConfig();
      SendState state = store.loadState();
      LocalDateTime now = LocalDateTime.now();
      state.setNextIntervalRunAt(now.plusMinutes(config.getIntervalMinutes()));
      store.saveState(state);

      if (!config.isEnabled()) {
        logger.warn("程序已禁用，本次任务不执行。");
        return;
      }
      if (config.getTargets().isEmpty()) {
        logger.warn("请先填写目标会话关键词，本次任务不执行。");
        return;
      }
      if (!DesktopSession.isInteractiveDesktopAvailable()) {
        logger.warn("当前没有可交互桌面会话，已进入等待状态，不启动浏览器。");
        return;
      }
      if (sendPolicy.dailyLimitReached(state, config)) {
        logger.warn("今日已达到 " + config.getDailyLimit() + " 次发送上限，本次任务不执行。");
        return;
      }

      logger.info("开始执行任务：" + taskId + "，触发方式：" + triggerName);
      List<TaskResult> results = new ArrayList<>();
      for (String target : config.getTargets()) {
        state = store.loadState();
        if (sendPolicy.dailyLimitReached(state, config)) {
          TaskResult skipped = TaskResult.skipped(target, "今日发送次数已达到上限");
          results.add(skipped);
          addRecent(skipped);
          logger.warn("目标 " + target + " 已跳过：今日发送次数已达到上限。");
          break;
        }

        String cooldownReason = sendPolicy.cooldownReason(state, config, target, LocalDateTime.now());
        if (!cooldownReason.isBlank()) {
          TaskResult skipped = TaskResult.skipped(target, cooldownReason);
          results.add(skipped);
          addRecent(skipped);
          logger.info("目标 " + target + " 已跳过：" + cooldownReason + "。");
          continue;
        }

        TaskResult result = runTargetWithRetry(config, target, taskId);
        results.add(result);
        addRecent(result);
        if (result.isSuccess() && "confirmed".equals(result.getDeliveryStatus())) {
          state = store.loadState();
          sendPolicy.markSent(state, target, LocalDateTime.now());
          store.saveState(state);
          logger.info("目标 " + target + " 发送成功，命中会话：" + emptyAsNone(result.getMatchedConversationName()) + "。");
        } else if (result.isSuccess() && "uncertain".equals(result.getDeliveryStatus())) {
          logger.warn("目标 " + target + " 发送结果不确定，未计入成功次数：" + result.getReason());
        } else if (result.isSkipped()) {
          logger.info("目标 " + target + " 已跳过：" + result.getReason());
        } else {
          logger.warn("目标 " + target + " 处理失败：" + nonEmpty(result.getReason(), result.getErrorDetail(), "未知错误"));
        }
      }
      RunCounts counts = countResults(results);
      allGood = counts.failed == 0 && counts.uncertain == 0;
      logger.info("任务结束：成功 " + counts.success + " 个，结果不确定 " + counts.uncertain + " 个，跳过 " + counts.skipped + " 个，失败 " + counts.failed + " 个。");
    } catch (Exception error) {
      logger.exception("任务执行失败", error);
    } finally {
      try {
        if (!allGood) {
          AppConfig config = store.loadConfig();
          SendState state = store.loadState();
          state.setNextIntervalRunAt(LocalDateTime.now().plusMinutes(Math.max(1, Math.min(config.getIntervalMinutes(), 5))));
          store.saveState(state);
          logger.info("由于本次任务存在异常，已安排较短间隔后自动重试。");
        }
      } catch (IOException error) {
        logger.exception("更新下次执行时间失败", error);
      }
      running.set(false);
    }
  }

  private TaskResult runTargetWithRetry(AppConfig config, String target, String taskId) {
    int maxAttempt = Math.max(1, config.getRetryCount() + 1);
    TaskResult last = TaskResult.failed(target, "NOT_STARTED", "尚未开始执行");
    for (int attempt = 1; attempt <= maxAttempt; attempt += 1) {
      logger.info("正在处理目标 " + target + "，第 " + attempt + " 次尝试。");
      last = automator.sendMessage(config, target, taskId);
      if (last.isSuccess() || last.isSkipped() || "LOGIN_REQUIRED".equals(last.getErrorCode())) {
        return last;
      }
      if (attempt < maxAttempt) {
        logger.warn("目标 " + target + " 本次尝试失败，将自动重试：" + nonEmpty(last.getReason(), last.getErrorDetail(), "未知错误"));
        sleep(1500);
      }
    }
    return last;
  }

  private void addRecent(TaskResult result) {
    synchronized (recentResults) {
      recentResults.addLast(result);
      while (recentResults.size() > 20) {
        recentResults.removeFirst();
      }
    }
  }

  private RunCounts countResults(List<TaskResult> results) {
    RunCounts counts = new RunCounts();
    for (TaskResult result : results) {
      if (result.isSuccess() && "confirmed".equals(result.getDeliveryStatus())) {
        counts.success += 1;
      } else if (result.isSuccess() && "uncertain".equals(result.getDeliveryStatus())) {
        counts.uncertain += 1;
      } else if (result.isSkipped()) {
        counts.skipped += 1;
      } else {
        counts.failed += 1;
      }
    }
    return counts;
  }

  private String emptyAsNone(String value) {
    return value == null || value.isBlank() ? "无" : value;
  }

  private String nonEmpty(String first, String second, String fallback) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    if (second != null && !second.isBlank()) {
      return second;
    }
    return fallback;
  }

  private void sleep(long milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
    worker.shutdownNow();
    automator.close();
  }

  private static class RunCounts {
    int success;
    int uncertain;
    int skipped;
    int failed;
  }
}
