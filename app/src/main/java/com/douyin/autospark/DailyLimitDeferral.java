package awa.uxu.douyin.autospark;

import java.time.LocalDateTime;

final class DailyLimitDeferral {
  static final String OUTCOME = "DEFERRED_DAILY_LIMIT";
  static final String ERROR_CODE = "DAILY_LIMIT_REACHED";

  private DailyLimitDeferral() {
  }

  static PendingRetry create(
      AppConfig config, ConversationTarget conversation, String messageDigest, LocalDateTime now) {
    PendingRetry retry = new PendingRetry();
    retry.setId("目标:" + conversation.stateKey());
    retry.setStateKey(conversation.stateKey());
    retry.setConversation(conversation);
    retry.setQuery(conversation.getQuery());
    retry.setMessageText(config.getMessageText());
    retry.setMessageDigest(messageDigest);
    retry.setOutcome(OUTCOME);
    retry.setErrorCode(ERROR_CODE);
    retry.setRetriesCompleted(0);
    retry.setMaxRetries(config.getRetryCount());
    retry.setAttemptedAt(now);
    retry.setDueAt(nextAllowanceAt(now));
    return retry;
  }

  static boolean isDeferred(PendingRetry retry) {
    return retry != null && OUTCOME.equals(retry.getOutcome());
  }

  static LocalDateTime postpone(PendingRetry retry, LocalDateTime now) {
    LocalDateTime dueAt = nextAllowanceAt(now);
    retry.setDueAt(dueAt);
    return dueAt;
  }

  static LocalDateTime nextAllowanceAt(LocalDateTime now) {
    return now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(1);
  }
}
