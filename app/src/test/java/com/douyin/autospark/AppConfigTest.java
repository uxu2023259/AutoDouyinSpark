package awa.uxu.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {
  @Test
  void normalizesTargetNamesAndKeepsOnlyOneDailyTime() {
    AppConfig config = AppConfig.defaults();
    config.setDailyTime(" 09:30 ");
    config.setTargets(List.of(" 张三  同学 ", "", "张三 同学", "李四"));

    config.normalize();

    assertEquals("09:30", config.getDailyTime());
    assertEquals(List.of("张三 同学", "李四"), config.getTargets());
    assertTrue(config.getMessageText().contains("续火花"));
    assertEquals("16:00", AppConfig.defaults().getDailyTime());
    assertTrue(AppConfig.defaults().isHeadlessMode());
  }

  @Test
  void rejectsInvalidDailyTime() {
    AppConfig config = AppConfig.defaults();
    config.setDailyTime("25:90");

    assertThrows(IllegalArgumentException.class, config::normalize);
  }
}
