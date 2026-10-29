package awa.uxu.douyin.autospark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SendState {
  private LocalDate dailyDate = LocalDate.now();
  private Map<String, DailyTargetResult> dailyTargets = new LinkedHashMap<>();
  private String blockedReason = "";

  public static SendState defaults() {
    return new SendState();
  }

  public void normalize() {
    if (dailyDate == null) {
      dailyDate = LocalDate.now();
    }
    if (dailyTargets == null) {
      dailyTargets = new LinkedHashMap<>();
    }
    dailyTargets.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null);
    if (blockedReason == null) {
      blockedReason = "";
    }
  }

  public boolean rollOverIfNeeded(LocalDate today) {
    normalize();
    if (dailyDate.equals(today)) {
      return false;
    }
    dailyDate = today;
    dailyTargets.clear();
    return true;
  }

  public boolean isConfirmed(String target) {
    normalize();
    DailyTargetResult result = dailyTargets.get(TargetNames.key(target));
    return result != null && "SENT".equals(result.getStatus());
  }

  public void beginAttempt(String target, LocalDateTime now) {
    normalize();
    String key = TargetNames.key(target);
    DailyTargetResult previous = dailyTargets.get(key);
    int attemptCount = previous == null ? 1 : previous.getAttemptCount() + 1;
    dailyTargets.put(key, new DailyTargetResult(target, "PROCESSING", "正在处理", now, attemptCount));
  }

  public void recordResult(String target, String resultStatus, String detail, LocalDateTime now) {
    normalize();
    String key = TargetNames.key(target);
    DailyTargetResult previous = dailyTargets.get(key);
    int attemptCount = previous == null ? 0 : previous.getAttemptCount();
    dailyTargets.put(key, new DailyTargetResult(target, resultStatus, detail, now, attemptCount));
  }

  public int confirmedCount(List<String> targets) {
    normalize();
    return (int) targets.stream()
        .map(target -> dailyTargets.get(TargetNames.key(target)))
        .filter(item -> item != null && "SENT".equals(item.getStatus()))
        .count();
  }

  public boolean hasConfirmedAll(List<String> targets) {
    normalize();
    return targets.stream().allMatch(this::isConfirmed);
  }

  public LocalDate getDailyDate() {
    return dailyDate;
  }

  public void setDailyDate(LocalDate dailyDate) {
    this.dailyDate = dailyDate;
  }

  public Map<String, DailyTargetResult> getDailyTargets() {
    return dailyTargets;
  }

  public void setDailyTargets(Map<String, DailyTargetResult> dailyTargets) {
    this.dailyTargets = dailyTargets;
  }

  public String getBlockedReason() {
    return blockedReason;
  }

  public void setBlockedReason(String blockedReason) {
    this.blockedReason = blockedReason == null ? "" : blockedReason;
  }
}
