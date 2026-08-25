package com.douyin.autospark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ChatAutomator implements AutoCloseable {
  private static final String CHAT_PAGE_URL = "https://creator.douyin.com/creator-micro/data/following/chat";
  private static final DateTimeFormatter SCREENSHOT_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private final AccountPaths paths;
  private final ChineseLogger logger;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Random random = new Random();
  private Playwright playwright;
  private BrowserContext context;
  private Page page;
  private String automationScript;
  private Boolean browserHeadless;

  public ChatAutomator(AccountPaths paths, ChineseLogger logger) {
    this.paths = paths;
    this.logger = logger;
  }

  public synchronized void openBrowserForLogin() {
    Page current = ensureChatPage(false);
    current.bringToFront();
    logger.info("已打开创作者聊天页。如页面要求登录，请在浏览器中扫码或完成验证；程序会复用本次登录状态。");
  }

  public synchronized void keepLoginSessionDurable() {
    persistLoginSessionCookies(true);
  }

  public synchronized TaskResult sendMessage(AppConfig config, String target, String taskId) {
    ConversationTarget conversation = new ConversationTarget();
    conversation.setQuery(target);
    conversation.setName(target);
    return sendMessage(config, conversation, taskId);
  }

  public synchronized ConversationDiscoveryResult discoverConversations(AppConfig config, String target, String taskId) {
    try {
      Page current = ensureChatPage(shouldRunHeadless(config));
      humanScrollProbe(current, config);
      Map<String, Object> input = new HashMap<>();
      input.put("action", "discover");
      input.put("target", target);
      input.put("taskId", taskId);
      Object raw = current.evaluate(automationExpression(), input);
      return mapper.convertValue(raw, ConversationDiscoveryResult.class);
    } catch (BrowserProfileInUseException error) {
      throw error;
    } catch (RuntimeException error) {
      String summary = summarizeAutomationFailure(error);
      logger.error("发现目标会话时发生异常：" + diagnosticDetail("发现会话", error));
      captureFailureScreenshot(target, "DISCOVERY_EXCEPTION");
      if (isControlConnectionFailure(error)) {
        invalidateBrowser("浏览器控制连接已经关闭，下次尝试将自动重建浏览器。", error);
      } else {
        logger.warn("本次发现异常未证明浏览器连接已断开，已保留当前浏览器和标签页。原因：" + summary);
      }
      return ConversationDiscoveryResult.failed("DISCOVERY_EXCEPTION", summary);
    }
  }

  public synchronized TaskResult sendMessage(AppConfig config, ConversationTarget conversation, String taskId) {
    String target = conversation.displayName();
    String stage = "准备浏览器";
    String attemptId = "尝试-" + UUID.randomUUID();
    boolean sendAttempted = false;
    long clickedAtEpochMillis = 0;
    TaskResult baseline = null;
    AtomicBoolean apiAcknowledged = new AtomicBoolean(false);
    try {
      Page current = ensureChatPage(shouldRunHeadless(config));
      stage = "定位目标";
      Map<String, Object> input = humanInput("prepare-human-target", config, conversation, taskId, attemptId);
      TaskResult prepared = evaluateTask(current, input);
      if (!prepared.isOk()) return finishResult(target, prepared);
      if (!usable(prepared.getActionBounds())) {
        return finishResult(target, TaskResult.failed(target, "TARGET_BOUNDS_MISSING", "目标会话当前不可见，未执行点击"));
      }
      stage = "点击会话";
      humanClick(current, prepared.getActionBounds(), config);

      stage = "确认会话";
      input.put("action", "verify-human-switch");
      TaskResult ready = evaluateTask(current, input);
      if (!ready.isOk() && "CONVERSATION_SWITCH_UNCONFIRMED".equals(ready.getErrorCode())) {
        logger.warn("目标 " + target + " 首次点击后未确认切换，正在重新扫描网页会话列表并再点击一次；尚未执行发送。");
        stage = "重新定位目标";
        input.put("action", "prepare-human-target");
        TaskResult preparedAgain = evaluateTask(current, input);
        if (!preparedAgain.isOk()) return finishResult(target, preparedAgain);
        if (!usable(preparedAgain.getActionBounds())) {
          return finishResult(target, TaskResult.failed(target, "TARGET_BOUNDS_MISSING", "重新定位后目标会话仍不可见，未执行发送"));
        }
        stage = "再次点击会话";
        humanClick(current, preparedAgain.getActionBounds(), config);
        stage = "再次确认会话";
        input.put("action", "verify-human-switch");
        ready = evaluateTask(current, input);
      }
      if (!ready.isOk()) return finishResult(target, ready);
      baseline = ready;
      if (!usable(ready.getEditorBounds())) {
        return finishResult(target, TaskResult.failed(target, "EDITOR_BOUNDS_MISSING", "输入框当前不可见，未执行输入"));
      }
      stage = "输入消息";
      humanClick(current, ready.getEditorBounds(), config);
      current.keyboard().press("Control+A");
      current.keyboard().press("Backspace");
      humanType(current, config.getMessageText(), config);

      stage = "核验输入";
      input.put("action", "validate-human-input");
      TaskResult validated = evaluateTask(current, input);
      if (!validated.isOk()) return finishResult(target, validated);
      if (validated.isSendButtonDisabled()) {
        return finishResult(target, TaskResult.failed(target, "SEND_BUTTON_DISABLED", "发送按钮当前不可用，未执行发送"));
      }
      if (!usable(validated.getSendButtonBounds())) {
        return finishResult(target, TaskResult.failed(target, "SEND_BUTTON_BOUNDS_MISSING", "发送按钮当前不可见，未执行发送"));
      }
      Consumer<Response> responseObserver = response -> {
        String url = response.url().toLowerCase();
        String method = response.request().method();
        if ("POST".equalsIgnoreCase(method)
            && response.status() >= 200
            && response.status() < 300
            && (url.contains("message") || url.contains("chat") || url.contains("conversation") || url.contains("/im/"))) {
          apiAcknowledged.set(true);
        }
      };
      current.onResponse(responseObserver);
      TaskResult result;
      try {
        stage = "点击发送";
        clickedAtEpochMillis = System.currentTimeMillis();
        sendAttempted = true;
        humanClick(current, validated.getSendButtonBounds(), config);
        stage = "确认发送结果";
        input.put("action", "verify-human-send");
        input.put("clickedAtEpochMillis", (double) clickedAtEpochMillis);
        result = evaluateTask(current, input);
      } finally {
        current.offResponse(responseObserver);
      }
      result.setApiAcknowledged(apiAcknowledged.get());
      result.setTarget(target);
      sleep(randomBetween(2000, 5000));
      return finishResult(target, result);
    } catch (BrowserProfileInUseException error) {
      throw error;
    } catch (RuntimeException error) {
      String summary = summarizeAutomationFailure(error);
      logger.error("发送流程在“" + stage + "”阶段发生异常：" + diagnosticDetail(stage, error));
      captureFailureScreenshot(target, sendAttempted ? "SEND_UNCERTAIN" : "AUTOMATION_EXCEPTION");
      if (isControlConnectionFailure(error)) {
        invalidateBrowser("浏览器控制连接已经关闭，下次任务将自动重建浏览器。", error);
      } else {
        logger.warn("异常未证明浏览器控制连接已断开，已保留当前浏览器和标签页。原因：" + summary);
      }
      if (sendAttempted) {
        return uncertainAfterSend(target, attemptId, clickedAtEpochMillis, baseline, apiAcknowledged.get(),
            "发送动作已触发，但“" + stage + "”阶段异常，已转入延迟核验，禁止立即重发");
      }
      return TaskResult.failed(target,
          isControlConnectionFailure(error) ? "CONTROL_CONNECTION_LOST" : "AUTOMATION_STAGE_EXCEPTION",
          "发送流程在“" + stage + "”阶段未完成：" + summary);
    }
  }

  public synchronized TaskResult reconcileMessage(
      AppConfig config, ConversationTarget conversation, PendingRetry retry, String taskId) {
    String target = conversation.displayName();
    String stage = "准备浏览器";
    try {
      Page current = ensureChatPage(shouldRunHeadless(config));
      String attemptId = "核验-" + UUID.randomUUID();
      stage = "定位核验目标";
      Map<String, Object> input = humanInput("prepare-human-target", config, conversation, taskId, attemptId);
      input.put("settings", Map.of("messageText", retry.getMessageText()));
      TaskResult prepared = evaluateTask(current, input);
      if (!prepared.isOk()) return finishResult(target, prepared);
      if (!usable(prepared.getActionBounds())) {
        return finishResult(target, TaskResult.failed(target, "TARGET_BOUNDS_MISSING", "结果核验时目标会话当前不可见"));
      }
      stage = "点击核验会话";
      humanClick(current, prepared.getActionBounds(), config);
      stage = "确认核验会话";
      input.put("action", "verify-human-switch");
      TaskResult ready = evaluateTask(current, input);
      if (!ready.isOk()) return finishResult(target, ready);
      stage = "核验原消息";
      input.put("action", "reconcile-human-message");
      input.put("retry", retryEvidence(retry));
      TaskResult result = evaluateTask(current, input);
      result.setTarget(target);
      return finishResult(target, result);
    } catch (BrowserProfileInUseException error) {
      throw error;
    } catch (RuntimeException error) {
      String summary = summarizeAutomationFailure(error);
      logger.error("结果核验在“" + stage + "”阶段发生异常：" + diagnosticDetail(stage, error));
      if (isControlConnectionFailure(error)) {
        invalidateBrowser("核验时浏览器控制连接已经关闭，下次任务将自动重建浏览器。", error);
      } else {
        logger.warn("结果核验异常未证明浏览器连接已断开，已保留当前浏览器和标签页。原因：" + summary);
      }
      return TaskResult.failed(target, "RECONCILIATION_EXCEPTION", "结果核验在“" + stage + "”阶段未完成：" + summary);
    }
  }

  private Map<String, Object> humanInput(
      String action, AppConfig config, ConversationTarget conversation, String taskId, String attemptId) {
    Map<String, Object> input = new HashMap<>();
    input.put("action", action);
    input.put("settings", Map.of("messageText", config.getMessageText()));
    input.put("target", Map.of(
        "query", conversation.getQuery(),
        "name", conversation.getName(),
        "identity", conversation.getIdentity(),
        "occurrence", conversation.getOccurrence(),
        "duplicateName", conversation.isDuplicateName()));
    input.put("taskId", taskId);
    input.put("attemptId", attemptId);
    return input;
  }

  private Map<String, Object> retryEvidence(PendingRetry retry) {
    Map<String, Object> evidence = new HashMap<>();
    evidence.put("messageText", retry.getMessageText());
    evidence.put("baselineMessageCount", retry.getBaselineMessageCount());
    evidence.put("baselineFingerprints", retry.getBaselineFingerprints());
    evidence.put("baselineMessageTimestamps", retry.getBaselineMessageTimestamps().stream()
        .filter(java.util.Objects::nonNull)
        .map(Long::doubleValue)
        .toList());
    evidence.put("clickedAtEpochMillis", (double) retry.getClickedAtEpochMillis());
    return evidence;
  }

  private TaskResult evaluateTask(Page current, Map<String, Object> input) {
    return mapper.convertValue(current.evaluate(automationExpression(), input), TaskResult.class);
  }

  private TaskResult finishResult(String target, TaskResult result) {
    result.setTarget(target);
    if (!result.isOk() || (!result.isSuccess() && !"NOT_CONFIRMED".equals(result.getOutcome()))) {
      if (!"PAGE_NOT_READY".equals(result.getErrorCode())) {
        captureFailureScreenshot(target, result.getErrorCode().isBlank() ? "TARGET_FAILED" : result.getErrorCode());
      }
    }
    return result;
  }

  private void humanClick(Page current, ElementBounds bounds, AppConfig config) {
    if (!usable(bounds)) {
      throw new IllegalArgumentException("页面返回的操作坐标不可用");
    }
    double endX = bounds.getX() + bounds.getWidth() * (0.35 + random.nextDouble() * 0.3);
    double endY = bounds.getY() + bounds.getHeight() * (0.35 + random.nextDouble() * 0.3);
    double startX = Math.max(4, endX + randomBetween(-240, 240));
    double startY = Math.max(4, endY + randomBetween(-160, 160));
    double controlX = (startX + endX) / 2 + randomBetween(-80, 80);
    double controlY = (startY + endY) / 2 + randomBetween(-50, 50);
    int steps = randomBetween(9, 18);
    for (int step = 1; step <= steps; step++) {
      double t = step / (double) steps;
      double x = (1 - t) * (1 - t) * startX + 2 * (1 - t) * t * controlX + t * t * endX;
      double y = (1 - t) * (1 - t) * startY + 2 * (1 - t) * t * controlY + t * t * endY;
      current.mouse().move(x, y);
      sleep(randomBetween(8, 22));
    }
    sleep(actionDelay(config));
    current.mouse().down();
    sleep(randomBetween(70, 180));
    current.mouse().up();
    sleep(actionDelay(config));
  }

  private boolean usable(ElementBounds bounds) {
    return bounds != null && bounds.isUsable();
  }

  private TaskResult uncertainAfterSend(
      String target, String attemptId, long clickedAtEpochMillis, TaskResult baseline,
      boolean apiAcknowledged, String reason) {
    TaskResult result = new TaskResult();
    result.setOk(true);
    result.setSuccess(true);
    result.setOutcome("UNCERTAIN");
    result.setDeliveryStatus("uncertain");
    result.setTarget(target);
    result.setAttemptId(attemptId);
    result.setClickedAtEpochMillis(clickedAtEpochMillis);
    result.setApiAcknowledged(apiAcknowledged);
    result.setReason(reason);
    result.setPhase("sending_message");
    if (baseline != null) {
      result.setBaselineMessageCount(baseline.getBaselineMessageCount());
      result.setBaselineFingerprints(baseline.getBaselineFingerprints());
      result.setBaselineMessageTimestamps(baseline.getBaselineMessageTimestamps());
      result.setCurrentChatTarget(baseline.getCurrentChatTarget());
      result.setMatchedConversationName(baseline.getMatchedConversationName());
    }
    return result;
  }

  private void humanType(Page current, String text, AppConfig config) {
    text.codePoints().forEach(codePoint -> {
      String character = new String(Character.toChars(codePoint));
      current.keyboard().insertText(character);
      sleep(typingDelay(config));
      if ("，。！？、,.!?".contains(character)) sleep(randomBetween(250, 700));
    });
  }

  private void humanScrollProbe(Page current, AppConfig config) {
    Locator candidates = current.locator("[role='grid'], [role='list'], .ReactVirtualized__Grid");
    for (int index = 0; index < Math.min(6, candidates.count()); index++) {
      BoundingBox box = candidates.nth(index).boundingBox();
      if (box == null || box.height < 100 || box.x > 700) continue;
      current.mouse().move(box.x + box.width * 0.6, box.y + box.height * 0.55);
      int steps = randomBetween(2, 4);
      for (int step = 0; step < steps; step++) {
        current.mouse().wheel(0, randomBetween(90, 220));
        sleep(randomBetween(140, 420));
      }
      current.mouse().wheel(0, -randomBetween(35, 90));
      sleep(actionDelay(config));
      return;
    }
  }

  private int actionDelay(AppConfig config) {
    return "CUSTOM".equals(config.getHumanizationPreset())
        ? randomBetween(config.getHumanActionDelayMinMillis(), config.getHumanActionDelayMaxMillis())
        : randomBetween(350, 1200);
  }

  private int typingDelay(AppConfig config) {
    return "CUSTOM".equals(config.getHumanizationPreset())
        ? randomBetween(config.getHumanTypingDelayMinMillis(), config.getHumanTypingDelayMaxMillis())
        : randomBetween(80, 220);
  }

  private int randomBetween(int min, int max) {
    return max <= min ? min : min + random.nextInt(max - min + 1);
  }

  private void sleep(long milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
    }
  }

  private boolean shouldRunHeadless(AppConfig config) {
    return config.isHeadlessMode() || !DesktopSession.isInteractiveDesktopAvailable();
  }

  private String automationExpression() {
    return "async (input) => {" + loadAutomationScript() + "\nreturn await runDouyinAutoSpark(input);\n}";
  }

  private Page ensureChatPage(boolean headless) {
    ensureBrowserContext(headless);
    List<Page> pages = context.pages();
    Page selected = page != null && !page.isClosed() && isChatPage(page) ? page : null;
    if (selected == null) {
      selected = pages.stream().filter(candidate -> !candidate.isClosed() && isChatPage(candidate)).findFirst().orElse(null);
    }
    if (selected == null) {
      selected = pages.stream().filter(candidate -> !candidate.isClosed() && isBlankPage(candidate)).findFirst().orElse(null);
    }
    if (selected == null) {
      selected = context.newPage();
    }
    page = selected;
    closeDuplicateChatPages(page, pages);
    page.setViewportSize(1280, 900);
    if (page.url() == null || !page.url().contains("/creator-micro/data/following/chat")) {
      logger.info("正在打开抖音创作者中心聊天页");
      page.navigate(CHAT_PAGE_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45000));
    }
    page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(30000));
    persistLoginSessionCookies(true);
    return page;
  }

  private boolean isChatPage(Page candidate) {
    String url = candidate.url();
    return url != null && url.contains("/creator-micro/data/following/chat");
  }

  private boolean isBlankPage(Page candidate) {
    String url = candidate.url();
    return url == null || url.isBlank() || "about:blank".equals(url) || url.startsWith("chrome://newtab");
  }

  private void closeDuplicateChatPages(Page selected, List<Page> pages) {
    int closed = 0;
    for (Page candidate : pages) {
      if (candidate == selected || candidate.isClosed() || !isChatPage(candidate)) {
        continue;
      }
      try {
        candidate.close();
        closed++;
      } catch (RuntimeException error) {
        logger.warn("清理重复聊天标签页未完成，已保留当前主聊天页。原因：" + summarizeAutomationFailure(error));
      }
    }
    if (closed > 0) {
      logger.info("已清理 " + closed + " 个程序恢复出的重复聊天标签页，仅保留当前聊天页。非聊天标签页未受影响。");
    }
  }

  private void ensureBrowserContext(boolean headless) {
    if (context != null && browserHeadless != null && browserHeadless == headless) {
      try {
        context.pages();
        return;
      } catch (RuntimeException error) {
        if (isControlConnectionFailure(error)) {
          invalidateBrowser("检测到浏览器控制连接已关闭，正在自动恢复。", error);
        } else {
          throw error;
        }
      }
    }
    closeBrowserResources();
    launchPersistentBrowser(headless);
  }

  private void launchPersistentBrowser(boolean headless) {
    try {
      paths.ensureDirectories();
    } catch (IOException error) {
      throw new IllegalStateException("无法创建程序数据目录：" + error.getMessage(), error);
    }
    logger.info("正在准备浏览器运行环境。资料目录：" + paths.browserProfileDir());
    List<Long> owners = waitForProfileRelease();
    if (!owners.isEmpty()) {
      throw new BrowserProfileInUseException(paths.browserProfileDir(), owners, null);
    }
    if (playwright == null) {
      logger.info("正在初始化 Playwright 控制器，已跳过浏览器下载/安装检查。");
      playwright = Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
      logger.info("Playwright 控制器初始化完成。");
    }
    BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
        .setHeadless(headless)
        .setTimeout(45000)
        .setArgs(List.of(
            "--disable-background-timer-throttling",
            "--disable-backgrounding-occluded-windows",
            "--disable-renderer-backgrounding",
            "--disable-blink-features=AutomationControlled"));
    Path bundledChrome = findBundledChrome();
    if (bundledChrome == null) {
      throw new IllegalStateException("未找到程序内置 Chromium 浏览器文件 chrome.exe，请完整解压程序包后再运行。");
    }
    options.setExecutablePath(bundledChrome);
    logger.info("正在使用程序内置 Chromium 浏览器：" + bundledChrome);
    logger.info(headless ? "正在启动服务器无界面浏览器，请稍候。" : "正在启动持久化浏览器窗口，请稍候。");
    try {
      context = playwright.chromium().launchPersistentContext(paths.browserProfileDir(), options);
    } catch (RuntimeException error) {
      List<Long> detectedOwners = BrowserProfileGuard.findOwners(paths.browserProfileDir());
      boolean profileConflict = !detectedOwners.isEmpty() || isProfileConflict(error);
      closeBrowserResources();
      if (profileConflict) {
        throw new BrowserProfileInUseException(paths.browserProfileDir(), detectedOwners, error);
      }
      throw new IllegalStateException(summarizeBrowserLaunchFailure(error), error);
    }
    browserHeadless = headless;
    context.setDefaultTimeout(15000);
    page = null;
    logger.info("浏览器已启动，登录状态目录：" + paths.browserProfileDir());
  }

  private List<Long> waitForProfileRelease() {
    List<Long> owners = BrowserProfileGuard.findOwners(paths.browserProfileDir());
    if (owners.isEmpty()) {
      return owners;
    }
    logger.warn("检测到账号浏览器目录可能仍被进程 " + owners + " 占用，正在进行一次短暂复查。");
    try {
      Thread.sleep(1200);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
    }
    return BrowserProfileGuard.findOwners(paths.browserProfileDir());
  }

  static boolean isProfileConflict(Throwable error) {
    String message = collectMessages(error).toLowerCase();
    return message.contains("exitcode=21")
        || message.contains("exit code 21")
        || message.contains("process did exit: exitcode=21")
        || message.contains("profile in use")
        || message.contains("user data directory is already in use");
  }

  static String summarizeBrowserLaunchFailure(Throwable error) {
    String message = collectMessages(error);
    if (message.contains("Executable doesn't exist") || message.contains("Failed to launch")) {
      return "内置 Chromium 启动失败，请确认程序已完整解压，且安全软件未拦截浏览器文件。";
    }
    return "内置 Chromium 启动失败。请查看中文日志确认浏览器文件、目录权限和服务器桌面环境是否正常。";
  }

  static String summarizeAutomationFailure(Throwable error) {
    String message = collectMessages(error);
    if (isProfileConflict(error)) {
      return "账号浏览器登录目录被其他进程占用，请关闭旧实例后重试。";
    }
    if (isControlConnectionFailure(error)) {
      return "浏览器页面或控制连接已关闭，程序将在下次任务中自动重建。";
    }
    if (message.contains("Timeout")) {
      return "页面操作等待超时，已保留当前浏览器并等待后续核验或重试。";
    }
    return "页面自动化操作未完成，已保留当前浏览器并等待后续处理。";
  }

  static boolean isControlConnectionFailure(Throwable error) {
    String message = collectMessages(error).toLowerCase();
    return message.contains("target page, context or browser has been closed")
        || message.contains("browser has been closed")
        || message.contains("context has been closed")
        || message.contains("page has been closed")
        || message.contains("target closed")
        || message.contains("connection closed")
        || message.contains("playwright connection closed")
        || message.contains("websocket is not open")
        || message.contains("pipe closed");
  }

  private static String diagnosticDetail(String stage, Throwable error) {
    String type = error == null ? "未知异常" : error.getClass().getSimpleName();
    String detail = collectMessages(error)
        .replaceAll("https?://\\S+", "[网页地址已隐藏]")
        .replaceAll("(?i)[A-Z]:\\\\[^\\r\\n]+", "[本地路径已隐藏]")
        .replaceAll("[\\r\\n]+", " ")
        .trim();
    if (detail.length() > 500) {
      detail = detail.substring(0, 500) + "…";
    }
    return "阶段：" + stage + "；异常类型：" + type + (detail.isBlank() ? "" : "；脱敏详情：" + detail);
  }

  private static String collectMessages(Throwable error) {
    StringBuilder messages = new StringBuilder();
    Throwable current = error;
    while (current != null) {
      if (current.getMessage() != null) {
        messages.append(current.getMessage()).append('\n');
      }
      current = current.getCause();
    }
    return messages.toString();
  }

  private Path findBundledChrome() {
    String appDir = System.getProperty("jpackage.app-path");
    List<Path> roots = new java.util.ArrayList<>();
    if (appDir != null && !appDir.isBlank()) {
      Path appPath = Path.of(appDir).toAbsolutePath();
      roots.add(Files.isDirectory(appPath) ? appPath : appPath.getParent());
    }
    ProcessHandle.current().info().command().ifPresent(command -> {
      Path commandPath = Path.of(command).toAbsolutePath();
      roots.add(Files.isDirectory(commandPath) ? commandPath : commandPath.getParent());
    });
    roots.add(Path.of("").toAbsolutePath());

    for (Path root : roots) {
      Path chrome = findBundledChrome(root, logger);
      if (chrome != null) {
        return chrome;
      }
    }
    return null;
  }

  static Path findBundledChrome(Path searchRoot, ChineseLogger logger) {
    if (searchRoot == null) {
      return null;
    }
    List<Path> candidates = List.of(searchRoot.resolve("browsers"), searchRoot.resolve("app").resolve("browsers"));
    for (Path bundledBrowsers : candidates) {
      if (!Files.isDirectory(bundledBrowsers)) {
        continue;
      }
      try (var stream = Files.walk(bundledBrowsers, 6)) {
        Path chrome = stream
            .filter(path -> path.getFileName() != null && "chrome.exe".equalsIgnoreCase(path.getFileName().toString()))
            .findFirst()
            .orElse(null);
        if (chrome != null) {
          return chrome;
        }
      } catch (IOException error) {
        if (logger != null) {
          logger.warn("查找内置 Chromium 浏览器失败：" + error.getMessage());
        }
      }
    }
    return null;
  }

  private String loadAutomationScript() {
    if (automationScript != null) {
      return automationScript;
    }
    try (InputStream input = ChatAutomator.class.getResourceAsStream("/content-automation.js")) {
      if (input == null) {
        throw new IllegalStateException("自动化脚本资源不存在");
      }
      automationScript = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      return automationScript;
    } catch (IOException error) {
      throw new IllegalStateException("读取自动化脚本失败：" + error.getMessage(), error);
    }
  }

  private void captureFailureScreenshot(String target, String code) {
    try {
      if (page == null || page.isClosed()) {
        return;
      }
      paths.ensureDirectories();
      String safeTarget = target == null || target.isBlank() ? "未知目标" : target.replaceAll("[\\\\/:*?\"<>|]", "_");
      Path screenshot = paths.screenshotDir().resolve(SCREENSHOT_TIME.format(LocalDateTime.now()) + "-" + safeTarget + "-" + code + ".png");
      page.screenshot(new Page.ScreenshotOptions().setPath(screenshot).setFullPage(true));
      logger.warn("已保存失败截图：" + screenshot);
    } catch (RuntimeException | IOException error) {
      logger.warn("保存失败截图时发生问题：" + error.getMessage());
    }
  }

  private void invalidateBrowser(String message, RuntimeException error) {
    logger.warn(message + " 原因：" + summarizeAutomationFailure(error));
    closeBrowserResources();
  }

  @Override
  public synchronized void close() {
    closeBrowserResources();
  }

  private void closeBrowserResources() {
    if (context != null) {
      persistLoginSessionCookies(false);
      try {
        context.close();
      } catch (RuntimeException ignored) {
        // 关闭浏览器失败不阻断程序退出。
      }
      context = null;
    }
    page = null;
    if (playwright != null) {
      try {
        playwright.close();
      } catch (RuntimeException ignored) {
        // 关闭 Playwright 失败不阻断程序退出。
      }
      playwright = null;
    }
    browserHeadless = null;
  }

  private void persistLoginSessionCookies(boolean writeLog) {
    if (context == null || context.isClosed()) {
      return;
    }
    try {
      var durableCookies = BrowserLoginPersistence.createDurableDouyinCookies(
          context.cookies(List.of(CHAT_PAGE_URL)), Instant.now());
      if (durableCookies.isEmpty()) {
        return;
      }
      context.addCookies(durableCookies);
      if (writeLog) {
        logger.info("已将当前抖音登录会话写入持久化浏览器资料，可在程序重启后继续使用。");
      }
    } catch (RuntimeException error) {
      if (writeLog) {
        logger.warn("本次登录状态持久化未完成，将在下一轮自动重试。");
      }
    }
  }
}
