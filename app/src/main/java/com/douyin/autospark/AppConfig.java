package com.douyin.autospark;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class AppConfig {
  private boolean enabled = true;
  private boolean intervalModeEnabled = true;
  private int intervalMinutes = 30;
  private boolean fixedModeEnabled = true;
  private List<String> fixedTimes = new ArrayList<>(List.of("16:00"));
  private List<String> targets = new ArrayList<>();
  private String messageText = "续火花啦，记得回我一下~";
  private int dailyLimit = 20;
  private int cooldownMinutes = 60;
  private int retryCount = 1;
  private int retryDelayMinutes = 60;
  private String humanizationPreset = "SAFE";
  private int humanActionDelayMinMillis = 350;
  private int humanActionDelayMaxMillis = 1200;
  private int humanTypingDelayMinMillis = 80;
  private int humanTypingDelayMaxMillis = 220;
  private int humanTargetDelayMinSeconds = 8;
  private int humanTargetDelayMaxSeconds = 20;
  private int humanBatchSize = 5;
  private int humanBatchRestMinSeconds = 45;
  private int humanBatchRestMaxSeconds = 120;
  private boolean startWithWindows = true;
  private boolean headlessMode = false;

  public static AppConfig defaults() {
    return new AppConfig();
  }

  public void normalize() {
    intervalMinutes = Math.max(1, intervalMinutes);
    dailyLimit = Math.max(1, dailyLimit);
    cooldownMinutes = Math.max(0, cooldownMinutes);
    retryCount = Math.max(0, retryCount);
    retryDelayMinutes = Math.max(1, retryDelayMinutes);
    if (fixedTimes == null) {
      fixedTimes = new ArrayList<>();
    }
    List<String> normalizedTimes = new ArrayList<>();
    for (String fixedTime : fixedTimes) {
      if (fixedTime == null || fixedTime.isBlank()) {
        continue;
      }
      String normalized = fixedTime.trim();
      try {
        LocalTime.parse(normalized, DateTimeFormatter.ofPattern("HH:mm"));
      } catch (DateTimeParseException error) {
        throw new IllegalArgumentException("定时时间必须使用 HH:mm 格式，例如 16:00");
      }
      if (!normalizedTimes.contains(normalized)) {
        normalizedTimes.add(normalized);
      }
    }
    fixedTimes = normalizedTimes.stream().sorted().toList();
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
    if (!"CUSTOM".equalsIgnoreCase(humanizationPreset)) {
      humanizationPreset = "SAFE";
    } else {
      humanizationPreset = "CUSTOM";
    }
    humanActionDelayMinMillis = Math.max(200, humanActionDelayMinMillis);
    humanActionDelayMaxMillis = Math.max(humanActionDelayMinMillis, humanActionDelayMaxMillis);
    humanTypingDelayMinMillis = Math.max(40, humanTypingDelayMinMillis);
    humanTypingDelayMaxMillis = Math.max(humanTypingDelayMinMillis, humanTypingDelayMaxMillis);
    humanTargetDelayMinSeconds = Math.max(3, humanTargetDelayMinSeconds);
    humanTargetDelayMaxSeconds = Math.max(humanTargetDelayMinSeconds, humanTargetDelayMaxSeconds);
    humanBatchSize = Math.max(1, humanBatchSize);
    humanBatchRestMinSeconds = Math.max(15, humanBatchRestMinSeconds);
    humanBatchRestMaxSeconds = Math.max(humanBatchRestMinSeconds, humanBatchRestMaxSeconds);
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

  public boolean isIntervalModeEnabled() {
    return intervalModeEnabled;
  }

  public void setIntervalModeEnabled(boolean intervalModeEnabled) {
    this.intervalModeEnabled = intervalModeEnabled;
  }

  public boolean isFixedModeEnabled() {
    return fixedModeEnabled;
  }

  public void setFixedModeEnabled(boolean fixedModeEnabled) {
    this.fixedModeEnabled = fixedModeEnabled;
  }

  public List<String> getFixedTimes() {
    return fixedTimes;
  }

  public void setFixedTimes(List<String> fixedTimes) {
    this.fixedTimes = fixedTimes;
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

  public int getRetryDelayMinutes() {
    return retryDelayMinutes;
  }

  public void setRetryDelayMinutes(int retryDelayMinutes) {
    this.retryDelayMinutes = retryDelayMinutes;
  }

  public String getHumanizationPreset() {
    return humanizationPreset;
  }

  public void setHumanizationPreset(String humanizationPreset) {
    this.humanizationPreset = humanizationPreset;
  }

  public int getHumanActionDelayMinMillis() {
    return humanActionDelayMinMillis;
  }

  public void setHumanActionDelayMinMillis(int value) {
    humanActionDelayMinMillis = value;
  }

  public int getHumanActionDelayMaxMillis() {
    return humanActionDelayMaxMillis;
  }

  public void setHumanActionDelayMaxMillis(int value) {
    humanActionDelayMaxMillis = value;
  }

  public int getHumanTypingDelayMinMillis() {
    return humanTypingDelayMinMillis;
  }

  public void setHumanTypingDelayMinMillis(int value) {
    humanTypingDelayMinMillis = value;
  }

  public int getHumanTypingDelayMaxMillis() {
    return humanTypingDelayMaxMillis;
  }

  public void setHumanTypingDelayMaxMillis(int value) {
    humanTypingDelayMaxMillis = value;
  }

  public int getHumanTargetDelayMinSeconds() {
    return humanTargetDelayMinSeconds;
  }

  public void setHumanTargetDelayMinSeconds(int value) {
    humanTargetDelayMinSeconds = value;
  }

  public int getHumanTargetDelayMaxSeconds() {
    return humanTargetDelayMaxSeconds;
  }

  public void setHumanTargetDelayMaxSeconds(int value) {
    humanTargetDelayMaxSeconds = value;
  }

  public int getHumanBatchSize() {
    return humanBatchSize;
  }

  public void setHumanBatchSize(int value) {
    humanBatchSize = value;
  }

  public int getHumanBatchRestMinSeconds() {
    return humanBatchRestMinSeconds;
  }

  public void setHumanBatchRestMinSeconds(int value) {
    humanBatchRestMinSeconds = value;
  }

  public int getHumanBatchRestMaxSeconds() {
    return humanBatchRestMaxSeconds;
  }

  public void setHumanBatchRestMaxSeconds(int value) {
    humanBatchRestMaxSeconds = value;
  }

  public boolean isStartWithWindows() {
    return startWithWindows;
  }

  public void setStartWithWindows(boolean startWithWindows) {
    this.startWithWindows = startWithWindows;
  }

  public boolean isHeadlessMode() {
    return headlessMode;
  }

  public void setHeadlessMode(boolean headlessMode) {
    this.headlessMode = headlessMode;
  }
}
