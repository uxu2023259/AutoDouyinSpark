package com.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyLimitDeferralTest {
  @Test
  void createsPersistentContinuationForNextDayWithoutConsumingFailureRetry() {
    AppConfig config = AppConfig.defaults();
    config.setMessageText("续火花");
    config.setRetryCount(2);
    ConversationTarget conversation = new ConversationTarget();
    conversation.setQuery("好友");
    conversation.setName("好友一号");
    conversation.setIdentity("会话-1");
    LocalDateTime now = LocalDateTime.of(2026, 8, 29, 10, 30);

    PendingRetry retry = DailyLimitDeferral.create(config, conversation, "摘要", now);

    assertTrue(DailyLimitDeferral.isDeferred(retry));
    assertEquals("目标:会话标识:会话-1", retry.getId());
    assertEquals(0, retry.getRetriesCompleted());
    assertEquals(LocalDateTime.of(2026, 8, 30, 0, 1), retry.getDueAt());
  }

  @Test
  void postponesAgainWhenNextDayCapacityIsStillExhausted() {
    PendingRetry retry = new PendingRetry();
    retry.setOutcome(DailyLimitDeferral.OUTCOME);

    LocalDateTime dueAt = DailyLimitDeferral.postpone(retry, LocalDateTime.of(2026, 8, 30, 23, 50));

    assertEquals(LocalDateTime.of(2026, 8, 31, 0, 1), dueAt);
    assertEquals(dueAt, retry.getDueAt());
  }
}
