package awa.uxu.douyin.autospark;

import java.time.LocalDateTime;

public class PendingTrigger {
  private String id = "";
  private String type = "";
  private String slotKey = "";
  private LocalDateTime dueAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id == null ? "" : id; }
  public String getType() { return type; }
  public void setType(String type) { this.type = type == null ? "" : type; }
  public String getSlotKey() { return slotKey; }
  public void setSlotKey(String slotKey) { this.slotKey = slotKey == null ? "" : slotKey; }
  public LocalDateTime getDueAt() { return dueAt; }
  public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }
}
