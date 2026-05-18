package com.douyin.autospark;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class SendState {
  private Map<String, Long> perTarget = new LinkedHashMap<>();
  private Map<String, Boolean> fixedRuns = new LinkedHashMap<>();
  private LocalDate sentDate = LocalDate.now();
  private int sentToday = 0;
  private LocalDateTime nextIntervalRunAt;

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
    if (sentDate == null) {
      sentDate = LocalDate.now();
    }
    if (!sentDate.equals(LocalDate.now())) {
      sentDate = LocalDate.now();
      sentToday = 0;
    }
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
}
