package awa.uxu.douyin.autospark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {
  private boolean enabled = true;
  private String dailyTime = "16:00";
  private List<String> targets = new ArrayList<>();
  private String messageText = "续火花啦，记得回我一下~";
  private boolean startWithWindows = true;
  private boolean headlessMode = true;

  public static AppConfig defaults() {
    return new AppConfig();
  }

  public void normalize() {
    dailyTime = dailyTime == null ? "" : dailyTime.trim();
    try {
      LocalTime.parse(dailyTime, DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT));
    } catch (RuntimeException error) {
      throw new IllegalArgumentException("每日发送时间必须使用 HH:mm 格式，例如 16:00");
    }
    if (messageText == null || messageText.isBlank()) {
      messageText = "续火花啦，记得回我一下~";
    }
    if (targets == null) {
      targets = new ArrayList<>();
    }
    Map<String, String> uniqueTargets = new LinkedHashMap<>();
    for (String target : targets) {
      String displayName = TargetNames.displayName(target);
      if (!displayName.isBlank()) {
        uniqueTargets.putIfAbsent(TargetNames.key(displayName), displayName);
      }
    }
    targets = List.copyOf(uniqueTargets.values());
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getDailyTime() {
    return dailyTime;
  }

  public void setDailyTime(String dailyTime) {
    this.dailyTime = dailyTime;
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
