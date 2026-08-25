package com.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HumanInteractionSourceTest {
  @Test
  void playwrightOwnsMouseKeyboardAndWheelActions() throws Exception {
    String source = Files.readString(
        Path.of("app/src/main/java/com/douyin/autospark/ChatAutomator.java"), StandardCharsets.UTF_8);

    assertTrue(source.contains("current.mouse().move"));
    assertTrue(source.contains("current.mouse().down"));
    assertTrue(source.contains("current.mouse().wheel"));
    assertTrue(source.contains("current.keyboard().insertText"));
    assertTrue(source.contains("humanType"));
    assertTrue(source.contains("current.onResponse(responseObserver)"));
    assertTrue(source.contains("result.setApiAcknowledged"));
    assertTrue(source.contains("prepared.getActionBounds()"));
    assertTrue(source.contains("validated.getSendButtonBounds()"));
    assertTrue(source.contains("input.put(\"clickedAtEpochMillis\", (double) clickedAtEpochMillis)"));
    assertTrue(source.contains("retryEvidence(retry)"));
    assertTrue(source.contains("首次点击后未确认切换"));
    assertFalse(source.contains("mapper.convertValue(retry, Map.class)"));
    assertFalse(source.contains("data-autospark-human-target"));
    assertFalse(source.contains("--restore-last-session"));
  }
}
