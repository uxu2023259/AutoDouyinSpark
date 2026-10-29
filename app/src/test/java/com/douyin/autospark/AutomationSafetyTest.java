package awa.uxu.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationSafetyTest {
  @Test
  void selectsOnlyOneExactNameAndDoesNotSendWhenThatNameIsAmbiguous() throws Exception {
    try (var input = AutomationSafetyTest.class.getResourceAsStream("/content-automation.js")) {
      assertTrue(input != null, "自动化脚本资源必须存在");
      String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(script.contains("discoverTargetConversations"));
      assertTrue(script.contains("TARGET_NOT_FOUND"));
      assertTrue(script.contains("TARGET_AMBIGUOUS"));
      assertTrue(script.contains("normalizeForMatch(item.name) === normalizedTarget"));
      assertTrue(script.contains("scan.matches.length > 1"));
      assertFalse(script.contains("containsMatches"));
      assertFalse(script.contains("normalizedName.includes(normalizedTarget)"));
      assertFalse(script.contains("duplicateName"));
      assertTrue(script.contains("CONVERSATION_SWITCH_UNCONFIRMED"));
      assertFalse(script.contains("pickSelected"));
      assertFalse(script.contains("sendButton.click();"));
      assertFalse(script.contains("dispatchMouseSequence(sendButton)"));
      assertFalse(script.contains("send-button:fallback-footer"));
      assertTrue(script.contains("PAGE_READY_TIMEOUT = 45000"));
      assertTrue(script.contains("PAGE_NOT_READY"));
      assertFalse(script.contains("page.reload"));
      assertTrue(script.contains("prepare-target"));
      assertTrue(script.contains("verify-message-send"));
      assertTrue(script.contains("exactOutgoingMessageElements"));
      assertTrue(script.contains("conversation-items:actual-gridcell"));
      assertTrue(script.contains("[class*='box-item-'][class*='is-me-']"));
      assertTrue(script.contains("[class*='text-item-message-']"));
      assertTrue(script.contains("messageIsPending"));
      assertTrue(script.contains("messageHasExplicitFailure"));
      assertTrue(script.contains("status.querySelector(\"[class*='sending-']\")"));
      assertTrue(script.contains("SECURITY_CHECK_REQUIRED"));
      assertFalse(script.contains("reconcile-human-message"));
    }
  }
}
