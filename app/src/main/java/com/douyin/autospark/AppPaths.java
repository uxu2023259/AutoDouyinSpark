package awa.uxu.douyin.autospark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AppPaths {
  private final Path appDir;
  private final Path accountsDir;
  private final Path accountsPath;
  private final Path logDir;

  public AppPaths() {
    this(resolveDefaultAppDir());
  }

  public AppPaths(Path appDir) {
    this.appDir = appDir;
    this.accountsDir = appDir.resolve("accounts");
    this.accountsPath = appDir.resolve("accounts.json");
    this.logDir = appDir.resolve("logs");
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
    Files.createDirectories(accountsDir);
    Files.createDirectories(logDir);
  }

  public Path appDir() {
    return appDir;
  }

  public Path accountsDir() {
    return accountsDir;
  }

  public Path accountsPath() {
    return accountsPath;
  }

  public Path logDir() {
    return logDir;
  }

  public AccountPaths account(String accountId) {
    return new AccountPaths(accountsDir.resolve(AccountProfile.requireValidId(accountId)));
  }

  public Path legacyConfigPath() {
    return appDir.resolve("config.json");
  }

  public Path legacyStatePath() {
    return appDir.resolve("state.json");
  }

  public Path legacyBrowserProfileDir() {
    return appDir.resolve("browser-profile");
  }

  public Path legacyScreenshotDir() {
    return appDir.resolve("screenshots");
  }
}
