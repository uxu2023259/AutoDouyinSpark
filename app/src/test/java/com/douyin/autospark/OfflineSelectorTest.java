package awa.uxu.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineSelectorTest {
  @Test
  void savedDouyinPageContainsSelectorsUsedByAutomation() throws Exception {
    try (InputStream snapshot = OfflineSelectorTest.class.getResourceAsStream("/offline-chat.html")) {
      assertNotNull(snapshot, "未找到脱敏离线页面测试资源");
      String html = new String(snapshot.readAllBytes(), StandardCharsets.UTF_8);

      assertTrue(html.contains("ReactVirtualized__Grid"));
      assertTrue(html.contains("role=\"gridcell\""));
      assertTrue(html.contains("role=\"list-item\""));
      assertTrue(html.contains("item-header-name"));
      assertTrue(html.contains("box-header-name"));
      assertTrue(html.contains("contenteditable=\"true\""));
      assertTrue(html.contains("semi-button-content\">发送</span>"));
      assertTrue(html.contains("is-me-"));
      assertTrue(html.contains("text-item-message-"));
    }
  }
}
