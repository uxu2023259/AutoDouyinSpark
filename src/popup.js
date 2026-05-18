const DEFAULT_SETTINGS = {
  enabled: true,
  intervalMinutes: 30,
  fixedTime: "16:00",
  targets: [],
  messageText: "续火花啦，记得回我一下~",
  dailyLimit: 20,
  cooldownMinutes: 60
};

const FIXED_CONFIG_PATH = "C:\\Users\\Administrator\\Downloads\\douyin-chat-config.json";
const FIXED_CONFIG_URL = "file:///C:/Users/Administrator/Downloads/douyin-chat-config.json";
const pageType = document.body.dataset.page || "home";

const elements = {
  enabled: document.getElementById("enabled"),
  intervalMinutes: document.getElementById("intervalMinutes"),
  fixedTime: document.getElementById("fixedTime"),
  targets: document.getElementById("targets"),
  messageText: document.getElementById("messageText"),
  dailyLimit: document.getElementById("dailyLimit"),
  cooldownMinutes: document.getElementById("cooldownMinutes"),
  saveButton: document.getElementById("saveButton"),
  reloadConfigButton: document.getElementById("reloadConfigButton"),
  settingsButton: document.getElementById("settingsButton"),
  backButton: document.getElementById("backButton"),
  openButton: document.getElementById("openButton"),
  panelButton: document.getElementById("panelButton"),
  releaseTopButton: document.getElementById("releaseTopButton"),
  runButton: document.getElementById("runButton"),
  statusText: document.getElementById("statusText")
};

async function loadSettings() {
  const data = await chrome.storage.local.get(["settings", "runtimeStatus"]);
  const settings = { ...DEFAULT_SETTINGS, ...(data.settings || {}) };
  if (elements.enabled) {
    elements.enabled.checked = settings.enabled;
  }
  if (elements.intervalMinutes) {
    elements.intervalMinutes.value = settings.intervalMinutes;
  }
  if (elements.fixedTime) {
    elements.fixedTime.value = settings.fixedTime;
  }
  if (elements.targets) {
    elements.targets.value = settings.targets.join("\n");
  }
  if (elements.messageText) {
    elements.messageText.value = settings.messageText;
  }
  if (elements.dailyLimit) {
    elements.dailyLimit.value = settings.dailyLimit;
  }
  if (elements.cooldownMinutes) {
    elements.cooldownMinutes.value = settings.cooldownMinutes;
  }
  renderStatus(data.runtimeStatus);
}

function renderStatus(runtimeStatus) {
  if (!runtimeStatus) {
    elements.statusText.textContent = "尚未运行";
    return;
  }

  const recentResults = Array.isArray(runtimeStatus.recentResults) ? runtimeStatus.recentResults.slice(-3) : [];
  const lines = [
    `最后运行：${runtimeStatus.lastRunAt || "无"}`,
    `状态：${runtimeStatus.message || "未知"}`,
    `阶段：${runtimeStatus.phase || "无"}`,
    `阶段详情：${runtimeStatus.phaseDetail || "无"}`,
    `任务编号：${runtimeStatus.taskId || "无"}`,
    `当前聊天对象：${runtimeStatus.currentChatTarget || "无"}`,
    `当前处理目标：${runtimeStatus.currentTargetKeyword || "无"}`,
    `命中会话：${runtimeStatus.matchedConversationName || "无"}`,
    `最后发送对象：${runtimeStatus.lastTarget || "无"}`,
    `今日发送：${runtimeStatus.sentToday || 0}`,
    `错误代码：${runtimeStatus.lastErrorCode || "无"}`,
    `错误详情：${runtimeStatus.lastErrorDetail || "无"}`,
    `最后结果：${runtimeStatus.lastResult || "无"}`
  ];

  if (recentResults.length) {
    lines.push("最近结果：");
    for (const item of recentResults) {
      const status = item.success ? (item.deliveryStatus === "uncertain" ? "结果不确定" : "成功") : item.skipped ? "跳过" : "失败";
      lines.push(`- ${item.target || "未知目标"}：${status} ${item.reason || ""}`.trim());
    }
  }

  elements.statusText.textContent = lines.join("\n");
}

function getSettingsFromForm() {
  return {
    enabled: elements.enabled.checked,
    intervalMinutes: Math.max(1, Number(elements.intervalMinutes.value) || DEFAULT_SETTINGS.intervalMinutes),
    fixedTime: elements.fixedTime ? (elements.fixedTime.value || DEFAULT_SETTINGS.fixedTime) : DEFAULT_SETTINGS.fixedTime,
    targets: elements.targets.value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean),
    messageText: elements.messageText.value.trim() || DEFAULT_SETTINGS.messageText,
    dailyLimit: Math.max(1, Number(elements.dailyLimit.value) || DEFAULT_SETTINGS.dailyLimit),
    cooldownMinutes: Math.max(0, Number(elements.cooldownMinutes?.value) || DEFAULT_SETTINGS.cooldownMinutes)
  };
}

