package com.douyin.autospark;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.KeyStroke;
import javax.swing.AbstractAction;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.function.Consumer;

public class MainWindow extends JFrame {
  private static final Color ACCENT = new Color(22, 119, 255);
  private static final Color SUBTLE_TEXT = new Color(95, 99, 104);

  private final MultiAccountManager accountManager;
  private final ChineseLogger logger;
  private final StartupManager startupManager;
  private final DefaultListModel<AccountProfile> accountModel = new DefaultListModel<>();
  private final JList<AccountProfile> accountList = new JList<>(accountModel);
  private final JLabel accountTitle = new JLabel("账号配置");
  private final JLabel accountHint = new JLabel("每个账号拥有独立登录态、配置与发送记录");
  private final JLabel status = new JLabel("状态：正在初始化");
  private final JLabel runtimeStatus = new JLabel("运行状态：读取中");
  private final JLabel saveStatus = new JLabel("配置修改后请保存");
  private final JCheckBox enabled = new JCheckBox("启用当前账号自动执行");
  private final JCheckBox headlessMode = new JCheckBox("服务器无界面模式（首次登录后启用）");
  private final JCheckBox startWithWindows = new JCheckBox("Windows 登录后自动启动整个程序");
  private final JCheckBox intervalModeEnabled = new JCheckBox("启用间隔模式");
  private final JSpinner intervalMinutes = new JSpinner(new SpinnerNumberModel(30, 1, 1440, 1));
  private final JCheckBox fixedModeEnabled = new JCheckBox("启用定时模式");
  private final JTextArea fixedTimes = new JTextArea(3, 14);
  private final JTextArea targets = new JTextArea(8, 28);
  private final JTextArea messageText = new JTextArea(5, 28);
  private final JSpinner dailyLimit = new JSpinner(new SpinnerNumberModel(20, 1, 500, 1));
  private final JSpinner cooldownMinutes = new JSpinner(new SpinnerNumberModel(60, 0, 10080, 1));
  private final JSpinner retryCount = new JSpinner(new SpinnerNumberModel(1, 0, 10, 1));
  private final JSpinner retryDelayMinutes = new JSpinner(new SpinnerNumberModel(60, 1, 1440, 1));
  private final JComboBox<String> humanizationPreset = new JComboBox<>(new String[]{"安全预设", "自定义"});
  private final JSpinner humanActionMin = new JSpinner(new SpinnerNumberModel(350, 200, 10000, 50));
  private final JSpinner humanActionMax = new JSpinner(new SpinnerNumberModel(1200, 200, 20000, 50));
  private final JSpinner humanTypingMin = new JSpinner(new SpinnerNumberModel(80, 40, 2000, 10));
  private final JSpinner humanTypingMax = new JSpinner(new SpinnerNumberModel(220, 40, 3000, 10));
  private final JSpinner humanTargetMin = new JSpinner(new SpinnerNumberModel(8, 3, 600, 1));
  private final JSpinner humanTargetMax = new JSpinner(new SpinnerNumberModel(20, 3, 1200, 1));
  private final JSpinner humanBatchSize = new JSpinner(new SpinnerNumberModel(5, 1, 100, 1));
  private final JSpinner humanRestMin = new JSpinner(new SpinnerNumberModel(45, 15, 3600, 5));
  private final JSpinner humanRestMax = new JSpinner(new SpinnerNumberModel(120, 15, 7200, 5));
  private final JTextArea logs = new JTextArea(20, 80);
  private final Consumer<String> logListener = this::appendLog;
  private boolean selectingAccount;

  public MainWindow(MultiAccountManager accountManager, ChineseLogger logger, StartupManager startupManager) {
    super("抖音自动续火花助手 · 多账号工作台");
    this.accountManager = accountManager;
    this.logger = logger;
    this.startupManager = startupManager;
    configureWindow();
    buildUi();
    bindEvents();
    bindKeyboardShortcuts();
    reloadAccounts(accountManager.selectedAccountId());
    synchronizeStartupSetting();
    logger.addListener(logListener);
    new Timer(2000, _event -> refreshRuntimeStatus()).start();
  }

