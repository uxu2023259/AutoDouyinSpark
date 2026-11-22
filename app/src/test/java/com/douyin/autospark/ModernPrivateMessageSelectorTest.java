package awa.uxu.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModernPrivateMessageSelectorTest {
  @Test
  void supportsTheProductionImSaasSelectorsWhileKeepingLegacySelectors() throws Exception {
    String script = readResource("/content-automation.js");
    String snapshot = readResource("/offline-chat-saas.html");

    assertTrue(script.contains("[data-e2e='conversation-item']"));
    assertTrue(script.contains(".conversationConversationListwrapper"));
    assertTrue(script.contains(".conversationConversationItemtitle"));
    assertTrue(script.contains("conversationconversationitemcurconversation"));
    assertTrue(script.contains(".RightPanelHeadertitle"));
    assertTrue(script.contains("[data-e2e='msg-input'] [contenteditable='true'][data-slate-editor='true']"));
    assertTrue(script.contains("messageMsgInputpublishBtn"));
    assertTrue(script.contains("messageMsgInputpublishRedBtn"));
    assertTrue(script.contains("messageMessageBoxisFromMe"));
    assertTrue(script.contains(".MessageItemTextbubbleTextContent"));
    assertTrue(script.contains(".ContentSideSendStatussending"));
    assertTrue(script.contains(".ContentSideSendStatusretry"));

    assertTrue(script.contains("ReactVirtualized__Grid"));
    assertTrue(script.contains("[class*='box-item-'][class*='is-me-']"));
    assertTrue(script.contains("[class*='chat-editor-'] button"));

    assertTrue(snapshot.contains("data-e2e=\"conversation-item\""));
    assertTrue(snapshot.contains("conversationConversationItemcurConversation"));
    assertTrue(snapshot.contains("data-slate-editor=\"true\""));
    assertTrue(snapshot.contains("messageMsgInputpublishRedBtn"));
    assertTrue(snapshot.contains("messageMessageBoxisFromMe"));
  }

  private String readResource(String path) throws Exception {
    try (InputStream input = ModernPrivateMessageSelectorTest.class.getResourceAsStream(path)) {
      assertNotNull(input, "未找到测试资源：" + path);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
