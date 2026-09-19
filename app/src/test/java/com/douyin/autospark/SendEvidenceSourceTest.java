package awa.uxu.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SendEvidenceSourceTest {
  @Test
  void successRequiresNewDeliverableOutgoingBubble() throws Exception {
    String script = Files.readString(
        Path.of("app/src/main/resources/content-automation.js"), StandardCharsets.UTF_8);

    assertTrue(script.contains("[class*='box-item-'][class*='is-me-']"));
    assertTrue(script.contains("normalize(node.textContent) === expected"));
    assertTrue(script.contains("newDeliverableFingerprints"));
    assertTrue(script.contains("deliverableCountIncreased"));
    assertTrue(script.contains("messageIsPending"));
    assertTrue(script.contains("messageHasExplicitFailure"));
    assertTrue(script.contains("outcome: \"UNCERTAIN\""));
    assertFalse(script.contains("data-autospark-human-editor"));
    assertFalse(script.contains("data-autospark-human-send"));
  }
}
