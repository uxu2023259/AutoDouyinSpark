package awa.uxu.douyin.autospark;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SaasOfflineFlowTest {
  private static final String CHAT_URL = "https://creator.douyin.com/creator-micro/data/following/chat";

  @Test
  @SuppressWarnings("unchecked")
  void discoversSwitchesTypesAndConfirmsTheNewSaasBubble() throws Exception {
    Path chrome = findBundledChrome();
    Assumptions.assumeTrue(chrome != null, "未找到用于离线流程验证的内置 Chromium");

    String html = readResource("/offline-chat-saas.html");
    String automation = readResource("/content-automation.js");
    String evaluator = "async (input) => {" + automation + "\nreturn await runDouyinAutoSpark(input);\n}";

    try (Playwright playwright = Playwright.create(new Playwright.CreateOptions()
             .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
         Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
             .setHeadless(true)
             .setExecutablePath(chrome))) {
      Page page = browser.newPage();
      page.setViewportSize(1280, 900);
      page.route("**/*", route -> {
        if (CHAT_URL.equals(route.request().url())) {
          route.fulfill(new Route.FulfillOptions()
              .setStatus(200)
              .setContentType("text/html; charset=utf-8")
              .setBody(html));
        } else {
          route.abort();
        }
      });
      page.navigate(CHAT_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

      Map<String, Object> discoverInput = new HashMap<>();
      discoverInput.put("action", "discover");
      discoverInput.put("target", "测试会话");
      Map<String, Object> discovered = evaluate(page, evaluator, discoverInput);
      assertEquals(Boolean.TRUE, discovered.get("ok"), String.valueOf(discovered));
      List<Map<String, Object>> conversations = (List<Map<String, Object>>) discovered.get("conversations");
      assertEquals(1, conversations.size(), String.valueOf(discovered));

      Map<String, Object> target = conversations.getFirst();
      String attemptId = "saas-offline-flow";
      Map<String, Object> prepareInput = new HashMap<>();
      prepareInput.put("action", "prepare-target");
      prepareInput.put("target", target);
      prepareInput.put("settings", Map.of("messageText", "测试消息"));
      prepareInput.put("attemptId", attemptId);
      Map<String, Object> prepared = evaluate(page, evaluator, prepareInput);
      assertEquals(Boolean.TRUE, prepared.get("ok"), String.valueOf(prepared));
      clickCenter(page, (Map<String, Object>) prepared.get("actionBounds"));

      Map<String, Object> verifyInput = new HashMap<>();
      verifyInput.put("action", "verify-target-switch");
      verifyInput.put("attemptId", attemptId);
      Map<String, Object> ready = evaluate(page, evaluator, verifyInput);
      assertEquals("READY", ready.get("outcome"), String.valueOf(ready));

      clickCenter(page, (Map<String, Object>) ready.get("editorBounds"));
      page.keyboard().insertText("测试消息");

      Map<String, Object> validateInput = new HashMap<>();
      validateInput.put("action", "validate-message-input");
      validateInput.put("attemptId", attemptId);
      Map<String, Object> validated = evaluate(page, evaluator, validateInput);
      assertEquals(Boolean.TRUE, validated.get("ok"), String.valueOf(validated));

      clickCenter(page, (Map<String, Object>) validated.get("sendButtonBounds"));

      Map<String, Object> resultInput = new HashMap<>();
      resultInput.put("action", "verify-message-send");
      resultInput.put("attemptId", attemptId);
      resultInput.put("clickedAtEpochMillis", (double) System.currentTimeMillis());
      Map<String, Object> result = evaluate(page, evaluator, resultInput);
      assertEquals("CONFIRMED", result.get("outcome"), String.valueOf(result));
      assertEquals("confirmed", result.get("deliveryStatus"), String.valueOf(result));
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> evaluate(Page page, String evaluator, Map<String, Object> input) {
    Object value = page.evaluate(evaluator, input);
    assertNotNull(value, "页面自动化脚本未返回结果");
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private void clickCenter(Page page, Map<String, Object> bounds) {
    assertNotNull(bounds, "元素边界不存在");
    double x = ((Number) bounds.get("x")).doubleValue();
    double y = ((Number) bounds.get("y")).doubleValue();
    double width = ((Number) bounds.get("width")).doubleValue();
    double height = ((Number) bounds.get("height")).doubleValue();
    page.mouse().click(x + width / 2, y + height / 2);
  }

  private Path findBundledChrome() throws Exception {
    Path browsers = Path.of("build", "bundled-browsers");
    if (!Files.isDirectory(browsers)) {
      return null;
    }
    try (Stream<Path> paths = Files.walk(browsers)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> "chrome.exe".equalsIgnoreCase(path.getFileName().toString()))
          .findFirst()
          .orElse(null);
    }
  }

  private String readResource(String path) throws Exception {
    try (InputStream input = SaasOfflineFlowTest.class.getResourceAsStream(path)) {
      assertNotNull(input, "未找到测试资源：" + path);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
