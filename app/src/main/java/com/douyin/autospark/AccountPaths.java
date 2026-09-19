package awa.uxu.douyin.autospark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AccountPaths {
  private final Path accountDir;

  public AccountPaths(Path accountDir) {
    this.accountDir = accountDir.toAbsolutePath().normalize();
  }

  public void ensureDirectories() throws IOException {
    Files.createDirectories(accountDir);
    Files.createDirectories(screenshotDir());
    Files.createDirectories(browserProfileDir());
  }

  public Path accountDir() {
    return accountDir;
  }

  public Path configPath() {
    return accountDir.resolve("config.json");
  }

  public Path statePath() {
    return accountDir.resolve("state.json");
  }

  public Path screenshotDir() {
    return accountDir.resolve("screenshots");
  }

  public Path browserProfileDir() {
    return accountDir.resolve("browser-profile");
  }
}
