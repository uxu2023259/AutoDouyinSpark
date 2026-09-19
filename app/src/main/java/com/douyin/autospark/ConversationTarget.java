package awa.uxu.douyin.autospark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationTarget {
  private String query = "";
  private String name = "";
  private String identity = "";
  private int occurrence;
  private boolean duplicateName;

  public String stateKey() {
    if (!identity.isBlank()) {
      return "会话标识:" + identity;
    }
    return "会话名称:" + name + "#" + Math.max(0, occurrence);
  }

  public String displayName() {
    return name.isBlank() ? query : name;
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query == null ? "" : query;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name == null ? "" : name;
  }

  public String getIdentity() {
    return identity;
  }

  public void setIdentity(String identity) {
    this.identity = identity == null ? "" : identity;
  }

  public int getOccurrence() {
    return occurrence;
  }

  public void setOccurrence(int occurrence) {
    this.occurrence = Math.max(0, occurrence);
  }

  public boolean isDuplicateName() {
    return duplicateName;
  }

  public void setDuplicateName(boolean duplicateName) {
    this.duplicateName = duplicateName;
  }
}
