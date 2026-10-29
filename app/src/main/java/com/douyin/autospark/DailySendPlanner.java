package awa.uxu.douyin.autospark;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class DailySendPlanner {
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
  private static final DateTimeFormatter RETRY_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
  private static final long MAX_RETRY_MINUTES = 60;
  private final Clock clock;

  public DailySendPlanner(Clock clock) {
    this.clock = clock;
  }

  public LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  public List<String> pendingTargets(AppConfig config, SendState state) {
    LocalDateTime now = now();
    state.rollOverIfNeeded(now.toLocalDate());
    if (!config.isEnabled()
        || config.getTargets().isEmpty()
        || now.toLocalTime().isBefore(LocalTime.parse(config.getDailyTime(), TIME_FORMAT))) {
      return List.of();
    }
    return config.getTargets().stream()
        .filter(target -> !state.isConfirmed(target))
        .filter(target -> !now.isBefore(nextAttemptAt(target, state)))
        .toList();
  }

  public String nextRunDescription(AppConfig config, SendState state) {
    LocalDateTime now = now();
    if (config.getTargets().isEmpty()) {
      return "名单为空";
    }
    if (state.getDailyDate().equals(now.toLocalDate()) && state.hasConfirmedAll(config.getTargets())) {
      return "今日名单全部确认";
    }
    LocalTime sendTime = LocalTime.parse(config.getDailyTime(), TIME_FORMAT);
    if (now.toLocalTime().isBefore(sendTime)) {
      return "今日 " + config.getDailyTime();
    }
    List<LocalDateTime> retryTimes = config.getTargets().stream()
        .filter(target -> !state.isConfirmed(target))
        .map(target -> nextAttemptAt(target, state))
        .toList();
    LocalDateTime nextRetry = retryTimes.stream().min(LocalDateTime::compareTo).orElse(now);
    return now.isBefore(nextRetry) ? "重试于 " + nextRetry.format(RETRY_TIME_FORMAT) : "待处理/重试";
  }

  public LocalDateTime nextAttemptAt(String target, SendState state) {
    DailyTargetResult previous = state.getDailyTargets().get(TargetNames.key(target));
    if (previous == null || previous.getAttemptedAt() == null) {
      return now();
    }
    return previous.getAttemptedAt().plus(retryDelay(previous.getAttemptCount()));
  }

  static Duration retryDelay(int attemptCount) {
    if (attemptCount <= 0) {
      return Duration.ZERO;
    }
    int exponent = Math.min(attemptCount - 1, 6);
    long minutes = Math.min(MAX_RETRY_MINUTES, 1L << exponent);
    return Duration.ofMinutes(minutes);
  }
}
