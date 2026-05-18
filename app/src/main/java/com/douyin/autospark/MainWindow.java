package com.douyin.autospark;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class MainWindow extends JFrame {
  private final ConfigStore store;
  private final AutoRunner runner;
  private final ChineseLogger logger;
  private final StartupManager startupManager;

  private final JCheckBox enabled = new JCheckBox("启用自动执行");
  private final JCheckBox startWithWindows = new JCheckBox("开机后自动守护");
  private final JSpinner intervalMinutes = new JSpinner(new SpinnerNumberModel(30, 1, 240, 1));
  private final JTextField fixedTime = new JTextField("16:00");
  private final JTextArea targets = new JTextArea(5, 24);
  private final JTextArea messageText = new JTextArea(4, 24);
  private final JSpinner dailyLimit = new JSpinner(new SpinnerNumberModel(20, 1, 200, 1));
  private final JSpinner cooldownMinutes = new JSpinner(new SpinnerNumberModel(60, 0, 10080, 1));
  private final JSpinner retryCount = new JSpinner(new SpinnerNumberModel(2, 0, 10, 1));
  private final JTextArea logs = new JTextArea(18, 80);
  private final JLabel status = new JLabel("状态：正在初始化");

  public MainWindow(ConfigStore store, AutoRunner runner, ChineseLogger logger, StartupManager startupManager) {
    super("抖音自动续火花助手");
    this.store = store;
    this.runner = runner;
    this.logger = logger;
    this.startupManager = startupManager;
    buildUi();
    bindEvents();
    loadConfigToForm();
    logger.addListener(this::appendLog);
  }

  private void buildUi() {
    setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
    setMinimumSize(new Dimension(980, 760));
    setLayout(new BorderLayout(12, 12));

    JPanel form = new JPanel(new GridBagLayout());
    form.setBorder(BorderFactory.createTitledBorder("运行配置"));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(6, 8, 6, 8);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1;

    addRow(form, gbc, 0, "启用状态", enabled);
    addRow(form, gbc, 1, "巡检间隔（分钟）", intervalMinutes);
    addRow(form, gbc, 2, "固定触发时间（HH:mm）", fixedTime);
    addAreaRow(form, gbc, 3, "目标会话关键词（每行一个）", targets);
    addAreaRow(form, gbc, 4, "发送内容", messageText);
    addRow(form, gbc, 5, "每日最大发送次数", dailyLimit);
    addRow(form, gbc, 6, "每个用户冷却时间（分钟）", cooldownMinutes);
    addRow(form, gbc, 7, "失败重试次数", retryCount);
    addRow(form, gbc, 8, "启动方式", startWithWindows);

    JPanel buttons = new JPanel();
    JButton saveButton = new JButton("保存配置");
    JButton runNowButton = new JButton("立即执行一次");
    JButton openBrowserButton = new JButton("打开登录/聊天页");
    JButton openDataDirButton = new JButton("打开数据目录");
    JButton clearLogButton = new JButton("清空窗口日志");
    buttons.add(saveButton);
    buttons.add(runNowButton);
    buttons.add(openBrowserButton);
    buttons.add(openDataDirButton);
    buttons.add(clearLogButton);

    saveButton.addActionListener(_event -> saveFormConfig());
    runNowButton.addActionListener(_event -> runner.runNow());
    openBrowserButton.addActionListener(_event -> runner.openBrowserForLogin());
    openDataDirButton.addActionListener(_event -> openDataDir());
    clearLogButton.addActionListener(_event -> logs.setText(""));

    logs.setEditable(false);
    logs.setLineWrap(true);
    logs.setWrapStyleWord(true);
    JScrollPane logScroll = new JScrollPane(logs);
    logScroll.setBorder(BorderFactory.createTitledBorder("运行日志"));

    JPanel top = new JPanel(new BorderLayout());
    top.add(form, BorderLayout.CENTER);
    top.add(buttons, BorderLayout.SOUTH);

    status.setBorder(BorderFactory.createEmptyBorder(4, 12, 8, 12));
    add(top, BorderLayout.NORTH);
    add(logScroll, BorderLayout.CENTER);
    add(status, BorderLayout.SOUTH);
  }

  private void bindEvents() {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> logger.removeListener(this::appendLog)));
  }

  private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, java.awt.Component component) {
    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.weightx = 0;
    panel.add(new JLabel(label), gbc);
    gbc.gridx = 1;
    gbc.weightx = 1;
    panel.add(component, gbc);
  }

  private void addAreaRow(JPanel panel, GridBagConstraints gbc, int row, String label, JTextArea area) {
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    addRow(panel, gbc, row, label, new JScrollPane(area));
  }

  private void loadConfigToForm() {
    try {
      AppConfig config = store.loadConfig();
      enabled.setSelected(config.isEnabled());
      startWithWindows.setSelected(config.isStartWithWindows());
      intervalMinutes.setValue(config.getIntervalMinutes());
      fixedTime.setText(config.getFixedTime());
      targets.setText(String.join(System.lineSeparator(), config.getTargets()));
      messageText.setText(config.getMessageText());
      dailyLimit.setValue(config.getDailyLimit());
      cooldownMinutes.setValue(config.getCooldownMinutes());
      retryCount.setValue(config.getRetryCount());
      ensureStartupSetting(config);
      status.setText("状态：配置已读取，数据目录为 " + AppPaths.resolveDefaultAppDir());
    } catch (IOException error) {
      showError("读取配置失败：" + error.getMessage());
    }
  }

  private void ensureStartupSetting(AppConfig config) {
    try {
      if (config.isStartWithWindows() && !startupManager.isInstalled()) {
        startupManager.installForCurrentUser();
        logger.info("已安装当前用户开机自启入口。");
      }
      if (!config.isStartWithWindows() && startupManager.isInstalled()) {
        startupManager.uninstallForCurrentUser();
        logger.info("已移除当前用户开机自启入口。");
      }
    } catch (IOException error) {
      logger.warn("同步开机自启设置失败：" + error.getMessage());
    }
  }

  private void saveFormConfig() {
    try {
      AppConfig config = new AppConfig();
      config.setEnabled(enabled.isSelected());
      config.setStartWithWindows(startWithWindows.isSelected());
      config.setIntervalMinutes((Integer) intervalMinutes.getValue());
      config.setFixedTime(fixedTime.getText().trim());
      config.setTargets(Arrays.stream(targets.getText().split("\\R")).map(String::trim).filter(item -> !item.isBlank()).toList());
      config.setMessageText(messageText.getText().trim());
      config.setDailyLimit((Integer) dailyLimit.getValue());
      config.setCooldownMinutes((Integer) cooldownMinutes.getValue());
      config.setRetryCount((Integer) retryCount.getValue());
      store.saveConfig(config);
      if (config.isStartWithWindows()) {
        startupManager.installForCurrentUser();
      } else {
        startupManager.uninstallForCurrentUser();
      }
      logger.info("配置已保存。配置文件位置：" + AppPaths.resolveDefaultAppDir().resolve("config.json"));
      status.setText("状态：配置已保存，保存时间 " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()));
    } catch (Exception error) {
      showError("保存配置失败：" + error.getMessage());
    }
  }

  private void appendLog(String line) {
    SwingUtilities.invokeLater(() -> {
      logs.append(line + System.lineSeparator());
      logs.setCaretPosition(logs.getDocument().getLength());
      status.setText("状态：" + line);
    });
  }

  private void openDataDir() {
    try {
      Files.createDirectories(AppPaths.resolveDefaultAppDir());
      Desktop.getDesktop().open(AppPaths.resolveDefaultAppDir().toFile());
    } catch (Exception error) {
      showError("打开数据目录失败：" + error.getMessage());
    }
  }

  private void showError(String message) {
    logger.error(message);
    JOptionPane.showMessageDialog(this, message, "抖音自动续火花助手", JOptionPane.ERROR_MESSAGE);
  }
}
