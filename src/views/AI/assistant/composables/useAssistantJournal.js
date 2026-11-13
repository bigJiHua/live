import { nextTick, watch } from 'vue';
import { mergeSyncMessages } from '../../../../utils/aiConversationSync.js';

// Local storage keeps text drafts and unfinished turns; image data never enters it.
export function useAssistantJournal({ state, services, runtime, actions }) {
  const { conversationId, input, grant, messages } = state;
  const { route, router, readDraft, saveDraft, readTurns, addTurn, updateTurn, dropTurn,
    saveLastConversation, getAiBusinessReceipt, restoreAiBusinessHistory,
    getAiDraft, getAiProposal, getAiTransactionCandidate, getAiPendingTransaction } = services;
  const { stopSyncPolling, refreshGrant, resizeInput, startSyncPolling,
    applyReceipt, startReceiptPoll, bottom } = actions;
  let draftConversation = conversationId.value;
  let draftTimer;
  let journalRevision = 0;
  let initialized = false;
  let disposed = false;
  const stops = [];
  const isActive = () => !disposed && runtime.isActive();

  function flushDraft() {
    clearTimeout(draftTimer);
    saveDraft(draftConversation, input.value);
  }
  // 删除会话时丢弃该会话的待落盘草稿，避免 watcher 在切换前又写回旧键。
  function discardDraft(id) {
    clearTimeout(draftTimer);
    draftTimer = undefined;
    if (id && id === conversationId.value) {
      draftConversation = null;
      input.value = '';
    }
  }
  function syncConversationUrl(id) {
    const query = { ...route.query };
    if (id) query.c = id; else delete query.c;
    if ((typeof route.query.c === 'string' ? route.query.c : '') === (id || '')) return;
    Promise.resolve(router.replace({ path: route.path, query })).catch(() => {});
  }
  function requestedConversationId() {
    const value = route.query.c;
    return typeof value === 'string' && /^[A-Za-z0-9_-]{8,64}$/.test(value) ? value : '';
  }
  function journalTurn(entry, text, sentAt = Date.now()) {
    addTurn(conversationId.value, { requestId: entry.requestId, userText: text, sentAt,
      ...(entry.turn?.request?.retryOf ? { retryOf: entry.turn.request.retryOf } : {}),
      ...(entry.supersededRequestIds?.length ? { supersededRequestIds: entry.supersededRequestIds } : {}) });
  }
  function settleTurn(entry, patch) { updateTurn(conversationId.value, entry.requestId, patch); }
  function finishTurn(entry) { dropTurn(conversationId.value, entry.requestId); }

  async function restoreBusinessHistory(list, id = conversationId.value) {
    const restored = await restoreAiBusinessHistory(list, getAiDraft,
      proposalId => getAiProposal(proposalId, { suppressToast: true }), getAiTransactionCandidate,
      () => getAiPendingTransaction(id));
    const originals = new Map();
    for (const source of list) if (source.role === 'user' && source.requestId)
      originals.set(source.requestId, source.content ?? source.text ?? '');
    for (const entry of restored) if (entry.role === 'assistant' && entry.requestId && !entry.turn) {
      const message = originals.get(entry.requestId);
      if (typeof message === 'string' && message.trim()) entry.turn = {
        requestId: entry.requestId, state: 'completed', message,
        request: { message, conversationId: id },
      };
    }
    // Retry recovered failures in the original conversation, never a new default.
    for (const entry of restored) if (entry.turn?.state === 'failed' && entry.turn.request && id)
      entry.turn.request = { ...entry.turn.request, conversationId: id };
    return mergeSyncMessages([], restored);
  }

  async function appendJournal() {
    const id = conversationId.value;
    const revision = ++journalRevision;
    const current = () => isActive() && revision === journalRevision && id === conversationId.value;
    const turns = readTurns(id);
    if (!turns.length || !current()) return;
    for (const turn of turns) {
      if (!current()) return;
      const previous = turn.supersededRequestIds || (turn.retryOf ? [turn.retryOf] : []);
      const predecessorTimes = messages.value.filter(item => previous.includes(item.requestId))
        .map(item => Number(item.sentAt) || Date.parse(item.createdAt) || 0).filter(time => time > 0);
      const existing = messages.value.filter(item => item.requestId === turn.requestId);
      if (existing.some(item => item.role === 'assistant')) {
        const sentAt = turn.sentAt || turn.at;
        for (const item of existing) if (!item.sentAt && sentAt) item.sentAt = sentAt;
        messages.value = mergeSyncMessages(messages.value, []);
        if (existing.some(item => item.role === 'assistant' && (item.turn?.state === 'completed'
          || item._failedPersistedRequestId === turn.requestId || item.messageId && item.turn?.state === 'failed'))) dropTurn(id, turn.requestId);
        continue;
      }
      const sentAt = predecessorTimes.length ? Math.min(...predecessorTimes) : turn.sentAt || turn.at;
      const additions = existing.some(item => item.role === 'user') ? [] : [{ id: runtime.nextId(), role: 'user', requestId: turn.requestId,
        supersededRequestIds: previous, text: turn.userText || '（这条原话未能保存）', sentAt }];
      const entry = { id: runtime.nextId(), role: 'assistant', requestId: turn.requestId,
        pending: true, sentAt, supersededRequestIds: previous,
        turn: { requestId: turn.requestId, message: turn.userText || '', state: 'uncertain',
          request: { message: turn.userText || '', conversationId: id, ...(turn.retryOf ? { retryOf: turn.retryOf } : {}) } } };
      additions.push(entry);
      messages.value = mergeSyncMessages(messages.value, additions);
      const restoredEntry = messages.value.find(item => item.role === 'assistant' && item.requestId === turn.requestId);
      // These dedicated codes are emitted only before server run admission.
      // Preserve their explicit zero-call proof across a local page refresh.
      if (['AI_PREPARATION_TIMEOUT', 'AI_PREPARATION_CANCELLED'].includes(turn.lastError?.code) &&
        turn.lastError?.providerCalls === 0 && turn.lastError?.notAdmitted === true) {
        Object.assign(restoredEntry, { pending: false, error: true, text: turn.text || '本次请求未启动，请重试。',
          lastError: turn.lastError, _requestNotAdmitted: true });
        restoredEntry.turn.state = 'failed';
        continue;
      }
      try {
        const receipt = await getAiBusinessReceipt(turn.requestId);
        if (!current() || !messages.value.includes(restoredEntry)) return;
        const status = applyReceipt(restoredEntry, receipt);
        if (status === 'completed') dropTurn(id, turn.requestId);
        else if (status === 'processing') startReceiptPoll(restoredEntry);
      } catch (error) {
        if (!current() || !messages.value.includes(restoredEntry)) return;
        Object.assign(restoredEntry, { pending: false, error: true,
          text: turn.text || '回执暂时无法读取，可点「读取回执」查询。',
          turn: { ...restoredEntry.turn, state: turn.state === 'failed' ? 'failed' : 'uncertain' },
          ...(turn.lastError ? { lastError: turn.lastError } : {}) });
        if (restoredEntry.turn.state === 'uncertain' && error?.kind !== 'protocol' && error?.code !== 'AI_RESPONSE_INVALID')
          startReceiptPoll(restoredEntry);
      }
    }
    if (current()) await bottom();
  }

  // Install effects after every module exists, so their callbacks are available.
  function initialize() {
    if (initialized || disposed) return;
    initialized = true;
    stops.push(watch(conversationId, id => {
      if (!isActive()) return;
      stopSyncPolling();
      grant.value = { granted: false };
      if (id) refreshGrant(id);
      flushDraft();
      draftConversation = id;
      syncConversationUrl(id);
      if (id) saveLastConversation(id);
      input.value = readDraft(id);
      nextTick(() => { if (isActive() && id === conversationId.value) resizeInput(); });
      startSyncPolling();
    }));
    stops.push(watch(input, value => {
      if (!isActive()) return;
      clearTimeout(draftTimer);
      const key = draftConversation;
      draftTimer = setTimeout(() => { if (isActive()) saveDraft(key, value); }, 300);
    }));
    if (typeof window !== 'undefined') window.addEventListener('pagehide', flushDraft);
  }
  function dispose() {
    if (disposed) return;
    disposed = true; journalRevision++;
    for (const stop of stops.splice(0)) stop();
    if (typeof window !== 'undefined') window.removeEventListener('pagehide', flushDraft);
    flushDraft();
  }
  return { flushDraft, discardDraft, syncConversationUrl, requestedConversationId, journalTurn,
    settleTurn, finishTurn, restoreBusinessHistory, appendJournal, initialize, dispose };
}
