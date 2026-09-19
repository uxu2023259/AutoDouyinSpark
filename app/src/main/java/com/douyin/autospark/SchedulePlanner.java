package awa.uxu.douyin.autospark;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class SchedulePlanner {
  static final String INTERVAL_TRIGGER_ID = "INTERVAL";
  private static final DateTimeFormatter SLOT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private final Clock clock;
  private final Random random;

  public SchedulePlanner(Clock clock, Random random) {
    this.clock = clock;
    this.random = random;
  }

  public LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  public boolean plan(AppConfig config, SendState state) {
    LocalDateTime now = now();
    boolean changed = false;
    if (!config.isIntervalModeEnabled()) {
      changed |= state.getPendingTriggers().removeIf(item -> "INTERVAL".equals(item.getType()));
      if (state.getNextIntervalRunAt() != null) {
        state.setNextIntervalRunAt(null);
        changed = true;
      }
    }
    if (!config.isFixedModeEnabled()) {
      changed |= state.getPendingTriggers().removeIf(item -> "FIXED".equals(item.getType()));
    }
    if (config.isIntervalModeEnabled()) {
      if (state.getNextIntervalRunAt() == null) {
        state.setNextIntervalRunAt(now.plusMinutes(randomJitterMinutes()));
        changed = true;
      }
      if (!now.isBefore(state.getNextIntervalRunAt()) && !hasTrigger(state, INTERVAL_TRIGGER_ID, "")) {
        state.getPendingTriggers().add(trigger(INTERVAL_TRIGGER_ID, "INTERVAL", "", now));
        changed = true;
      }
    }

    if (config.isFixedModeEnabled() && !config.getFixedTimes().isEmpty()) {
      LocalTime currentTime = now.toLocalTime();
      List<LocalTime> passed = config.getFixedTimes().stream()
          .map(LocalTime::parse)
          .filter(time -> !time.isAfter(currentTime))
          .sorted(Comparator.reverseOrder())
          .toList();
      if (!passed.isEmpty()) {
        LocalDateTime slot = LocalDateTime.of(LocalDate.now(clock), passed.getFirst());
        String slotKey = SLOT_FORMAT.format(slot);
        if (!Boolean.TRUE.equals(state.getFixedRuns().get(slotKey)) && !hasTrigger(state, "FIXED", slotKey)) {
          state.getPendingTriggers().add(trigger("FIXED:" + slotKey, "FIXED", slotKey, now.plusMinutes(randomJitterMinutes())));
          changed = true;
        }
      }
    }
    return changed;
  }

  public void complete(AppConfig config, SendState state, List<PendingTrigger> completed) {
    boolean intervalCompleted = false;
    for (PendingTrigger trigger : completed) {
      state.getPendingTriggers().removeIf(item -> item.getId().equals(trigger.getId()));
      if ("FIXED".equals(trigger.getType())) {
        state.getFixedRuns().put(trigger.getSlotKey(), true);
      } else if ("INTERVAL".equals(trigger.getType())) {
        intervalCompleted = true;
      }
    }
    if (intervalCompleted) {
      if (config.isIntervalModeEnabled()) {
        state.setNextIntervalRunAt(now().plusMinutes(config.getIntervalMinutes() + randomJitterMinutes()));
      } else {
        state.setNextIntervalRunAt(null);
      }
    }
  }

  public List<PendingTrigger> dueTriggers(SendState state) {
    LocalDateTime now = now();
    return state.getPendingTriggers().stream()
        .filter(item -> !now.isBefore(item.getDueAt()))
        .toList();
  }

  public List<PendingRetry> dueRetries(SendState state) {
    LocalDateTime now = now();
    return state.getPendingRetries().stream()
        .filter(item -> !now.isBefore(item.getDueAt()))
        .toList();
  }

  private boolean hasTrigger(SendState state, String typeOrId, String slotKey) {
    return state.getPendingTriggers().stream().anyMatch(item ->
        item.getId().equals(typeOrId)
            || (item.getType().equals(typeOrId) && item.getSlotKey().equals(slotKey)));
  }

  private PendingTrigger trigger(String id, String type, String slotKey, LocalDateTime dueAt) {
    PendingTrigger trigger = new PendingTrigger();
    trigger.setId(id);
    trigger.setType(type);
    trigger.setSlotKey(slotKey);
    trigger.setDueAt(dueAt);
    return trigger;
  }

  private int randomJitterMinutes() {
    return random.nextInt(6);
  }
}
