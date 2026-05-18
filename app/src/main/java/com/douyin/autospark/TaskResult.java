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
  private String errorCode = "";
  private String errorDetail = "";
  private String reason = "";
  private String currentChatTarget = "";
  private String selectedConversation = "";
  private String matchedConversationName = "";
  private List<String> selectorTrace = new ArrayList<>();

  public static TaskResult skipped(String target, String reason) {
    TaskResult result = new TaskResult();
    result.ok = true;
    result.success = false;
    result.skipped = true;
    result.target = target;
    result.reason = reason;
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
}
