package com.douyin.autospark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
  private final SendPolicy sendPolicy = new SendPolicy();
  private final Clock clock;
  private final Random random;
  private final SchedulePlanner planner;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "抖音自动续火花-调度器"));
  private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> daemon(r, "抖音自动续火花-执行器"));
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean cycleQueued = new AtomicBoolean(false);
  private final AtomicBoolean loginMaintenanceQueued = new AtomicBoolean(false);
  private final Deque<TaskResult> recentResults = new ArrayDeque<>();

  public AutoRunner(ConfigStore store, ChatAutomator automator, ChineseLogger logger) {
    this(store, automator, logger, Clock.systemDefaultZone(), new Random());
  }

  AutoRunner(ConfigStore store, ChatAutomator automator, ChineseLogger logger, Clock clock, Random random) {
    this.store = store;
    this.automator = automator;
    this.logger = logger;
    this.clock = clock;
    this.random = random;
    planner = new SchedulePlanner(clock, random);
  }

  public void start() {
    start(2);
  }

  public void start(long initialDelaySeconds) {
    scheduler.scheduleWithFixedDelay(this::scheduledTick, Math.max(1, initialDelaySeconds), 15, TimeUnit.SECONDS);
    scheduler.scheduleWithFixedDelay(this::scheduleLoginMaintenance, 15, 15, TimeUnit.SECONDS);
    logger.info("自动调度器已启动，定时、间隔和失败重试将按独立时间线执行。");
  }

  public void runNow() {
    worker.submit(() -> {
      try {
        SendState state = store.loadState();
        state.setBlockedReason("");
        store.saveState(state);
      } catch (IOException error) {
        logger.exception("清除人工处理状态失败", error);
      }
      runManual();
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

  private void scheduledTick() {
    try {
      AppConfig config = store.loadConfig();
      SendState state = store.loadState();
      if (!config.isEnabled() || !state.getBlockedReason().isBlank()) return;
      if (planner.plan(config, state)) store.saveState(state);
      if (!planner.dueTriggers(state).isEmpty() || !planner.dueRetries(state).isEmpty()) queueDueCycle();
    } catch (Exception error) {
      logger.exception("调度器检查失败", error);
    }
  }

  private void queueDueCycle() {
    if (!cycleQueued.compareAndSet(false, true)) return;
    worker.submit(() -> {
      cycleQueued.set(false);
      runDueCycle();
    });
  }

  private void runManual() {
    if (!running.compareAndSet(false, true)) {
      logger.info("已有任务正在执行，手动请求已进入执行队列。");
      return;
    }
    try {
      executeFullRun(store.loadConfig(), "手动执行", taskId("手动执行"), new HashSet<>());
    } catch (Exception error) {
      logger.exception("手动任务执行失败", error);
    } finally {
      running.set(false);
    }
  }

  private void runDueCycle() {
    if (!running.compareAndSet(false, true)) return;
    List<PendingTrigger> dueTriggers = List.of();
    try {
      AppConfig config = store.loadConfig();
      SendState state = store.loadState();
      if (!config.isEnabled() || !state.getBlockedReason().isBlank()) return;
      dueTriggers = planner.dueTriggers(state);
      List<PendingRetry> dueRetries = planner.dueRetries(state);
      if (dueTriggers.isEmpty() && dueRetries.isEmpty()) return;
      Set<String> processedKeys = new HashSet<>();
      for (PendingRetry retry : dueRetries) {
        processRetry(config, retry, processedKeys);
        if (isAccountBlocked()) break;
      }
      if (!dueTriggers.isEmpty() && !isAccountBlocked()) {
        String triggerName = triggerName(dueTriggers);
        executeFullRun(config, triggerName, taskId(triggerName), processedKeys);
      }
    } catch (Exception error) {
      logger.exception("自动任务执行失败", error);
    } finally {
      if (!dueTriggers.isEmpty()) {
        try {
          AppConfig config = store.loadConfig();
          SendState state = store.loadState();
          planner.complete(config, state, dueTriggers);
          store.saveState(state);
        } catch (IOException error) {
          logger.exception("保存调度完成状态失败", error);
        }
      }
      running.set(false);
    }
  }

  private void executeFullRun(AppConfig config, String triggerName, String taskId, Set<String> processedKeys) throws IOException {
    if (!config.isEnabled()) {
      logger.info("当前账号已停用，本次任务不执行。");
      return;
    }
    if (config.getTargets().isEmpty()) {
      logger.info("目标会话为空，本次任务按程序策略跳过，不计为失败。");
      return;
    }
    SendState state = store.loadState();
    if (sendPolicy.dailyLimitReached(state, config)) {
      logger.info("今日已达到 " + config.getDailyLimit() + " 次发送上限，本次仍会扫描全部目标，并将未发送会话排入次日续发队列。");
    }

    logger.info("开始执行任务：" + taskId + "，触发方式：" + triggerName + "。");
    List<TaskResult> results = new ArrayList<>();
    Map<String, ConversationTarget> conversations = discoverConversations(config, taskId, results, true);
    int sentInBatch = 0;
    for (ConversationTarget conversation : conversations.values()) {
      String stateKey = conversation.stateKey();
      String target = conversation.displayName();
      if (processedKeys.contains(stateKey)) {
        addSkipped(results, target, "本轮已处理，已合并重复触发");
        continue;
      }
      state = store.loadState();
      PendingRetry pendingRetry = findPendingRetry(state, stateKey);
      if (pendingRetry != null && !DailyLimitDeferral.isDeferred(pendingRetry)) {
        addSkipped(results, target, "该会话已有独立重试任务，正常调度暂不提前重发");
        continue;
      }
      if (sendPolicy.dailyLimitReached(state, config)) {
        LocalDateTime dueAt = deferForDailyLimit(config, conversation);
        addSkipped(results, target, "今日发送次数已达到上限，已排入 " + dueAt + " 后的自动续发队列");
        continue;
      }
      String cooldown = sendPolicy.cooldownReason(state, config, stateKey, target, now());
      if (!cooldown.isBlank()) {
        addSkipped(results, target, cooldown);
        continue;
      }
      TaskResult result = sendTarget(config, conversation, taskId);
      processedKeys.add(stateKey);
      results.add(result);
      addRecent(result);
      handleTargetResult(config, conversation, result, 0);
      sentInBatch += result.isConfirmed() ? 1 : 0;
      if (result.isBlocked()) break;
      humanPauseBetweenTargets(config, sentInBatch);
    }
    RunCounts counts = countResults(results);
    logger.info("任务结束：确认成功 " + counts.success + " 个，结果不确定 " + counts.uncertain
        + " 个，主动跳过 " + counts.skipped + " 个，失败 " + counts.failed + " 个，需人工处理 " + counts.blocked + " 个。");
  }

  private Map<String, ConversationTarget> discoverConversations(
      AppConfig config, String taskId, List<TaskResult> results, boolean scheduleFailure) throws IOException {
    Map<String, ConversationTarget> conversations = new LinkedHashMap<>();
    for (String query : config.getTargets()) {
      ConversationDiscoveryResult discovery;
      try {
        logger.info("正在查找与关键词“" + query + "”匹配的全部会话。");
        discovery = automator.discoverConversations(config, query, taskId);
      } catch (BrowserProfileInUseException error) {
        discovery = ConversationDiscoveryResult.failed("BROWSER_PROFILE_IN_USE", error.getMessage());
      }
      if (!discovery.isOk()) {
        TaskResult failed = TaskResult.failed(query, discovery.getErrorCode(), discovery.getReason());
        if (isBlockedCode(discovery.getErrorCode())) {
          failed.setOutcome("BLOCKED");
          blockAccount(discovery.getReason());
        }
        results.add(failed);
        addRecent(failed);
        if (scheduleFailure && !failed.isBlocked()) scheduleDiscoveryRetry(config, query, failed, 0);
        continue;
      }
      logger.info("关键词“" + query + "”已完整扫描会话列表，匹配到 "
          + discovery.getConversations().size() + " 个会话。完全同名会话优先，其余包含关键词的会话继续处理。");
      for (ConversationTarget conversation : discovery.getConversations()) {
        if (conversation.getQuery().isBlank()) conversation.setQuery(query);
        conversations.putIfAbsent(conversation.stateKey(), conversation);
      }
    }
    logger.info("全部关键词扫描完成，去重后本轮共有 " + conversations.size() + " 个待处理会话。");
    return conversations;
  }

  private TaskResult sendTarget(AppConfig config, ConversationTarget conversation, String taskId) {
    try {
      return automator.sendMessage(config, conversation, taskId);
    } catch (BrowserProfileInUseException error) {
      return TaskResult.failed(conversation.displayName(), "BROWSER_PROFILE_IN_USE", error.getMessage());
    }
  }

  private void handleTargetResult(AppConfig config, ConversationTarget conversation, TaskResult result, int retriesCompleted) throws IOException {
    String stateKey = conversation.stateKey();
    SendState state = store.loadState();
    if (result.isConfirmed()) {
      sendPolicy.markSent(state, stateKey, now());
      state.getPendingRetries().removeIf(item -> item.getStateKey().equals(stateKey));
      store.saveState(state);
      logger.info("目标 " + conversation.displayName() + " 已检测到新增己方消息气泡，确认发送成功。");
      return;
    }
    if (result.isSkipped()) {
      logger.info("目标 " + conversation.displayName() + " 已主动跳过：" + result.getReason() + "。");
      return;
    }
    if (result.isBlocked() || isBlockedCode(result.getErrorCode())) {
      result.setOutcome("BLOCKED");
      blockAccount(nonEmpty(result.getSecurityPrompt(), result.getReason(), "页面要求人工完成安全验证"));
      state = store.loadState();
      state.getPendingRetries().removeIf(item -> item.getStateKey().equals(stateKey));
      store.saveState(state);
      return;
    }
    if (retriesCompleted >= config.getRetryCount() && !result.isUncertain()) {
      state.getPendingRetries().removeIf(item -> item.getStateKey().equals(stateKey));
      store.saveState(state);
      result.setOutcome("FINAL_FAILURE");
      logger.warn("目标 " + conversation.displayName() + " 本次处理失败："
          + nonEmpty(result.getReason(), result.getErrorDetail(), "未提供失败原因")
          + "；已达到消息重试上限，等待下一次正常调度。");
      return;
    }
    PendingRetry retry = createRetry(config, conversation, result, retriesCompleted);
    upsertRetry(state, retry);
    store.saveState(state);
    if (result.isUncertain()) {
      String followUp = retriesCompleted >= config.getRetryCount() ? "只核验原消息，不再点击发送" : "先核验原消息，确认不存在后才执行重试";
      logger.warn("目标 " + conversation.displayName() + " 的发送结果不确定，将在约 " + config.getRetryDelayMinutes()
          + " 分钟后" + followUp + "。");
    } else {
      logger.warn("目标 " + conversation.displayName() + " 未确认成功，将在约 " + config.getRetryDelayMinutes() + " 分钟后独立重试。");
    }
  }

  private void processRetry(AppConfig config, PendingRetry retry, Set<String> processedKeys) throws IOException {
    SendState state = store.loadState();
    if (!config.getTargets().contains(retry.getQuery())) {
      removeRetry(state, retry.getId());
      store.saveState(state);
      logger.info("重试目标已从配置移除，本次按程序策略跳过，不计为失败。");
      return;
    }
    if (retry.isDiscoveryRetry()) {
      processDiscoveryRetry(config, retry, processedKeys);
      return;
    }

    ConversationTarget conversation = retry.getConversation();
    processedKeys.add(conversation.stateKey());
    if ("UNCERTAIN".equals(retry.getOutcome())) {
      TaskResult reconciliation = automator.reconcileMessage(config, conversation, retry, taskId("结果核验"));
      addRecent(reconciliation);
      if (reconciliation.isConfirmed() || reconciliation.isBlocked()) {
        handleTargetResult(config, conversation, reconciliation, retry.getRetriesCompleted());
        return;
      }
      if (!reconciliation.isOk() || !"NOT_CONFIRMED".equals(reconciliation.getOutcome())) {
        state = store.loadState();
        retry.setDueAt(now().plusMinutes(config.getRetryDelayMinutes()));
        upsertRetry(state, retry);
        store.saveState(state);
        logger.warn("目标 " + conversation.displayName() + " 的原消息核验未完成，已延期核验；本次不会重复发送。");
        return;
      }
      if (retry.getRetriesCompleted() >= retry.getMaxRetries()) {
        state = store.loadState();
        removeRetry(state, retry.getId());
        store.saveState(state);
        reconciliation.setOutcome("FINAL_FAILURE");
        logger.warn("目标 " + conversation.displayName() + " 未核验到原消息且发送重试次数已用完，本次结束且不再发送。");
        return;
      }
    }
    if (!digest(config.getMessageText()).equals(retry.getMessageDigest())) {
      state = store.loadState();
      removeRetry(state, retry.getId());
      store.saveState(state);
      logger.info("发送内容已变更，旧重试任务已主动取消，不发送过期内容。");
      return;
    }
    state = store.loadState();
    if (sendPolicy.dailyLimitReached(state, config)) {
      LocalDateTime dueAt = DailyLimitDeferral.postpone(retry, now());
      upsertRetry(state, retry);
      store.saveState(state);
      logger.info("目标 " + conversation.displayName() + " 今日发送次数已达到上限，续发任务已顺延至 " + dueAt + " 后。");
      return;
    }
    TaskResult result = sendTarget(config, conversation, taskId("失败重试"));
    addRecent(result);
    state = store.loadState();
    removeRetry(state, retry.getId());
    store.saveState(state);
    int retriesCompleted = DailyLimitDeferral.isDeferred(retry) ? 0 : retry.getRetriesCompleted() + 1;
    handleTargetResult(config, conversation, result, retriesCompleted);
  }

  private void processDiscoveryRetry(AppConfig config, PendingRetry retry, Set<String> processedKeys) throws IOException {
    List<TaskResult> discoveryResults = new ArrayList<>();
    Map<String, ConversationTarget> conversations = discoverConversations(config, taskId("发现重试"), discoveryResults, false, retry.getQuery());
    SendState state = store.loadState();
    removeRetry(state, retry.getId());
    store.saveState(state);
    if (conversations.isEmpty()) {
      TaskResult failed = discoveryResults.isEmpty()
          ? TaskResult.failed(retry.getQuery(), "TARGET_NOT_FOUND", "重试时仍未发现目标会话")
          : discoveryResults.getLast();
      if (!failed.isBlocked() && retry.getRetriesCompleted() + 1 < retry.getMaxRetries()) {
        scheduleDiscoveryRetry(config, retry.getQuery(), failed, retry.getRetriesCompleted() + 1);
      }
      return;
    }
    for (ConversationTarget conversation : conversations.values()) {
      processedKeys.add(conversation.stateKey());
      state = store.loadState();
      if (sendPolicy.dailyLimitReached(state, config)) {
        LocalDateTime dueAt = deferForDailyLimit(config, conversation);
        logger.info("目标 " + conversation.displayName() + " 今日发送次数已达到上限，已排入 " + dueAt + " 后的自动续发队列。");
        continue;
      }
      TaskResult result = sendTarget(config, conversation, taskId("发现重试"));
      addRecent(result);
      handleTargetResult(config, conversation, result, retry.getRetriesCompleted() + 1);
      if (result.isBlocked()) break;
    }
  }

  private Map<String, ConversationTarget> discoverConversations(
      AppConfig config, String taskId, List<TaskResult> results, boolean scheduleFailure, String onlyQuery) throws IOException {
    List<String> originalTargets = config.getTargets();
    config.setTargets(List.of(onlyQuery));
    try {
      return discoverConversations(config, taskId, results, scheduleFailure);
    } finally {
      config.setTargets(originalTargets);
    }
  }

  private PendingRetry createRetry(AppConfig config, ConversationTarget conversation, TaskResult result, int retriesCompleted) {
    PendingRetry retry = new PendingRetry();
    retry.setId("目标:" + conversation.stateKey());
    retry.setStateKey(conversation.stateKey());
    retry.setConversation(conversation);
    retry.setQuery(conversation.getQuery());
    retry.setMessageText(config.getMessageText());
    retry.setMessageDigest(digest(config.getMessageText()));
    retry.setOutcome(result.isUncertain() ? "UNCERTAIN" : "FAILED");
    retry.setErrorCode(result.getErrorCode());
    retry.setRetriesCompleted(retriesCompleted);
    retry.setMaxRetries(config.getRetryCount());
    retry.setAttemptedAt(now());
    retry.setDueAt(now().plusMinutes(config.getRetryDelayMinutes()));
    retry.setClickedAtEpochMillis(result.getClickedAtEpochMillis());
    retry.setBaselineMessageCount(result.getBaselineMessageCount());
    retry.setBaselineFingerprints(result.getBaselineFingerprints());
    retry.setBaselineMessageTimestamps(result.getBaselineMessageTimestamps());
    return retry;
  }

  private void scheduleDiscoveryRetry(AppConfig config, String query, TaskResult result, int retriesCompleted) throws IOException {
    if (retriesCompleted >= config.getRetryCount()) {
      logger.warn("关键词“" + query + "”发现失败且已达到重试上限。");
      return;
    }
    PendingRetry retry = new PendingRetry();
    retry.setId("关键词:" + query);
    retry.setStateKey("关键词:" + query);
    retry.setQuery(query);
    retry.setMessageText(config.getMessageText());
    retry.setMessageDigest(digest(config.getMessageText()));
    retry.setOutcome("FAILED");
    retry.setErrorCode(result.getErrorCode());
    retry.setRetriesCompleted(retriesCompleted);
    retry.setMaxRetries(config.getRetryCount());
    retry.setAttemptedAt(now());
    retry.setDueAt(now().plusMinutes(config.getRetryDelayMinutes()));
    SendState state = store.loadState();
    upsertRetry(state, retry);
    store.saveState(state);
  }

  private void addSkipped(List<TaskResult> results, String target, String reason) {
    TaskResult skipped = TaskResult.skipped(target, reason);
    results.add(skipped);
    addRecent(skipped);
    logger.info("目标 " + target + " 已主动跳过：" + reason + "。");
  }

  private PendingRetry findPendingRetry(SendState state, String stateKey) {
    return state.getPendingRetries().stream()
        .filter(item -> item.getStateKey().equals(stateKey))
        .findFirst()
        .orElse(null);
  }

  private LocalDateTime deferForDailyLimit(AppConfig config, ConversationTarget conversation) throws IOException {
    SendState state = store.loadState();
    PendingRetry existing = findPendingRetry(state, conversation.stateKey());
    PendingRetry retry;
    if (existing != null && DailyLimitDeferral.isDeferred(existing)) {
      retry = existing;
      DailyLimitDeferral.postpone(retry, now());
    } else {
      retry = DailyLimitDeferral.create(config, conversation, digest(config.getMessageText()), now());
    }
    upsertRetry(state, retry);
    store.saveState(state);
    return retry.getDueAt();
  }

  private void upsertRetry(SendState state, PendingRetry retry) {
    state.getPendingRetries().removeIf(item -> item.getId().equals(retry.getId()));
    state.getPendingRetries().add(retry);
  }

  private void removeRetry(SendState state, String id) {
    state.getPendingRetries().removeIf(item -> item.getId().equals(id));
  }

  private boolean isAccountBlocked() throws IOException {
    return !store.loadState().getBlockedReason().isBlank();
  }

  private void blockAccount(String reason) throws IOException {
    SendState state = store.loadState();
    state.setBlockedReason(nonEmpty(reason, "", "需要人工处理页面安全验证"));
    store.saveState(state);
    logger.warn("当前账号自动发送已暂停，需要人工处理：" + state.getBlockedReason() + "。");
  }

  private boolean isBlockedCode(String code) {
    return List.of("LOGIN_REQUIRED", "SECURITY_CHECK_REQUIRED").contains(code);
  }

  private void humanPauseBetweenTargets(AppConfig config, int sentInBatch) {
    if (sentInBatch > 0 && sentInBatch % config.getHumanBatchSize() == 0) {
      long seconds = randomBetween(config.getHumanBatchRestMinSeconds(), config.getHumanBatchRestMaxSeconds());
      logger.info("已完成一批发送，仿人节奏休息约 " + seconds + " 秒。");
      sleep(seconds * 1000L);
    } else {
      sleep(randomBetween(config.getHumanTargetDelayMinSeconds(), config.getHumanTargetDelayMaxSeconds()) * 1000L);
    }
  }

  private long randomBetween(int min, int max) {
    return max <= min ? min : min + random.nextInt(max - min + 1);
  }

  private RunCounts countResults(List<TaskResult> results) {
    RunCounts counts = new RunCounts();
    for (TaskResult result : results) {
      if (result.isConfirmed()) counts.success++;
      else if (result.isSkipped()) counts.skipped++;
      else if (result.isBlocked()) counts.blocked++;
      else if (result.isUncertain()) counts.uncertain++;
      else counts.failed++;
    }
    return counts;
  }

  private String triggerName(List<PendingTrigger> triggers) {
    boolean fixed = triggers.stream().anyMatch(item -> "FIXED".equals(item.getType()));
    boolean interval = triggers.stream().anyMatch(item -> "INTERVAL".equals(item.getType()));
    return fixed && interval ? "定时模式与间隔模式合并触发" : fixed ? "定时模式" : "间隔模式";
  }

  private String taskId(String name) {
    return name + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  private String digest(String text) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("系统不支持 SHA-256 摘要", error);
    }
  }

  private void addRecent(TaskResult result) {
    synchronized (recentResults) {
      recentResults.addLast(result);
      while (recentResults.size() > 30) recentResults.removeFirst();
    }
  }

  private String nonEmpty(String first, String second, String fallback) {
    if (first != null && !first.isBlank()) return first;
    if (second != null && !second.isBlank()) return second;
    return fallback;
  }

  private void sleep(long milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
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

  private static final class RunCounts {
    int success;
    int uncertain;
    int skipped;
    int failed;
    int blocked;
  }
}
