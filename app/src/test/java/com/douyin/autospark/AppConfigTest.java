package com.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {
  @Test
  void normalizeKeepsSafeChineseDefaults() {
    AppConfig config = new AppConfig();
    config.setIntervalMinutes(0);
    config.setDailyLimit(0);
    config.setCooldownMinutes(-3);
    config.setRetryCount(-1);
    config.setMessageText("   ");
    config.setTargets(List.of(" 张三 ", "", "张三", "李四"));

    config.normalize();

    assertEquals(1, config.getIntervalMinutes());
    assertEquals(1, config.getDailyLimit());
    assertEquals(0, config.getCooldownMinutes());
    assertEquals(0, config.getRetryCount());
    assertEquals("续火花啦，记得回我一下~", config.getMessageText());
    assertEquals(List.of("张三", "李四"), config.getTargets());
  }

  @Test
  void defaultTextIsChinese() {
    AppConfig config = AppConfig.defaults();
    assertTrue(config.getMessageText().contains("续火花"));
  }
}
