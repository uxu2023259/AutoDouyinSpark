package com.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SendPolicyTest {
  @Test
  void cooldownPreventsRepeatedSend() {
    AppConfig config = new AppConfig();
    config.setCooldownMinutes(60);
    SendState state = new SendState();
    SendPolicy policy = new SendPolicy();
    LocalDateTime now = LocalDateTime.now();

    policy.markSent(state, "张三", now.minusMinutes(30));

    String reason = policy.cooldownReason(state, config, "张三", now);
    assertTrue(reason.contains("仍在冷却中"));
  }

  @Test
  void dailyLimitBlocksWhenReached() {
    AppConfig config = new AppConfig();
    config.setDailyLimit(2);
    SendState state = new SendState();
    state.setSentToday(2);

    assertTrue(new SendPolicy().dailyLimitReached(state, config));
  }

  @Test
  void markSentIncreasesCounter() {
    SendState state = new SendState();
    new SendPolicy().markSent(state, "李四", LocalDateTime.now());

    assertEquals(1, state.getSentToday());
    assertFalse(state.getPerTarget().isEmpty());
  }
}
