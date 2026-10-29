package awa.uxu.douyin.autospark;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
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
  private final ConfigStore store;
  private final ChatAutomator automator;
  private final ChineseLogger logger;
  private final DailySendPlanner planner;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "抖音自动续火花-调度器"));
  private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> daemon(r, "抖音自动续火花-执行器"));
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean cycleQueued = new AtomicBoolean(false);
  private final AtomicBoolean loginMaintenanceQueued = new AtomicBoolean(false);
  private final AtomicBoolean legacyPauseLogged = new AtomicBoolean(false);
  private final Deque<TaskResult> recentResults = new ArrayDeque<>();

  public AutoRunner(ConfigStore store, ChatAutomator automator, ChineseLogger logger) {
    this(store, automator, logger, Clock.systemDefaultZone());
  }

  AutoRunner(ConfigStore store, ChatAutomator automator, ChineseLogger logger, Clock clock) {
    this.store = store;
    this.automator = automator;
    this.logger = logger;
    planner = new DailySendPlanner(clock);
  }

  public void start() {
    start(2);
  }

  public void start(long initialDelaySeconds) {
    scheduler.scheduleWithFixedDelay(this::scheduledTick, Math.max(1, initialDelaySeconds), 5, TimeUnit.SECONDS);
    scheduler.scheduleWithFixedDelay(this::scheduleLoginMaintenance, 15, 15, TimeUnit.SECONDS);
    logger.info("每日自动发送已启动；每个目标每天最多处理一次，并按名单逐项记录结果。");
  }

  public void resumeAutomaticWork() {
    worker.submit(() -> {
      try {
        SendState state = store.loadState();
        if (state.getBlockedReason().isBlank()) {
          logger.info("当前账号没有需要解除的人工暂停。");
          return;
        }
        String reason = state.getBlockedReason();
        state.setBlockedReason("");
        store.saveState(state);
        logger.info("已解除账号暂停（此前原因：“ + reason + ”）；后续仍按今日计划时间和未处理名单执行，不会立即发送。");
      } catch (IOException error) {
        logger.exception("解除账号暂停失败", error);
      }
    });
  }

  public void openBrowserForLogin() {
    logger.info("已收到打开登录/聊天页请求，正在启动内置浏览器，请稍候。");
    worker.submit(() -> {
      try {
        if (!DesktopSession.isInteractiveDesktopAvailable()) {
          logger.warn("当前没有可交互桌面会话，无法打开登录窗口；后台自动任务仍会使用无界面浏览器运行。");
          return;
        }
        automator.openBrowserForLogin();
      } catch (BrowserProfileInUseException error) {
        logger.warn(error.getMessage());
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

  public boolean isRunning() {
    return running.get();
  }

  DailySendPlanner planner() {
    return planner;
  }

  private void scheduledTick() {
    try {
      AppConfig config = store.loadConfig();
      SendState state = store.loadState();
      if (state.rollOverIfNeeded(planner.now().toLocalDate())) {
        store.saveState(state);
        legacyPauseLogged.set(false);
      }
      if (state.isLegacyUnattributedSentToday() && legacyPauseLogged.compareAndSet(false, true)) {
        logger.warn("旧版本今天存在无法对应到具体用户的发送记录。为避免重复发送，本账号今天暂停剩余名单；明天恢复正常计划。");
      }
      if (!config.isEnabled() || !state.getBlockedReason().isBlank()) return;
      if (!planner.pendingTargets(config, state).isEmpty()) queueDailyCycle();
    } catch (Exception error) {
      logger.exception("每日发送计划检查失败", error);
    }
  }

  private void queueDailyCycle() {
    if (!cycleQueued.compareAndSet(false, true)) return;
    try {
      worker.submit(() -> {
        try {
          runDailyCycle();
        } finally {
          cycleQueued.set(false);
        }
      });
    } catch (java.util.concurrent.RejectedExecutionException ignored) {
      cycleQueued.set(false);
    }
  }

  void runDailyCycle() {
    if (!running.compareAndSet(false, true)) return;
    try {
      AppConfig config = store.loadConfig();
      SendState state = store.loadState();
      if (state.rollOverIfNeeded(planner.now().toLocalDate())) {
        store.saveState(state);
        legacyPauseLogged.set(false);
      }
      if (!config.isEnabled()) {
        logger.info("当前账号已停用，本次每日发送不执行。");
        return;
      }
      if (!state.getBlockedReason().isBlank()) {
        logger.warn("当前账号处于人工暂停状态，未处理的目标保持待发送：" + state.getBlockedReason());
        logDailySummary(config, state, true);
        return;
      }
      if (state.isLegacyUnattributedSentToday()) {
        logger.warn("旧版本今天存在无法归属到具体用户的发送记录，今日跳过剩余名单以避免重复发送。");
        return;
      }
      List<String> pending = planner.pendingTargets(config, state);
      if (pending.isEmpty()) return;

      logger.info("开始每日发送：计划时间 " + config.getDailyTime() + "，名单 " + config.getTargets().size()
          + " 位，今日尚未处理 " + pending.size() + " 位。");
      LocalDate runDate = planner.now().toLocalDate();
      for (String target : pending) {
        if (!planner.now().toLocalDate().equals(runDate)) {
          logger.info("当前发送跨过本地午夜，今日任务停止；新的一天会在设定时间重新处理名单。");
          break;
        }
        if (!store.loadConfig().isEnabled()) {
          logger.info("当前账号在名单处理中被停用，剩余用户保持待处理。");
          break;
        }
        state = store.loadState();
        if (state.rollOverIfNeeded(planner.now().toLocalDate())) {
          store.saveState(state);
        }
        if (!state.getBlockedReason().isBlank()) break;
        if (state.isLegacyUnattributedSentToday() || state.hasAttempted(target)) continue;

        state.beginAttempt(target, planner.now());
        store.saveState(state);
        TaskResult result = processTarget(config, target);
        addRecent(result);
        state = store.loadState();
        String resultStatus = resultStatus(result);
        if (result.isBlocked() || isAccountLevelBlock(result.getErrorCode())) {
          resultStatus = "PAUSED";
          String reason = nonEmpty(result.getSecurityPrompt(), result.getReason(), result.getErrorDetail(), "页面需要人工处理");
          state.setBlockedReason(reason);
        }
        state.recordResult(target, resultStatus, resultDetail(result), planner.now());
        store.saveState(state);
        logTargetResult(target, result, resultStatus);
        if ("PAUSED".equals(resultStatus)) break;
      }
      state = store.loadState();
      logDailySummary(config, state, !state.getBlockedReason().isBlank());
    } catch (Exception error) {
      logger.exception("每日发送任务中断", error);
      try {
        SendState state = store.loadState();
        logDailySummary(store.loadConfig(), state, !state.getBlockedReason().isBlank());
      } catch (Exception statusError) {
        logger.exception("读取中断后的每日发送状态失败", statusError);
      }
    } finally {
      try {
        automator.closeAfterAutomaticCycle();
      } catch (RuntimeException error) {
        logger.warn("关闭每日自动化浏览器时遇到问题：" + error.getMessage());
      }
      running.set(false);
    }
  }

  private TaskResult processTarget(AppConfig config, String target) {
    ConversationDiscoveryResult discovery;
    try {
      logger.info("正在查找指定用户“" + target + "”的完全同名会话。");
      discovery = automator.discoverConversations(config, target, taskId("查找用户"));
    } catch (BrowserProfileInUseException error) {
      return TaskResult.failed(target, "BROWSER_PROFILE_IN_USE", error.getMessage());
    }
    if (!discovery.isOk()) {
      return TaskResult.failed(target, discovery.getErrorCode(), discovery.getReason());
    }
    if (discovery.getConversations().isEmpty()) {
      return TaskResult.failed(target, "TARGET_NOT_FOUND", "会话列表中没有完全同名的用户");
    }
    if (discovery.getConversations().size() != 1) {
      return TaskResult.failed(target, "TARGET_AMBIGUOUS", "存在多个完全同名会话，无法确认指定用户；未发送");
    }
    ConversationTarget conversation = discovery.getConversations().getFirst();
    if (!TargetNames.key(conversation.displayName()).equals(TargetNames.key(target))) {
      return TaskResult.failed(target, "TARGET_NAME_MISMATCH", "扫描结果与配置名称不完全一致；未发送");
    }
    conversation.setQuery(target);
    try {
      return automator.sendMessage(config, conversation, taskId("发送消息"));
    } catch (BrowserProfileInUseException error) {
      return TaskResult.failed(target, "BROWSER_PROFILE_IN_USE", error.getMessage());
    }
  }

  private String resultStatus(TaskResult result) {
    if (result.isConfirmed()) return "SENT";
    if (result.isUncertain()) return "UNCERTAIN";
    if ("TARGET_NOT_FOUND".equals(result.getErrorCode())) return "NOT_FOUND";
    if ("TARGET_AMBIGUOUS".equals(result.getErrorCode())) return "AMBIGUOUS";
    if (result.isBlocked() || isAccountLevelBlock(result.getErrorCode())) return "PAUSED";
    return "NOT_SENT";
  }

  private boolean isAccountLevelBlock(String errorCode) {
    return List.of("LOGIN_REQUIRED", "SECURITY_CHECK_REQUIRED", "BROWSER_PROFILE_IN_USE").contains(errorCode);
  }

  private String resultDetail(TaskResult result) {
    return nonEmpty(result.getReason(), result.getErrorDetail(), result.getSecurityPrompt(), result.getOutcome());
  }

  private void logTargetResult(String target, TaskResult result, String status) {
    if ("SENT".equals(status)) {
      logger.info("用户“" + target + "”已发送并确认。");
    } else if ("PAUSED".equals(status)) {
      logger.warn("用户“" + target + "”处理后账号进入人工暂停，已停止后续目标。");
    } else if ("UNCERTAIN".equals(status)) {
      logger.warn("用户“" + target + "”的发送结果不确定；今日不自动重试，以免重复发送。");
    } else if ("NOT_FOUND".equals(status)) {
      logger.warn("用户“" + target + "”未找到完全同名会话；今天不再重试。");
    } else if ("AMBIGUOUS".equals(status)) {
      logger.warn("用户“" + target + "”存在重名会话，未发送；今天不再重试。");
    } else {
      logger.warn("用户“" + target + "”未发送成功，今天不自动重试：" + resultDetail(result));
    }
  }

  private void logDailySummary(AppConfig config, SendState state, boolean paused) {
    List<String> targets = config.getTargets();
    int total = targets.size();
    int sent = 0;
    int uncertain = 0;
    int ambiguous = 0;
    int notFound = 0;
    int failed = 0;
    int pausedTargets = 0;
    int processed = 0;
    for (String target : targets) {
      DailyTargetResult result = state.getDailyTargets().get(TargetNames.key(target));
      if (result == null) continue;
      processed++;
      switch (result.getStatus()) {
        case "SENT", "MIGRATED_SENT" -> sent++;
        case "UNCERTAIN", "PROCESSING" -> uncertain++;
        case "AMBIGUOUS" -> ambiguous++;
        case "NOT_FOUND" -> notFound++;
        case "PAUSED" -> pausedTargets++;
        case "NOT_SENT", "FAILED" -> failed++;
      }
    }
    int remaining = Math.max(0, total - processed);
    if (paused || !state.getBlockedReason().isBlank()) {
      logger.warn("每日发送已暂停：确认发送 " + sent + "/" + total + "，已处理 " + processed + "/" + total
          + "，剩余 " + remaining + " 位未处理。原因：" + state.getBlockedReason());
    } else if (remaining > 0) {
      logger.warn("每日名单尚未处理完：确认发送 " + sent + "/" + total + "，结果不确定 " + uncertain
          + "，未找到 " + notFound + "，重名 " + ambiguous + "，失败 " + failed + "，剩余 " + remaining + " 位。");
    } else if (total > 0 && sent == total) {
      logger.info("今日名单全部发送成功：" + sent + "/" + total + " 位用户。");
    } else if (total == 0) {
      logger.warn("今日名单为空，没有发送消息。");
    } else {
      logger.warn("今日名单已处理完，但并非全部发送成功：确认发送 " + sent + "/" + total
          + "，结果不确定 " + uncertain + "，未找到 " + notFound + "，重名 " + ambiguous
          + "，失败 " + failed + "，账号级暂停目标 " + pausedTargets + "。");
    }
  }

  private String taskId(String action) {
    return action + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String nonEmpty(String first, String second, String third, String fallback) {
    if (first != null && !first.isBlank()) return first;
    if (second != null && !second.isBlank()) return second;
    if (third != null && !third.isBlank()) return third;
    return fallback;
  }

  private void addRecent(TaskResult result) {
    synchronized (recentResults) {
      recentResults.addLast(result);
      while (recentResults.size() > 30) recentResults.removeFirst();
    }
  }

  private void scheduleLoginMaintenance() {
    if (!loginMaintenanceQueued.compareAndSet(false, true)) return;
    try {
      worker.submit(() -> {
        try {
          automator.keepLoginSessionDurable();
        } finally {
          loginMaintenanceQueued.set(false);
        }
      });
    } catch (java.util.concurrent.RejectedExecutionException ignored) {
      loginMaintenanceQueued.set(false);
    }
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
    worker.shutdown();
    try {
      if (!worker.awaitTermination(10, TimeUnit.SECONDS)) worker.shutdownNow();
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      worker.shutdownNow();
    }
    automator.close();
  }

  private static Thread daemon(Runnable runnable, String name) {
    Thread thread = new Thread(runnable, name);
    thread.setDaemon(true);
    return thread;
  }
}
