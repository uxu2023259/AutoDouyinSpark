package awa.uxu.douyin.autospark;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ApplicationInstanceLock implements AutoCloseable {
  private static final DateTimeFormatter START_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final FileChannel channel;
  private final FileLock lock;

  private ApplicationInstanceLock(FileChannel channel, FileLock lock) {
    this.channel = channel;
    this.lock = lock;
  }

  public static ApplicationInstanceLock acquire(AppPaths paths) throws IOException {
    paths.ensureDirectories();
    Path lockPath = paths.appDir().resolve("application.lock");
    FileChannel channel = FileChannel.open(lockPath,
        StandardOpenOption.CREATE,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE);
    FileLock lock;
    try {
      lock = channel.tryLock();
    } catch (OverlappingFileLockException error) {
      lock = null;
    }
    if (lock == null) {
      channel.close();
      throw new ApplicationAlreadyRunningException("检测到另一个抖音自动续火花助手实例正在运行。请先切换到已有窗口；如已有实例异常，请在任务管理器中结束旧程序后重试。");
    }

    String owner = "进程=" + ProcessHandle.current().pid() + System.lineSeparator()
        + "启动时间=" + START_TIME.format(LocalDateTime.now()) + System.lineSeparator();
    channel.truncate(0);
    channel.write(ByteBuffer.wrap(owner.getBytes(StandardCharsets.UTF_8)));
    channel.force(true);
    return new ApplicationInstanceLock(channel, lock);
  }

  @Override
  public void close() {
    try {
      if (lock.isValid()) {
        lock.release();
      }
    } catch (IOException ignored) {
      // 进程退出时释放失败不阻断关闭。
    }
    try {
      channel.close();
    } catch (IOException ignored) {
      // 进程退出时关闭失败不阻断关闭。
    }
  }
}
