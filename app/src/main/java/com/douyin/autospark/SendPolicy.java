package awa.uxu.douyin.autospark;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class SendPolicy {
  public boolean dailyLimitReached(SendState state, AppConfig config) {
    state.normalize();
    return state.getSentToday() >= config.getDailyLimit();
  }

  public String cooldownReason(SendState state, AppConfig config, String target, LocalDateTime now) {
    return cooldownReason(state, config, target, target, now);
  }

  public String cooldownReason(SendState state, AppConfig config, String stateKey, String displayName, LocalDateTime now) {
    state.normalize();
    if (config.getCooldownMinutes() <= 0) {
      return "";
    }
    Long lastSentAt = state.getPerTarget().get(stateKey);
    if (lastSentAt == null && !stateKey.equals(displayName)) {
      lastSentAt = state.getPerTarget().get(displayName);
    }
    if (lastSentAt == null || lastSentAt <= 0) {
      return "";
    }
    long nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    long elapsedMinutes = Duration.ofMillis(Math.max(0, nowMillis - lastSentAt)).toMinutes();
    if (elapsedMinutes >= config.getCooldownMinutes()) {
      return "";
    }
    long remaining = config.getCooldownMinutes() - elapsedMinutes;
    return displayName + " 仍在冷却中，剩余约 " + Math.max(1, remaining) + " 分钟";
  }

  public void markSent(SendState state, String target, LocalDateTime now) {
    state.normalize();
    long epochMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    state.getPerTarget().put(target, epochMillis);
    state.setSentToday(state.getSentToday() + 1);
  }
}
