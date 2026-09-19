package awa.uxu.douyin.autospark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
    migrateConfig(json);
    AppConfig config = mapper.treeToValue(json, AppConfig.class);
    config.normalize();
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

  private void migrateConfig(ObjectNode json) {
    if (!json.has("fixedTimes")) {
      ArrayNode fixedTimes = json.putArray("fixedTimes");
      String legacyTime = json.path("fixedTime").asText("").trim();
      if (!legacyTime.isBlank()) {
        fixedTimes.add(legacyTime);
      }
    }
    if (!json.has("fixedModeEnabled")) {
      json.put("fixedModeEnabled", json.path("fixedTimes").size() > 0);
    }
    if (!json.has("intervalModeEnabled")) {
      json.put("intervalModeEnabled", true);
    }
    json.remove("fixedTime");
  }

  private boolean migrateState(ObjectNode json) {
    if (!json.path("pendingRetries").isArray()) {
      return false;
    }
    boolean migrated = false;
    for (var retry : json.withArray("pendingRetries")) {
      if (retry instanceof ObjectNode retryObject && retryObject.has("discoveryRetry")) {
        retryObject.remove("discoveryRetry");
        migrated = true;
      }
    }
    return migrated;
  }
}
