package com.douyin.autospark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AppPaths {
  private final Path appDir;
  private final Path configPath;
  private final Path statePath;
  private final Path logDir;
  private final Path screenshotDir;
  private final Path browserProfileDir;

  public AppPaths() {
    this(resolveDefaultAppDir());
  }

  public AppPaths(Path appDir) {
    this.appDir = appDir;
    this.configPath = appDir.resolve("config.json");
    this.statePath = appDir.resolve("state.json");
    this.logDir = appDir.resolve("logs");
    this.screenshotDir = appDir.resolve("screenshots");
    this.browserProfileDir = appDir.resolve("browser-profile");
  }

  public static Path resolveDefaultAppDir() {
    String appData = System.getenv("APPDATA");
    if (appData == null || appData.isBlank()) {
      appData = System.getProperty("user.home");
    }
    return Path.of(appData, "DouyinAutoSpark");
  }

  public void ensureDirectories() throws IOException {
    Files.createDirectories(appDir);
    Files.createDirectories(logDir);
    Files.createDirectories(screenshotDir);
    Files.createDirectories(browserProfileDir);
  }

  public Path appDir() {
    return appDir;
  }

  public Path configPath() {
    return configPath;
  }

  public Path statePath() {
    return statePath;
  }

  public Path logDir() {
    return logDir;
  }

  public Path screenshotDir() {
    return screenshotDir;
  }

  public Path browserProfileDir() {
    return browserProfileDir;
  }
}
