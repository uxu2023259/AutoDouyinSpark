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
  private boolean legacyUnattributedSentToday;
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
    legacyUnattributedSentToday = false;
    return true;
  }

  public boolean hasAttempted(String target) {
    normalize();
    return dailyTargets.containsKey(TargetNames.key(target));
  }

  public void beginAttempt(String target, LocalDateTime now) {
    normalize();
    dailyTargets.put(TargetNames.key(target), new DailyTargetResult(target, "PROCESSING", "正在处理", now));
  }

  public void recordResult(String target, String resultStatus, String detail, LocalDateTime now) {
    normalize();
    dailyTargets.put(TargetNames.key(target), new DailyTargetResult(target, resultStatus, detail, now));
  }

  public int confirmedCount(List<String> targets) {
    normalize();
    return (int) targets.stream()
        .map(target -> dailyTargets.get(TargetNames.key(target)))
        .filter(item -> item != null && ("SENT".equals(item.getStatus()) || "MIGRATED_SENT".equals(item.getStatus())))
        .count();
  }

  public boolean hasAttemptedAll(List<String> targets) {
    normalize();
    return targets.stream().allMatch(this::hasAttempted);
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

  public boolean isLegacyUnattributedSentToday() {
    return legacyUnattributedSentToday;
  }

  public void setLegacyUnattributedSentToday(boolean legacyUnattributedSentToday) {
    this.legacyUnattributedSentToday = legacyUnattributedSentToday;
  }

  public String getBlockedReason() {
    return blockedReason;
  }

  public void setBlockedReason(String blockedReason) {
    this.blockedReason = blockedReason == null ? "" : blockedReason;
  }
}