  private void configureWindow() {
    setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
    setMinimumSize(new Dimension(1180, 800));
    setPreferredSize(new Dimension(1280, 860));
    setLayout(new BorderLayout());
  }

  private void buildUi() {
    add(buildHeader(), BorderLayout.NORTH);
    JSplitPane workspace = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildAccountSidebar(), buildWorkspace());
    workspace.setDividerLocation(270);
    workspace.setResizeWeight(0);
    workspace.setContinuousLayout(true);
    workspace.setBorder(BorderFactory.createEmptyBorder());
    add(workspace, BorderLayout.CENTER);
    status.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(225, 228, 232)),
        BorderFactory.createEmptyBorder(8, 16, 8, 16)));
    status.setForeground(SUBTLE_TEXT);
    add(status, BorderLayout.SOUTH);
  }

  private JPanel buildHeader() {
    JPanel header = new JPanel(new BorderLayout(16, 0));
    header.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
    JLabel brand = new JLabel("抖音自动续火花助手");
    brand.setFont(brand.getFont().deriveFont(Font.BOLD, 20f));
    JLabel edition = new JLabel("多账号工作台");
    edition.setForeground(ACCENT);
    JPanel title = new JPanel();
    title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));
    title.add(brand);
    title.add(Box.createVerticalStrut(3));
    title.add(edition);
    header.add(title, BorderLayout.WEST);

    JPanel actions = new JPanel();
    JButton save = primaryButton("保存当前账号");
    JButton run = new JButton("立即执行");
    JButton login = new JButton("打开登录/聊天页");
    save.addActionListener(_event -> saveSelectedAccount());
    run.addActionListener(_event -> selectedRuntime().runner().runNow());
    login.addActionListener(_event -> selectedRuntime().runner().openBrowserForLogin());
    actions.add(login);
    actions.add(run);
    actions.add(save);
    header.add(actions, BorderLayout.EAST);
    return header;
  }

  private JPanel buildAccountSidebar() {
    JPanel sidebar = new JPanel(new BorderLayout(0, 12));
    sidebar.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(225, 228, 232)),
        BorderFactory.createEmptyBorder(16, 14, 16, 14)));
    JLabel title = new JLabel("账号工作区");
    title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
    JLabel description = new JLabel("切换账号不会共享登录态");
    description.setForeground(SUBTLE_TEXT);
    JPanel heading = new JPanel();
    heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
    heading.add(title);
    heading.add(Box.createVerticalStrut(4));
    heading.add(description);
    sidebar.add(heading, BorderLayout.NORTH);

    accountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    accountList.setFixedCellHeight(48);
    accountList.setCellRenderer(new AccountCellRenderer());
    sidebar.add(new JScrollPane(accountList), BorderLayout.CENTER);

    JPanel accountActions = new JPanel(new GridLayout(0, 1, 0, 7));
    JButton add = primaryButton("新增账号");
    JButton rename = new JButton("重命名账号");
    JButton archive = new JButton("归档账号");
    add.addActionListener(_event -> createAccount());
    rename.addActionListener(_event -> renameAccount());
    archive.addActionListener(_event -> archiveAccount());
    accountActions.add(add);
    accountActions.add(rename);
    accountActions.add(archive);
    sidebar.add(accountActions, BorderLayout.SOUTH);
    return sidebar;
  }

  private JPanel buildWorkspace() {
    JPanel workspace = new JPanel(new BorderLayout(0, 12));
    workspace.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));
    accountTitle.setFont(accountTitle.getFont().deriveFont(Font.BOLD, 18f));
    accountHint.setForeground(SUBTLE_TEXT);
    runtimeStatus.setForeground(ACCENT);
    JPanel context = new JPanel(new BorderLayout());
    JPanel labels = new JPanel();
    labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
    labels.add(accountTitle);
    labels.add(Box.createVerticalStrut(4));
    labels.add(accountHint);
    context.add(labels, BorderLayout.WEST);
    context.add(runtimeStatus, BorderLayout.EAST);
    workspace.add(context, BorderLayout.NORTH);

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("发送策略", buildSendingPanel());
    tabs.addTab("运行维护", buildRuntimePanel());
    tabs.addTab("实时日志", buildLogPanel());
    workspace.add(tabs, BorderLayout.CENTER);
    return workspace;
  }

  private JComponent buildSendingPanel() {
    JPanel form = new JPanel(new GridBagLayout());
    form.setBorder(BorderFactory.createEmptyBorder(18, 18, 24, 18));
    int row = 0;
    addWideRow(form, row++, "账号开关", enabled, "关闭后仅暂停当前账号，不影响其他账号。");
    addWideRow(form, row++, "目标好友", scrollArea(targets), "每行一个名称或关键词；匹配到多个会话时会逐一全部发送，并自动去重。");
    addWideRow(form, row++, "续火花内容", scrollArea(messageText), "发送前会再次确认当前聊天对象。");
    JPanel intervalSchedule = new JPanel(new GridLayout(1, 2, 16, 0));
    intervalSchedule.add(fieldGroup("模式开关", intervalModeEnabled));
    intervalSchedule.add(fieldGroup("完成后间隔（分钟）", intervalMinutes));
    addWideRow(form, row++, "间隔模式", intervalSchedule, "从上一轮完成后开始计时，到点后随机延迟 0–5 分钟启动。");
    JPanel fixedSchedule = new JPanel(new GridLayout(1, 2, 16, 0));
    fixedSchedule.add(fieldGroup("模式开关", fixedModeEnabled));
    fixedSchedule.add(fieldGroup("每日时间点（每行一个 HH:mm）", new JScrollPane(fixedTimes)));
    addWideRow(form, row++, "定时模式", fixedSchedule, "支持多个每日时间点；恢复运行时仅补当天最近一次错过的定时。");
    JPanel limits = new JPanel(new GridLayout(1, 2, 16, 0));
    limits.add(fieldGroup("每日上限", dailyLimit));
    limits.add(fieldGroup("好友冷却（分钟）", cooldownMinutes));
    addWideRow(form, row++, "安全限制", limits, "限制与计数仅作用于当前账号；程序主动跳过不计为失败。");
    JPanel retry = new JPanel(new GridLayout(1, 2, 16, 0));
    retry.add(fieldGroup("最多额外重试次数", retryCount));
    retry.add(fieldGroup("重试间隔（分钟）", retryDelayMinutes));
    addWideRow(form, row++, "失败重试", retry, "仅重试失败会话；结果不确定时先核验消息，再决定是否发送。");
    JPanel humanBasic = new JPanel(new GridLayout(1, 3, 16, 0));
    humanBasic.add(fieldGroup("节奏方案", humanizationPreset));
    humanBasic.add(fieldGroup("操作停顿（毫秒，最小/最大）", rangePanel(humanActionMin, humanActionMax)));
    humanBasic.add(fieldGroup("逐字输入（毫秒，最小/最大）", rangePanel(humanTypingMin, humanTypingMax)));
    addWideRow(form, row++, "仿人节奏", humanBasic, "安全预设会固定采用保守随机范围；选择自定义后使用下列参数。");
    JPanel humanAdvanced = new JPanel(new GridLayout(1, 3, 16, 0));
    humanAdvanced.add(fieldGroup("目标间停顿（秒，最小/最大）", rangePanel(humanTargetMin, humanTargetMax)));
    humanAdvanced.add(fieldGroup("每批发送数量", humanBatchSize));
    humanAdvanced.add(fieldGroup("批次休息（秒，最小/最大）", rangePanel(humanRestMin, humanRestMax)));
    addWideRow(form, row, "仿人高级参数", humanAdvanced, "鼠标曲线、随机落点、按压停顿、滚动修正、标点停顿和发送后观察始终启用。");

    JPanel panel = new JPanel(new BorderLayout(0, 10));
    panel.add(new JScrollPane(form), BorderLayout.CENTER);
    panel.add(buildSendingActions(), BorderLayout.SOUTH);
    return panel;
  }

  private JPanel buildSendingActions() {
    JPanel actions = new JPanel(new BorderLayout(12, 0));
    actions.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(225, 228, 232)),
        BorderFactory.createEmptyBorder(12, 14, 8, 14)));
    JLabel hint = new JLabel("仅保存当前选中账号的发送策略与运行配置 · 快捷键 Ctrl+S");
    hint.setForeground(SUBTLE_TEXT);
    saveStatus.setForeground(SUBTLE_TEXT);
    JPanel feedback = new JPanel();
    feedback.setLayout(new BoxLayout(feedback, BoxLayout.Y_AXIS));
    feedback.add(hint);
    feedback.add(Box.createVerticalStrut(4));
    feedback.add(saveStatus);
    JButton save = primaryButton("保存当前账号配置");
    save.setPreferredSize(new Dimension(180, 38));
    save.addActionListener(_event -> saveSelectedAccount());
    actions.add(feedback, BorderLayout.CENTER);
    actions.add(save, BorderLayout.EAST);
    return actions;
  }

  private JPanel rangePanel(JSpinner minimum, JSpinner maximum) {
    JPanel panel = new JPanel(new GridLayout(1, 2, 8, 0));
    panel.add(minimum);
    panel.add(maximum);
    return panel;
  }

  private JComponent buildRuntimePanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
    panel.add(sectionCard("服务器运行", "无界面模式使用当前账号独立的浏览器资料目录，不依赖窗口刷新或前台焦点。", headlessMode));
    panel.add(Box.createVerticalStrut(12));
    panel.add(sectionCard("全局启动", "此设置作用于整个程序；程序启动后会同时守护所有已启用账号。", startWithWindows));
    panel.add(Box.createVerticalStrut(12));
    JPanel dataCard = new JPanel(new BorderLayout(12, 0));
    dataCard.setBorder(cardBorder("账号数据"));
    JLabel dataHint = new JLabel("配置、状态、截图和浏览器登录态均按账号隔离保存。");
    dataHint.setForeground(SUBTLE_TEXT);
    JButton openData = new JButton("打开当前账号目录");
    openData.addActionListener(_event -> openSelectedAccountDir());
    dataCard.add(dataHint, BorderLayout.CENTER);
    dataCard.add(openData, BorderLayout.EAST);
    panel.add(dataCard);
    panel.add(Box.createVerticalGlue());
    return panel;
  }

  private JComponent buildLogPanel() {
    logs.setEditable(false);
    logs.setLineWrap(true);
    logs.setWrapStyleWord(true);
    logs.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
    JPanel panel = new JPanel(new BorderLayout(0, 8));
    JPanel toolbar = new JPanel(new BorderLayout());
    JLabel hint = new JLabel("统一日志会标注账号名称，便于排查并行任务。");
    hint.setForeground(SUBTLE_TEXT);
    JButton clear = new JButton("清空窗口日志");
    clear.addActionListener(_event -> logs.setText(""));
    toolbar.add(hint, BorderLayout.WEST);
    toolbar.add(clear, BorderLayout.EAST);
    panel.add(toolbar, BorderLayout.NORTH);
    panel.add(new JScrollPane(logs), BorderLayout.CENTER);
    return panel;
  }

  private void addWideRow(JPanel panel, int row, String label, Component component, String help) {
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = row * 2;
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.insets = new Insets(8, 6, 4, 18);
    JLabel labelComponent = new JLabel(label);
    labelComponent.setFont(labelComponent.getFont().deriveFont(Font.BOLD));
    panel.add(labelComponent, constraints);
    constraints.gridx = 1;
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(6, 0, 2, 6);
    panel.add(component, constraints);
    constraints.gridy += 1;
    constraints.insets = new Insets(0, 0, 12, 6);
    JLabel helper = new JLabel(help);
    helper.setForeground(SUBTLE_TEXT);
    panel.add(helper, constraints);
    if (row == 4) {
      constraints.gridy += 1;
      constraints.weighty = 1;
      panel.add(Box.createVerticalGlue(), constraints);
    }
  }

  private JPanel fieldGroup(String label, JComponent field) {
    JPanel group = new JPanel(new BorderLayout(0, 5));
    JLabel title = new JLabel(label);
    title.setForeground(SUBTLE_TEXT);
    group.add(title, BorderLayout.NORTH);
    group.add(field, BorderLayout.CENTER);
    return group;
  }

  private JScrollPane scrollArea(JTextArea area) {
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    return new JScrollPane(area);
  }

  private JPanel sectionCard(String title, String description, JComponent control) {
    JPanel card = new JPanel(new BorderLayout(16, 10));
    card.setBorder(cardBorder(title));
    JLabel hint = new JLabel(description);
    hint.setForeground(SUBTLE_TEXT);
    card.add(hint, BorderLayout.CENTER);
    card.add(control, BorderLayout.SOUTH);
    return card;
  }

  private javax.swing.border.Border cardBorder(String title) {
    return BorderFactory.createCompoundBorder(
        BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(220, 224, 230)), title),
        BorderFactory.createEmptyBorder(10, 12, 12, 12));
  }

  private JButton primaryButton(String text) {
    JButton button = new JButton(text);
    button.setBackground(ACCENT);
    button.setForeground(Color.WHITE);
    button.setFocusPainted(false);
    return button;
  }

  private void bindEvents() {
    accountList.addListSelectionListener(event -> {
      if (event.getValueIsAdjusting() || selectingAccount || accountList.getSelectedValue() == null) {
        return;
      }
      try {
        accountManager.selectAccount(accountList.getSelectedValue().getId());
        loadSelectedAccount();
      } catch (IOException error) {
        showError("切换账号失败：" + error.getMessage());
      }
    });
  }

  private void bindKeyboardShortcuts() {
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
        KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
        "保存当前账号配置");
    getRootPane().getActionMap().put("保存当前账号配置", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent event) {
        saveSelectedAccount();
      }
    });
  }

  private void reloadAccounts(String selectedId) {
    selectingAccount = true;
    try {
      accountModel.clear();
      int selectedIndex = 0;
      for (AccountRuntime runtime : accountManager.runtimes()) {
        accountModel.addElement(runtime.profile());
        if (runtime.profile().getId().equals(selectedId)) {
          selectedIndex = accountModel.size() - 1;
        }
      }
      if (!accountModel.isEmpty()) {
        accountList.setSelectedIndex(selectedIndex);
      }
    } finally {
      selectingAccount = false;
    }
    loadSelectedAccount();
  }

  private void loadSelectedAccount() {
    AccountProfile profile = accountList.getSelectedValue();
    if (profile == null) {
      return;
    }
    try {
      AppConfig config = selectedRuntime().store().loadConfig();
      enabled.setSelected(config.isEnabled());
      headlessMode.setSelected(config.isHeadlessMode());
      startWithWindows.setSelected(accountManager.isStartWithWindows());
      intervalModeEnabled.setSelected(config.isIntervalModeEnabled());
      intervalMinutes.setValue(config.getIntervalMinutes());
      fixedModeEnabled.setSelected(config.isFixedModeEnabled());
      fixedTimes.setText(String.join(System.lineSeparator(), config.getFixedTimes()));
      targets.setText(String.join(System.lineSeparator(), config.getTargets()));
      messageText.setText(config.getMessageText());
      dailyLimit.setValue(config.getDailyLimit());
      cooldownMinutes.setValue(config.getCooldownMinutes());
      retryCount.setValue(config.getRetryCount());
      retryDelayMinutes.setValue(config.getRetryDelayMinutes());
      humanizationPreset.setSelectedItem("CUSTOM".equals(config.getHumanizationPreset()) ? "自定义" : "安全预设");
      humanActionMin.setValue(config.getHumanActionDelayMinMillis());
      humanActionMax.setValue(config.getHumanActionDelayMaxMillis());
      humanTypingMin.setValue(config.getHumanTypingDelayMinMillis());
      humanTypingMax.setValue(config.getHumanTypingDelayMaxMillis());
      humanTargetMin.setValue(config.getHumanTargetDelayMinSeconds());
      humanTargetMax.setValue(config.getHumanTargetDelayMaxSeconds());
      humanBatchSize.setValue(config.getHumanBatchSize());
      humanRestMin.setValue(config.getHumanBatchRestMinSeconds());
      humanRestMax.setValue(config.getHumanBatchRestMaxSeconds());
      accountTitle.setText("当前账号：" + profile.getName());
      accountHint.setText("账号标识：" + profile.getId() + " · 登录态与发送状态完全独立");
      refreshRuntimeStatus();
      status.setText("状态：已加载账号“" + profile.getName() + "”的独立配置");
    } catch (IOException error) {
      showError("读取账号配置失败：" + error.getMessage());
    }
  }

  private void saveSelectedAccount() {
    try {
      AppConfig config = new AppConfig();
      config.setEnabled(enabled.isSelected());
      config.setHeadlessMode(headlessMode.isSelected());
      config.setStartWithWindows(startWithWindows.isSelected());
      config.setIntervalModeEnabled(intervalModeEnabled.isSelected());
      config.setIntervalMinutes((Integer) intervalMinutes.getValue());
      config.setFixedModeEnabled(fixedModeEnabled.isSelected());
      config.setFixedTimes(Arrays.stream(fixedTimes.getText().split("\\R")).map(String::trim).filter(item -> !item.isBlank()).toList());
      config.setTargets(Arrays.stream(targets.getText().split("\\R")).map(String::trim).filter(item -> !item.isBlank()).toList());
      config.setMessageText(messageText.getText().trim());
      config.setDailyLimit((Integer) dailyLimit.getValue());
      config.setCooldownMinutes((Integer) cooldownMinutes.getValue());
      config.setRetryCount((Integer) retryCount.getValue());
      config.setRetryDelayMinutes((Integer) retryDelayMinutes.getValue());
      config.setHumanizationPreset("自定义".equals(humanizationPreset.getSelectedItem()) ? "CUSTOM" : "SAFE");
      config.setHumanActionDelayMinMillis((Integer) humanActionMin.getValue());
      config.setHumanActionDelayMaxMillis((Integer) humanActionMax.getValue());
      config.setHumanTypingDelayMinMillis((Integer) humanTypingMin.getValue());
      config.setHumanTypingDelayMaxMillis((Integer) humanTypingMax.getValue());
      config.setHumanTargetDelayMinSeconds((Integer) humanTargetMin.getValue());
      config.setHumanTargetDelayMaxSeconds((Integer) humanTargetMax.getValue());
      config.setHumanBatchSize((Integer) humanBatchSize.getValue());
      config.setHumanBatchRestMinSeconds((Integer) humanRestMin.getValue());
      config.setHumanBatchRestMaxSeconds((Integer) humanRestMax.getValue());
      selectedRuntime().store().saveConfig(config);
      accountManager.setStartWithWindows(startWithWindows.isSelected());
      synchronizeStartupSetting();
      String accountName = selectedRuntime().profile().getName();
      logger.info("账号“" + accountName + "”的配置已保存。");
      String savedAt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now());
      saveStatus.setText("最近保存：" + savedAt + " · 账号：" + accountName);
      saveStatus.setForeground(new Color(28, 133, 74));
      status.setText("状态：配置已保存 · " + savedAt);
      accountList.repaint();
    } catch (Exception error) {
      showError("保存配置失败：" + error.getMessage());
    }
  }

  private void createAccount() {
    String name = JOptionPane.showInputDialog(this, "请输入用于区分登录态的账号名称：", "新增账号", JOptionPane.PLAIN_MESSAGE);
    if (name == null) {
      return;
    }
    try {
      AccountRuntime runtime = accountManager.createAccount(name);
      reloadAccounts(runtime.profile().getId());
      status.setText("状态：新账号已创建，请先打开登录页完成登录");
    } catch (Exception error) {
      showError("新增账号失败：" + error.getMessage());
    }
  }

  private void renameAccount() {
    AccountProfile selected = accountList.getSelectedValue();
    if (selected == null) {
      return;
    }
    String name = JOptionPane.showInputDialog(this, "请输入新的账号名称：", selected.getName());
    if (name == null) {
      return;
    }
    try {
      accountManager.renameAccount(selected.getId(), name);
      reloadAccounts(selected.getId());
    } catch (Exception error) {
      showError("重命名账号失败：" + error.getMessage());
    }
  }

  private void archiveAccount() {
    AccountProfile selected = accountList.getSelectedValue();
    if (selected == null) {
      return;
    }
    int answer = JOptionPane.showConfirmDialog(this,
        "确认归档账号“" + selected.getName() + "”吗？\n账号将停止运行，配置和登录态会移动到归档目录，不会直接删除。",
        "归档账号", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
    if (answer != JOptionPane.OK_OPTION) {
      return;
    }
    try {
      accountManager.archiveAccount(selected.getId());
      reloadAccounts(accountManager.selectedAccountId());
    } catch (Exception error) {
      showError("归档账号失败：" + error.getMessage());
    }
  }

  private void synchronizeStartupSetting() {
    try {
      if (accountManager.isStartWithWindows() && !startupManager.isInstalled()) {
        startupManager.installForCurrentUser();
        logger.info("已安装当前用户开机自启入口。");
      } else if (!accountManager.isStartWithWindows() && startupManager.isInstalled()) {
        startupManager.uninstallForCurrentUser();
        logger.info("已移除当前用户开机自启入口。");
      }
    } catch (IOException error) {
      logger.warn("同步开机自启设置失败：" + error.getMessage());
    }
  }

  private void refreshRuntimeStatus() {
    AccountProfile selected = accountList.getSelectedValue();
    if (selected == null) {
      return;
    }
    AccountRuntime runtime = selectedRuntime();
    try {
      AppConfig config = runtime.store().loadConfig();
      SendState state = runtime.store().loadState();
      String runningText = runtime.runner().isRunning() ? "正在执行" : config.isEnabled() ? "守护中" : "已停用";
      String nextInterval = state.getNextIntervalRunAt() == null ? "未安排" : DateTimeFormatter.ofPattern("MM-dd HH:mm").format(state.getNextIntervalRunAt());
      String nextRetry = state.getPendingRetries().stream().map(PendingRetry::getDueAt).min(LocalDateTime::compareTo)
          .map(time -> DateTimeFormatter.ofPattern("MM-dd HH:mm").format(time)).orElse("无");
      String blocked = state.getBlockedReason().isBlank() ? "" : " · 需人工处理";
      runtimeStatus.setText("运行状态：" + runningText + blocked + " · 今日成功 " + state.getSentToday()
          + " 次 · 下次间隔 " + nextInterval + " · 待重试 " + nextRetry);
    } catch (IOException error) {
      runtimeStatus.setText("运行状态：读取失败");
    }
  }

  private AccountRuntime selectedRuntime() {
    AccountProfile selected = accountList.getSelectedValue();
    if (selected == null) {
      throw new IllegalStateException("请先选择账号");
    }
    return accountManager.runtime(selected.getId());
  }

  private void openSelectedAccountDir() {
    try {
      AccountPaths paths = selectedRuntime().paths();
      Files.createDirectories(paths.accountDir());
      Desktop.getDesktop().open(paths.accountDir().toFile());
    } catch (Exception error) {
      showError("打开账号目录失败：" + error.getMessage());
    }
  }

  private void appendLog(String line) {
    SwingUtilities.invokeLater(() -> {
      logs.append(line + System.lineSeparator());
      logs.setCaretPosition(logs.getDocument().getLength());
      status.setText("状态：" + line);
    });
  }

  private void showError(String message) {
    logger.error(message);
    JOptionPane.showMessageDialog(this, message, "抖音自动续火花助手", JOptionPane.ERROR_MESSAGE);
  }

  @Override
  public void dispose() {
    logger.removeListener(logListener);
    super.dispose();
  }

  private static final class AccountCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
      JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
      if (value instanceof AccountProfile account) {
        label.setText("  " + account.getName());
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setToolTipText("账号标识：" + account.getId());
      }
      label.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
      return label;
    }
  }
}
