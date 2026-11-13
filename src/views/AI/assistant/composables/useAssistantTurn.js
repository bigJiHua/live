import { nextTick } from 'vue';
import { isAiAuthorizationFailure } from '../../../../utils/aiTurn.js';

/** Runs one request at a time and recovers its original receipt without a paid replay. */
export function useAssistantTurn({ state, services, runtime, actions }) {
  const { capability, input, imageAttachment, consent, aiMode, conversationId, messages,
    chatBusy, requestActive, streamingActive, allowed, busy, transferBusy, businessEnabled,
    authGranted, settingsNotice } = state;
  const { recognizeAiImage, applyAiReply, streamAiBusiness, validateAiAuthorizationRequest,
    getAiBusinessReceipt, getAiConversationSync, createAiRequestId, createReceiptPoll, dropTurn } = services;
  const { clearImage, resizeInput, setFocusEntry, hasFocusEntry, tryFocusSent, nearBottom,
    bottom, journalTurn, settleTurn, finishTurn, applyFinancialFields, applyBusinessReply,
    pushActivity, trackStage, trackTool, trackReasoning, trackReasoningDelta = () => {}, trackReasoningStatus = () => {},
    sealReasoningStatus = () => {}, sealActivities, openGrantDialog, recoverPendingTask = () => {} } = actions;
  const pollers = new Map();
  const receiptDeadlines = new Set();
  let streamAbort = null;
  let replyAbort = null;
  let currentRun = null;
  let currentRequestId = null;
  let disposed = false;
  const isActive = () => !disposed && runtime.isActive();

  const completedReply = entry => Boolean(entry?.requestId) &&
    (entry._replyCompletedRequestId === entry.requestId ||
      entry.turn?.state === 'completed' && Boolean(entry.messageId || !entry.remote));
  function stopReceiptPoll(entry) {
    pollers.get(entry?.id)?.stop();
    pollers.delete(entry?.id);
  }
  function stopAllPolling() {
    for (const poller of pollers.values()) poller.stop();
    pollers.clear();
  }
  // A persisted final history message can arrive before its POST stream ends.
  // Revoke only that run; a delayed catch/finally can no longer own new work.
  function settleSyncedTurn(entry, finalState) {
    if (!isActive() || !entry || entry.requestId !== currentRequestId || !currentRun ||
      !messages.value.includes(entry) || !['completed', 'failed', 'awaiting_authorization'].includes(finalState)) return;
    sealReasoningStatus(entry, finalState !== 'completed');
    const stream = streamAbort, reply = replyAbort;
    currentRun = null; currentRequestId = null; streamAbort = null; replyAbort = null;
    // Release the sending guard without claiming a local completed answer:
    // the following history merge must install its authoritative final text.
    if (entry.turn) entry.turn.state = 'uncertain';
    // Keep the placeholder until merge so a persisted first artifact occupies
    // this bubble; incoming history then sets pending to false.
    streamingActive.value = false; requestActive.value = false; chatBusy.value = false;
    setFocusEntry(null); stopReceiptPoll(entry);
    stream?.abort(); reply?.abort();
  }

  async function requestReply(text, entry, image = null) {
    if (!isActive() || entry.sent) return;
    const run = Symbol('image-reply');
    currentRun = run; currentRequestId = entry.requestId;
    const isCurrent = () => isActive() && currentRun === run && messages.value.includes(entry);
    entry.sent = true;
    chatBusy.value = true;
    const controller = new AbortController();
    replyAbort = controller; requestActive.value = true;
    const requestConsent = image ? true : consent.value;
    const turnMessage = text || '识别这张图片';

    // 图片轮次同样需要 turn：失败态与重试入口都依赖它；图片只留在内存，
    // journal 仍只保存原话，不落图片数据。
    if (image?.dataUrl) {
      entry.turn = { requestId: entry.requestId, message: turnMessage, imageDataUrl: image.dataUrl,
        request: { message: turnMessage, conversationId: conversationId.value, imageDataUrl: image.dataUrl },
        state: 'sending', sent: true };
    }

    try {
      const options = { requestId: entry.requestId, message: text, conversationId: conversationId.value, consent: requestConsent, connectionId: capability.value.connectionId, signal: controller.signal };
      if (!image) throw new Error('文字聊天必须使用新版 AI 流程');
      const result = await recognizeAiImage({ ...options, image: { dataUrl: image.dataUrl } });
      if (!isCurrent() || controller.signal.aborted) return;
      conversationId.value = result.conversationId;
      messages.value = applyAiReply(messages.value, entry, result);
      applyFinancialFields(entry, result);
      if (entry.turn) { entry.turn.state = 'completed'; entry.turn.preview = null; }
      entry._replyCompletedRequestId = entry.requestId; stopReceiptPoll(entry);
      if (hasFocusEntry()) nextTick(tryFocusSent);
      finishTurn(entry);
    } catch (error) {
      if (!isCurrent()) return;
      if (completedReply(entry)) { stopReceiptPoll(entry); return; }
      if (controller.signal.aborted) {
        if (entry.turn) entry.turn.state = 'uncertain';
        Object.assign(entry, { pending: false, error: true, text: '已请求停止，正在读取最终回执。' });
        settleTurn(entry, { state: 'uncertain', text: entry.text, error: true });
        startReceiptPoll(entry);
        return;
      }
      if (entry.turn) entry.turn.state = 'failed';
      Object.assign(entry, { pending: false, error: true, text: error?.message || '本次请求未收到完成回执，请读取状态。' });
      settleTurn(entry, { state: 'failed', text: entry.text, error: true });
    } finally {
      if (replyAbort === controller) { replyAbort = null; requestActive.value = false; }
      image = null;
      if (currentRun === run) {
        chatBusy.value = false; setFocusEntry(null);
        if (isActive() && nearBottom()) await bottom();
        if (currentRun === run) { currentRun = null; currentRequestId = null; }
      }
    }
  }
  async function send(example) {
    const text = (typeof example === 'string' ? example : input.value).trim();
    const image = imageAttachment.value;
    if (!isActive() || !allowed.value || (!text && !image)) return;
    settingsNotice.value = '';
    const requestId = createAiRequestId(), sentAt = Date.now();
    messages.value.push({ id: runtime.nextId(), role: 'user', requestId, text: text || '识别这张图片', sentAt, ...(image ? { imagePreview: image.dataUrl } : {}) });
    const userEntry = messages.value[messages.value.length - 1];
    messages.value.push({ id: runtime.nextId(), role: 'assistant', pending: true, requestId, sentAt, thinkingState: null });
    const entry = messages.value[messages.value.length - 1];
    journalTurn(entry, text || '识别这张图片', sentAt);
    input.value = ''; clearImage(); nextTick(resizeInput);
    setFocusEntry(userEntry);
    nextTick(() => tryFocusSent(true));
    if (image) await requestReply(text, entry, image);
    else await prepareTurn(text, entry);
  }

  async function prepareTurn(text, entry) {
    const request = Object.freeze({ message: text, mode: aiMode.value, ...(conversationId.value ? { conversationId: conversationId.value } : {}) });
    entry.turn = { requestId: entry.requestId, message: text, request, state: 'ready', sent: false };
    await confirmTurn(entry);
  }

  async function sendAiTurnStream(turn, entry, run = currentRun) {
    turn.sent = true;
    turn.state = 'sending';
    const controller = new AbortController();
    streamAbort = controller;
    streamingActive.value = true;
    let sawDone = false, doneResult = null;
    try {
      await streamAiBusiness({ requestId: turn.requestId, ...turn.request, consent: true, signal: controller.signal,
        ...(capability.value?.requestPolicy?.turnTimeoutMs != null
          ? { turnTimeoutMs: capability.value.requestPolicy.turnTimeoutMs, queueWaitMs: capability.value.requestPolicy.queueWaitMs } : {}),
        onEvent: ({ event, data }) => {
          if (!isActive() || currentRun !== run || controller.signal.aborted || entry.requestId !== turn.requestId || !messages.value.includes(entry)) return;
          if (event === 'status') trackStage(entry, data);
          else if (event === 'tool') trackTool(entry, data);
          else if (event === 'reasoning_status' && !sawDone) trackReasoningStatus(entry, data);
          else if (event === 'reasoning_delta' && !sawDone) trackReasoningDelta(entry, data);
          else if (event === 'reasoning') trackReasoning(entry, data);
          else if (event === 'done') { sawDone = true; doneResult = data.data; }
          else if (event === 'error') throw Object.assign(new Error(data.message || 'AI 流式请求失败'), { status: data.status, code: data.code, requestId: data.requestId, traceId: data.traceId, httpStatus: data.status, providerCalls: data.providerCalls, notAdmitted: data.notAdmitted, missingDomains: data.missingDomains, providerDetail: data.providerDetail, authorizationRequest: data.authorizationRequest });
        } });
      if (!sawDone) throw Object.assign(new Error('流式回执不完整，请读取状态'), { status: -1 });
      return doneResult;
    } finally {
      if (streamAbort === controller) { streamingActive.value = false; streamAbort = null; }
      if (isActive() && currentRun === run && nearBottom()) await bottom();
    }
  }
  function stopStream() { streamAbort?.abort(); replyAbort?.abort(); }

  // A conversation boundary ends ownership immediately even if its transport
  // ignores abort. Ordinary stopStream keeps ownership until receipt recovery.
  function invalidateRequests() {
    for (const entry of messages.value) if (entry.pending && entry.thinkingState) sealReasoningStatus(entry, true);
    const stream = streamAbort, reply = replyAbort;
    streamAbort = null; replyAbort = null; currentRun = null; currentRequestId = null;
    stream?.abort(); reply?.abort();
    streamingActive.value = false; requestActive.value = false; chatBusy.value = false;
    setFocusEntry(null);
    stopAllPolling();
  }

  function handleTurnError(entry, error) {
    if (completedReply(entry)) { stopReceiptPoll(entry); return; }
    const status = Number(error?.status ?? error?.response?.status ?? -1);
    sealReasoningStatus(entry, true);
    sealActivities(entry);
    entry.lastError = { code: error?.code || null, status, traceId: error?.traceId || null, httpStatus: error?.httpStatus ?? error?.response?.status ?? null, missingDomains: Array.isArray(error?.missingDomains) ? error.missingDomains : null,
      ...(Number.isInteger(error?.providerCalls) ? { providerCalls: error.providerCalls } : {}),
      ...(error?.notAdmitted === true ? { notAdmitted: true } : {}) };
    if (error?.providerDetail) entry.providerDetail = error.providerDetail;
    else delete entry.providerDetail;
    const preparationNotAdmitted = ['AI_PREPARATION_TIMEOUT', 'AI_PREPARATION_CANCELLED'].includes(error?.code) &&
      error?.notAdmitted === true && error?.providerCalls === 0;
    entry._requestNotAdmitted = preparationNotAdmitted || ['AI_REQUEST_BUSY', 'AI_RATE_LIMIT'].includes(error?.code) && error?.providerCalls === 0;
    entry.turn.state = preparationNotAdmitted || error?.code === 'AI_REQUEST_BUSY' || [400, 403, 404, 409, 410, 422, 429].includes(status) ? 'failed' : 'uncertain';
    Object.assign(entry, { pending: false, error: true, text: error?.message || '本次 AI 请求未收到完成回执，请读取状态。' });
    if (error?.kind === 'protocol' || error?.code === 'AI_RESPONSE_INVALID')
      entry.receiptNotice = '响应暂时无法显示，已停止自动读取，可稍后手动读取回执。';
    if (error?.authorizationRequest) {
      entry.authorizationRequest = validateAiAuthorizationRequest(error.authorizationRequest);
      entry.authorizationDenied = false; entry.error = false; entry.turn.state = 'awaiting_authorization';
      entry.text = '需要你授权后，我才能继续使用这项能力。';
    } else if (isAiAuthorizationFailure(error?.code)) {
      entry.error = false; entry.turn.state = 'awaiting_authorization';
    }
    settleTurn(entry, { state: entry.turn.state, text: entry.text, error: entry.error, lastError: entry.lastError });
    if (entry.turn.state === 'uncertain' && error?.kind !== 'protocol' && error?.code !== 'AI_RESPONSE_INVALID') startReceiptPoll(entry);
    if (entry.turn.state === 'failed') void recoverPendingTask(entry);
  }
  async function confirmTurn(entry) {
    if (!isActive() || transferBusy.value || !businessEnabled.value || entry.turn?.state !== 'ready' || entry.turn.sent) {
      // 未能启动的 ready 轮次必须收尾，否则 busy 会一直被这条消息锁住。
      if (entry.turn?.state === 'ready' && !entry.turn.sent) {
        entry.turn.state = 'failed'; entry.pending = false;
        entry._requestNotAdmitted = true;
        if (!entry.text) Object.assign(entry, { error: true, text: '上一条消息还在处理中，本轮未发送，请稍后再试。' });
      }
      return;
    }
    const run = Symbol('business-turn');
    const ownedRequestId = entry.requestId;
    currentRun = run; currentRequestId = entry.requestId;
    const isCurrent = () => isActive() && currentRun === run && entry.requestId === ownedRequestId && messages.value.includes(entry);
    chatBusy.value = true; entry.pending = true; entry.cancelError = '';
    pushActivity(entry, 'AI 处理原话');
    try {
      const result = await sendAiTurnStream(entry.turn, entry, run);
      if (result && isCurrent()) {
        delete entry.lastError; applyBusinessReply(entry, result);
        entry._replyCompletedRequestId = entry.requestId; stopReceiptPoll(entry);
      }
      if (isCurrent() && hasFocusEntry()) nextTick(tryFocusSent);
    } catch (error) {
      if (!isCurrent()) return;
      if (completedReply(entry)) { stopReceiptPoll(entry); return; }
      if (error?.name === 'AbortError') {
        entry.pending = false;
        sealReasoningStatus(entry, true);
        sealActivities(entry);
        entry.text = '已请求停止，正在读取最终回执。'; entry.turn.state = 'uncertain';
        settleTurn(entry, { state: 'uncertain', text: entry.text, error: true });
        startReceiptPoll(entry); return;
      }
      handleTurnError(entry, error);
    } finally {
      if (currentRun === run) {
        if (entry.requestId === ownedRequestId) {
          if (entry.turn?.state === 'sending') entry.turn.state = 'uncertain';
          entry.pending = false;
        }
        chatBusy.value = false; setFocusEntry(null);
        if (isActive() && nearBottom()) await bottom();
        if (currentRun === run) { currentRun = null; currentRequestId = null; }
      }
    }
  }

  // 把回执应用到消息视图；授权等待与真正失败分别显示。
  function applyReceipt(entry, receipt) {
    if (completedReply(entry)) { stopReceiptPoll(entry); return 'completed'; }
    entry.receiptNotice = '';
    if (receipt.result) {
      dropTurn(conversationId.value, entry.requestId);
      if (receipt.result.contractVersion === 1) { conversationId.value = receipt.result.conversationId; messages.value = applyAiReply(messages.value, entry, receipt.result); applyFinancialFields(entry, receipt.result); }
      else applyBusinessReply(entry, receipt.result);
      sealReasoningStatus(entry);
      if (entry.turn) entry.turn.state = 'completed';
      entry._replyCompletedRequestId = entry.requestId; stopReceiptPoll(entry);
      return 'completed';
    }
    if (receipt.status === 'failed') stopReceiptPoll(entry);
    if (receipt.status === 'failed') sealReasoningStatus(entry, true);
    if (entry.turn) entry.turn.state = receipt.status === 'failed' ? 'failed' : 'uncertain';
    if (receipt.status === 'failed' && isAiAuthorizationFailure(receipt.error?.code)) {
      if (entry.turn) entry.turn.state = 'awaiting_authorization';
      entry.pending = false; entry.error = false;
      entry.lastError = { code: receipt.error.code };
      entry.text = receipt.error.message || '需要你授权后，我才能继续使用这项能力。';
      settleTurn(entry, { state: entry.turn.state, text: entry.text, error: false, lastError: entry.lastError });
      return 'awaiting_authorization';
    }
    if (receipt.providerDetail) entry.providerDetail = receipt.providerDetail;
    if (receipt.status === 'failed') entry.lastError = { code: receipt.error?.code || null,
      ...(Number.isInteger(receipt.providerCalls) ? { providerCalls: receipt.providerCalls } : {}) };
    entry.pending = false;
    entry.error = receipt.status === 'failed';
    entry.text = receipt.status === 'failed' ? (receipt.error?.message || '本次请求已停止，没有再次调用 AI。') : '本次请求仍在处理中，已自动等待回执。';
    if (receipt.status === 'failed') settleTurn(entry, { state: 'failed', text: entry.text, error: true, lastError: entry.lastError });
    if (receipt.status === 'failed') void recoverPendingTask(entry);
    return receipt.status === 'failed' ? 'failed' : 'processing';
  }
  // 只读回执自动轮询：覆盖重启租约，绝不重发 POST。
  function startReceiptPoll(entry) {
    if (completedReply(entry)) { stopReceiptPoll(entry); return; }
    if (!isActive() || !entry?.requestId || pollers.has(entry.id) || !messages.value.includes(entry)) return;
    const requestId = entry.requestId, id = conversationId.value;
    const poller = createReceiptPoll({ fetchReceipt: () => getAiBusinessReceipt(requestId) });
    pollers.set(entry.id, poller);
    poller.start(async event => {
      if (!isActive() || id !== conversationId.value || entry.requestId !== requestId ||
        pollers.get(entry.id) !== poller || !messages.value.includes(entry)) return;
      if (completedReply(entry)) { stopReceiptPoll(entry); return; }
      if (event.type === 'completed') { applyReceipt(entry, event.receipt); stopReceiptPoll(entry); await bottom(); }
      else if (event.type === 'failed') { applyReceipt(entry, event.receipt); stopReceiptPoll(entry); await bottom(); }
      else if (event.type === 'exhausted') {
        entry.pending = false;
        entry.receiptNotice = '自动读取已结束，服务恢复后可点「立即读取」核对结果。';
        pollers.delete(entry.id);
      }
      else if (event.type === 'invalid') {
        entry.pending = false; entry.receiptNotice = '回执暂时无法显示，已停止自动读取，可稍后手动读取回执。'; pollers.delete(entry.id);
      }
    });
  }
  // 图片轮次没有 retryOf 契约；失败后可按内存中的原图重新发起一次 OCR。
  async function retryImageTurn(entry) {
    const original = entry.turn;
    const imageDataUrl = original?.imageDataUrl;
    if (!imageDataUrl) {
      entry.receiptNotice = '这张图片已不在本机，请重新选择图片后发送。';
      return;
    }
    stopReceiptPoll(entry);
    dropTurn(conversationId.value, original.requestId);
    const requestId = createAiRequestId();
    const user = messages.value.find(item => item.role === 'user' && item.requestId === original.requestId);
    for (const item of [entry, user].filter(Boolean)) {
      item.supersededRequestIds = [...new Set([...(item.supersededRequestIds || []), original.requestId])];
      item.requestId = requestId; item.remote = false;
      delete item.messageId; delete item.createdAt; delete item.failure; delete item.lastError;
      delete item.authorizationRequest; delete item.authorizationDenied; delete item.providerDetail;
      delete item.receiptNotice; delete item.retryOf; delete item.continuedBy;
      delete item._failedPersistedRequestId; delete item._replyCompletedRequestId; delete item._requestNotAdmitted;
    }
    Object.assign(entry, { pending: true, error: false, text: '', cancelError: '', thinking: '', thinkingRound: 0, thinkingRounds: {}, thinkingDraftRounds: {}, thinkingLive: '', thinkingState: null,
      operationOutcome: null, activities: [], activitiesTotal: '', activitiesOpen: undefined, activitiesUserTouched: false,
      draft: null, draftId: null, proposal: null, datasets: [], chart: null, evidence: [], toolTrace: [], usage: null,
      financialError: '', draftNotice: '', ocr: null, financialCheck: null, financialChoices: null, financialClosed: false,
      inputCandidate: null, pendingTask: null, businessArtifactId: null, sent: false });
    entry.turn = { requestId, message: original.message, imageDataUrl,
      request: { message: original.message, conversationId: conversationId.value, imageDataUrl }, state: 'ready', sent: false };
    journalTurn(entry, original.message);
    await requestReply(original.message, entry, { dataUrl: imageDataUrl });
  }
  // Manual retry creates one new run. An uncertain run must reach a terminal receipt first.
  async function retryTurn(entry, { regenerate = false } = {}) {
    const regeneration = regenerate && entry.turn?.state === 'completed' && canRegenerate(entry);
    if (!isActive() || transferBusy.value || entry._retrying || !messages.value.includes(entry) || !entry.turn?.message ||
        !regeneration && !['failed', 'cancelled', 'awaiting_authorization'].includes(entry.turn.state)) return;
    if (entry.authorizationRequest && !authGranted.value) { openGrantDialog(entry); return; }
    if (!regeneration && entry.turn.imageDataUrl) {
      entry._retrying = true;
      try { await retryImageTurn(entry); }
      finally { entry._retrying = false; if (isActive() && messages.value.includes(entry)) await bottom(); }
      return;
    }
    entry._retrying = true;
    try {
      const original = entry.turn;
      if (!regeneration && !entry._failedPersistedRequestId &&
        ['AI_TURN_INTERRUPTED', 'AI_TURN_TIMEOUT', 'AI_REQUEST_CANCELLED'].includes(entry.lastError?.code)) {
        // The receipt endpoint reconstructs a crash's missing failure history.
        // Confirm that terminal authority before asking for the continuation.
        await recoverReply(entry);
        if (!isActive() || entry.turn !== original || !messages.value.includes(entry) ||
          !['failed', 'cancelled', 'awaiting_authorization'].includes(entry.turn.state)) return;
      }
      let retryOf = !regeneration && entry._failedPersistedRequestId === original.requestId
        ? original.requestId : null;
      if (!regeneration && !retryOf && !entry._requestNotAdmitted) {
        // A failed run's receipt may precede its durable failure message.
        // Never replace a missing parent claim with an unrelated paid request.
        const viewId = conversationId.value;
        const id = original.request.conversationId || viewId;
        try {
          if (typeof getAiConversationSync !== 'function') throw new Error('Failure history unavailable');
          const snapshot = await receiptDeadline(getAiConversationSync(id), 12000);
          if (!isActive() || conversationId.value !== viewId || entry.turn !== original ||
            entry.requestId !== original.requestId || !messages.value.includes(entry) || transferBusy.value) return;
          const saved = snapshot.messages.find(item => item.role === 'assistant' && item.requestId === original.requestId);
          const continuedBy = saved?.continuedBy || snapshot.retryLinks?.find(link => link.retryOf === original.requestId)?.requestId;
          if (continuedBy) { entry.receiptNotice = '此问题已有后续请求，请先刷新或读取当前结果。'; return; }
          if (saved?.failure?.status === 'failed') {
            const users = snapshot.messages.filter(item => item.role === 'user' && item.requestId === original.requestId);
            const texts = [...new Set(users.map(item => item.content))];
            const message = texts.length === 1 && typeof texts[0] === 'string' ? texts[0] : '';
            if (users.length && message !== original.message) {
              entry.turn = { ...original, message, request: { ...original.request, message } };
              entry.receiptNotice = '已恢复这条请求的原问题，请核对后再重试。'; return;
            }
            retryOf = original.requestId;
          } else if (saved) { entry.receiptNotice = '上一轮已有结果，请先读取回执。'; await recoverReply(entry); return; }
          else { entry.receiptNotice = '上一轮失败记录尚未保存，请稍后读取回执再重试。'; return; }
        } catch {
          if (isActive() && messages.value.includes(entry)) entry.receiptNotice = '暂时无法确认上一轮状态，请稍后再试。';
          return;
        }
      }
      stopReceiptPoll(entry);
      dropTurn(conversationId.value, original.requestId);
      const requestId = createAiRequestId();
      // The continued run has a new server identity. Keep its predecessors only
      // to absorb delayed history/activity echoes; they no longer own this view.
      const user = messages.value.find(item => item.role === 'user' && item.requestId === original.requestId);
      for (const item of [entry, user].filter(Boolean)) {
        item.supersededRequestIds = [...new Set([...(item.supersededRequestIds || []), original.requestId])];
        item.requestId = requestId; item.remote = false;
        delete item.messageId; delete item.createdAt;
        delete item.failure; delete item.lastError; delete item.authorizationRequest;
        delete item.authorizationDenied; delete item.providerDetail; delete item.receiptNotice;
        delete item.retryOf; delete item.continuedBy;
        delete item._failedPersistedRequestId; delete item._replyCompletedRequestId; delete item._requestNotAdmitted;
        if (retryOf) item.retryOf = retryOf;
      }
      const originalRequest = { ...original.request };
      delete originalRequest.retryOf;
      entry.turn = { requestId, message: original.message, request: { ...originalRequest, ...(retryOf ? { retryOf } : {}), mode: aiMode.value, conversationId: original.request.conversationId || conversationId.value }, state: 'ready', sent: false };
      entry.requestId = requestId;
      journalTurn(entry, original.message);
      Object.assign(entry, { pending: true, error: false, text: '', cancelError: '', thinking: '', thinkingRound: 0, thinkingRounds: {}, thinkingDraftRounds: {}, thinkingLive: '', thinkingState: null,
        operationOutcome: null, activities: [], activitiesTotal: '', activitiesOpen: undefined, activitiesUserTouched: false,
        draft: null, draftId: null, proposal: null, datasets: [], chart: null, evidence: [], toolTrace: [], usage: null,
        financialError: '', draftNotice: '', pendingTask: null });
      delete entry.providerDetail;
      await confirmTurn(entry);
    } finally { entry._retrying = false; if (isActive() && messages.value.includes(entry)) await bottom(); }
  }

  // 回执读取是只读操作：不占用全局 busy，避免网络抖动时整页按钮一起锁死。
  // 即使底层请求一直不返回，也要在超时后交还按钮并转入只读轮询。
  function receiptDeadline(promise, ms) {
    let timer, cancel;
    return Promise.race([
      promise,
      new Promise((_, reject) => {
        cancel = () => reject(Object.assign(new Error('回执读取已结束'), { name: 'AbortError' }));
        receiptDeadlines.add(cancel);
        timer = setTimeout(() => reject(Object.assign(new Error('回执读取超时'), { kind: 'network', status: -1 })), ms);
      }),
    ]).finally(() => { clearTimeout(timer); receiptDeadlines.delete(cancel); });
  }
  async function recoverReply(entry) {
    if (completedReply(entry)) { stopReceiptPoll(entry); return; }
    if (!isActive() || !messages.value.includes(entry) || entry.receiptBusy || !entry.requestId || entry.turn?.state === 'sending') return;
    const id = conversationId.value, requestId = entry.requestId;
    const isCurrent = () => isActive() && id === conversationId.value &&
      entry.requestId === requestId && messages.value.includes(entry);
    entry.receiptBusy = true;
    entry.receiptNotice = '';
    try {
      const receipt = await receiptDeadline(getAiBusinessReceipt(requestId), 12000);
      if (!isCurrent()) return;
      if (completedReply(entry)) { stopReceiptPoll(entry); return; }
      const status = applyReceipt(entry, receipt);
      if (status === 'processing') startReceiptPoll(entry);
      else {
        pollers.get(entry.id)?.stop(); pollers.delete(entry.id);
        // 手动读取后给一次可见回执，即使结论与气泡原文相同。
        if (status === 'failed') {
          const stamp = new Date().toLocaleTimeString('zh-CN', { hour12: false });
          entry.receiptNotice = `回执已读取 ${stamp} · 失败${receipt.error?.code ? ` · ${receipt.error.code}` : ''}`;
        }
      }
    } catch (error) {
      if (!isCurrent()) return;
      if (completedReply(entry)) { stopReceiptPoll(entry); return; }
      const network = error?.kind === 'network' || error?.code === 'ECONNABORTED' || error?.status === -1 || Number(error?.status) >= 500;
      if (network) {
        entry.receiptNotice = '回执暂时读取不到，已转为自动等待；服务恢复后会继续核对结果。';
        startReceiptPoll(entry);
      } else entry.receiptNotice = error?.message || '回执暂时无法读取';
    } finally { entry.receiptBusy = false; if (isCurrent()) await bottom(); }
  }

  function isTextAnswer(item) {
    if (!businessEnabled.value || item.role !== 'assistant' || !messages.value.includes(item) || !item.turn?.message?.trim() || item.pending) return false;
    const related = item.requestId ? messages.value.filter(entry => entry.requestId === item.requestId) : [item];
    return !related.some(entry => entry.authorizationRequest || entry.authorizationDenied || entry.ocr || entry.imagePreview || entry.requestImage ||
      entry.draft || entry.draftId || entry.proposal || entry.proposalId || entry.inputCandidate || entry.candidateId || entry.businessArtifactId ||
      entry.operationOutcome?.artifacts?.length);
  }
  function canRegenerate(item) {
    if (!isTextAnswer(item)) return false;
    const list = messages.value;
    return list[list.length - 1] === item && ['completed', 'failed', 'cancelled'].includes(item.turn.state);
  }
  function canReask(item) {
    return isTextAnswer(item) && item.turn.state === 'completed' && messages.value[messages.value.length - 1] !== item;
  }
  async function reaskTurn(original) {
    if (!isActive() || !capability.value?.enabled || busy.value || !canReask(original)) return;
    const text = original.turn.message;
    const requestId = createAiRequestId(), sentAt = Date.now();
    messages.value.push({ id: runtime.nextId(), role: 'user', requestId, text, sentAt });
    const userEntry = messages.value[messages.value.length - 1];
    messages.value.push({ id: runtime.nextId(), role: 'assistant', pending: true, requestId, sentAt, thinkingState: null });
    const entry = messages.value[messages.value.length - 1];
    journalTurn(entry, text, sentAt);
    settingsNotice.value = '';
    setFocusEntry(userEntry);
    nextTick(() => tryFocusSent(true));
    await prepareTurn(text, entry);
  }
  // 追加式重试：中间历史应答无法原地重发（服务端无回退接口），只能以对应原话新发一轮；
  // 原应答与其产物保留原地，新应答追加到会话尾部。恢复中（uncertain，回执未定）的轮次
  // 先走「读取回执」，不提供重试入口。
  // 2026-11-10 G：canRetryAnswer/retryAnswer 死代码删除（中段应答无法原地重发，追加式重试由 regenerate 承担）。
  // Regeneration applies only to answers without a persisted write artifact.
  async function regenerate(entry) {
    if (!isActive() || transferBusy.value || !canRegenerate(entry)) return;
    await retryTurn(entry, { regenerate: entry.turn.state === 'completed' });
  }

  function dispose() {
    disposed = true;
    invalidateRequests();
    for (const cancel of receiptDeadlines) cancel();
    receiptDeadlines.clear();
    setFocusEntry(null);
  }

  return { requestReply, send, prepareTurn, sendAiTurnStream, stopStream, invalidateRequests, stopAllPolling,
    handleTurnError, confirmTurn, applyReceipt, startReceiptPoll, stopReceiptPoll, settleSyncedTurn, retryTurn, recoverReply,
    canRegenerate, canReask, reaskTurn, regenerate, dispose };
}
