package com.douyin.autospark;

import java.nio.file.Path;
import java.util.List;

public class BrowserProfileInUseException extends IllegalStateException {
  private final Path profileDirectory;
  private final List<Long> ownerProcessIds;

  public BrowserProfileInUseException(Path profileDirectory, List<Long> ownerProcessIds, Throwable cause) {
    super(buildMessage(profileDirectory, ownerProcessIds), cause);
    this.profileDirectory = profileDirectory;
    this.ownerProcessIds = List.copyOf(ownerProcessIds);
  }

  public Path getProfileDirectory() {
    return profileDirectory;
  }

  public List<Long> getOwnerProcessIds() {
    return ownerProcessIds;
  }

  private static String buildMessage(Path profileDirectory, List<Long> ownerProcessIds) {
    String owners = ownerProcessIds.isEmpty() ? "未识别" : ownerProcessIds.toString();
    return "当前账号的浏览器登录目录正在被其他进程占用，无法安全启动。占用进程：" + owners
        + "；目录：" + profileDirectory
        + "。请关闭旧版助手或占用该目录的 Chromium 后重试，不要同时启动两个程序实例。";
  }
}
