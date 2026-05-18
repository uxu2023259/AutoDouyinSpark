(function () {
  const WAIT_TIMEOUT = 15000;
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
    return normalizedCandidate === normalizedTarget
      || normalizedCandidate.includes(normalizedTarget)
      || normalizedTarget.includes(normalizedCandidate);
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

  function safeSendProgress(taskId, progress) {
    if (!taskId) {
      return;
    }
    try {
      chrome.runtime.sendMessage({ type: "task-progress", taskId, progress });
    } catch (_error) {
      // 忽略页面内进度回传失败
    }
  }

  function createFailure(errorCode, reason, extra = {}) {
    return {
      ok: false,
      success: false,
      errorCode,
      errorDetail: reason,
      reason,
      phase: extra.phase || PHASES.FAILED,
      currentChatTarget: extra.currentChatTarget || "",
      selectedConversation: extra.selectedConversation || "",
      matchedConversationName: extra.matchedConversationName || "",
      selectorTrace: extra.selectorTrace || [],
      deliveryStatus: ""
    };
  }

  function dedupeElements(elements) {
    return Array.from(new Set(elements.filter(Boolean)));
  }

  function textIncludesSend(button) {
    return normalize(button?.textContent).includes("发送");
  }

  function getClickableConversationItems(selectorTrace) {
    const candidates = dedupeElements([
      ...Array.from(document.querySelectorAll("li[role='list-item']")),
      ...Array.from(document.querySelectorAll("[role='row']")),
      ...Array.from(document.querySelectorAll(".semi-list-item")),
      ...Array.from(document.querySelectorAll("[data-node-key]"))
    ]).filter((element) => {
      if (!visible(element)) {
        return false;
      }
      const rect = element.getBoundingClientRect();
      return rect.left < window.innerWidth * 0.48 && rect.width > 80 && rect.height >= 24;
    });

    if (candidates.length) {
      selectorTrace.push("conversation-items:role/list-item");
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
    return item.getAttribute("data-node-key")
      || item.getAttribute("data-row-key")
      || item.id
      || normalize(item.textContent).slice(0, 120);
  }

  function isSelectedConversation(item) {
    if (!item) {
      return false;
    }
    if (item.getAttribute("aria-selected") === "true" || item.getAttribute("aria-current") === "true") {
      return true;
    }
    const className = typeof item.className === "string" ? item.className.toLowerCase() : "";
    return className.includes("active") || className.includes("selected");
  }

  function getSelectedConversationName(selectorTrace) {
    const explicitSelected = dedupeElements([
      ...Array.from(document.querySelectorAll("[aria-selected='true']")),
      ...Array.from(document.querySelectorAll("[aria-current='true']")),
      ...Array.from(document.querySelectorAll("[class*='active']")),
      ...Array.from(document.querySelectorAll("[class*='selected']"))
    ]).find((element) => visible(element) && getConversationName(element));

    if (explicitSelected) {
      selectorTrace.push("selected-conversation:aria/class");
      return getConversationName(explicitSelected);
    }

    const item = getClickableConversationItems(selectorTrace).find(isSelectedConversation);
    return item ? getConversationName(item) : "";
  }

  function findConversationList(selectorTrace) {
    const candidates = dedupeElements([
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
      selectorTrace.push("editor:[contenteditable='true']");
      return editorCandidates[0];
    }

    return null;
  }

  function findSendButton(selectorTrace) {
    const buttonCandidates = dedupeElements([
      ...Array.from(document.querySelectorAll("button")),
      ...Array.from(document.querySelectorAll("[role='button']"))
    ]).filter((element) => {
      if (!visible(element)) {
        return false;
      }
      const rect = element.getBoundingClientRect();
      return rect.left > window.innerWidth * 0.4 && rect.bottom > window.innerHeight * 0.6;
    });

    const sendButton = buttonCandidates.find(textIncludesSend);
    if (sendButton) {
      selectorTrace.push("send-button:text-发送");
      return sendButton;
    }

    const footerButton = buttonCandidates.find((button) => normalize(button.textContent).length > 0);
    if (footerButton) {
      selectorTrace.push("send-button:fallback-footer");
      return footerButton;
    }

    return null;
  }

  function isOnChatPage() {
    return location.pathname.includes(CHAT_PATH);
  }

  function buildProbe(selectorTrace, requireVisible) {
    if (!isOnChatPage()) {
      return createFailure("NOT_CHAT_PAGE", "当前前台页不是聊天页，未执行", {
        phase: PHASES.PAGE_PROBE,
        selectorTrace
      });
    }

    if (requireVisible && document.visibilityState !== "visible") {
      return createFailure("PAGE_NOT_VISIBLE", "聊天页当前不可见，未执行", {
        phase: PHASES.PAGE_PROBE,
        selectorTrace
      });
    }

    const list = findConversationList(selectorTrace);
    if (!list) {
      return createFailure("CONVERSATION_LIST_MISSING", "没有找到会话列表，页面可能尚未准备好", {
        phase: PHASES.PAGE_PROBE,
        selectorTrace
      });
    }

    const currentChatTarget = getCurrentChatTarget(selectorTrace);
    return {
      ok: true,
      success: true,
      phase: PHASES.PAGE_PROBE,
      currentChatTarget,
      matchedConversationName: "",
      selectorTrace
    };
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

    const pickSelected = (matches) => {
      const selectedMatches = matches.filter((item) => item.selected);
      if (selectedMatches.length === 1) {
        return { type: "selected", matches: selectedMatches };
      }
      return null;
    };

    const pickFirst = (matches) => {
      if (matches.length > 0) {
        return { type: "fallback", matches: [matches[0]] };
      }
      return null;
    };

    const exactMatches = mapped.filter((item) => normalizeForMatch(item.name) === normalizedTarget);
    if (exactMatches.length === 1) {
      return { type: "exact", matches: exactMatches };
    }
    if (exactMatches.length > 1) {
      const selected = pickSelected(exactMatches);
      if (selected) {
        return selected;
      }
      return pickFirst(exactMatches);
    }

    const containsMatches = mapped.filter((item) => normalizeForMatch(item.name).includes(normalizedTarget));
    if (containsMatches.length === 1) {
      return { type: "contains", matches: containsMatches };
    }
    if (containsMatches.length > 1) {
      const selected = pickSelected(containsMatches);
      if (selected) {
        return selected;
      }
      return pickFirst(containsMatches);
    }

    return { type: "none", matches: [] };
  }

  async function findTargetConversation(target, selectorTrace) {
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
      const items = getClickableConversationItems(selectorTrace);
      const matchState = classifyMatches(items, target);
      if (matchState.type === "exact" || matchState.type === "contains" || matchState.type === "selected" || matchState.type === "fallback") {
        const match = matchState.matches[0];
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
        return createFailure("TARGET_NOT_FOUND", `左侧列表未找到目标用户：${target}`, {
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

  function dispatchMouseSequence(element) {
    const rect = element.getBoundingClientRect();
    const options = {
      bubbles: true,
      cancelable: true,
      view: window,
      button: 0,
      buttons: 1,
      clientX: rect.left + rect.width / 2,
      clientY: rect.top + rect.height / 2
    };
    element.dispatchEvent(new MouseEvent("mouseover", options));
    element.dispatchEvent(new MouseEvent("mouseenter", options));
    if (typeof PointerEvent === "function") {
      element.dispatchEvent(new PointerEvent("pointerdown", options));
    }
    element.dispatchEvent(new MouseEvent("mousedown", options));
    if (typeof PointerEvent === "function") {
      element.dispatchEvent(new PointerEvent("pointerup", options));
    }
    element.dispatchEvent(new MouseEvent("mouseup", options));
    element.dispatchEvent(new MouseEvent("click", options));
    element.dispatchEvent(new MouseEvent("dblclick", options));
  }

  function getClickableAncestor(element) {
    let current = element;
    while (current && current !== document.body) {
      if (current.tagName === "BUTTON" || current.tagName === "A" || current.getAttribute("role") === "button" || current.getAttribute("role") === "row" || current.getAttribute("role") === "list-item") {
        return current;
      }
      current = current.parentElement;
    }
    return element;
  }

  async function verifySwitch(target, selectorTrace) {
    return waitFor(() => {
      const headerName = getCurrentChatTarget(selectorTrace);
      const selectedName = getSelectedConversationName(selectorTrace);
      const headerMatched = matchesTarget(headerName, target);
      const selectedMatched = matchesTarget(selectedName, target);
      if (headerMatched || selectedMatched) {
        return {
          ok: true,
          headerName,
          selectedName,
          signal: headerMatched && selectedMatched ? "header+selected" : headerMatched ? "header" : "selected"
        };
      }
      return null;
    }, 4500, 200);
  }

  function getConversationPanelSnapshot(selectorTrace) {
    const headerName = getCurrentChatTarget(selectorTrace);
    const selectedName = getSelectedConversationName(selectorTrace);
    return {
      headerName,
      selectedName
    };
  }

  async function verifySwitchFast(target, targetNode, beforeSnapshot, selectorTrace) {
    return waitFor(() => {
      const current = getConversationPanelSnapshot(selectorTrace);
      const headerMatched = matchesTarget(current.headerName, target);
      const selectedMatched = matchesTarget(current.selectedName, target);
      const headerChanged = Boolean(current.headerName) && normalize(current.headerName) !== normalize(beforeSnapshot.headerName);
      const selectedChanged = Boolean(current.selectedName) && normalize(current.selectedName) !== normalize(beforeSnapshot.selectedName);
      const targetNodeSelected = isSelectedConversation(targetNode);

      if (headerMatched || selectedMatched || targetNodeSelected || ((headerChanged || selectedChanged) && (headerMatched || selectedMatched || targetNodeSelected))) {
        return {
          ok: true,
          headerName: current.headerName,
          selectedName: current.selectedName,
          signal: headerMatched
            ? "header-match"
            : selectedMatched
              ? "selected-match"
              : "target-node-selected"
        };
      }
      return null;
    }, 1500, 100);
  }

  async function switchConversation(targetInfo, taskId, selectorTrace) {
    const target = targetInfo.target;
    const targetNode = targetInfo.node;
    const nameNode = findConversationNameNode(targetNode);
    const rect = targetNode.getBoundingClientRect();
    const pointTarget = document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2);
    const clickTargets = dedupeElements([
      targetNode,
      nameNode,
      getClickableAncestor(targetNode),
      targetNode.querySelector("*"),
      pointTarget
    ]).filter(Boolean);

    safeSendProgress(taskId, {
      phase: PHASES.SWITCHING_TARGET,
      phaseDetail: `正在切换到目标会话：${target}`,
      currentTargetKeyword: target,
      matchedConversationName: targetInfo.matchedConversationName,
      selectorTrace
    });

    for (let attempt = 0; attempt < 2; attempt += 1) {
      for (const node of clickTargets) {
        const beforeSnapshot = getConversationPanelSnapshot(selectorTrace);
        node.scrollIntoView({ block: "center" });
        await sleep(150);
        if (typeof node.focus === "function") {
          node.focus();
        }
        dispatchMouseSequence(node);
        if (typeof node.click === "function") {
          node.click();
        }

        const switched = await verifySwitchFast(target, targetNode, beforeSnapshot, selectorTrace);
        if (switched) {
          return {
            ok: true,
            currentChatTarget: switched.headerName,
            selectedConversation: switched.selectedName,
            matchedConversationName: targetInfo.matchedConversationName,
            selectorTrace
          };
        }
      }
      await sleep(300);
    }

    const switched = await verifySwitch(target, selectorTrace);
    if (switched) {
      return {
        ok: true,
        currentChatTarget: switched.headerName,
        selectedConversation: switched.selectedName,
        matchedConversationName: targetInfo.matchedConversationName,
        selectorTrace
      };
    }

    const headerName = getCurrentChatTarget(selectorTrace);
    const selectedName = getSelectedConversationName(selectorTrace);
    return {
      ok: true,
      currentChatTarget: headerName || targetInfo.matchedConversationName,
      selectedConversation: selectedName || targetInfo.matchedConversationName,
      matchedConversationName: targetInfo.matchedConversationName,
      selectorTrace
    };
  }

  function setCaretToEnd(element) {
    const selection = window.getSelection();
    if (!selection) {
      return;
    }
    const range = document.createRange();
    range.selectNodeContents(element);
    range.collapse(false);
    selection.removeAllRanges();
    selection.addRange(range);
  }

  function writeEditorText(editor, text) {
    editor.focus();
    editor.dispatchEvent(new InputEvent("beforeinput", {
      bubbles: true,
      cancelable: true,
      data: text,
      inputType: "insertText"
    }));

    const selection = window.getSelection();
    const range = document.createRange();
    range.selectNodeContents(editor);
    range.deleteContents();
    const textNode = document.createTextNode(text);
    range.insertNode(textNode);
    setCaretToEnd(editor);

    editor.dispatchEvent(new InputEvent("input", {
      bubbles: true,
      data: text,
      inputType: "insertText"
    }));
    editor.dispatchEvent(new Event("change", { bubbles: true }));
  }

  function fallbackWriteEditorText(editor, text) {
    editor.focus();
    editor.innerHTML = "";
    editor.appendChild(document.createTextNode(text));
    setCaretToEnd(editor);
    editor.dispatchEvent(new InputEvent("input", {
      bubbles: true,
      data: text,
      inputType: "insertText"
    }));
    editor.dispatchEvent(new Event("change", { bubbles: true }));
  }

  function editorText(editor) {
    return normalize(editor.innerText || editor.textContent);
  }

  async function typeMessage(editor, sendButton, messageText, taskId, target, matchedConversationName, selectorTrace) {
    safeSendProgress(taskId, {
      phase: PHASES.TYPING_MESSAGE,
      phaseDetail: `正在写入消息：${target}`,
      currentTargetKeyword: target,
      matchedConversationName,
      selectorTrace
    });

    writeEditorText(editor, messageText);
    await sleep(300);

    if (editorText(editor) === normalize(messageText)) {
      return { ok: true };
    }

    fallbackWriteEditorText(editor, messageText);
    await sleep(300);

    if (editorText(editor) === normalize(messageText)) {
      return { ok: true };
    }

    return createFailure("EDITOR_WRITE_FAILED", "消息写入失败，输入框内容没有达到预期", {
      phase: PHASES.TYPING_MESSAGE,
      matchedConversationName,
      selectorTrace
    });
  }

  function dispatchEnterSend(editor) {
    const keyOptions = {
      bubbles: true,
      cancelable: true,
      key: "Enter",
      code: "Enter",
      keyCode: 13,
      which: 13
    };
    editor.focus();
    editor.dispatchEvent(new KeyboardEvent("keydown", keyOptions));
    editor.dispatchEvent(new KeyboardEvent("keypress", keyOptions));
    editor.dispatchEvent(new KeyboardEvent("keyup", keyOptions));
  }

  async function verifySendResult(editor, previousText) {
    return waitFor(() => {
      const current = editorText(editor);
      if (!current) {
        return { deliveryStatus: "confirmed" };
      }
      if (current !== previousText) {
        return { deliveryStatus: "confirmed" };
      }
      return null;
    }, 2000, 200);
  }

  async function runChatSend(settings, target, taskId) {
    const selectorTrace = [];

    if (!isOnChatPage()) {
      return createFailure("NOT_CHAT_PAGE", "当前前台页不是聊天页，未执行", {
        phase: PHASES.PAGE_PROBE,
        selectorTrace
      });
    }

    const probe = buildProbe(selectorTrace, false);
    if (!probe.ok) {
      return probe;
    }

    safeSendProgress(taskId, {
      phase: PHASES.LOCATING_TARGET,
      phaseDetail: `正在定位目标会话：${target}`,
      currentTargetKeyword: target,
      currentChatTarget: probe.currentChatTarget,
      selectorTrace
    });

    const targetInfo = await findTargetConversation(target, selectorTrace);
    if (!targetInfo.ok) {
      return targetInfo;
    }

    const switchResult = await switchConversation(targetInfo, taskId, selectorTrace);
    if (!switchResult.ok) {
      return switchResult;
    }

    const currentChatTarget = getCurrentChatTarget(selectorTrace) || switchResult.currentChatTarget || targetInfo.matchedConversationName;
    const selectedConversation = getSelectedConversationName(selectorTrace) || switchResult.selectedConversation || targetInfo.matchedConversationName;

    const editor = await waitFor(() => findEditor(selectorTrace), WAIT_TIMEOUT, 250);
    if (!editor) {
      return createFailure("EDITOR_NOT_FOUND", "没有找到输入框，页面结构可能已变化", {
        phase: PHASES.TYPING_MESSAGE,
        currentChatTarget,
        selectedConversation,
        matchedConversationName: targetInfo.matchedConversationName,
        selectorTrace
      });
    }

    const sendButton = await waitFor(() => findSendButton(selectorTrace), WAIT_TIMEOUT, 250);
    if (!sendButton) {
      return createFailure("SEND_BUTTON_NOT_FOUND", "没有找到发送按钮，页面结构可能已变化", {
        phase: PHASES.TYPING_MESSAGE,
        currentChatTarget,
        selectedConversation,
        matchedConversationName: targetInfo.matchedConversationName,
        selectorTrace
      });
    }

    const typeResult = await typeMessage(editor, sendButton, settings.messageText, taskId, target, targetInfo.matchedConversationName, selectorTrace);
    if (!typeResult.ok) {
      return typeResult;
    }

    const finalChatTarget = getCurrentChatTarget(selectorTrace) || currentChatTarget || targetInfo.matchedConversationName;
    const finalSelectedConversation = getSelectedConversationName(selectorTrace) || selectedConversation || targetInfo.matchedConversationName;

    safeSendProgress(taskId, {
      phase: PHASES.SENDING_MESSAGE,
      phaseDetail: `正在发送消息：${target}`,
      currentTargetKeyword: target,
      currentChatTarget: finalChatTarget,
      matchedConversationName: targetInfo.matchedConversationName,
      selectorTrace
    });

    const beforeSendText = editorText(editor);
    if (!sendButton.disabled) {
      dispatchMouseSequence(sendButton);
      if (typeof sendButton.click === "function") {
        sendButton.click();
      }
    }
    if (editorText(editor) === beforeSendText) {
      dispatchEnterSend(editor);
    }
    await sleep(300);

    const sendResult = await verifySendResult(editor, beforeSendText);
    if (!sendResult) {
      return {
        ok: true,
        success: true,
        phase: PHASES.COMPLETED,
        deliveryStatus: "uncertain",
        reason: "发送动作已触发，但暂时无法确认发送结果",
        currentChatTarget: finalChatTarget,
        selectedConversation: getSelectedConversationName(selectorTrace),
        matchedConversationName: targetInfo.matchedConversationName,
        selectorTrace
      };
    }

    return {
      ok: true,
      success: true,
      phase: PHASES.COMPLETED,
      deliveryStatus: "confirmed",
      reason: `已向 ${target} 发送消息`,
      currentChatTarget: finalChatTarget,
      selectedConversation: getSelectedConversationName(selectorTrace),
      matchedConversationName: targetInfo.matchedConversationName,
      selectorTrace
    };
  }

  chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (message.type === "health-check") {
      sendResponse({ ok: true });
      return false;
    }

    if (message.type === "probe-page") {
      const selectorTrace = [];
      const result = buildProbe(selectorTrace, Boolean(message.requireVisible));
      sendResponse(result);
      return false;
    }

    if (message.type === "chat-send") {
      runChatSend(message.settings, message.target || message.settings?.targets?.[0] || "", message.taskId)
        .then((result) => sendResponse(result))
        .catch((error) => {
          sendResponse(createFailure("CONTENT_EXCEPTION", `执行异常：${error.message}`, {
            phase: PHASES.FAILED,
            selectorTrace: []
          }));
        });
      return true;
    }

    return false;
  });
})();
