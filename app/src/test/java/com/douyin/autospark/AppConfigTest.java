package com.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigTest {
  @Test
  void normalizeKeepsSafeChineseDefaults() {
    AppConfig config = new AppConfig();
    config.setIntervalMinutes(0);
    config.setDailyLimit(0);
    config.setCooldownMinutes(-3);
    config.setRetryCount(-1);
    config.setRetryDelayMinutes(0);
    config.setFixedTimes(List.of("16:00", "09:30", "16:00"));
    config.setMessageText("   ");
    config.setTargets(List.of(" 张三 ", "", "张三", "李四"));

    config.normalize();

    assertEquals(1, config.getIntervalMinutes());
    assertEquals(1, config.getDailyLimit());
    assertEquals(0, config.getCooldownMinutes());
    assertEquals(0, config.getRetryCount());
    assertEquals(1, config.getRetryDelayMinutes());
    assertEquals(List.of("09:30", "16:00"), config.getFixedTimes());
    assertEquals("续火花啦，记得回我一下~", config.getMessageText());
    assertEquals(List.of("张三", "李四"), config.getTargets());
    assertFalse(config.isHeadlessMode());
  }

  @Test
  void defaultTextIsChinese() {
    AppConfig config = AppConfig.defaults();
    assertTrue(config.getMessageText().contains("续火花"));
  }

  @Test
  void rejectsInvalidFixedTime() {
    AppConfig config = new AppConfig();
    config.setFixedTimes(List.of("25:90"));

    assertThrows(IllegalArgumentException.class, config::normalize);
  }

  @Test
  void newDefaultsUseIndependentSchedulesAndOneHourSingleRetry() {
    AppConfig config = AppConfig.defaults();

    assertTrue(config.isFixedModeEnabled());
    assertTrue(config.isIntervalModeEnabled());
    assertEquals(60, config.getRetryDelayMinutes());
    assertEquals(1, config.getRetryCount());
    assertEquals("SAFE", config.getHumanizationPreset());
  }
}
