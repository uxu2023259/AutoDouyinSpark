package com.douyin.autospark;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.JOptionPane;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class DouyinAutoSparkApp {
  private static ApplicationInstanceLock instanceLock;

  public static void main(String[] args) {
    System.setProperty("file.encoding", "UTF-8");
    AppPaths paths = new AppPaths();
    ChineseLogger logger = new ChineseLogger(paths);
    try {
      instanceLock = ApplicationInstanceLock.acquire(paths);
      ApplicationInstanceLock acquiredLock = instanceLock;
      Runtime.getRuntime().addShutdownHook(new Thread(acquiredLock::close, "抖音自动续火花-实例锁释放"));
      AccountRegistry registry = new AccountRegistry(paths);
      registry.initialize();
      MultiAccountManager accountManager = new MultiAccountManager(paths, registry, logger);
      StartupManager startupManager = new StartupManager();

      Runtime.getRuntime().addShutdownHook(new Thread(accountManager::close, "抖音自动续火花-关闭钩子"));
      accountManager.start();

      if (!StandardCharsets.UTF_8.equals(Charset.defaultCharset())) {
        logger.warn("当前默认字符集不是 UTF-8，程序已尽量使用 UTF-8 读写文件。当前字符集：" + Charset.defaultCharset());
      }

      if (!DesktopSession.isInteractiveDesktopAvailable()) {
        logger.info("当前没有可交互桌面会话，程序已进入服务器无界面守护模式；自动任务会继续正常运行。");
        keepProcessAlive();
        return;
      }

      SwingUtilities.invokeLater(() -> {
        try {
          UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
          // 外观设置失败不影响主流程。
        }
        MainWindow window = new MainWindow(accountManager, logger, startupManager);
        window.pack();
        window.setLocationRelativeTo(null);
        window.setAlwaysOnTop(true);
        window.setVisible(true);
        window.toFront();
        window.requestFocus();
        window.setAlwaysOnTop(false);
        logger.info("抖音自动续火花助手多账号工作台已启动。请为各账号分别完成登录和发送配置。");
      });
    } catch (ApplicationAlreadyRunningException error) {
      logger.warn(error.getMessage());
      System.err.println(error.getMessage());
      if (DesktopSession.isInteractiveDesktopAvailable()) {
        JOptionPane.showMessageDialog(null, error.getMessage(), "程序已在运行", JOptionPane.WARNING_MESSAGE);
      }
    } catch (Throwable error) {
      logger.exception("程序启动失败", error);
      System.err.println("程序启动失败：" + error.getMessage());
      if (error instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (error instanceof Error fatalError) {
        throw fatalError;
      }
      throw new IllegalStateException("程序启动失败：" + error.getMessage(), error);
    }
  }

  private static void keepProcessAlive() {
    try {
      Thread.currentThread().join();
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
    }
  }
}
