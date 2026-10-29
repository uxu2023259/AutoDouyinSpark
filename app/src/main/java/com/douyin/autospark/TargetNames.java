package awa.uxu.douyin.autospark;

import java.util.Locale;

public final class TargetNames {
  private TargetNames() {}

  public static String displayName(String value) {
    return value == null ? "" : value.strip().replaceAll("(?U)\\s+", " ");
  }

  public static String key(String value) {
    return displayName(value).toLowerCase(Locale.ROOT);
  }
}
