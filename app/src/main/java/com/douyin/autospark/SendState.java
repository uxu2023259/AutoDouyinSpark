package awa.uxu.douyin.autospark;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class SendState {
  private Map<String, Long> perTarget = new LinkedHashMap<>();
  private Map<String, Boolean> fixedRuns = new LinkedHashMap<>();
  private LocalDate sentDate = LocalDate.now();
  private int sentToday = 0;
  private LocalDateTime nextIntervalRunAt;
  private List<PendingTrigger> pendingTriggers = new ArrayList<>();
  private List<PendingRetry> pendingRetries = new ArrayList<>();
  private String blockedReason = "";

  public static SendState defaults() {
    return new SendState();
  }

  public void normalize() {
    if (perTarget == null) {
      perTarget = new LinkedHashMap<>();
    }
    if (fixedRuns == null) {
      fixedRuns = new LinkedHashMap<>();
    }
    if (pendingTriggers == null) {
      pendingTriggers = new ArrayList<>();
    }
    if (pendingRetries == null) {
      pendingRetries = new ArrayList<>();
    }
    if (blockedReason == null) {
      blockedReason = "";
    }
    if (sentDate == null) {
      sentDate = LocalDate.now();
    }
    if (!sentDate.equals(LocalDate.now())) {
      sentDate = LocalDate.now();
      sentToday = 0;
    }
    LocalDateTime retentionStart = LocalDateTime.now().minusDays(8);
    perTarget.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() < retentionStart.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
    String oldestFixedRun = LocalDate.now().minusDays(8).toString();
    fixedRuns.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().compareTo(oldestFixedRun) < 0);
    pendingTriggers.removeIf(item -> item == null || item.getDueAt() == null || item.getId().isBlank());
    pendingRetries.removeIf(item -> item == null || item.getDueAt() == null || item.getId().isBlank());
  }

  public Map<String, Long> getPerTarget() {
    return perTarget;
  }

  public void setPerTarget(Map<String, Long> perTarget) {
    this.perTarget = perTarget;
  }

  public Map<String, Boolean> getFixedRuns() {
    return fixedRuns;
  }

  public void setFixedRuns(Map<String, Boolean> fixedRuns) {
    this.fixedRuns = fixedRuns;
  }

  public LocalDate getSentDate() {
    return sentDate;
  }

  public void setSentDate(LocalDate sentDate) {
    this.sentDate = sentDate;
  }

  public int getSentToday() {
    return sentToday;
  }

  public void setSentToday(int sentToday) {
    this.sentToday = sentToday;
  }

  public LocalDateTime getNextIntervalRunAt() {
    return nextIntervalRunAt;
  }

  public void setNextIntervalRunAt(LocalDateTime nextIntervalRunAt) {
    this.nextIntervalRunAt = nextIntervalRunAt;
  }

  public List<PendingTrigger> getPendingTriggers() {
    return pendingTriggers;
  }

  public void setPendingTriggers(List<PendingTrigger> pendingTriggers) {
    this.pendingTriggers = pendingTriggers;
  }

  public List<PendingRetry> getPendingRetries() {
    return pendingRetries;
  }

  public void setPendingRetries(List<PendingRetry> pendingRetries) {
    this.pendingRetries = pendingRetries;
  }

  public String getBlockedReason() {
    return blockedReason;
  }

  public void setBlockedReason(String blockedReason) {
    this.blockedReason = blockedReason == null ? "" : blockedReason;
  }
}
