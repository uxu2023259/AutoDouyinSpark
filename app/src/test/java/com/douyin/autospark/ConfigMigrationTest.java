package awa.uxu.douyin.autospark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrationTest {
  @TempDir
  Path tempDir;

  @Test
  void migratesLegacySingleFixedTimeAndPreservesExplicitRetryCount() throws Exception {
    AccountPaths paths = new AccountPaths(tempDir.resolve("账号"));
    paths.ensureDirectories();
    Files.writeString(paths.configPath(), """
        {
          "enabled": true,
          "intervalMinutes": 45,
          "fixedTime": "18:20",
          "targets": ["好友"],
          "messageText": "续火花",
          "dailyLimit": 20,
          "cooldownMinutes": 60,
          "retryCount": 2
        }
        """, StandardCharsets.UTF_8);

    AppConfig config = new ConfigStore(paths).loadConfig();

    assertTrue(config.isIntervalModeEnabled());
    assertTrue(config.isFixedModeEnabled());
    assertEquals(List.of("18:20"), config.getFixedTimes());
    assertEquals(2, config.getRetryCount());
    assertEquals(60, config.getRetryDelayMinutes());
  }

  @Test
  void removesLegacyComputedDiscoveryRetryFieldWithoutDroppingRetry() throws Exception {
    AccountPaths paths = new AccountPaths(tempDir.resolve("状态兼容账号"));
    paths.ensureDirectories();
    Files.writeString(paths.statePath(), """
        {
          "sentDate": "2026-08-25",
          "pendingRetries": [
            {
              "id": "关键词:好友",
              "stateKey": "关键词:好友",
              "query": "好友",
              "dueAt": "2026-08-25T15:30:00",
              "discoveryRetry": true
            }
          ]
        }
        """, StandardCharsets.UTF_8);

    SendState state = new ConfigStore(paths).loadState();

    assertEquals(1, state.getPendingRetries().size());
    assertTrue(state.getPendingRetries().getFirst().isDiscoveryRetry());
    assertEquals(LocalDateTime.of(2026, 8, 25, 15, 30), state.getPendingRetries().getFirst().getDueAt());
    assertFalse(Files.readString(paths.statePath(), StandardCharsets.UTF_8).contains("discoveryRetry"));
  }
}
