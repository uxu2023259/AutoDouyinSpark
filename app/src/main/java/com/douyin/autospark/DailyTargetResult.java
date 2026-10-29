package awa.uxu.douyin.autospark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DailyTargetResult {
  private String target = "";
  private String status = "";
  private String detail = "";
  private LocalDateTime attemptedAt;

  public DailyTargetResult() {}

  public DailyTargetResult(String target, String status, String detail, LocalDateTime attemptedAt) {
    this.target = TargetNames.displayName(target);
    this.status = status == null ? "" : status;
    this.detail = detail == null ? "" : detail;
    this.attemptedAt = attemptedAt;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = TargetNames.displayName(target);
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status == null ? "" : status;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail == null ? "" : detail;
  }

  public LocalDateTime getAttemptedAt() {
    return attemptedAt;
  }

  public void setAttemptedAt(LocalDateTime attemptedAt) {
    this.attemptedAt = attemptedAt;
  }
}
