package awa.uxu.douyin.autospark;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class DailySendPlanner {
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
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
        || state.isLegacyUnattributedSentToday()
        || now.toLocalTime().isBefore(LocalTime.parse(config.getDailyTime(), TIME_FORMAT))) {
      return List.of();
    }
    return config.getTargets().stream().filter(target -> !state.hasAttempted(target)).toList();
  }

  public String nextRunDescription(AppConfig config, SendState state) {
    LocalDateTime now = now();
    if (config.getTargets().isEmpty()) {
      return "名单为空";
    }
    if (state.getDailyDate().equals(now.toLocalDate()) && state.hasAttemptedAll(config.getTargets())) {
      return "今日名单已处理";
    }
    LocalTime sendTime = LocalTime.parse(config.getDailyTime(), TIME_FORMAT);
    if (now.toLocalTime().isBefore(sendTime)) {
      return "今日 " + config.getDailyTime();
    }
    return "待处理";
  }
}
