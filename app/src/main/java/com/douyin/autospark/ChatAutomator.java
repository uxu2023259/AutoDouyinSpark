package com.douyin.autospark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ChatAutomator implements AutoCloseable {
  private static final String CHAT_PAGE_URL = "https://creator.douyin.com/creator-micro/data/following/chat";
  private static final DateTimeFormatter SCREENSHOT_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private final AppPaths paths;
  private final ChineseLogger logger;
  private final ObjectMapper mapper = new ObjectMapper();
  private Playwright playwright;
  private BrowserContext context;
  private Page page;
  private String automationScript;

  public ChatAutomator(AppPaths paths, ChineseLogger logger) {
    this.paths = paths;
    this.logger = logger;
  }

  public synchronized void openBrowserForLogin() {
    Page current = ensureChatPage();
    current.bringToFront();
    logger.info("已打开创作者聊天页。如页面要求登录，请在浏览器中扫码或完成验证；程序会复用本次登录状态。");
  }

  public synchronized TaskResult sendMessage(AppConfig config, String target, String taskId) {
    try {
      Page current = ensureChatPage();
      Map<String, Object> input = new HashMap<>();
      input.put("settings", Map.of("messageText", config.getMessageText()));
      input.put("target", target);
      input.put("taskId", taskId);
      String expression = "async (input) => {" + loadAutomationScript() + "\nreturn await runDouyinAutoSpark(input);\n}";
      Object raw = current.evaluate(expression, input);
      TaskResult result = mapper.convertValue(raw, TaskResult.class);
      result.setTarget(target);
      if (!result.isOk() || !result.isSuccess()) {
        captureFailureScreenshot(target, result.getErrorCode().isBlank() ? "TARGET_FAILED" : result.getErrorCode());
      }
      return result;
    } catch (RuntimeException error) {
      logger.exception("执行页面自动化时发生异常", error);
      captureFailureScreenshot(target, "PAGE_EXCEPTION");
      refreshQuietly();
      return TaskResult.failed(target, "PAGE_EXCEPTION", "页面自动化异常：" + error.getMessage());
    }
  }

  private Page ensureChatPage() {
    ensureBrowserContext();
    if (page == null || page.isClosed()) {
      List<Page> pages = context.pages();
      page = pages.isEmpty() ? context.newPage() : pages.getFirst();
    }
    if (page.url() == null || !page.url().contains("/creator-micro/data/following/chat")) {
      logger.info("正在打开抖音创作者中心聊天页");
      page.navigate(CHAT_PAGE_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45000));
    }
    page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(30000));
    return page;
  }

  private void ensureBrowserContext() {
    if (context != null) {
      return;
    }
    launchPersistentBrowserWithTimeout();
  }

  private void launchPersistentBrowserWithTimeout() {
    ExecutorService launcher = Executors.newSingleThreadExecutor(r -> {
      Thread thread = new Thread(r, "抖音自动续火花-浏览器启动器");
      thread.setDaemon(true);
      return thread;
    });
    try {
      launcher.submit(this::launchPersistentBrowser).get(45, TimeUnit.SECONDS);
    } catch (TimeoutException error) {
      logger.warn("浏览器自动控制初始化超过 45 秒仍未完成。已确认常见原因是 Playwright 浏览器安装检查被阻塞，请查看日志详情。");
      throw new IllegalStateException("浏览器自动控制初始化超时", error);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("浏览器启动被中断", error);
    } catch (java.util.concurrent.ExecutionException error) {
      Throwable cause = error.getCause() == null ? error : error.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException("浏览器启动失败：" + cause.getMessage(), cause);
    } finally {
      launcher.shutdownNow();
    }
  }

  private void launchPersistentBrowser() {
    try {
      paths.ensureDirectories();
    } catch (IOException error) {
      throw new IllegalStateException("无法创建程序数据目录：" + error.getMessage(), error);
    }
    logger.info("正在准备浏览器运行环境。资料目录：" + paths.browserProfileDir());
    if (playwright == null) {
      logger.info("正在初始化 Playwright 控制器，已跳过浏览器下载/安装检查。");
      playwright = Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
      logger.info("Playwright 控制器初始化完成。");
    }
    BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
        .setHeadless(false)
        .setArgs(List.of("--start-maximized", "--disable-blink-features=AutomationControlled"));
    Path bundledChrome = findBundledChrome();
    if (bundledChrome == null) {
      throw new IllegalStateException("未找到程序内置 Chromium 浏览器文件 chrome.exe，请完整解压程序包后再运行。");
    }
    options.setExecutablePath(bundledChrome);
    logger.info("正在使用程序内置 Chromium 浏览器：" + bundledChrome);
    logger.info("正在启动持久化浏览器窗口，请稍候。");
    context = playwright.chromium().launchPersistentContext(paths.browserProfileDir(), options);
    context.setDefaultTimeout(15000);
    List<Page> pages = context.pages();
    page = pages.isEmpty() ? context.newPage() : pages.getFirst();
    page.setViewportSize(1280, 900);
    logger.info("浏览器已启动，登录状态目录：" + paths.browserProfileDir());
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

  private void refreshQuietly() {
    try {
      if (page != null && !page.isClosed()) {
        page.reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(30000));
        logger.info("页面已刷新，等待下次重试。");
      }
    } catch (RuntimeException ignored) {
      logger.warn("页面刷新失败，下一次任务会重新尝试打开页面。");
    }
  }

  @Override
  public synchronized void close() {
    if (context != null) {
      try {
        context.close();
      } catch (RuntimeException ignored) {
        // 关闭浏览器失败不阻断程序退出。
      }
      context = null;
    }
    if (playwright != null) {
      try {
        playwright.close();
      } catch (RuntimeException ignored) {
        // 关闭 Playwright 失败不阻断程序退出。
      }
      playwright = null;
    }
  }
}
