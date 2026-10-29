package awa.uxu.douyin.autospark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoRunnerTest {
  @TempDir
  Path tempDir;

  @Test
  void processesAllExactNameMatchesAndReportsRemainingTargetsAccurately() throws Exception {
    List<String> logs = new ArrayList<>();
    ConfigStore store = store("逐项处理", List.of("张三", "查无此人", "重名", "李四"));
    ChineseLogger logger = logger("逐项处理日志", logs);
    FakeAutomator automator = new FakeAutomator(new AccountPaths(tempDir.resolve("逐项处理浏览器")));

    try (AutoRunner runner = new AutoRunner(store, automator, logger, afterConfiguredTime())) {
      runner.runDailyCycle();
      runner.runDailyCycle();
    }

    assertEquals(List.of("张三", "重名", "重名", "李四"), automator.sentTargets);
    SendState state = store.loadState();
    assertEquals("SENT", state.getDailyTargets().get("张三").getStatus());
    assertEquals("NOT_FOUND", state.getDailyTargets().get("查无此人").getStatus());
    assertEquals("SENT", state.getDailyTargets().get("重名").getStatus());
    assertEquals("SENT", state.getDailyTargets().get("李四").getStatus());
    assertEquals(2, automator.automaticCycleCloseCount);
    assertTrue(logs.stream().anyMatch(line -> line.contains("名单仍有待确认用户")));
    assertTrue(logs.stream().anyMatch(line -> line.contains("将逐个尝试发送以覆盖名单目标")));
    assertFalse(logs.stream().anyMatch(line -> line.contains("今日名单全部发送成功")));
  }

  @Test
  void retriesUnconfirmedUsersUntilConfirmedAndNeverRepeatsConfirmedUsers() throws Exception {
    List<String> logs = new ArrayList<>();
    ConfigStore store = store("持续重试", List.of("张三", "查无此人"));
    ChineseLogger logger = logger("持续重试日志", logs);
    FakeAutomator automator = new FakeAutomator(new AccountPaths(tempDir.resolve("持续重试浏览器")));
    MutableClock clock = new MutableClock(LocalDate.now().atTime(16, 30).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    try (AutoRunner runner = new AutoRunner(store, automator, logger, clock)) {
      runner.runDailyCycle();
      assertEquals(List.of("张三"), automator.sentTargets);
      assertEquals("NOT_FOUND", store.loadState().getDailyTargets().get("查无此人").getStatus());

      clock.advance(Duration.ofMinutes(2));
      automator.missingTargetAvailable = true;
      runner.runDailyCycle();
    }

    assertEquals(List.of("张三", "查无此人"), automator.sentTargets);
    SendState state = store.loadState();
    assertTrue(state.hasConfirmedAll(List.of("张三", "查无此人")));
    assertTrue(logs.stream().anyMatch(line -> line.contains("今日名单全部发送成功：2/2")));
  }

  @Test
  void accountLevelBlockStopsTheListAndLeavesRemainingUsersPending() throws Exception {
    List<String> logs = new ArrayList<>();
    ConfigStore store = store("安全暂停", List.of("张三", "李四"));
    ChineseLogger logger = logger("安全暂停日志", logs);
    FakeAutomator automator = new FakeAutomator(new AccountPaths(tempDir.resolve("安全暂停浏览器")));
    automator.blockTarget = "张三";

    try (AutoRunner runner = new AutoRunner(store, automator, logger, afterConfiguredTime())) {
      runner.runDailyCycle();
    }

    SendState state = store.loadState();
    assertEquals("PAUSED", state.getDailyTargets().get("张三").getStatus());
    assertFalse(state.isConfirmed("李四"));
    assertTrue(state.getBlockedReason().contains("安全验证"));
    assertTrue(logs.stream().anyMatch(line -> line.contains("待确认 2 位")));
    assertFalse(logs.stream().anyMatch(line -> line.contains("今日名单全部发送成功")));
    assertEquals(1, automator.automaticCycleCloseCount);
  }

  private ConfigStore store(String name, List<String> targets) throws Exception {
    ConfigStore store = new ConfigStore(new AccountPaths(tempDir.resolve(name)));
    AppConfig config = AppConfig.defaults();
    config.setDailyTime("16:00");
    config.setTargets(targets);
    store.saveConfig(config);
    return store;
  }

  private ChineseLogger logger(String name, List<String> lines) {
    ChineseLogger logger = new ChineseLogger(new AppPaths(tempDir.resolve(name)));
    logger.addListener(lines::add);
    return logger;
  }

  private Clock afterConfiguredTime() {
    ZoneId zone = ZoneId.systemDefault();
    return Clock.fixed(LocalDate.now().atTime(16, 30).atZone(zone).toInstant(), zone);
  }

  private static final class FakeAutomator extends ChatAutomator {
    private final List<String> sentTargets = new ArrayList<>();
    private String blockTarget = "";
    private int automaticCycleCloseCount;
    private boolean missingTargetAvailable;

    private FakeAutomator(AccountPaths paths) {
      super(paths, new ChineseLogger(new AppPaths(paths.accountDir().resolve("logs"))));
    }

    @Override
    public synchronized ConversationDiscoveryResult discoverConversations(AppConfig config, String target, String taskId) {
      if ("查无此人".equals(target) && !missingTargetAvailable) {
        return ConversationDiscoveryResult.failed("TARGET_NOT_FOUND", "未找到完全同名会话");
      }
      ConversationDiscoveryResult result = new ConversationDiscoveryResult();
      result.setOk(true);
      int matches = "重名".equals(target) ? 2 : 1;
      List<ConversationTarget> conversations = new ArrayList<>();
      for (int index = 0; index < matches; index++) {
        ConversationTarget conversation = new ConversationTarget();
        conversation.setQuery(target);
        conversation.setName(target);
        conversation.setIdentity("id-" + target + "-" + index);
        conversations.add(conversation);
      }
      result.setConversations(conversations);
      return result;
    }

    @Override
    public synchronized TaskResult sendMessage(AppConfig config, ConversationTarget conversation, String taskId) {
      sentTargets.add(conversation.displayName());
      if (conversation.displayName().equals(blockTarget)) {
        TaskResult blocked = TaskResult.failed(conversation.displayName(), "SECURITY_CHECK_REQUIRED", "页面要求人工完成安全验证");
        blocked.setOutcome("BLOCKED");
        blocked.setSecurityPrompt("需要完成安全验证");
        return blocked;
      }
      TaskResult sent = new TaskResult();
      sent.setOk(true);
      sent.setSuccess(true);
      sent.setOutcome("CONFIRMED");
      sent.setDeliveryStatus("confirmed");
      sent.setTarget(conversation.displayName());
      sent.setReason("已确认");
      return sent;
    }

    @Override
    public synchronized void keepLoginSessionDurable() {}

    @Override
    public synchronized void closeAfterAutomaticCycle() {
      automaticCycleCloseCount++;
    }

    @Override
    public synchronized void close() {}
  }

  private static final class MutableClock extends Clock {
    private Instant instant;
    private final ZoneId zone;

    private MutableClock(Instant instant, ZoneId zone) {
      this.instant = instant;
      this.zone = zone;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
