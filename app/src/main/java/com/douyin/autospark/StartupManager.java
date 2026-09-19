package awa.uxu.douyin.autospark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class StartupManager {
  public Path startupScriptPath() {
    String appData = System.getenv("APPDATA");
    if (appData == null || appData.isBlank()) {
      appData = System.getProperty("user.home");
    }
    return Path.of(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup", "抖音自动续火花助手.cmd");
  }

  public void installForCurrentUser() throws IOException {
    Path script = startupScriptPath();
    Files.createDirectories(script.getParent());
    String command = currentLaunchCommand();
    String content = "@echo off" + System.lineSeparator()
        + "chcp 65001 >nul" + System.lineSeparator()
        + "start \"抖音自动续火花助手\" " + command + System.lineSeparator();
    Files.writeString(script, content, StandardCharsets.UTF_8);
  }

  public void uninstallForCurrentUser() throws IOException {
    Files.deleteIfExists(startupScriptPath());
  }

  public boolean isInstalled() {
    return Files.exists(startupScriptPath());
  }

  private String currentLaunchCommand() {
    String command = ProcessHandle.current().info().command().orElse("");
    if (command.isBlank()) {
      return "\"抖音自动续火花助手.exe\"";
    }
    String classPath = System.getProperty("java.class.path", "");
    if (command.toLowerCase().endsWith("java.exe") || command.toLowerCase().endsWith("javaw.exe")) {
      return "\"" + command + "\" -Dfile.encoding=UTF-8 -cp \"" + classPath + "\" awa.uxu.douyin.autospark.DouyinAutoSparkApp";
    }
    return "\"" + command + "\"";
  }
}
