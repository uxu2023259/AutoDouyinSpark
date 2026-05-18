package com.douyin.autospark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class ChineseLogger {
  private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private final AppPaths paths;
  private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

  public ChineseLogger(AppPaths paths) {
    this.paths = paths;
  }

  public void addListener(Consumer<String> listener) {
    listeners.add(listener);
  }

  public void removeListener(Consumer<String> listener) {
    listeners.remove(listener);
  }

  public void info(String message) {
    write("信息", message);
  }

  public void warn(String message) {
    write("提醒", message);
  }

  public void error(String message) {
    write("错误", message);
  }

  public void exception(String message, Throwable throwable) {
    String detail = throwable == null ? "未知异常" : throwable.getMessage();
    write("错误", message + "：" + detail);
  }

  private void write(String level, String message) {
    String line = "[" + LINE_TIME.format(LocalDateTime.now()) + "] [" + level + "] " + message;
    for (Consumer<String> listener : listeners) {
      listener.accept(line);
    }
    try {
      paths.ensureDirectories();
      Path logFile = paths.logDir().resolve(LocalDate.now() + ".log");
      Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.APPEND);
    } catch (IOException ignored) {
      // 日志文件写入失败时，不阻断主流程。
    }
  }
}
