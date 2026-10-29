package awa.uxu.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserInteractionSourceTest {
  @Test
  void usesDirectBrowserActionsAndVerifiesTheMessageBeforeRecordingSuccess() throws Exception {
    String source = Files.readString(
        Path.of("app/src/main/java/com/douyin/autospark/ChatAutomator.java"), StandardCharsets.UTF_8);
    String script = Files.readString(
        Path.of("app/src/main/resources/content-automation.js"), StandardCharsets.UTF_8);

    assertTrue(source.contains("current.mouse().click"));
    assertTrue(source.contains("current.keyboard().insertText(text)"));
    assertTrue(source.contains("current.onResponse(responseObserver)"));
    assertTrue(source.contains("result.setApiAcknowledged"));
    assertTrue(source.contains("prepared.getActionBounds()"));
    assertTrue(source.contains("validated.getSendButtonBounds()"));
    assertTrue(source.contains("input.put(\"clickedAtEpochMillis\", (double) clickedAtEpochMillis)"));
    assertTrue(script.contains("verify-message-send"));
    assertTrue(script.contains("exactOutgoingMessageElements"));
    assertFalse(source.contains("humanizationPreset"));
    assertFalse(source.contains("randomBetween"));
    assertFalse(source.contains("AutomationControlled"));
    assertFalse(source.contains("--disable-background-timer-throttling"));
    assertFalse(source.contains("--disable-backgrounding-occluded-windows"));
    assertFalse(source.contains("--disable-renderer-backgrounding"));
    assertTrue(source.contains("closeAfterAutomaticCycle()"));
    assertTrue(source.contains("interactiveBrowserRequested = true"));
    assertTrue(source.contains("if (hasOpenInteractiveBrowser())"));
    assertTrue(source.indexOf("persistLoginSessionCookies(false)") < source.indexOf("context.close();"));
    assertFalse(script.contains("dispatchMouseSequence"));
    assertFalse(script.contains("runChatSend"));
    assertFalse(script.contains("reconcile-human-message"));
    assertFalse(script.contains("data-autospark-human-target"));
    assertFalse(script.contains("--restore-last-session"));
  }
}
