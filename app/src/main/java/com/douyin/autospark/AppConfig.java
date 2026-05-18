package com.douyin.autospark;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {
  private boolean enabled = true;
  private int intervalMinutes = 30;
  private String fixedTime = "16:00";
  private List<String> targets = new ArrayList<>();
  private String messageText = "续火花啦，记得回我一下~";
  private int dailyLimit = 20;
  private int cooldownMinutes = 60;
  private int retryCount = 2;
  private boolean startWithWindows = true;

  public static AppConfig defaults() {
    return new AppConfig();
  }

  public void normalize() {
    intervalMinutes = Math.max(1, intervalMinutes);
    dailyLimit = Math.max(1, dailyLimit);
    cooldownMinutes = Math.max(0, cooldownMinutes);
    retryCount = Math.max(0, retryCount);
    if (fixedTime == null) {
      fixedTime = "";
    }
    if (messageText == null || messageText.isBlank()) {
      messageText = "续火花啦，记得回我一下~";
    }
    if (targets == null) {
      targets = new ArrayList<>();
    }
    targets = targets.stream()
        .filter(item -> item != null && !item.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getIntervalMinutes() {
    return intervalMinutes;
  }

  public void setIntervalMinutes(int intervalMinutes) {
    this.intervalMinutes = intervalMinutes;
  }

  public String getFixedTime() {
    return fixedTime;
  }

  public void setFixedTime(String fixedTime) {
    this.fixedTime = fixedTime;
  }

  public List<String> getTargets() {
    return targets;
  }

  public void setTargets(List<String> targets) {
    this.targets = targets;
  }

  public String getMessageText() {
    return messageText;
  }

  public void setMessageText(String messageText) {
    this.messageText = messageText;
  }

  public int getDailyLimit() {
    return dailyLimit;
  }

  public void setDailyLimit(int dailyLimit) {
    this.dailyLimit = dailyLimit;
  }

  public int getCooldownMinutes() {
    return cooldownMinutes;
  }

  public void setCooldownMinutes(int cooldownMinutes) {
    this.cooldownMinutes = cooldownMinutes;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(int retryCount) {
    this.retryCount = retryCount;
  }

  public boolean isStartWithWindows() {
    return startWithWindows;
  }

  public void setStartWithWindows(boolean startWithWindows) {
    this.startWithWindows = startWithWindows;
  }
}
