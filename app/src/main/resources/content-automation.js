const WAIT_TIMEOUT = 15000;
const PAGE_READY_TIMEOUT = 45000;
const CHAT_PATH = "/creator-micro/data/following/chat";
const PHASES = {
  PAGE_PROBE: "page_probe",
  LOCATING_TARGET: "locating_target",
  SWITCHING_TARGET: "switching_target",
  TYPING_MESSAGE: "typing_message",
  SENDING_MESSAGE: "sending_message",
  COMPLETED: "completed",
  FAILED: "failed"
};

function sleep(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function normalize(text) {
  return (text || "").replace(/\s+/g, " ").trim();
}

function normalizeForMatch(text) {
  return normalize(text).toLowerCase();
}

function matchesTarget(candidate, target) {
  const normalizedCandidate = normalizeForMatch(candidate);
  const normalizedTarget = normalizeForMatch(target);
  if (!normalizedCandidate || !normalizedTarget) {
    return false;
  }
  return normalizedCandidate === normalizedTarget;
}

function visible(element) {
  if (!element) {
    return false;
  }
  const style = window.getComputedStyle(element);
  const rect = element.getBoundingClientRect();
  return style.display !== "none" && style.visibility !== "hidden" && rect.width > 0 && rect.height > 0;
}

async function waitFor(checker, timeout = WAIT_TIMEOUT, interval = 250) {
  const started = Date.now();
  while (Date.now() - started < timeout) {
    const value = checker();
    if (value) {
      return value;
    }
    await sleep(interval);
  }
  return null;
}

function createFailure(errorCode, reason, extra = {}) {
  return {
    ok: false,
    success: false,
    skipped: false,
    errorCode,
    errorDetail: reason,
    reason,
    phase: extra.phase || PHASES.FAILED,
    currentChatTarget: extra.currentChatTarget || "",
    selectedConversation: extra.selectedConversation || "",
    matchedConversationName: extra.matchedConversationName || "",
    selectorTrace: extra.selectorTrace || [],
    deliveryStatus: "",
    outcome: extra.outcome || "FAILED",
    attemptId: extra.attemptId || "",
    securityPrompt: extra.securityPrompt || ""
  };
}

function dedupeElements(elements) {
  return Array.from(new Set(elements.filter(Boolean)));
}

function textIncludesSend(button) {
  return normalize(button?.textContent).includes("发送");
}

function getClickableConversationItems(selectorTrace) {
  const grids = Array.from(document.querySelectorAll("[role='grid'].ReactVirtualized__Grid, [role='grid'][aria-label='grid']"))
    .filter((element) => visible(element) && element.getBoundingClientRect().left < window.innerWidth * 0.5);
  const gridCells = grids.flatMap((grid) => Array.from(grid.querySelectorAll("[role='gridcell']")));
  const listItems = grids.flatMap((grid) => Array.from(grid.querySelectorAll("li[role='list-item'], .semi-list-item")));
  const source = gridCells.length ? gridCells : listItems;
  const candidates = dedupeElements(source).filter((element) => {
    if (!visible(element)) {
      return false;
    }
    const rect = element.getBoundingClientRect();
    return rect.left < window.innerWidth * 0.5
      && rect.width > 80
      && rect.height >= 24
      && Boolean(element.querySelector("[class*='item-header-name']"));
  });

  if (candidates.length) {
    selectorTrace.push(gridCells.length ? "conversation-items:actual-gridcell" : "conversation-items:actual-list-item");
  }
  return candidates;
}

function findConversationNameNode(item) {
  const selectors = [
    "[class*='item-header-name']",
    "[class*='author-name']",
    "strong",
    "span",
    "div"
  ];
  for (const selector of selectors) {
    const nodes = Array.from(item.querySelectorAll(selector)).filter(visible);
    const node = nodes.find((entry) => normalize(entry.textContent).length > 0 && normalize(entry.textContent).length <= 40);
    if (node) {
      return node;
    }
  }
  return null;
}

function getConversationName(item) {
  return normalize(findConversationNameNode(item)?.textContent);
}

function getConversationIdentity(item) {
  if (!item) {
    return "";
  }
  const identityNodes = [item, ...Array.from(item.querySelectorAll("[data-conversation-id], [data-user-id], [data-id], [data-node-key], [data-row-key], a[href]"))];
  const identityAttributes = ["data-conversation-id", "data-user-id", "data-id", "data-node-key", "data-row-key", "href"];
  for (const node of identityNodes) {
    for (const attribute of identityAttributes) {
      const value = node.getAttribute(attribute);
      if (value) {
        return `${attribute}:${value}`;
      }
    }
  }
  return item.id ? `id:${item.id}` : "";
}

function isSelectedConversation(item) {
  if (!item) {
    return false;
  }
  if (item.getAttribute("aria-selected") === "true" || item.getAttribute("aria-current") === "true") {
    return true;
  }
  const className = typeof item.className === "string" ? item.className.toLowerCase() : "";
  if (className.includes("active-") || className.includes("selected-")) {
    return true;
  }
  return Boolean(item.querySelector(":scope > [role='list-item'][class*='active-'], :scope > [role='list-item'][class*='selected-'], :scope > [class*='selected-']"));
}

function getSelectedConversationName(selectorTrace) {
  const item = getClickableConversationItems(selectorTrace).find(isSelectedConversation);
  if (item) {
    selectorTrace.push("selected-conversation:actual-grid-item");
  }
  return item ? getConversationName(item) : "";
}

function findConversationList(selectorTrace) {
  const candidates = dedupeElements([
    ...Array.from(document.querySelectorAll("[role='grid'].ReactVirtualized__Grid")),
    ...Array.from(document.querySelectorAll("[role='grid']")),
    ...Array.from(document.querySelectorAll("[role='list']")),
    ...Array.from(document.querySelectorAll(".ReactVirtualized__Grid")),
    ...Array.from(document.querySelectorAll("[style*='overflow: auto']"))
  ]).filter((element) => {
    if (!visible(element)) {
      return false;
    }
    const rect = element.getBoundingClientRect();
    const style = window.getComputedStyle(element);
    return rect.left < window.innerWidth * 0.5 && rect.height > 100 && (style.overflowY === "auto" || style.overflowY === "scroll" || element.scrollHeight > element.clientHeight);
  });

  if (!candidates.length) {
    return null;
  }

  selectorTrace.push("conversation-list:role-grid/scrollable");
  return candidates.sort((a, b) => b.clientHeight - a.clientHeight)[0];
}

function getCurrentChatTarget(selectorTrace) {
  const headerCandidates = dedupeElements([
    ...Array.from(document.querySelectorAll("[class*='box-header-name']")),
    ...Array.from(document.querySelectorAll("[class*='header-name']")),
    ...Array.from(document.querySelectorAll("[class*='box-header'] strong")),
    ...Array.from(document.querySelectorAll("[class*='box-header'] *")),
    ...Array.from(document.querySelectorAll("strong"))
  ]).filter((element) => {
    if (!visible(element)) {
      return false;
    }
    const rect = element.getBoundingClientRect();
    const text = normalize(element.textContent).replace(/查看Ta的主页/g, "").trim();
    return rect.left > window.innerWidth * 0.3 && rect.top < 220 && rect.width > 20 && text.length > 0 && text.length <= 40;
  }).map((element) => {
    const rect = element.getBoundingClientRect();
    return {
      element,
      rect,
      text: normalize(element.textContent).replace(/查看Ta的主页/g, "").trim()
    };
  }).sort((a, b) => {
    if (a.rect.top !== b.rect.top) {
      return a.rect.top - b.rect.top;
    }
    return a.rect.left - b.rect.left;
  });

  if (headerCandidates.length) {
    selectorTrace.push("chat-header:top-right-text");
    return headerCandidates[0].text;
  }

  return "";
}

function findEditor(selectorTrace) {
  const editorCandidates = dedupeElements([
    ...Array.from(document.querySelectorAll("[class*='chat-editor-'] [class*='chat-input-'][contenteditable='true']")),
    ...Array.from(document.querySelectorAll("[contenteditable='true']")),
    ...Array.from(document.querySelectorAll("[role='textbox']"))
  ]).filter((element) => {
    if (!visible(element)) {
      return false;
    }
    const rect = element.getBoundingClientRect();
    return rect.left > window.innerWidth * 0.25 && rect.bottom > window.innerHeight * 0.6;
  });

  if (editorCandidates.length) {
    selectorTrace.push("editor:actual-chat-input");
    return editorCandidates[0];
  }

  return null;
}

function findSendButton(selectorTrace) {
  const buttonCandidates = dedupeElements([
    ...Array.from(document.querySelectorAll("[class*='chat-editor-'] button")),
    ...Array.from(document.querySelectorAll("button")),
    ...Array.from(document.querySelectorAll("[role='button']"))
  ]).filter((element) => {
    if (!visible(element)) {
      return false;
    }
    const rect = element.getBoundingClientRect();
    return rect.left > window.innerWidth * 0.4 && rect.bottom > window.innerHeight * 0.6;
  });

  const sendButton = buttonCandidates.find((button) => normalize(button.textContent) === "发送")
    || buttonCandidates.find(textIncludesSend);
  if (sendButton) {
    selectorTrace.push("send-button:actual-chat-editor-text");
    return sendButton;
  }

  return null;
}

function isOnChatPage() {
  return location.pathname.includes(CHAT_PATH);
}

function detectLoginRequired() {
  const pageText = normalize(document.body?.innerText || "");
  return /(请登录|扫码登录|登录后|验证码|手机号登录|抖音扫码)/.test(pageText);
}

function detectSecurityPrompt() {
  const dialogs = dedupeElements([
    ...Array.from(document.querySelectorAll("[role='dialog']")),
    ...Array.from(document.querySelectorAll(".semi-modal")),
    ...Array.from(document.querySelectorAll(".semi-toast, .semi-notification, [role='alert']")),
    ...Array.from(document.querySelectorAll("[class*='captcha']")),
    ...Array.from(document.querySelectorAll("[class*='verify']"))
  ]).filter(visible);
  const prompt = dialogs.map((element) => normalize(element.textContent)).find((text) =>
    /(安全验证|验证码|操作频繁|行为异常|风险提示|完成验证|滑块)/.test(text));
  return prompt || "";
}

async function waitForPageReady(selectorTrace) {
  if (!isOnChatPage()) {
    return createFailure("NOT_CHAT_PAGE", "当前页面不是聊天页，未执行", {
      phase: PHASES.PAGE_PROBE,
      selectorTrace
    });
  }

  const startedAt = Date.now();
  while (Date.now() - startedAt < PAGE_READY_TIMEOUT) {
    const securityPrompt = detectSecurityPrompt();
    if (securityPrompt) {
      return createFailure("SECURITY_CHECK_REQUIRED", "页面要求人工完成安全验证", {
        phase: PHASES.PAGE_PROBE,
        selectorTrace,
        outcome: "BLOCKED",
        securityPrompt
      });
    }
    if (detectLoginRequired()) {
      return createFailure("LOGIN_REQUIRED", "登录状态已失效，请在浏览器中重新登录抖音创作者中心", {
        phase: PHASES.PAGE_PROBE,
        selectorTrace,
        outcome: "BLOCKED"
      });
    }
    const currentTrace = [];
    const list = findConversationList(currentTrace);
    if (list) {
      selectorTrace.push(...currentTrace);
      const currentChatTarget = getCurrentChatTarget(selectorTrace);
      return {
        ok: true,
        success: true,
        skipped: false,
        phase: PHASES.PAGE_PROBE,
        currentChatTarget,
        matchedConversationName: "",
        selectorTrace
      };
    }
    await sleep(500);
  }

  return createFailure("PAGE_NOT_READY", "聊天页面等待 45 秒后仍未加载完成，本次保留页面并等待下次巡检，不执行刷新", {
    phase: PHASES.PAGE_PROBE,
    selectorTrace
  });
}

function classifyMatches(items, target) {
  const normalizedTarget = normalizeForMatch(target);
  const mapped = items
    .map((item) => ({
      node: item,
      name: getConversationName(item),
      selected: isSelectedConversation(item),
      identity: getConversationIdentity(item)
    }))
    .filter((item) => item.name);

  return mapped.filter((item) => normalizeForMatch(item.name) === normalizedTarget);
}

function conversationKey(item, list) {
  const listRect = list?.getBoundingClientRect();
  const itemRect = item.node?.getBoundingClientRect();
  const approximateTop = listRect && itemRect
    ? Math.round((list?.scrollTop || 0) + itemRect.top - listRect.top)
    : 0;
  return item.identity
    ? `identity:${item.identity}`
    : `name:${normalizeForMatch(item.name)}|top:${approximateTop}`;
}

async function scanMatchingConversations(target, selectorTrace) {
  const list = await waitFor(() => findConversationList(selectorTrace), WAIT_TIMEOUT, 300);
  if (!list) {
    return createFailure("CONVERSATION_LIST_MISSING", "没有找到会话列表，页面可能尚未准备好", {
      phase: PHASES.LOCATING_TARGET,
      selectorTrace
    });
  }

  list.scrollTop = 0;
  await sleep(300);

  const exactMatches = new Map();
  let lastScrollTop = -1;
  while (true) {
    const items = getClickableConversationItems(selectorTrace);
    const matches = classifyMatches(items, target);
    for (const match of matches) {
      exactMatches.set(conversationKey(match, list), match);
    }

    const maxScrollTop = Math.max(0, list.scrollHeight - list.clientHeight);
    if (list.scrollTop >= maxScrollTop || list.scrollTop === lastScrollTop) {
      break;
    }

    lastScrollTop = list.scrollTop;
    list.scrollTop = Math.min(maxScrollTop, list.scrollTop + Math.max(list.clientHeight - 40, 220));
    list.dispatchEvent(new Event("scroll", { bubbles: true }));
    await sleep(500);
  }

  return { ok: true, matches: [...exactMatches.values()], selectorTrace };
}

async function discoverTargetConversations(target, selectorTrace) {
  const scan = await scanMatchingConversations(target, selectorTrace);
  if (!scan.ok) {
    return scan;
  }
  if (!scan.matches.length) {
    return createFailure("TARGET_NOT_FOUND", `左侧列表未找到目标用户：${target}`, {
      phase: PHASES.LOCATING_TARGET,
      selectorTrace
    });
  }

  if (scan.matches.length > 1) {
    return createFailure("TARGET_AMBIGUOUS", `找到 ${scan.matches.length} 个同名会话，无法确认指定用户：${target}`, {
      phase: PHASES.LOCATING_TARGET,
      selectorTrace
    });
  }

  const conversations = scan.matches.map((match) => ({
    query: target,
    name: match.name,
    identity: match.identity
  }));

  return {
    ok: true,
    errorCode: "",
    reason: `已匹配到 ${conversations.length} 个会话`,
    conversations
  };
}

async function findTargetConversation(conversation, selectorTrace) {
  const target = conversation.name || conversation.query;
  const expectedIdentity = conversation.identity || "";
  const scan = await scanMatchingConversations(target, selectorTrace);
  if (!scan.ok) return scan;
  if (scan.matches.length > 1) {
    return createFailure("TARGET_AMBIGUOUS", `找到 ${scan.matches.length} 个同名会话，无法确认指定用户：${target}`, {
      phase: PHASES.LOCATING_TARGET,
      selectorTrace
    });
  }
  if (!scan.matches.length) {
    return createFailure("TARGET_NOT_FOUND", `左侧列表未找到目标会话：${target}`, {
      phase: PHASES.LOCATING_TARGET,
      selectorTrace
    });
  }
  const uniqueMatch = scan.matches[0];
  if (expectedIdentity && uniqueMatch.identity !== expectedIdentity) {
    return createFailure("TARGET_CHANGED", `目标用户“${target}”的会话标识已变化；未发送`, {
      phase: PHASES.LOCATING_TARGET,
      selectorTrace
    });
  }

  const list = await waitFor(() => findConversationList(selectorTrace), WAIT_TIMEOUT, 300);
  if (!list) {
    return createFailure("CONVERSATION_LIST_MISSING", "没有找到会话列表，页面可能尚未准备好", {
      phase: PHASES.LOCATING_TARGET,
      selectorTrace
    });
  }

  list.scrollTop = 0;
  await sleep(300);
  let lastScrollTop = -1;
  while (true) {
    const items = getClickableConversationItems(selectorTrace)
      .map((node) => ({ node, name: getConversationName(node), identity: getConversationIdentity(node) }))
      .filter((item) => item.name && normalizeForMatch(item.name) === normalizeForMatch(target));
    const identityMatch = expectedIdentity && items.find((item) => item.identity === expectedIdentity);
    const match = identityMatch || (!expectedIdentity ? items[0] : null);
    if (match) {
      return {
        ok: true,
        target,
        node: match.node,
        matchedConversationName: match.name,
        selectorTrace
      };
    }

    const maxScrollTop = Math.max(0, list.scrollHeight - list.clientHeight);
    if (list.scrollTop >= maxScrollTop || list.scrollTop === lastScrollTop) {
      return createFailure("TARGET_NOT_FOUND", `左侧列表未找到目标会话：${target}`, {
        phase: PHASES.LOCATING_TARGET,
        selectorTrace
      });
    }
    lastScrollTop = list.scrollTop;
    list.scrollTop = Math.min(maxScrollTop, list.scrollTop + Math.max(list.clientHeight - 40, 220));
    list.dispatchEvent(new Event("scroll", { bubbles: true }));
    await sleep(500);
  }
}

function editorText(editor) {
  return normalize(editor.innerText || editor.textContent);
}

function sendSessions() {
  window.__douyinAutoSparkSendSessions = window.__douyinAutoSparkSendSessions || {};
  return window.__douyinAutoSparkSendSessions;
}

function elementBounds(element) {
  if (!element || !visible(element)) {
    return null;
  }
  const rect = element.getBoundingClientRect();
  return { x: rect.x, y: rect.y, width: rect.width, height: rect.height };
}

function exactOutgoingMessageElements(messageText) {
  const expected = normalize(messageText);
  if (!expected) {
    return [];
  }
  const outgoingItems = Array.from(document.querySelectorAll("[class*='box-item-'][class*='is-me-']"));
  return dedupeElements(outgoingItems).filter((item) => {
    if (!visible(item)) {
      return false;
    }
    const textNodes = Array.from(item.querySelectorAll("[class*='text-item-message-'], [class*='text-prz9K6'], pre"));
    return textNodes.some((node) => visible(node) && normalize(node.textContent) === expected);
  });
}

function messageFingerprint(element, index) {
  const attributes = ["data-message-id", "data-msg-id", "data-id", "data-key", "data-node-key"];
  let current = element;
  for (let depth = 0; current && depth < 3; depth += 1, current = current.parentElement) {
    for (const attribute of attributes) {
      const value = current.getAttribute?.(attribute);
      if (value) {
        return `${attribute}:${value}`;
      }
    }
  }
  return "";
}

function messageTimestampEpochMillis(element) {
  const nodes = [element, ...Array.from(element.querySelectorAll("[data-timestamp], [data-time], time[datetime]"))];
  let current = element.parentElement;
  for (let depth = 0; current && depth < 2; depth += 1, current = current.parentElement) {
    nodes.push(current);
  }
  for (const node of dedupeElements(nodes)) {
    for (const attribute of ["data-timestamp", "data-time", "datetime"]) {
      const raw = node.getAttribute?.(attribute);
      if (!raw) continue;
      const numeric = Number(raw);
      if (Number.isFinite(numeric) && numeric > 0) {
        return numeric < 100000000000 ? numeric * 1000 : numeric;
      }
      const parsed = Date.parse(raw);
      if (Number.isFinite(parsed)) {
        return parsed;
      }
    }
  }
  return 0;
}

function messageIsPending(element) {
  const status = element.querySelector("[class*='box-item-message-status-']");
  if (!status) {
    return false;
  }
  const signal = `${status.className || ""} ${status.textContent || ""} ${status.getAttribute("title") || ""} ${status.getAttribute("aria-label") || ""}`.toLowerCase();
  return Boolean(status.querySelector("[class*='sending-']")) || /发送中|正在发送|sending|loading/.test(signal);
}

function messageHasExplicitFailure(element) {
  const status = element.querySelector("[class*='box-item-message-status-']");
  if (!status || !visible(status)) {
    return false;
  }
  const signal = `${status.className || ""} ${status.textContent || ""} ${status.getAttribute("title") || ""} ${status.getAttribute("aria-label") || ""}`.toLowerCase();
  return /发送失败|发送未成功|重新发送|点击重试|send-fail|failed|error/.test(signal);
}

function captureMessageEvidence(messageText) {
  const elements = exactOutgoingMessageElements(messageText);
  const pendingElements = elements.filter(messageIsPending);
  const failedElements = elements.filter(messageHasExplicitFailure);
  const deliverableElements = elements.filter((element) => !pendingElements.includes(element) && !failedElements.includes(element));
  const fingerprintOf = (element) => messageFingerprint(element, elements.indexOf(element));
  return {
    elements,
    count: elements.length,
    fingerprints: elements.map(messageFingerprint).filter(Boolean),
    pendingElements,
    failedElements,
    deliverableElements,
    pendingFingerprints: pendingElements.map(fingerprintOf).filter(Boolean),
    failedFingerprints: failedElements.map(fingerprintOf).filter(Boolean),
    deliverableFingerprints: deliverableElements.map(fingerprintOf).filter(Boolean),
    deliverableTimestamps: deliverableElements.map(messageTimestampEpochMillis).filter((value) => value > 0),
    pendingCount: pendingElements.length,
    failedCount: failedElements.length,
    deliverableCount: deliverableElements.length
  };
}

async function prepareTarget(input) {
  const selectorTrace = [];
  const attemptId = input.attemptId || `尝试-${Date.now()}`;
  const probe = await waitForPageReady(selectorTrace);
  if (!probe.ok) {
    probe.attemptId = attemptId;
    return probe;
  }
  const targetInfo = await findTargetConversation(input.target || {}, selectorTrace);
  if (!targetInfo.ok) {
    targetInfo.attemptId = attemptId;
    return targetInfo;
  }
  const actionBounds = elementBounds(targetInfo.node);
  if (!actionBounds) {
    return createFailure("TARGET_BOUNDS_MISSING", "目标会话当前不可见，未执行点击", {
      phase: PHASES.LOCATING_TARGET,
      attemptId,
      selectorTrace
    });
  }
  sendSessions()[attemptId] = {
    targetInfo,
    conversation: input.target || {},
    messageText: input.settings?.messageText || "",
    actionBounds,
    selectorTrace
  };
  return {
    ok: true,
    success: false,
    skipped: false,
    outcome: "PREPARED",
    attemptId,
    phase: PHASES.LOCATING_TARGET,
    actionBounds,
    matchedConversationName: targetInfo.matchedConversationName,
    selectorTrace
  };
}

async function verifyTargetSwitch(input) {
  const attemptId = input.attemptId || "";
  const session = sendSessions()[attemptId];
  if (!session) {
    return createFailure("SEND_SESSION_MISSING", "当前发送操作已失效", { attemptId });
  }
  const selectorTrace = session.selectorTrace;
  const targetInfo = session.targetInfo;
  const target = targetInfo.target;
  const switched = await waitFor(() => {
    const headerName = getCurrentChatTarget(selectorTrace);
    const selectedName = getSelectedConversationName(selectorTrace);
    const selectedItem = getClickableConversationItems(selectorTrace).find(isSelectedConversation);
    const selectedIdentity = getConversationIdentity(selectedItem);
    const selectedBounds = elementBounds(selectedItem);
    const identityConfirmed = Boolean(session.conversation.identity)
      && selectedIdentity === session.conversation.identity;
    const sameVisualRow = Boolean(selectedBounds)
      && normalizeForMatch(getConversationName(selectedItem)) === normalizeForMatch(target)
      && Math.abs(selectedBounds.y - session.actionBounds.y) <= Math.max(selectedBounds.height, session.actionBounds.height) * 1.5;
    const ordinaryConfirmed = matchesTarget(headerName, target) || matchesTarget(selectedName, target);
    const confirmed = identityConfirmed || sameVisualRow || ordinaryConfirmed;
    return confirmed ? { headerName, selectedName } : null;
  }, WAIT_TIMEOUT, 250);
  if (!switched) {
    const currentChatTarget = getCurrentChatTarget(selectorTrace);
    const selectedConversation = getSelectedConversationName(selectorTrace);
    const actualState = `当前聊天标题：${currentChatTarget || "未识别"}；当前选中会话：${selectedConversation || "未识别"}`;
    return createFailure("CONVERSATION_SWITCH_UNCONFIRMED", `无法确认已切换到目标会话：${targetInfo.matchedConversationName}。${actualState}`, {
      phase: PHASES.SWITCHING_TARGET,
      attemptId,
      currentChatTarget,
      selectedConversation,
      matchedConversationName: targetInfo.matchedConversationName,
      selectorTrace
    });
  }
  const securityPrompt = detectSecurityPrompt();
  if (securityPrompt) {
    return createFailure("SECURITY_CHECK_REQUIRED", "页面要求人工完成安全验证", {
      phase: PHASES.SWITCHING_TARGET,
      attemptId,
      outcome: "BLOCKED",
      securityPrompt,
      selectorTrace
    });
  }
  const editor = await waitFor(() => findEditor(selectorTrace), WAIT_TIMEOUT, 250);
  const sendButton = await waitFor(() => findSendButton(selectorTrace), WAIT_TIMEOUT, 250);
  if (!editor || !sendButton) {
    return createFailure(!editor ? "EDITOR_NOT_FOUND" : "SEND_BUTTON_NOT_FOUND", !editor ? "没有找到输入框" : "没有找到发送按钮", {
      phase: PHASES.TYPING_MESSAGE,
      attemptId,
      selectorTrace
    });
  }
  const baseline = captureMessageEvidence(session.messageText);
  session.baselineCount = baseline.count;
  session.baselinePendingCount = baseline.pendingCount;
  session.baselineFailedCount = baseline.failedCount;
  session.baselineDeliverableCount = baseline.deliverableCount;
  session.baselineFingerprints = baseline.fingerprints;
  session.baselineDeliverableTimestamps = baseline.deliverableTimestamps;
  session.currentChatTarget = switched.headerName || targetInfo.matchedConversationName;
  return {
    ok: true,
    success: false,
    skipped: false,
    outcome: "READY",
    attemptId,
    phase: PHASES.TYPING_MESSAGE,
    currentChatTarget: session.currentChatTarget,
    selectedConversation: switched.selectedName,
    matchedConversationName: targetInfo.matchedConversationName,
    baselineMessageCount: baseline.count,
    baselineFingerprints: baseline.fingerprints,
    baselineMessageTimestamps: baseline.deliverableTimestamps,
    editorBounds: elementBounds(editor),
    sendButtonBounds: elementBounds(sendButton),
    sendButtonDisabled: Boolean(sendButton.disabled || sendButton.getAttribute("aria-disabled") === "true"),
    selectorTrace
  };
}

function validateMessageInput(input) {
  const attemptId = input.attemptId || "";
  const session = sendSessions()[attemptId];
  const editor = session ? findEditor(session.selectorTrace) : null;
  if (!session || !editor || editorText(editor) !== normalize(session.messageText)) {
    return createFailure("EDITOR_WRITE_FAILED", "逐字输入后输入框内容与预期不一致", {
      phase: PHASES.TYPING_MESSAGE,
      attemptId,
      selectorTrace: session?.selectorTrace || []
    });
  }
  const sendButton = findSendButton(session.selectorTrace);
  if (!sendButton || !visible(sendButton)) {
    return createFailure("SEND_BUTTON_NOT_FOUND", "逐字输入后没有找到实际发送按钮", {
      phase: PHASES.SENDING_MESSAGE,
      attemptId,
      selectorTrace: session.selectorTrace
    });
  }
  const sendButtonDisabled = Boolean(sendButton.disabled || sendButton.getAttribute("aria-disabled") === "true");
  if (sendButtonDisabled) {
    return createFailure("SEND_BUTTON_DISABLED", "逐字输入后发送按钮仍不可用，未执行发送", {
      phase: PHASES.SENDING_MESSAGE,
      attemptId,
      selectorTrace: session.selectorTrace
    });
  }
  return {
    ok: true,
    success: false,
    skipped: false,
    outcome: "READY",
    attemptId,
    editorBounds: elementBounds(editor),
    sendButtonBounds: elementBounds(sendButton),
    sendButtonDisabled,
    selectorTrace: session.selectorTrace
  };
}

async function verifyMessageSend(input) {
  const attemptId = input.attemptId || "";
  const session = sendSessions()[attemptId];
  if (!session) {
    return createFailure("SEND_SESSION_MISSING", "发送后的核验上下文已失效", { attemptId });
  }
  const clickedAtEpochMillis = Number(input.clickedAtEpochMillis) || Date.now();
  const observed = await waitFor(() => {
    const securityPrompt = detectSecurityPrompt();
    if (securityPrompt) {
      return { securityPrompt };
    }
    const currentTarget = getCurrentChatTarget(session.selectorTrace);
    if (currentTarget && !matchesTarget(currentTarget, session.targetInfo.target)) {
      return null;
    }
    const evidence = captureMessageEvidence(session.messageText);
    const newDeliverableFingerprints = evidence.deliverableFingerprints
      .filter((fingerprint) => !session.baselineFingerprints.includes(fingerprint));
    const countIncreased = evidence.count > session.baselineCount;
    const newPending = evidence.pendingCount > session.baselinePendingCount
      || evidence.pendingFingerprints.some((fingerprint) => !session.baselineFingerprints.includes(fingerprint));
    const newFailed = evidence.failedCount > session.baselineFailedCount
      || evidence.failedFingerprints.some((fingerprint) => !session.baselineFingerprints.includes(fingerprint));
    if (newFailed) {
      return { evidence, failed: true };
    }
    const deliverableCountIncreased = evidence.deliverableCount > session.baselineDeliverableCount;
    const hasRecentDeliverable = evidence.deliverableTimestamps.some((timestamp) =>
      timestamp >= clickedAtEpochMillis - 10000 && !session.baselineDeliverableTimestamps.includes(timestamp));
    if ((newDeliverableFingerprints.length > 0 || (countIncreased && deliverableCountIncreased) || hasRecentDeliverable) && !newPending) {
      return { evidence, confirmed: true };
    }
    return null;
  }, 15000, 250);
  const editor = findEditor(session.selectorTrace);
  const inputCleared = !editor || !editorText(editor);
  if (observed?.securityPrompt) {
    return createFailure("SECURITY_CHECK_REQUIRED", "发送后页面要求人工完成安全验证", {
      phase: PHASES.SENDING_MESSAGE,
      attemptId,
      outcome: "BLOCKED",
      securityPrompt: observed.securityPrompt,
      selectorTrace: session.selectorTrace
    });
  }
  if (observed?.failed) {
    return createFailure("MESSAGE_SEND_REJECTED", "页面显示本次消息发送失败", {
      phase: PHASES.SENDING_MESSAGE,
      attemptId,
      selectorTrace: session.selectorTrace
    });
  }
  if (!observed?.confirmed) {
    const evidence = captureMessageEvidence(session.messageText);
    return {
      ok: true,
      success: true,
      skipped: false,
      outcome: "UNCERTAIN",
      phase: PHASES.COMPLETED,
      deliveryStatus: "uncertain",
      reason: "发送动作已触发，但没有检测到新增己方消息气泡",
      attemptId,
      clickedAtEpochMillis,
      inputCleared,
      baselineMessageCount: session.baselineCount,
      observedMessageCount: evidence.count,
      baselineFingerprints: session.baselineFingerprints,
      baselineMessageTimestamps: session.baselineDeliverableTimestamps,
      observedFingerprints: evidence.fingerprints,
      currentChatTarget: session.currentChatTarget,
      matchedConversationName: session.targetInfo.matchedConversationName,
      selectorTrace: session.selectorTrace
    };
  }
  return {
    ok: true,
    success: true,
    skipped: false,
    outcome: "CONFIRMED",
    phase: PHASES.COMPLETED,
    deliveryStatus: "confirmed",
    reason: "已检测到本次新增的己方消息气泡",
    attemptId,
    clickedAtEpochMillis,
    inputCleared,
    baselineMessageCount: session.baselineCount,
    observedMessageCount: observed.evidence.count,
    baselineFingerprints: session.baselineFingerprints,
    baselineMessageTimestamps: session.baselineDeliverableTimestamps,
    observedFingerprints: observed.evidence.fingerprints,
    currentChatTarget: session.currentChatTarget,
    matchedConversationName: session.targetInfo.matchedConversationName,
    selectorTrace: session.selectorTrace
  };
}

async function runDouyinAutoSpark(input) {
  if (input.action === "discover") {
    const selectorTrace = [];
    const probe = await waitForPageReady(selectorTrace);
    if (!probe.ok) {
      return probe;
    }
    return await discoverTargetConversations(input.target || "", selectorTrace);
  }
  if (input.action === "prepare-target") {
    return await prepareTarget(input);
  }
  if (input.action === "verify-target-switch") {
    return await verifyTargetSwitch(input);
  }
  if (input.action === "validate-message-input") {
    return validateMessageInput(input);
  }
  if (input.action === "verify-message-send") {
    return await verifyMessageSend(input);
  }
  return createFailure("ACTION_UNSUPPORTED", "未识别的页面自动化动作，未执行发送");
}
