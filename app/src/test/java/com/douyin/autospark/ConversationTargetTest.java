package awa.uxu.douyin.autospark;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationTargetTest {
  @Test
  void carriesTheExactNameAndIdentityUsedToConfirmTheSelectedConversation() throws Exception {
    String json = """
        {"query":"张三","name":"张三","identity":"conversation-1"}
        """;

    ConversationTarget target = new ObjectMapper().readValue(json, ConversationTarget.class);

    assertEquals("张三", target.getQuery());
    assertEquals("张三", target.displayName());
    assertEquals("conversation-1", target.getIdentity());
  }
}
