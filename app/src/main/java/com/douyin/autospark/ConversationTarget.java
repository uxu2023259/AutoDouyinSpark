package awa.uxu.douyin.autospark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationTarget {
  private String query = "";
  private String name = "";
  private String identity = "";

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
}
