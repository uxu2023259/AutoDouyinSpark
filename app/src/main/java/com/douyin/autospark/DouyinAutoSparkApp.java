package com.douyin.autospark;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class DouyinAutoSparkApp {
  public static void main(String[] args) {
    System.setProperty("file.encoding", "UTF-8");
    AppPaths paths = new AppPaths();
    ChineseLogger logger = new ChineseLogger(paths);
    try {
      ConfigStore store = new ConfigStore(paths);
      ChatAutomator automator = new ChatAutomator(paths, logger);
      AutoRunner runner = new AutoRunner(store, automator, logger);
      StartupManager startupManager = new StartupManager();

      Runtime.getRuntime().addShutdownHook(new Thread(runner::close, "抖音自动续火花-关闭钩子"));
      runner.start();

      if (!StandardCharsets.UTF_8.equals(Charset.defaultCharset())) {
        logger.warn("当前默认字符集不是 UTF-8，程序已尽量使用 UTF-8 读写文件。当前字符集：" + Charset.defaultCharset());
      }

      if (!DesktopSession.isInteractiveDesktopAvailable()) {
        logger.warn("当前没有可交互桌面会话，程序将保持后台守护并等待用户登录 Windows 桌面。");
        keepProcessAlive();
        return;
      }

      SwingUtilities.invokeLater(() -> {
        try {
          UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
          // 外观设置失败不影响主流程。
        }
        MainWindow window = new MainWindow(store, runner, logger, startupManager);
        window.pack();
        window.setLocationRelativeTo(null);
        window.setAlwaysOnTop(true);
        window.setVisible(true);
        window.toFront();
        window.requestFocus();
        window.setAlwaysOnTop(false);
        logger.info("抖音自动续火花助手已启动。首次使用请点击“打开登录/聊天页”完成登录。");
      });
    } catch (Throwable error) {
      logger.exception("程序启动失败", error);
      System.err.println("程序启动失败：" + error.getMessage());
      throw error;
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
