package awa.uxu.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulePlannerTest {
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-21T08:30:00Z"), ZONE);

  @Test
  void bothModesCreateIndependentDueTicketsAndOnlyLatestMissedFixedTime() {
    AppConfig config = AppConfig.defaults();
    config.setFixedTimes(List.of("09:00", "16:00"));
    SendState state = SendState.defaults();
    state.setNextIntervalRunAt(LocalDateTime.of(2026, 8, 21, 16, 0));
    SchedulePlanner planner = new SchedulePlanner(CLOCK, zeroRandom());

    assertTrue(planner.plan(config, state));

    assertEquals(2, planner.dueTriggers(state).size());
    assertTrue(state.getPendingTriggers().stream().anyMatch(item -> "INTERVAL".equals(item.getType())));
    assertTrue(state.getPendingTriggers().stream().anyMatch(item -> "2026-08-21 16:00".equals(item.getSlotKey())));
    assertFalse(state.getPendingTriggers().stream().anyMatch(item -> "2026-08-21 09:00".equals(item.getSlotKey())));
  }

  @Test
  void intervalRestartsFromCompletionAndCompletedFixedSlotDoesNotRepeat() {
    AppConfig config = AppConfig.defaults();
    config.setIntervalMinutes(30);
    config.setFixedTimes(List.of("16:00"));
    SendState state = SendState.defaults();
    state.setNextIntervalRunAt(LocalDateTime.of(2026, 8, 21, 16, 0));
    SchedulePlanner planner = new SchedulePlanner(CLOCK, zeroRandom());
    planner.plan(config, state);
    List<PendingTrigger> completed = planner.dueTriggers(state);

    planner.complete(config, state, completed);

    assertEquals(LocalDateTime.of(2026, 8, 21, 17, 0), state.getNextIntervalRunAt());
    assertTrue(state.getPendingTriggers().isEmpty());
    assertTrue(state.getFixedRuns().containsKey("2026-08-21 16:00"));
    assertFalse(planner.plan(config, state));
  }

  @Test
  void disablingModesCancelsTheirPendingSchedules() {
    AppConfig config = AppConfig.defaults();
    SendState state = SendState.defaults();
    state.setNextIntervalRunAt(LocalDateTime.of(2026, 8, 21, 16, 0));
    SchedulePlanner planner = new SchedulePlanner(CLOCK, zeroRandom());
    planner.plan(config, state);
    config.setIntervalModeEnabled(false);
    config.setFixedModeEnabled(false);

    assertTrue(planner.plan(config, state));
    assertTrue(state.getPendingTriggers().isEmpty());
    assertEquals(null, state.getNextIntervalRunAt());
  }

  private Random zeroRandom() {
    return new Random(1) {
      @Override
      public int nextInt(int bound) {
        return 0;
      }
    };
  }
}
