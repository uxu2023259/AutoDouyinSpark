package com.douyin.autospark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountPathsTest {
  @TempDir
  Path tempDir;

  @Test
  void isolatesConfigStateBrowserAndScreenshots() {
    AppPaths paths = new AppPaths(tempDir);
    AccountPaths first = paths.account("account-one");
    AccountPaths second = paths.account("account-two");

    assertNotEquals(first.configPath(), second.configPath());
    assertNotEquals(first.statePath(), second.statePath());
    assertNotEquals(first.browserProfileDir(), second.browserProfileDir());
    assertNotEquals(first.screenshotDir(), second.screenshotDir());
    assertTrue(first.accountDir().startsWith(paths.accountsDir().toAbsolutePath()));
  }

  @Test
  void rejectsPathTraversalIdentifiers() {
    AppPaths paths = new AppPaths(tempDir);
    assertThrows(IllegalArgumentException.class, () -> paths.account("../其他目录"));
    assertThrows(IllegalArgumentException.class, () -> paths.account("账号/一"));
  }
}
