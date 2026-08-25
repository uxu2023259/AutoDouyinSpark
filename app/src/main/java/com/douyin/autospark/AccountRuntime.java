package com.douyin.autospark;

public final class AccountRuntime implements AutoCloseable {
  private final AccountProfile profile;
  private final AccountPaths paths;
  private final ConfigStore store;
  private final AutoRunner runner;

  public AccountRuntime(AccountProfile profile, AccountPaths paths, ChineseLogger rootLogger) {
    this.profile = profile;
    this.paths = paths;
    ChineseLogger accountLogger = rootLogger.forAccount(profile.getName());
    this.store = new ConfigStore(paths);
    this.runner = new AutoRunner(store, new ChatAutomator(paths, accountLogger), accountLogger);
  }

  public void start() {
    runner.start();
  }

  public void start(long initialDelaySeconds) {
    runner.start(initialDelaySeconds);
  }

  public AccountProfile profile() {
    return profile;
  }

  public AccountPaths paths() {
    return paths;
  }

  public ConfigStore store() {
    return store;
  }

  public AutoRunner runner() {
    return runner;
  }

  @Override
  public void close() {
    runner.close();
  }
}
