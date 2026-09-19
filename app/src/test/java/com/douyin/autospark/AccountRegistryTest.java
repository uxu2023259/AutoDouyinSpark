package awa.uxu.douyin.autospark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountRegistryTest {
  @TempDir
  Path tempDir;

  @Test
  void migratesLegacyAccountAndKeepsIndependentPaths() throws Exception {
    AppPaths paths = new AppPaths(tempDir);
    Files.writeString(paths.legacyConfigPath(), "{\"enabled\":false,\"targets\":[\"旧好友\"]}", StandardCharsets.UTF_8);
    Files.writeString(paths.legacyStatePath(), "{\"sentToday\":3}", StandardCharsets.UTF_8);
    Files.createDirectories(paths.legacyBrowserProfileDir());
    Files.writeString(paths.legacyBrowserProfileDir().resolve("登录态.txt"), "模拟登录态", StandardCharsets.UTF_8);

    AccountRegistry registry = new AccountRegistry(paths);
    registry.initialize();

    AccountProfile legacy = registry.accounts().getFirst();
    AccountPaths legacyPaths = paths.account(legacy.getId());
    assertEquals("默认账号", legacy.getName());
    assertFalse(new ConfigStore(legacyPaths).loadConfig().isEnabled());
    assertEquals(3, new ConfigStore(legacyPaths).loadState().getSentToday());
    assertTrue(Files.exists(legacyPaths.browserProfileDir().resolve("登录态.txt")));
    assertFalse(Files.exists(paths.legacyConfigPath()));

    AccountProfile second = registry.create("运营账号");
    assertNotEquals(legacyPaths.accountDir(), paths.account(second.getId()).accountDir());
    assertTrue(new ConfigStore(paths.account(second.getId())).loadConfig().isEnabled());
  }

  @Test
  void rejectsDuplicateNamesAndArchivesInsteadOfDeleting() throws Exception {
    AppPaths paths = new AppPaths(tempDir);
    AccountRegistry registry = new AccountRegistry(paths);
    registry.initialize();
    AccountProfile second = registry.create("直播账号");
    Files.writeString(paths.account(second.getId()).configPath(), "{}", StandardCharsets.UTF_8);

    assertThrows(IllegalArgumentException.class, () -> registry.create("直播账号"));
    Path archived = registry.archive(second.getId());

    assertTrue(Files.exists(archived.resolve("config.json")));
    assertFalse(Files.exists(paths.account(second.getId()).accountDir()));
    assertEquals(1, registry.accounts().size());
  }
}
