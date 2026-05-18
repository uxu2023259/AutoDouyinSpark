const DEFAULT_SETTINGS = {
  enabled: true,
  intervalMinutes: 30,
  fixedTime: "16:00",
  targets: [],
  messageText: "续火花啦，记得回我一下~",
  dailyLimit: 20,
  cooldownMinutes: 60
};

const CHAT_PAGE_URL = "https://creator.douyin.com/creator-micro/data/following/chat";
const DASHBOARD_URL = "dashboard.html";
const EXECUTION_TIMEOUT_MS = 90000;
const STALE_LOCK_MS = 120000;
const CONTENT_TIMEOUT_MS = 35000;

const PHASES = {
  IDLE: "idle",
  PRECHECK: "precheck",
  TAB_READY: "tab_ready",
  PAGE_PROBE: "page_probe",
  LOCATING_TARGET: "locating_target",
  SWITCHING_TARGET: "switching_target",
  TYPING_MESSAGE: "typing_message",
  SENDING_MESSAGE: "sending_message",
  COMPLETED: "completed",
  FAILED: "failed"
};

let memoryExecutionLock = null;
let pinnedPanelWindowId = null;

async function getSettings() {
  const data = await chrome.storage.local.get("settings");
  return { ...DEFAULT_SETTINGS, ...(data.settings || {}) };
}

async function getRuntimeStatus() {
  const data = await chrome.storage.local.get("runtimeStatus");
  return data.runtimeStatus || {};
}

