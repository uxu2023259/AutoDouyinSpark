package com.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationSafetyTest {
  @Test
  void automationSendsEveryMatchedConversationAndUsesSingleSendAction() throws Exception {
    try (var input = AutomationSafetyTest.class.getResourceAsStream("/content-automation.js")) {
      assertTrue(input != null, "自动化脚本资源必须存在");
      String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(script.contains("discoverTargetConversations"));
      assertTrue(script.contains("conversations"));
      assertTrue(script.contains("duplicateName"));
      assertFalse(script.contains("TARGET_AMBIGUOUS"));
      assertTrue(script.contains("CONVERSATION_SWITCH_UNCONFIRMED"));
      assertFalse(script.contains("pickSelected"));
      assertFalse(script.contains("sendButton.click();"));
      assertFalse(script.contains("dispatchMouseSequence(sendButton)"));
      assertFalse(script.contains("send-button:fallback-footer"));
      assertTrue(script.contains("PAGE_READY_TIMEOUT = 45000"));
      assertTrue(script.contains("PAGE_NOT_READY"));
      assertFalse(script.contains("page.reload"));
      assertTrue(script.contains("prepare-human-target"));
      assertTrue(script.contains("verify-human-send"));
      assertTrue(script.contains("exactOutgoingMessageElements"));
      assertTrue(script.contains("conversation-items:actual-gridcell"));
      assertTrue(script.contains("exactMatches: mapped.filter"));
      assertTrue(script.contains("containsMatches: mapped.filter"));
      assertTrue(script.contains("const matches = [...exactMatches.values(), ...containsMatches.values()]"));
      assertFalse(script.contains("const matches = exactMatches.size ?"));
      assertTrue(script.contains("[class*='box-item-'][class*='is-me-']"));
      assertTrue(script.contains("[class*='text-item-message-']"));
      assertTrue(script.contains("messageIsPending"));
      assertTrue(script.contains("messageHasExplicitFailure"));
      assertTrue(script.contains("status.querySelector(\"[class*='sending-']\")"));
      assertTrue(script.contains("SECURITY_CHECK_REQUIRED"));
      assertTrue(script.contains("旧版脚本发送入口已停用"));
    }
  }
}
