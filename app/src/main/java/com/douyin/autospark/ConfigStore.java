package awa.uxu.douyin.autospark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

public class ConfigStore {
  private final AccountPaths paths;
  private final ObjectMapper mapper;

  public ConfigStore(AccountPaths paths) {
    this.paths = paths;
    this.mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public synchronized AppConfig loadConfig() throws IOException {
    paths.ensureDirectories();
    if (!Files.exists(paths.configPath())) {
      AppConfig defaults = AppConfig.defaults();
      saveConfig(defaults);
      return defaults;
    }
    String text = Files.readString(paths.configPath(), StandardCharsets.UTF_8);
    ObjectNode json = (ObjectNode) mapper.readTree(text);
    boolean migrated = migrateConfig(json);
    AppConfig config = mapper.treeToValue(json, AppConfig.class);
    config.normalize();
    if (migrated) {
      saveConfig(config);
    }
    return config;
  }

  public synchronized void saveConfig(AppConfig config) throws IOException {
    paths.ensureDirectories();
    config.normalize();
    String text = mapper.writeValueAsString(config) + System.lineSeparator();
    writeAtomically(paths.configPath(), text);
  }

  public synchronized SendState loadState() throws IOException {
    paths.ensureDirectories();
    if (!Files.exists(paths.statePath())) {
      SendState defaults = SendState.defaults();
      saveState(defaults);
      return defaults;
    }
    String text = Files.readString(paths.statePath(), StandardCharsets.UTF_8);
    ObjectNode json = (ObjectNode) mapper.readTree(text);
    boolean migrated = migrateState(json);
    SendState state = mapper.treeToValue(json, SendState.class);
    state.normalize();
    if (state.rollOverIfNeeded(LocalDate.now())) {
      migrated = true;
    }
    if (migrated) {
      saveState(state);
    }
    return state;
  }

  public synchronized void saveState(SendState state) throws IOException {
    paths.ensureDirectories();
    state.normalize();
    String text = mapper.writeValueAsString(state) + System.lineSeparator();
    writeAtomically(paths.statePath(), text);
  }

  private void writeAtomically(Path destination, String text) throws IOException {
    Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
    Files.writeString(temporary, text, StandardCharsets.UTF_8);
    try {
      Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (java.nio.file.AtomicMoveNotSupportedException error) {
      Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private boolean migrateConfig(ObjectNode json) {
    boolean migrated = false;
    if (!json.hasNonNull("dailyTime")) {
      String oldTime = "";
      if (json.path("fixedTimes").isArray() && !json.path("fixedTimes").isEmpty()) {
        oldTime = json.path("fixedTimes").get(0).asText("").trim();
      }
      if (oldTime.isBlank()) {
        oldTime = json.path("fixedTime").asText("").trim();
      }
      json.put("dailyTime", oldTime.isBlank() ? "16:00" : oldTime);
      migrated = true;
    }
    migrated |= removeFields(json,
        "fixedTimes", "fixedTime", "fixedModeEnabled", "intervalModeEnabled", "intervalMinutes",
        "dailyLimit", "cooldownMinutes", "retryCount", "retryDelayMinutes", "humanizationPreset",
        "humanActionDelayMinMillis", "humanActionDelayMaxMillis", "humanTypingDelayMinMillis",
        "humanTypingDelayMaxMillis", "humanTargetDelayMinSeconds", "humanTargetDelayMaxSeconds",
        "humanBatchSize", "humanBatchRestMinSeconds", "humanBatchRestMaxSeconds");
    return migrated;
  }

  private boolean migrateState(ObjectNode json) {
    boolean hasLegacyState = json.has("sentDate") || json.has("sentToday") || json.has("perTarget")
        || json.has("fixedRuns") || json.has("pendingTriggers") || json.has("pendingRetries")
        || json.has("nextIntervalRunAt");
    if (!hasLegacyState) {
      return false;
    }

    LocalDate today = LocalDate.now();
    ObjectNode dailyTargets = json.path("dailyTargets").isObject()
        ? ((ObjectNode) json.path("dailyTargets")).deepCopy()
        : mapper.createObjectNode();
    int migratedSentCount = 0;
    boolean unattributed = json.path("legacyUnattributedSentToday").asBoolean(false);
    LocalDate oldSentDate = parseDate(json.path("sentDate").asText(""));
    if (oldSentDate == null && json.path("sentToday").asInt(0) > 0) {
      oldSentDate = today;
    }
    var perTarget = json.path("perTarget");
    if (today.equals(oldSentDate) && perTarget.isObject()) {
      var entries = perTarget.fields();
      while (entries.hasNext()) {
        var entry = entries.next();
        if (!entry.getValue().canConvertToLong()) {
          continue;
        }
        long epochMillis = entry.getValue().asLong();
        LocalDateTime attemptedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        if (!attemptedAt.toLocalDate().equals(today)) {
          continue;
        }
        String target = legacyTargetName(entry.getKey());
        if (target.isBlank()) {
          unattributed = true;
          continue;
        }
        ObjectNode result = dailyTargets.putObject(TargetNames.key(target));
        result.put("target", target);
        result.put("status", "MIGRATED_SENT");
        result.put("detail", "从旧版本的当日成功记录迁移");
        result.put("attemptedAt", attemptedAt.toString());
        migratedSentCount++;
      }
    }
    if (today.equals(oldSentDate) && json.path("sentToday").asInt(0) > migratedSentCount) {
      unattributed = true;
    }

    var retries = json.path("pendingRetries");
    if (retries.isArray()) {
      for (var retry : retries) {
        if (!retry.isObject() || retry.path("discoveryRetry").asBoolean(false)) {
          continue;
        }
        String target = TargetNames.displayName(retry.path("conversation").path("name").asText(""));
        if (target.isBlank()) {
          target = TargetNames.displayName(retry.path("query").asText(""));
        }
        LocalDateTime attemptedAt = parseDateTime(retry.path("attemptedAt").asText(""));
        if (!target.isBlank() && attemptedAt != null && attemptedAt.toLocalDate().equals(today)) {
          ObjectNode result = dailyTargets.putObject(TargetNames.key(target));
          result.put("target", target);
          String outcome = retry.path("outcome").asText("");
          result.put("status", "UNCERTAIN".equals(outcome) ? "UNCERTAIN" : "FAILED");
          result.put("detail", "旧版本的待重试任务已取消，不会在今天自动重复发送");
          result.put("attemptedAt", attemptedAt.toString());
        } else if (attemptedAt == null && "UNCERTAIN".equals(retry.path("outcome").asText(""))) {
          unattributed = true;
        }
      }
    }

    json.put("dailyDate", today.toString());
    json.set("dailyTargets", dailyTargets);
    json.put("legacyUnattributedSentToday", unattributed);
    return removeFields(json, "sentDate", "sentToday", "perTarget", "fixedRuns", "pendingTriggers",
        "pendingRetries", "nextIntervalRunAt");
  }

  private boolean removeFields(ObjectNode json, String... fields) {
    boolean changed = false;
    for (String field : fields) {
      changed |= json.has(field);
      json.remove(field);
    }
    return changed;
  }

  private String legacyTargetName(String stateKey) {
    String prefix = "会话名称:";
    if (stateKey.startsWith(prefix)) {
      String value = stateKey.substring(prefix.length());
      int occurrenceMarker = value.lastIndexOf('#');
      return TargetNames.displayName(occurrenceMarker < 0 ? value : value.substring(0, occurrenceMarker));
    }
    if (!stateKey.startsWith("会话标识:")) {
      return TargetNames.displayName(stateKey);
    }
    return "";
  }

  private LocalDate parseDate(String value) {
    try {
      return value.isBlank() ? null : LocalDate.parse(value);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private LocalDateTime parseDateTime(String value) {
    try {
      return value.isBlank() ? null : LocalDateTime.parse(value);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }
}
