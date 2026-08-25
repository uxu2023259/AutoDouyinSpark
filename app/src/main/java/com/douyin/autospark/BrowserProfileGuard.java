package com.douyin.autospark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BrowserProfileGuard {
  private BrowserProfileGuard() {
  }

  public static List<Long> findOwners(Path profileDirectory) {
    String expected = normalize(profileDirectory);
    long currentPid = ProcessHandle.current().pid();
    List<Long> owners = new ArrayList<>();
    ProcessHandle.allProcesses().forEach(process -> {
      if (process.pid() == currentPid || !process.isAlive()) {
        return;
      }
      String commandLine = process.info().commandLine().orElse("");
      if (!commandLine.isBlank() && normalizeCommandLine(commandLine).contains(expected)) {
        owners.add(process.pid());
      }
    });
    return owners;
  }

  static String normalize(Path path) {
    return path.toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
  }

  static String normalizeCommandLine(String commandLine) {
    return commandLine.replace('\\', '/').replace("\"", "").toLowerCase(Locale.ROOT);
  }
}
