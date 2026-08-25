package com.douyin.autospark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationDiscoveryResult {
  private boolean ok;
  private String errorCode = "";
  private String reason = "";
  private List<ConversationTarget> conversations = new ArrayList<>();

  public static ConversationDiscoveryResult failed(String code, String reason) {
    ConversationDiscoveryResult result = new ConversationDiscoveryResult();
    result.ok = false;
    result.errorCode = code;
    result.reason = reason;
    return result;
  }

  public boolean isOk() {
    return ok;
  }

  public void setOk(boolean ok) {
    this.ok = ok;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode == null ? "" : errorCode;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason == null ? "" : reason;
  }

  public List<ConversationTarget> getConversations() {
    return conversations;
  }

  public void setConversations(List<ConversationTarget> conversations) {
    this.conversations = conversations == null ? new ArrayList<>() : conversations;
  }
}