function createTaskId(triggerMode) {
  return `${triggerMode}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function todayKey() {
  return new Date().toISOString().slice(0, 10);
}

function nowMinuteKey() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  const hour = String(now.getHours()).padStart(2, "0");
  const minute = String(now.getMinutes()).padStart(2, "0");
  return `${year}-${month}-${day} ${hour}:${minute}`;
}

function isFreshLock(lock) {
  return Boolean(lock?.taskId) && typeof lock?.startedAt === "number" && Date.now() - lock.startedAt < STALE_LOCK_MS;
}

async function clearExecutionLock() {
  memoryExecutionLock = null;
  await chrome.storage.local.remove("executionLock");
}

async function acquireExecutionLock(triggerMode) {
  if (isFreshLock(memoryExecutionLock)) {
    return null;
  }

  const data = await chrome.storage.local.get("executionLock");
  if (isFreshLock(data.executionLock)) {
    return null;
  }

  const lock = {
    taskId: createTaskId(triggerMode),
    triggerMode,
    startedAt: Date.now()
  };
  memoryExecutionLock = lock;
  await chrome.storage.local.set({ executionLock: lock });
  return lock;
}

async function releaseExecutionLock(taskId) {
  const data = await chrome.storage.local.get("executionLock");
  if (!taskId || data.executionLock?.taskId === taskId) {
    await clearExecutionLock();
  } else {
    memoryExecutionLock = null;
  }
}

async function setRuntimeStatus(patch) {
  const current = await getRuntimeStatus();
  const nextStatus = {
    ...current,
    ...patch,
    lastRunAt: new Date().toLocaleString("zh-CN", { hour12: false })
  };
  await chrome.storage.local.set({ runtimeStatus: nextStatus });
  return nextStatus;
}

async function updateTaskStatus(taskId, patch) {
  const current = await getRuntimeStatus();
  if (current.taskId && current.taskId !== taskId) {
    return current;
  }
  return setRuntimeStatus({ ...patch, taskId });
}

async function setPhase(taskId, phase, phaseDetail, extra = {}) {
  const messageMap = {
    [PHASES.IDLE]: "待机中",
    [PHASES.PRECHECK]: "执行中",
    [PHASES.TAB_READY]: "执行中",
    [PHASES.PAGE_PROBE]: "执行中",
    [PHASES.LOCATING_TARGET]: "执行中",
    [PHASES.SWITCHING_TARGET]: "执行中",
    [PHASES.TYPING_MESSAGE]: "执行中",
    [PHASES.SENDING_MESSAGE]: "执行中",
    [PHASES.COMPLETED]: "执行完成",
    [PHASES.FAILED]: "执行失败"
  };

  return updateTaskStatus(taskId, {
    phase,
    phaseDetail,
    message: messageMap[phase] || "执行中",
    finishedAt: phase === PHASES.COMPLETED || phase === PHASES.FAILED ? new Date().toISOString() : null,
    ...extra
  });
}

async function setFailureStatus(taskId, errorCode, errorDetail, extra = {}) {
  return setPhase(taskId, PHASES.FAILED, errorDetail, {
    lastErrorCode: errorCode,
    lastErrorDetail: errorDetail,
    lastResult: errorDetail,
    ...extra
  });
}

async function updateAlarm() {
  const settings = await getSettings();
  await chrome.alarms.clear("chat-send");
  await chrome.alarms.clear("chat-send-fixed");
  if (!settings.enabled) {
    await setRuntimeStatus({
      phase: PHASES.IDLE,
      phaseDetail: "插件已禁用",
      message: "已停止",
      lastResult: "插件已禁用",
      finishedAt: new Date().toISOString()
    });
    return;
  }
  await chrome.alarms.create("chat-send", { periodInMinutes: settings.intervalMinutes });
  await chrome.alarms.create("chat-send-fixed", { periodInMinutes: 1 });
  await setRuntimeStatus({
    phase: PHASES.IDLE,
    phaseDetail: `已设置每 ${settings.intervalMinutes} 分钟巡检一次`,
    message: "待机中",
    lastResult: `已设置每 ${settings.intervalMinutes} 分钟巡检一次`,
    finishedAt: new Date().toISOString(),
    lastErrorCode: "",
    lastErrorDetail: ""
  });
}

async function incrementDailySent() {
  const current = await getRuntimeStatus();
  const key = todayKey();
  const sentToday = current.sentDate === key ? (current.sentToday || 0) + 1 : 1;
  await chrome.storage.local.set({
    runtimeStatus: {
      ...current,
      sentDate: key,
      sentToday
    }
  });
  return sentToday;
}

async function getSendState() {
  const data = await chrome.storage.local.get("sendState");
  return data.sendState || { perTarget: {}, fixedRuns: {} };
}

async function setSendState(sendState) {
  await chrome.storage.local.set({ sendState });
}

async function canSendToTarget(target, settings) {
  if (settings.cooldownMinutes <= 0) {
    return { allowed: true };
  }
  const sendState = await getSendState();
  const lastSentAt = sendState.perTarget?.[target];
  if (!lastSentAt) {
    return { allowed: true };
  }
  const elapsed = Date.now() - lastSentAt;
  const required = settings.cooldownMinutes * 60 * 1000;
  if (elapsed >= required) {
    return { allowed: true };
  }
  return { allowed: false, reason: `${target} 仍在冷却中` };
}

async function markSentTarget(target) {
  const sendState = await getSendState();
  sendState.perTarget = sendState.perTarget || {};
  sendState.perTarget[target] = Date.now();
  await setSendState(sendState);
}

async function shouldRunFixedTime(settings) {
  if (!settings.fixedTime) {
    return false;
  }
  const currentTime = new Date().toLocaleTimeString("zh-CN", {
    hour12: false,
    hour: "2-digit",
    minute: "2-digit"
  });
  if (currentTime !== settings.fixedTime) {
    return false;
  }
  const sendState = await getSendState();
  const key = nowMinuteKey();
  if (sendState.fixedRuns?.[key]) {
    return false;
  }
  sendState.fixedRuns = sendState.fixedRuns || {};
  sendState.fixedRuns[key] = true;
  await setSendState(sendState);
  return true;
}

async function ensureMessageTab() {
  const tabs = await chrome.tabs.query({ url: "https://creator.douyin.com/*" });
  const matchedTab = tabs.find((tab) => typeof tab.url === "string" && tab.url.includes("/creator-micro/data/following/chat"));
  if (matchedTab?.id) {
    return matchedTab;
  }
  return chrome.tabs.create({ url: CHAT_PAGE_URL, active: false });
}

async function refreshMessageTab(tabId) {
  try {
    await chrome.tabs.reload(tabId, { bypassCache: true });
    return true;
  } catch (_error) {
    return false;
  }
}

async function setPageRefreshedStatus(taskId, errorCode, errorDetail, extra = {}) {
  return setPhase(taskId, PHASES.FAILED, "页面未就绪，已自动刷新，等待下次重试", {
    message: "页面未就绪，已自动刷新",
    lastErrorCode: errorCode,
    lastErrorDetail: errorDetail,
    lastResult: "页面已刷新，等待下次重试",
    ...extra
  });
}

async function focusPinnedPanelWindow(windowId) {
  try {
    const panelWindow = await chrome.windows.get(windowId, { populate: false });
    if (panelWindow.state === "minimized") {
      await chrome.windows.update(windowId, { state: "normal" });
    }
    await chrome.windows.update(windowId, { focused: true, drawAttention: true });
  } catch (_error) {
    if (pinnedPanelWindowId === windowId) {
      pinnedPanelWindowId = null;
    }
  }
}

async function openPinnedPanelWindow() {
  if (pinnedPanelWindowId) {
    await focusPinnedPanelWindow(pinnedPanelWindowId);
    return { ok: true, windowId: pinnedPanelWindowId, reused: true };
  }

  const panelWindow = await chrome.windows.create({
    url: chrome.runtime.getURL(DASHBOARD_URL),
    type: "popup",
    width: 460,
    height: 920,
    focused: true
  });
  pinnedPanelWindowId = panelWindow.id || null;
  return { ok: true, windowId: pinnedPanelWindowId, reused: false };
}

function releasePinnedPanelWindow() {
  pinnedPanelWindowId = null;
  return { ok: true };
}

async function waitForTabComplete(tabId, timeoutMs = 20000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const tab = await chrome.tabs.get(tabId);
    if (tab.status === "complete") {
      return tab;
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error("创作者中心页面加载超时");
}

async function ensureContentScript(tabId) {
  try {
    await chrome.tabs.sendMessage(tabId, { type: "health-check" });
  } catch (_error) {
    await chrome.scripting.executeScript({
      target: { tabId },
      files: ["content.js"]
    });
  }
}

function withTimeout(promise, timeoutMs, message) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(message)), timeoutMs);
    promise
      .then((value) => {
        clearTimeout(timer);
        resolve(value);
      })
      .catch((error) => {
        clearTimeout(timer);
        reject(error);
      });
  });
}

async function probeChatPage(tabId, taskId, triggerMode) {
  await ensureContentScript(tabId);
  return withTimeout(
    chrome.tabs.sendMessage(tabId, {
      type: "probe-page",
      taskId,
      requireVisible: triggerMode === "manual"
    }),
    15000,
    "页面探测超时"
  );
}

async function runSingleTarget(tabId, settings, target, taskId) {
  await ensureContentScript(tabId);
  return withTimeout(
    chrome.tabs.sendMessage(tabId, {
      type: "chat-send",
      taskId,
      settings: {
        ...settings,
        targets: [target]
      },
      target
    }),
    CONTENT_TIMEOUT_MS,
    `处理目标 ${target} 超时`
  );
}

function buildResultSummary(results) {
  const successCount = results.filter((item) => item.success).length;
  const uncertainCount = results.filter((item) => item.deliveryStatus === "uncertain").length;
  const skippedCount = results.filter((item) => item.skipped).length;
  const failedCount = results.filter((item) => !item.success && !item.skipped).length;
  return {
    successCount,
    uncertainCount,
    skippedCount,
    failedCount
  };
}

async function executeCheck(triggerMode = "interval") {
  const lock = await acquireExecutionLock(triggerMode);
  if (!lock) {
    return setRuntimeStatus({
      phase: PHASES.PRECHECK,
        phaseDetail: "已有续火花任务正在执行，请稍后再试",
        message: "执行中",
        lastResult: "已有续火花任务正在执行，请稍后再试"
    });
  }

  const { taskId } = lock;
  const runPromise = (async () => {
    const settings = await getSettings();
    const currentStatus = await getRuntimeStatus();
    const sentToday = currentStatus.sentDate === todayKey() ? (currentStatus.sentToday || 0) : 0;
    const ignoreCooldown = triggerMode === "manual";

    await setRuntimeStatus({
      taskId,
      startedAt: new Date().toISOString(),
      finishedAt: null,
      phase: PHASES.PRECHECK,
      phaseDetail: "正在执行前置检查",
      message: "执行中",
      lastResult: "正在执行前置检查",
      lastErrorCode: "",
      lastErrorDetail: "",
      currentChatTarget: "",
      currentTargetKeyword: "",
      matchedConversationName: "",
      recentResults: []
    });

    if (!settings.enabled) {
      return setFailureStatus(taskId, "PLUGIN_DISABLED", "插件已禁用");
    }

    if (!settings.targets.length) {
      return setFailureStatus(taskId, "MISSING_TARGETS", "请先填写续火花目标会话关键词");
    }

    if (sentToday >= settings.dailyLimit) {
      return setFailureStatus(taskId, "DAILY_LIMIT_REACHED", `今日已达到 ${settings.dailyLimit} 次上限`);
    }

    const chatTab = await ensureMessageTab();
    const tabId = chatTab.id;
    if (!tabId) {
      return setFailureStatus(taskId, "TAB_NOT_FOUND", "未能创建或定位创作者聊天页标签");
    }

    if (triggerMode === "manual") {
      await chrome.windows.update(chatTab.windowId, { focused: true });
      await chrome.tabs.update(tabId, { active: true });
    }

    await setPhase(taskId, PHASES.TAB_READY, triggerMode === "manual" ? "已激活创作者聊天页，准备探测页面" : "已定位创作者聊天页，准备探测页面");
    try {
      await waitForTabComplete(tabId);
    } catch (error) {
      await refreshMessageTab(tabId);
      return setPageRefreshedStatus(taskId, "TAB_LOAD_TIMEOUT", error.message, { tabId });
    }

    await setPhase(taskId, PHASES.PAGE_PROBE, "正在确认页面可操作");
    let probe;
    try {
      probe = await probeChatPage(tabId, taskId, triggerMode);
    } catch (error) {
      await refreshMessageTab(tabId);
      return setPageRefreshedStatus(taskId, "PAGE_NOT_READY", error.message, { tabId });
    }
    if (!probe?.ok) {
      await refreshMessageTab(tabId);
      return setPageRefreshedStatus(taskId, probe?.errorCode || "PAGE_PROBE_FAILED", probe?.errorDetail || probe?.reason || "页面探测失败", {
        currentChatTarget: probe?.currentChatTarget || "",
        matchedConversationName: probe?.matchedConversationName || "",
        selectorTrace: probe?.selectorTrace || []
      });
    }

    await updateTaskStatus(taskId, {
      currentChatTarget: probe.currentChatTarget || "",
      matchedConversationName: probe.matchedConversationName || "",
      selectorTrace: probe.selectorTrace || [],
      lastResult: "页面探测通过"
    });

    const results = [];
    for (const target of settings.targets) {
      const liveStatus = await getRuntimeStatus();
      const currentSentToday = liveStatus.sentDate === todayKey() ? (liveStatus.sentToday || 0) : 0;
      if (currentSentToday >= settings.dailyLimit) {
        results.push({
          target,
          success: false,
          skipped: true,
          reason: `已达到今日 ${settings.dailyLimit} 次上限`
        });
        break;
      }

      if (!ignoreCooldown) {
        const cooldown = await canSendToTarget(target, settings);
        if (!cooldown.allowed) {
          results.push({
            target,
            success: false,
            skipped: true,
            reason: cooldown.reason
          });
          continue;
        }
      }

      await setPhase(taskId, PHASES.LOCATING_TARGET, `正在处理目标：${target}`, {
        currentTargetKeyword: target,
        matchedConversationName: "",
        lastResult: `正在处理目标：${target}`
      });

      let response;
      try {
        response = await runSingleTarget(tabId, settings, target, taskId);
      } catch (error) {
        await refreshMessageTab(tabId);
        return setPageRefreshedStatus(taskId, "CONTENT_NOT_READY", error.message, {
          currentTargetKeyword: target,
          recentResults: results.slice(-20)
        });
      }
      const baseResult = {
        target,
        success: Boolean(response?.success),
        skipped: Boolean(response?.skipped),
        phase: response?.phase || "",
        errorCode: response?.errorCode || "",
        reason: response?.reason || "",
        deliveryStatus: response?.deliveryStatus || "",
        currentChatTarget: response?.currentChatTarget || "",
        matchedConversationName: response?.matchedConversationName || "",
        selectedConversation: response?.selectedConversation || "",
        selectorTrace: response?.selectorTrace || []
      };

      if (response?.success && response.deliveryStatus === "confirmed") {
        await markSentTarget(target);
        const total = await incrementDailySent();
        results.push(baseResult);
        await updateTaskStatus(taskId, {
          sentToday: total,
          lastTarget: target,
          currentChatTarget: response.currentChatTarget || "",
          matchedConversationName: response.matchedConversationName || "",
          lastResult: `已向 ${target} 发送消息`,
          selectorTrace: response.selectorTrace || []
        });
        continue;
      }

      if (response?.success && response.deliveryStatus === "uncertain") {
        results.push(baseResult);
        await updateTaskStatus(taskId, {
          currentChatTarget: response.currentChatTarget || "",
          matchedConversationName: response.matchedConversationName || "",
          lastResult: `目标 ${target} 发送结果不确定，未计入成功次数`,
          lastErrorCode: "DELIVERY_UNCERTAIN",
          lastErrorDetail: response.reason || "发送结果不确定",
          selectorTrace: response.selectorTrace || []
        });
        continue;
      }

      if (response?.errorCode === "TARGET_AMBIGUOUS") {
        results.push({
          ...baseResult,
          success: false,
          skipped: true
        });
        await updateTaskStatus(taskId, {
          currentChatTarget: response?.currentChatTarget || "",
          matchedConversationName: response?.matchedConversationName || "",
          lastErrorCode: response?.errorCode || "TARGET_AMBIGUOUS",
          lastErrorDetail: response?.reason || "目标存在歧义，已跳过",
          lastResult: response?.reason || "目标存在歧义，已跳过",
          selectorTrace: response?.selectorTrace || []
        });
        continue;
      }

      results.push(baseResult);
      await updateTaskStatus(taskId, {
        currentChatTarget: response?.currentChatTarget || "",
        matchedConversationName: response?.matchedConversationName || "",
        lastErrorCode: response?.errorCode || "TARGET_PROCESS_FAILED",
        lastErrorDetail: response?.reason || "处理目标失败",
        lastResult: response?.reason || "处理目标失败",
        selectorTrace: response?.selectorTrace || []
      });
    }

    const summary = buildResultSummary(results);
    if (summary.successCount > 0) {
      return setPhase(taskId, PHASES.COMPLETED, `本次成功 ${summary.successCount} 个，结果不确定 ${summary.uncertainCount} 个，跳过 ${summary.skippedCount} 个，失败 ${summary.failedCount} 个`, {
        recentResults: results.slice(-20),
        lastResult: `本次成功 ${summary.successCount} 个，结果不确定 ${summary.uncertainCount} 个，跳过 ${summary.skippedCount} 个，失败 ${summary.failedCount} 个`
      });
    }

    if (summary.uncertainCount > 0) {
      return setFailureStatus(taskId, "ONLY_UNCERTAIN_RESULTS", `本次没有确认发送成功的目标，结果不确定 ${summary.uncertainCount} 个`, {
        recentResults: results.slice(-20)
      });
    }

    const lastFailure = [...results].reverse().find((item) => !item.success && !item.skipped) || results[0];
    return setFailureStatus(taskId, lastFailure?.errorCode || "NO_TARGET_SENT", lastFailure?.reason || "没有发送成功的目标", {
      recentResults: results.slice(-20)
    });
  })();

  try {
    return await withTimeout(runPromise, EXECUTION_TIMEOUT_MS, "执行总超时，请检查创作者中心页面是否正常打开");
  } catch (error) {
    return setFailureStatus(taskId, "EXECUTION_TIMEOUT", error.message);
  } finally {
    await releaseExecutionLock(taskId);
  }
}

chrome.runtime.onInstalled.addListener(async () => {
  const data = await chrome.storage.local.get("settings");
  if (!data.settings) {
    await chrome.storage.local.set({ settings: DEFAULT_SETTINGS });
  }
  await updateAlarm();
});

chrome.runtime.onStartup.addListener(updateAlarm);

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === "chat-send") {
    executeCheck("interval");
  }
  if (alarm.name === "chat-send-fixed") {
    getSettings().then(async (settings) => {
      if (await shouldRunFixedTime(settings)) {
        executeCheck("fixed");
      }
    });
  }
});

chrome.windows.onRemoved.addListener((windowId) => {
  if (windowId === pinnedPanelWindowId) {
    pinnedPanelWindowId = null;
  }
});

chrome.windows.onFocusChanged.addListener((windowId) => {
  if (!pinnedPanelWindowId || windowId === chrome.windows.WINDOW_ID_NONE || windowId === pinnedPanelWindowId) {
    return;
  }

  setTimeout(() => {
    if (pinnedPanelWindowId) {
      focusPinnedPanelWindow(pinnedPanelWindowId);
    }
  }, 120);
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message.type === "open-panel") {
    openPinnedPanelWindow().then(sendResponse);
    return true;
  }

  if (message.type === "release-panel-topmost") {
    sendResponse(releasePinnedPanelWindow());
    return false;
  }

  if (message.type === "settings-updated") {
    updateAlarm().then(() => sendResponse({ ok: true }));
    return true;
  }

  if (message.type === "task-progress") {
    const progress = message.progress || {};
    updateTaskStatus(message.taskId, {
      phase: progress.phase || PHASES.PRECHECK,
      phaseDetail: progress.phaseDetail || "",
      message: "执行中",
      currentChatTarget: progress.currentChatTarget || "",
      currentTargetKeyword: progress.currentTargetKeyword || "",
      matchedConversationName: progress.matchedConversationName || "",
      lastResult: progress.phaseDetail || progress.reason || "",
      lastErrorCode: progress.errorCode || "",
      lastErrorDetail: progress.errorDetail || progress.reason || "",
      selectorTrace: progress.selectorTrace || []
    }).then(() => sendResponse({ ok: true }));
    return true;
  }

  if (message.type === "run-now") {
    executeCheck("manual")
      .then((runtimeStatus) => sendResponse({ runtimeStatus }))
      .catch(async (error) => {
        const runtimeStatus = await setRuntimeStatus({
          phase: PHASES.FAILED,
          phaseDetail: `立即执行失败：${error.message}`,
          message: "执行失败",
          lastResult: `立即执行失败：${error.message}`,
          lastErrorCode: "RUN_NOW_FAILED",
          lastErrorDetail: error.message,
          finishedAt: new Date().toISOString()
        });
        sendResponse({ runtimeStatus });
      });
    return true;
  }

  return false;
});
