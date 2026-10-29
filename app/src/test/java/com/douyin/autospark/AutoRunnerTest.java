package awa.uxu.douyin.autospark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
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
  void processesEachConfiguredUserOnceAndReportsPartialFailuresAccurately() throws Exception {
    List<String> logs = new ArrayList<>();
    ConfigStore store = store("逐项处理", List.of("张三", "查无此人", "重名", "李四"));
    ChineseLogger logger = logger("逐项处理日志", logs);
    FakeAutomator automator = new FakeAutomator(new AccountPaths(tempDir.resolve("逐项处理浏览器")));

    try (AutoRunner runner = new AutoRunner(store, automator, logger, afterConfiguredTime())) {
      runner.runDailyCycle();
      runner.runDailyCycle();
    }

    assertEquals(List.of("张三", "李四"), automator.sentTargets);
    SendState state = store.loadState();
    assertEquals("SENT", state.getDailyTargets().get("张三").getStatus());
    assertEquals("NOT_FOUND", state.getDailyTargets().get("查无此人").getStatus());
    assertEquals("AMBIGUOUS", state.getDailyTargets().get("重名").getStatus());
    assertEquals("SENT", state.getDailyTargets().get("李四").getStatus());
    assertTrue(logs.stream().anyMatch(line -> line.contains("今日名单已处理完，但并非全部发送成功")));
    assertFalse(logs.stream().anyMatch(line -> line.contains("今日名单全部发送成功")));
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
    assertFalse(state.hasAttempted("李四"));
    assertTrue(state.getBlockedReason().contains("安全验证"));
    assertTrue(logs.stream().anyMatch(line -> line.contains("剩余 1 位未处理")));
    assertFalse(logs.stream().anyMatch(line -> line.contains("今日名单全部发送成功")));
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

    private FakeAutomator(AccountPaths paths) {
      super(paths, new ChineseLogger(new AppPaths(paths.accountDir().resolve("logs"))));
    }

    @Override
    public synchronized ConversationDiscoveryResult discoverConversations(AppConfig config, String target, String taskId) {
      if ("查无此人".equals(target)) {
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
    public synchronized void close() {}
  }
}
