package awa.uxu.douyin.autospark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrationTest {
  @TempDir
  Path tempDir;

  @Test
  void migratesOldSchedulesToOneDailyTimeAndPreservesTargetsAndMessage() throws Exception {
    AccountPaths paths = new AccountPaths(tempDir.resolve("账号"));
    paths.ensureDirectories();
    Files.writeString(paths.configPath(), """
        {
          "enabled": true,
          "intervalModeEnabled": true,
          "fixedTimes": ["09:30", "16:00"],
          "targets": ["好友"],
          "messageText": "续火花",
          "dailyLimit": 20,
          "cooldownMinutes": 60,
          "retryCount": 2,
          "humanizationPreset": "CUSTOM"
        }
        """, StandardCharsets.UTF_8);

    AppConfig config = new ConfigStore(paths).loadConfig();

    assertEquals("09:30", config.getDailyTime());
    assertEquals(List.of("好友"), config.getTargets());
    assertEquals("续火花", config.getMessageText());
    String migrated = Files.readString(paths.configPath(), StandardCharsets.UTF_8);
    assertTrue(migrated.contains("\"dailyTime\" : \"09:30\""));
    assertFalse(migrated.contains("fixedTimes"));
    assertFalse(migrated.contains("retryCount"));
    assertFalse(migrated.contains("cooldownMinutes"));
  }

  @Test
  void migratesTodaySendHistoryAndCancelsOldRetriesWithoutResending() throws Exception {
    AccountPaths paths = new AccountPaths(tempDir.resolve("状态兼容账号"));
    paths.ensureDirectories();
    LocalDateTime now = LocalDateTime.now();
    long epochMillis = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    Files.writeString(paths.statePath(), """
        {
          "sentDate": "%s",
          "sentToday": 1,
          "perTarget": {"会话名称:好友#0": %d},
          "pendingTriggers": [{"id": "FIXED:old"}],
          "pendingRetries": [
            {
              "id": "目标:另一个",
              "query": "另一个",
              "conversation": {"name": "另一个"},
              "outcome": "UNCERTAIN",
              "attemptedAt": "%s"
            }
          ]
        }
        """.formatted(LocalDate.now(), epochMillis, now), StandardCharsets.UTF_8);

    SendState state = new ConfigStore(paths).loadState();

    assertEquals("MIGRATED_SENT", state.getDailyTargets().get("好友").getStatus());
    assertEquals("UNCERTAIN", state.getDailyTargets().get("另一个").getStatus());
    String migrated = Files.readString(paths.statePath(), StandardCharsets.UTF_8);
    assertFalse(migrated.contains("pendingRetries"));
    assertFalse(migrated.contains("pendingTriggers"));
  }

  @Test
  void pausesTodayWhenLegacySuccessfulSendsCannotBeMatchedToUsers() throws Exception {
    AccountPaths paths = new AccountPaths(tempDir.resolve("不明发送账号"));
    paths.ensureDirectories();
    Files.writeString(paths.statePath(), """
        {"sentToday": 2, "perTarget": {"会话标识:opaque-id": 1790000000000}}
        """, StandardCharsets.UTF_8);

    SendState state = new ConfigStore(paths).loadState();

    assertTrue(state.isLegacyUnattributedSentToday());
    assertTrue(state.getDailyTargets().isEmpty());
  }
}
