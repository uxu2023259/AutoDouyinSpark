package awa.uxu.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailySendPlannerTest {
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  @Test
  void waitsForOneConfiguredTimeAndReturnsEachTargetInListOrder() {
    AppConfig config = config();
    SendState state = stateFor(LocalDate.of(2026, 8, 21));
    DailySendPlanner beforeTime = plannerAt("2026-08-21T07:59:00Z");
    DailySendPlanner atTime = plannerAt("2026-08-21T08:00:00Z");

    assertTrue(beforeTime.pendingTargets(config, state).isEmpty());
    assertEquals(List.of("小明", "小红"), atTime.pendingTargets(config, state));
  }

  @Test
  void retriesUnconfirmedTargetsAfterBackoffAndResetsOnTheNextDay() {
    AppConfig config = config();
    SendState state = stateFor(LocalDate.of(2026, 8, 21));
    DailySendPlanner planner = plannerAt("2026-08-21T08:30:00Z");

    state.beginAttempt("小明", LocalDateTime.of(2026, 8, 21, 16, 30));
    assertEquals(List.of("小红"), planner.pendingTargets(config, state));
    DailySendPlanner afterBackoff = plannerAt("2026-08-21T08:31:00Z");
    assertEquals(List.of("小明", "小红"), afterBackoff.pendingTargets(config, state));

    assertTrue(state.rollOverIfNeeded(LocalDate.of(2026, 8, 22)));
    assertEquals(List.of("小明", "小红"), planner.pendingTargets(config, state));
  }

  @Test
  void disabledOrEmptyConfigurationsDoNotScheduleSends() {
    AppConfig config = config();
    SendState state = stateFor(LocalDate.of(2026, 8, 21));
    DailySendPlanner planner = plannerAt("2026-08-21T08:30:00Z");

    config.setEnabled(false);
    assertTrue(planner.pendingTargets(config, state).isEmpty());
    config.setEnabled(true);
    config.setTargets(List.of());
    assertTrue(planner.pendingTargets(config, state).isEmpty());
  }

  private AppConfig config() {
    AppConfig config = AppConfig.defaults();
    config.setDailyTime("16:00");
    config.setTargets(List.of("小明", "小红"));
    config.normalize();
    return config;
  }

  private SendState stateFor(LocalDate date) {
    SendState state = SendState.defaults();
    state.setDailyDate(date);
    return state;
  }

  private DailySendPlanner plannerAt(String instant) {
    return new DailySendPlanner(Clock.fixed(Instant.parse(instant), ZONE));
  }
}
