package awa.uxu.douyin.autospark;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConversationTargetTest {
  @Test
  void platformIdentityKeepsSameNameConversationsIndependent() {
    ConversationTarget first = conversation("同名好友", "会话-1", 0);
    ConversationTarget second = conversation("同名好友", "会话-2", 1);

    assertNotEquals(first.stateKey(), second.stateKey());
  }

  @Test
  void occurrenceKeepsFallbackConversationsIndependent() {
    ConversationTarget first = conversation("同名好友", "", 0);
    ConversationTarget second = conversation("同名好友", "", 1);

    assertEquals("会话名称:同名好友#0", first.stateKey());
    assertEquals("会话名称:同名好友#1", second.stateKey());
  }

  @Test
  void discoveryResultAcceptsPageProbeFieldsAndMultipleConversations() throws Exception {
    String json = """
        {
          "ok": true,
          "reason": "已匹配到 2 个会话",
          "selectorTrace": ["会话列表"],
          "conversations": [
            {"query": "小明", "name": "小明一号", "identity": "会话-1", "occurrence": 0},
            {"query": "小明", "name": "小明二号", "identity": "会话-2", "occurrence": 0}
          ]
        }
        """;

    ConversationDiscoveryResult result = new ObjectMapper().readValue(json, ConversationDiscoveryResult.class);

    assertEquals(2, result.getConversations().size());
    assertNotEquals(result.getConversations().get(0).stateKey(), result.getConversations().get(1).stateKey());
  }

  private ConversationTarget conversation(String name, String identity, int occurrence) {
    ConversationTarget conversation = new ConversationTarget();
    conversation.setName(name);
    conversation.setIdentity(identity);
    conversation.setOccurrence(occurrence);
    return conversation;
  }
}
