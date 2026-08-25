package com.douyin.autospark;

import java.util.ArrayList;
import java.util.List;

public class TaskResult {
  private boolean ok;
  private boolean success;
  private boolean skipped;
  private String target = "";
  private String phase = "";
  private String deliveryStatus = "";
  private String outcome = "";
  private String attemptId = "";
  private String errorCode = "";
  private String errorDetail = "";
  private String reason = "";
  private String currentChatTarget = "";
  private String selectedConversation = "";
  private String matchedConversationName = "";
  private List<String> selectorTrace = new ArrayList<>();
  private long clickedAtEpochMillis;
  private int baselineMessageCount;
  private int observedMessageCount;
  private List<String> baselineFingerprints = new ArrayList<>();
  private List<String> observedFingerprints = new ArrayList<>();
  private List<Long> baselineMessageTimestamps = new ArrayList<>();
  private boolean inputCleared;
  private boolean apiAcknowledged;
  private String securityPrompt = "";
  private ElementBounds actionBounds;
  private ElementBounds editorBounds;
  private ElementBounds sendButtonBounds;
  private boolean sendButtonDisabled;

  public static TaskResult skipped(String target, String reason) {
    TaskResult result = new TaskResult();
    result.ok = true;
    result.success = false;
    result.skipped = true;
    result.target = target;
    result.reason = reason;
    result.outcome = "SKIPPED";
    return result;
  }

  public static TaskResult failed(String target, String code, String reason) {
    TaskResult result = new TaskResult();
    result.ok = false;
    result.success = false;
    result.skipped = false;
    result.target = target;
    result.errorCode = code;
    result.errorDetail = reason;
    result.reason = reason;
    result.outcome = "RETRYABLE_FAILURE";
    return result;
  }

  public boolean isOk() {
    return ok;
  }

  public void setOk(boolean ok) {
    this.ok = ok;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public boolean isSkipped() {
    return skipped;
  }

  public void setSkipped(boolean skipped) {
    this.skipped = skipped;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target == null ? "" : target;
  }

  public String getPhase() {
    return phase;
  }

  public void setPhase(String phase) {
    this.phase = phase == null ? "" : phase;
  }

  public String getDeliveryStatus() {
    return deliveryStatus;
  }

  public void setDeliveryStatus(String deliveryStatus) {
    this.deliveryStatus = deliveryStatus == null ? "" : deliveryStatus;
  }

  public String getOutcome() { return outcome; }
  public void setOutcome(String outcome) { this.outcome = outcome == null ? "" : outcome; }
  public String getAttemptId() { return attemptId; }
  public void setAttemptId(String attemptId) { this.attemptId = attemptId == null ? "" : attemptId; }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode == null ? "" : errorCode;
  }

  public String getErrorDetail() {
    return errorDetail;
  }

  public void setErrorDetail(String errorDetail) {
    this.errorDetail = errorDetail == null ? "" : errorDetail;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason == null ? "" : reason;
  }

  public String getCurrentChatTarget() {
    return currentChatTarget;
  }

  public void setCurrentChatTarget(String currentChatTarget) {
    this.currentChatTarget = currentChatTarget == null ? "" : currentChatTarget;
  }

  public String getSelectedConversation() {
    return selectedConversation;
  }

  public void setSelectedConversation(String selectedConversation) {
    this.selectedConversation = selectedConversation == null ? "" : selectedConversation;
  }

  public String getMatchedConversationName() {
    return matchedConversationName;
  }

  public void setMatchedConversationName(String matchedConversationName) {
    this.matchedConversationName = matchedConversationName == null ? "" : matchedConversationName;
  }

  public List<String> getSelectorTrace() {
    return selectorTrace;
  }

  public void setSelectorTrace(List<String> selectorTrace) {
    this.selectorTrace = selectorTrace == null ? new ArrayList<>() : selectorTrace;
  }

  public long getClickedAtEpochMillis() { return clickedAtEpochMillis; }
  public void setClickedAtEpochMillis(long value) { clickedAtEpochMillis = Math.max(0, value); }
  public int getBaselineMessageCount() { return baselineMessageCount; }
  public void setBaselineMessageCount(int value) { baselineMessageCount = Math.max(0, value); }
  public int getObservedMessageCount() { return observedMessageCount; }
  public void setObservedMessageCount(int value) { observedMessageCount = Math.max(0, value); }
  public List<String> getBaselineFingerprints() { return baselineFingerprints; }
  public void setBaselineFingerprints(List<String> value) { baselineFingerprints = value == null ? new ArrayList<>() : value; }
  public List<String> getObservedFingerprints() { return observedFingerprints; }
  public void setObservedFingerprints(List<String> value) { observedFingerprints = value == null ? new ArrayList<>() : value; }
  public List<Long> getBaselineMessageTimestamps() { return baselineMessageTimestamps; }
  public void setBaselineMessageTimestamps(List<Long> value) { baselineMessageTimestamps = value == null ? new ArrayList<>() : value; }
  public boolean isInputCleared() { return inputCleared; }
  public void setInputCleared(boolean inputCleared) { this.inputCleared = inputCleared; }
  public boolean isApiAcknowledged() { return apiAcknowledged; }
  public void setApiAcknowledged(boolean apiAcknowledged) { this.apiAcknowledged = apiAcknowledged; }
  public String getSecurityPrompt() { return securityPrompt; }
  public void setSecurityPrompt(String value) { securityPrompt = value == null ? "" : value; }
  public ElementBounds getActionBounds() { return actionBounds; }
  public void setActionBounds(ElementBounds value) { actionBounds = value; }
  public ElementBounds getEditorBounds() { return editorBounds; }
  public void setEditorBounds(ElementBounds value) { editorBounds = value; }
  public ElementBounds getSendButtonBounds() { return sendButtonBounds; }
  public void setSendButtonBounds(ElementBounds value) { sendButtonBounds = value; }
  public boolean isSendButtonDisabled() { return sendButtonDisabled; }
  public void setSendButtonDisabled(boolean value) { sendButtonDisabled = value; }

  public boolean isConfirmed() {
    return isSuccess() && "confirmed".equals(deliveryStatus);
  }

  public boolean isUncertain() {
    return "UNCERTAIN".equals(outcome) || (isSuccess() && "uncertain".equals(deliveryStatus));
  }

  public boolean isBlocked() {
    return "BLOCKED".equals(outcome) || !securityPrompt.isBlank()
        || List.of("LOGIN_REQUIRED", "SECURITY_CHECK_REQUIRED").contains(errorCode);
  }
}
