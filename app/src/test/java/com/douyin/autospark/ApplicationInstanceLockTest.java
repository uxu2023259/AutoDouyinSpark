package com.douyin.autospark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationInstanceLockTest {
  @TempDir
  Path tempDir;

  @Test
  void preventsSecondInstanceAndAllowsRestartAfterRelease() throws Exception {
    AppPaths paths = new AppPaths(tempDir);
    try (ApplicationInstanceLock first = ApplicationInstanceLock.acquire(paths)) {
      ApplicationAlreadyRunningException error = assertThrows(
          ApplicationAlreadyRunningException.class,
          () -> ApplicationInstanceLock.acquire(paths));
      assertTrue(error.getMessage().contains("另一个"));
    }

    assertTrue(Files.readString(tempDir.resolve("application.lock")).contains("进程="));

    assertDoesNotThrow(() -> {
      try (ApplicationInstanceLock ignored = ApplicationInstanceLock.acquire(paths)) {
        // 锁释放后允许下一次正常启动。
      }
    });
  }
}