function applySettingsToForm(settings) {
  elements.enabled.checked = settings.enabled;
  elements.intervalMinutes.value = settings.intervalMinutes;
  if (elements.fixedTime) {
    elements.fixedTime.value = settings.fixedTime;
  }
  elements.targets.value = settings.targets.join("\n");
  elements.messageText.value = settings.messageText;
  elements.dailyLimit.value = settings.dailyLimit;
  if (elements.cooldownMinutes) {
    elements.cooldownMinutes.value = settings.cooldownMinutes;
  }
}

async function saveSettings() {
  const settings = getSettingsFromForm();

  await chrome.storage.local.set({ settings });
  await chrome.runtime.sendMessage({ type: "settings-updated", settings });
  elements.statusText.textContent = `配置已保存。设置页默认读取固定文件：${FIXED_CONFIG_PATH}`;
}

async function loadFixedConfig({ syncToStorage = true } = {}) {
  const response = await fetch(FIXED_CONFIG_URL, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`固定配置文件读取失败：HTTP ${response.status}`);
  }
  const text = await response.text();
  const parsed = JSON.parse(text);
  const settings = { ...DEFAULT_SETTINGS, ...(parsed.settings || parsed) };
  applySettingsToForm(settings);
  if (syncToStorage) {
    await chrome.storage.local.set({ settings });
    await chrome.runtime.sendMessage({ type: "settings-updated", settings });
  }
  return settings;
}

async function openDouyin() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  const targetUrl = "https://creator.douyin.com/creator-micro/data/following/chat";
  if (tab?.id) {
    await chrome.tabs.update(tab.id, { url: targetUrl });
    return;
  }
  await chrome.tabs.create({ url: targetUrl });
}

async function openSettingsPage() {
  await chrome.windows.create({
    url: chrome.runtime.getURL("settings.html"),
    type: "popup",
    width: 460,
    height: 920,
    focused: true
  });
}

async function openPanel() {
  await chrome.runtime.sendMessage({ type: "open-panel" });
}

async function runNow() {
  elements.runButton.disabled = true;
  elements.statusText.textContent = "正在请求立即执行，请稍候...";
  try {
    const response = await chrome.runtime.sendMessage({ type: "run-now" });
    renderStatus(response?.runtimeStatus);
  } catch (error) {
    elements.statusText.textContent = `立即执行失败：${error.message}`;
  } finally {
    window.setTimeout(() => {
      elements.runButton.disabled = false;
    }, 800);
  }
}

async function goBackHome() {
  await chrome.windows.create({
    url: chrome.runtime.getURL("popup.html"),
    type: "popup",
    width: 400,
    height: 700,
    focused: true
  });
  window.close();
}

async function releaseTopWindow() {
  await chrome.runtime.sendMessage({ type: "release-panel-topmost" });
  window.close();
}

function bindEvents() {
  if (elements.saveButton) {
    elements.saveButton.addEventListener("click", saveSettings);
  }
  if (elements.reloadConfigButton) {
    elements.reloadConfigButton.addEventListener("click", async () => {
      try {
        await loadFixedConfig();
        elements.statusText.textContent = `已从固定文件读取配置：${FIXED_CONFIG_PATH}`;
      } catch (error) {
        elements.statusText.textContent = `读取固定配置失败：${error.message}\n如浏览器拦截，请在扩展详情中开启“允许访问文件网址”。`;
      }
    });
  }
  if (elements.settingsButton) {
    elements.settingsButton.addEventListener("click", openSettingsPage);
  }
  if (elements.backButton) {
    elements.backButton.addEventListener("click", goBackHome);
  }
  if (elements.openButton) {
    elements.openButton.addEventListener("click", openDouyin);
  }
  if (elements.releaseTopButton) {
    elements.releaseTopButton.addEventListener("click", releaseTopWindow);
  }
  if (elements.panelButton) {
  elements.panelButton.addEventListener("click", openPanel);
  }
  if (elements.runButton) {
    elements.runButton.addEventListener("click", runNow);
  }
}

chrome.storage.onChanged.addListener((changes) => {
  if (changes.runtimeStatus) {
    renderStatus(changes.runtimeStatus.newValue);
  }
});

async function initializePage() {
  bindEvents();
  await loadSettings();
  if (pageType === "settings") {
    try {
      await loadFixedConfig();
      elements.statusText.textContent = `已自动读取固定配置文件：${FIXED_CONFIG_PATH}`;
    } catch (error) {
      elements.statusText.textContent = `读取固定配置失败：${error.message}\n如浏览器拦截，请在扩展详情中开启“允许访问文件网址”。`;
    }
  }
}

initializePage();
