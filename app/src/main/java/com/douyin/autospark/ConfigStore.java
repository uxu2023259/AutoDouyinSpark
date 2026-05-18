package com.douyin.autospark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ConfigStore {
  private final AppPaths paths;
  private final ObjectMapper mapper;

  public ConfigStore(AppPaths paths) {
    this.paths = paths;
    this.mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public AppConfig loadConfig() throws IOException {
    paths.ensureDirectories();
    if (!Files.exists(paths.configPath())) {
      AppConfig defaults = AppConfig.defaults();
      saveConfig(defaults);
      return defaults;
    }
    String text = Files.readString(paths.configPath(), StandardCharsets.UTF_8);
    AppConfig config = mapper.readValue(text, AppConfig.class);
    config.normalize();
    return config;
  }

  public void saveConfig(AppConfig config) throws IOException {
    paths.ensureDirectories();
    config.normalize();
    String text = mapper.writeValueAsString(config) + System.lineSeparator();
    Files.writeString(paths.configPath(), text, StandardCharsets.UTF_8);
  }

  public SendState loadState() throws IOException {
    paths.ensureDirectories();
    if (!Files.exists(paths.statePath())) {
      SendState defaults = SendState.defaults();
      saveState(defaults);
      return defaults;
    }
    String text = Files.readString(paths.statePath(), StandardCharsets.UTF_8);
    SendState state = mapper.readValue(text, SendState.class);
    state.normalize();
    return state;
  }

  public void saveState(SendState state) throws IOException {
    paths.ensureDirectories();
    state.normalize();
    String text = mapper.writeValueAsString(state) + System.lineSeparator();
    Files.writeString(paths.statePath(), text, StandardCharsets.UTF_8);
  }
}
