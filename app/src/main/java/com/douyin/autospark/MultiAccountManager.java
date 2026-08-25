package com.douyin.autospark;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MultiAccountManager implements AutoCloseable {
  private final AppPaths paths;
  private final AccountRegistry registry;
  private final ChineseLogger logger;
  private final Map<String, AccountRuntime> runtimes = new LinkedHashMap<>();
  private boolean started;

  public MultiAccountManager(AppPaths paths, AccountRegistry registry, ChineseLogger logger) {
    this.paths = paths;
    this.registry = registry;
    this.logger = logger;
  }

  public synchronized void start() {
    if (started) {
      return;
    }
    int accountIndex = 0;
    for (AccountProfile profile : registry.accounts()) {
      AccountRuntime runtime = createRuntime(profile);
      runtimes.put(profile.getId(), runtime);
      runtime.start(Math.min(60, 2L + accountIndex * 5L));
      accountIndex += 1;
    }
    started = true;
    logger.info("多账号调度中心已启动，当前管理 " + runtimes.size() + " 个账号。");
  }

  public synchronized List<AccountRuntime> runtimes() {
    return new ArrayList<>(runtimes.values());
  }

  public synchronized AccountRuntime runtime(String accountId) {
    AccountRuntime runtime = runtimes.get(AccountProfile.requireValidId(accountId));
    if (runtime == null) {
      throw new IllegalArgumentException("账号运行单元不存在：" + accountId);
    }
    return runtime;
  }

  public synchronized AccountRuntime createAccount(String name) throws IOException {
    AccountProfile profile = registry.create(name);
    AccountRuntime runtime = createRuntime(profile);
    runtimes.put(profile.getId(), runtime);
    if (started) {
      runtime.start();
    }
    logger.info("已创建账号：“" + profile.getName() + "”。");
    return runtime;
  }

  public synchronized void renameAccount(String accountId, String name) throws IOException {
    AccountRuntime previous = runtime(accountId);
    registry.rename(accountId, name);
    previous.close();
    AccountProfile profile = registry.accounts().stream()
        .filter(account -> account.getId().equals(accountId))
        .findFirst()
        .orElseThrow();
    AccountRuntime replacement = createRuntime(profile);
    runtimes.put(accountId, replacement);
    if (started) {
      replacement.start();
    }
    logger.info("账号已重命名为：“" + profile.getName() + "”。");
  }

  public synchronized Path archiveAccount(String accountId) throws IOException {
    AccountRuntime runtime = runtime(accountId);
    String name = runtime.profile().getName();
    runtime.close();
    try {
      Path archivedAt = registry.archive(accountId);
      runtimes.remove(accountId);
      logger.info("账号“" + name + "”已移出运行列表，数据已归档至：" + archivedAt);
      return archivedAt;
    } catch (IOException | RuntimeException error) {
      AccountProfile restoredProfile = registry.accounts().stream()
          .filter(account -> account.getId().equals(accountId))
          .findFirst()
          .orElse(null);
      if (restoredProfile != null) {
        AccountRuntime replacement = createRuntime(restoredProfile);
        runtimes.put(accountId, replacement);
        if (started) {
          replacement.start();
        }
      }
      throw error;
    }
  }

  public synchronized void selectAccount(String accountId) throws IOException {
    registry.select(accountId);
  }

  public String selectedAccountId() {
    return registry.selectedAccountId();
  }

  public boolean isStartWithWindows() {
    return registry.isStartWithWindows();
  }

  public void setStartWithWindows(boolean enabled) throws IOException {
    registry.setStartWithWindows(enabled);
  }

  @Override
  public synchronized void close() {
    for (AccountRuntime runtime : runtimes.values()) {
      runtime.close();
    }
    runtimes.clear();
    started = false;
  }

  private AccountRuntime createRuntime(AccountProfile profile) {
    return new AccountRuntime(profile, paths.account(profile.getId()), logger);
  }
}
