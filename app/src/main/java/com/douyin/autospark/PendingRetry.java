package com.douyin.autospark;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PendingRetry {
  private String id = "";
  private String stateKey = "";
  private ConversationTarget conversation;
  private String query = "";
  private String messageText = "";
  private String messageDigest = "";
  private String outcome = "FAILED";
  private String errorCode = "";
  private int retriesCompleted;
  private int maxRetries = 1;
  private LocalDateTime attemptedAt;
  private LocalDateTime dueAt;
  private long clickedAtEpochMillis;
  private int baselineMessageCount;
  private List<String> baselineFingerprints = new ArrayList<>();
  private List<Long> baselineMessageTimestamps = new ArrayList<>();

  public String getId() { return id; }
  public void setId(String id) { this.id = id == null ? "" : id; }
  public String getStateKey() { return stateKey; }
  public void setStateKey(String stateKey) { this.stateKey = stateKey == null ? "" : stateKey; }
  public ConversationTarget getConversation() { return conversation; }
  public void setConversation(ConversationTarget conversation) { this.conversation = conversation; }
  public String getQuery() { return query; }
  public void setQuery(String query) { this.query = query == null ? "" : query; }
  public String getMessageText() { return messageText; }
  public void setMessageText(String messageText) { this.messageText = messageText == null ? "" : messageText; }
  public String getMessageDigest() { return messageDigest; }
  public void setMessageDigest(String messageDigest) { this.messageDigest = messageDigest == null ? "" : messageDigest; }
  public String getOutcome() { return outcome; }
  public void setOutcome(String outcome) { this.outcome = outcome == null ? "FAILED" : outcome; }
  public String getErrorCode() { return errorCode; }
  public void setErrorCode(String errorCode) { this.errorCode = errorCode == null ? "" : errorCode; }
  public int getRetriesCompleted() { return retriesCompleted; }
  public void setRetriesCompleted(int retriesCompleted) { this.retriesCompleted = Math.max(0, retriesCompleted); }
  public int getMaxRetries() { return maxRetries; }
  public void setMaxRetries(int maxRetries) { this.maxRetries = Math.max(0, maxRetries); }
  public LocalDateTime getAttemptedAt() { return attemptedAt; }
  public void setAttemptedAt(LocalDateTime attemptedAt) { this.attemptedAt = attemptedAt; }
  public LocalDateTime getDueAt() { return dueAt; }
  public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }
  public long getClickedAtEpochMillis() { return clickedAtEpochMillis; }
  public void setClickedAtEpochMillis(long value) { clickedAtEpochMillis = Math.max(0, value); }
  public int getBaselineMessageCount() { return baselineMessageCount; }
  public void setBaselineMessageCount(int baselineMessageCount) { this.baselineMessageCount = Math.max(0, baselineMessageCount); }
  public List<String> getBaselineFingerprints() { return baselineFingerprints; }
  public void setBaselineFingerprints(List<String> value) { baselineFingerprints = value == null ? new ArrayList<>() : value; }
  public List<Long> getBaselineMessageTimestamps() { return baselineMessageTimestamps; }
  public void setBaselineMessageTimestamps(List<Long> value) { baselineMessageTimestamps = value == null ? new ArrayList<>() : value; }
  @JsonIgnore
  public boolean isDiscoveryRetry() { return conversation == null; }
}
