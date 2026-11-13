import { watch } from 'vue';
import { isAiAuthorizationFailure } from '../../../../utils/aiTurn.js';

const entityFields = ['draft', 'draftId', 'draftNotice', 'financialClosed',
  'inputCandidate', 'financialCheck', 'financialChoices', 'proposal', 'proposalId'];
const candidateId = item => item.inputCandidate?.sourceCandidateId || item.inputCandidate?.id || item.candidateId;
function entityState(item) {
  if (item.draftId || item.draft?.id) return { kind: 'drafts', id: item.draftId || item.draft.id, entity: item.draft };
  if (item.proposalId || item.proposal?.id) return { kind: 'proposals', id: item.proposalId || item.proposal.id, entity: item.proposal };
  if (candidateId(item)) return { kind: 'candidates', id: candidateId(item), entity: item.inputCandidate, closed: item.financialClosed };
  return {};
}
function unchangedEntity(item, before) {
  const now = entityState(item);
  return now.kind === before.kind && now.id === before.id && now.entity === before.entity && now.closed === before.closed;
}

// Owns one visible subscription and commits snapshots after all entity reads.
export function useAssistantSync({ state, services, runtime, actions }) {
  const { messages, conversationId, cardWork, imageConsentRemembered, scrolledAway,
    input, settingsNotice, initializing, historyLoading, transferBusy } = state;
  const { createConversationSync, mergeSyncMessages, subscribeAiConversation,
    getAiConversationSync, clearConversation, clearLastConversation, STAGE_LABELS } = services;
  const { restoreBusinessHistory, nearBottom, bottom, invalidateRequests, stopAllPolling, stopReceiptPoll, settleSyncedTurn,
    startReceiptPoll, settleTurn, clearImage, releaseImages } = actions;
  let syncCursor = 0;
  let syncConversation = '';
  let stopBusyWatch;
  let initialized = false;
  let disposed = false;
  const isActive = () => !disposed && runtime.isActive();
  const cardWorking = item => Object.keys(cardWork.value).some(key => key.startsWith(item.id + '|'));

  function seedSyncCursor(list) {
    syncCursor = list.reduce((max, item) => Math.max(max, Number(item?.messageId) || 0), 0);
  }
  function resetSyncCursor() { syncCursor = 0; }

  async function prepareEntityUpdates(entities, context) {
    const current = () => isActive() && context.isCurrent() && context.conversationId === conversationId.value;
    const updates = [];
    for (const item of messages.value) {
      if (!current()) return [];
      if (cardWorking(item)) continue;
      const before = entityState(item), { kind, id, entity } = before;
      if (!kind) continue;
      const meta = entities[kind]?.find(entity => entity.id === id);
      if (!meta) continue;
      const revision = JSON.stringify(meta);
      if (item._syncEntityRevision === revision || entity && entity.status === meta.status &&
        entity.version != null && entity.version === meta.version && entity.expiresAt === meta.expiresAt) continue;
      const [restored] = await restoreBusinessHistory([{ role: 'assistant', content: '',
        ...(kind === 'drafts' ? { draftId: id } : kind === 'proposals' ? { proposalId: id } : { inputCandidate: { sourceCandidateId: id } }) }], context.conversationId);
      if (!current()) return [];
      const patch = Object.fromEntries(entityFields.map(key => [key, restored?.[key] ?? null]));
      if (restored?.draft || restored?.proposal || restored?.inputCandidate ||
        ['expired', 'discarded', 'failed', 'consumed'].includes(meta.status) &&
        (kind !== 'candidates' || restored?._syncCandidateResolved)) patch._syncEntityRevision = revision;
      updates.push({ item, patch, version: meta.version, before });
    }
    return updates;
  }

  async function applySyncSnapshot(snapshot, context) {
    const current = () => isActive() && context.isCurrent() && context.conversationId === conversationId.value;
    if (!current()) return false;
    const wasNear = nearBottom();
    // History reads can finish after a local candidate was edited, cancelled or
    // converted. Its old pending card must not return as a second artifact.
    const candidateReads = messages.value.map(item => ({ item, before: entityState(item) }))
      .filter(({ before }) => before.kind === 'candidates');
    const parents = new Map((snapshot.retryLinks || []).map(link => [link.requestId, link.retryOf]));
    const superseded = new Set(parents.values());
    const predecessors = requestId => {
      const previous = [], seen = new Set([requestId]);
      while (parents.has(requestId) && !seen.has(parents.get(requestId))) {
        requestId = parents.get(requestId); seen.add(requestId); previous.push(requestId);
      }
      return previous;
    };
    const restored = await restoreBusinessHistory(snapshot.messages.filter(entry => !superseded.has(entry.requestId))
      .map(entry => ({ ...entry, ...(parents.has(entry.requestId) ? { retryOf: parents.get(entry.requestId) } : {}) })), context.conversationId);
    if (!current()) return false;
    const updates = await prepareEntityUpdates(snapshot.entities, { ...context, isCurrent: current });
    if (!current()) return false;
    const originalMessage = requestId => {
      const saved = snapshot.messages.filter(item => item.role === 'user' && item.requestId === requestId);
      const originals = saved.length ? saved.map(item => item.content) : messages.value
        .filter(item => item.role === 'user' && item.requestId === requestId).map(item => item.text);
      const texts = [...new Set(originals)];
      return texts.length === 1 && typeof texts[0] === 'string' ? texts[0] : '';
    };
    for (const { item, patch, version, before } of updates) {
      if (!messages.value.includes(item) || cardWorking(item) || !unchangedEntity(item, before)) continue;
      if (version != null && (item.draft?.version || item.proposal?.version || 0) > version) continue;
      Object.assign(item, patch);
    }
    const incoming = restored.filter(entry => {
      if (entry.draft || entry.draftId || !candidateId(entry)) return true;
      const read = candidateReads.find(({ before }) => before.id === candidateId(entry));
      return !read || messages.value.includes(read.item) && unchangedEntity(read.item, read.before);
    }).map(entry => {
      const own = messages.value.find(item => item.role === entry.role && item.requestId && item.requestId === entry.requestId && !item.remote);
      const original = originalMessage(entry.requestId);
      return { ...entry, ...(parents.has(entry.requestId) ? { supersededRequestIds: predecessors(entry.requestId) } : {}),
        remote: true, pending: false, error: entry.error === true, id: runtime.nextId(),
        ...(entry.role === 'assistant' && entry.requestId && !entry.turn ? { turn: own?.turn ? { ...own.turn, state: 'completed' }
          : { requestId: entry.requestId, state: 'completed', message: original, request: { message: original, conversationId: snapshot.conversationId } } } : {}) };
    });
    for (const entry of incoming) if (entry.turn && ['failed', 'awaiting_authorization'].includes(entry.turn.state)) {
      const message = originalMessage(entry.requestId);
      entry.turn.message = message; entry.turn.request.message = message;
    }
    for (const entry of incoming) if (entry.role === 'assistant' && entry.messageId &&
      ['completed', 'failed', 'awaiting_authorization'].includes(entry.turn?.state)) {
      const live = messages.value.find(item => item.role === 'assistant' && item.requestId === entry.requestId);
      if (live) settleSyncedTurn?.(live, entry.turn.state);
    }
    messages.value = mergeSyncMessages(messages.value, incoming, cardWorking);
    for (const entry of messages.value) if (entry.role === 'assistant' && entry.turn?.state === 'completed' &&
      (entry.messageId || !entry.remote)) stopReceiptPoll?.(entry);
    for (const activity of snapshot.activities) {
      if (superseded.has(activity.requestId) || messages.value.some(item => item.role === 'assistant' && item.supersededRequestIds?.includes(activity.requestId))) continue;
      const existing = messages.value.find(item => item.role === 'assistant' && item.requestId === activity.requestId);
      if (existing && !existing.remote) {
        if (existing.turn?.state === 'sending' && activity.status === 'failed' &&
          ['AI_TURN_INTERRUPTED', 'AI_TURN_TIMEOUT', 'AI_REQUEST_CANCELLED'].includes(activity.errorCode)) {
          settleSyncedTurn?.(existing, 'failed');
          Object.assign(existing, { pending: false, error: true,
            text: activity.notice || '服务中断，正在核对最终回执。' });
          existing.turn.state = 'uncertain';
          settleTurn?.(existing, { state: 'uncertain', text: existing.text, error: true });
          startReceiptPoll?.(existing);
        }
        continue;
      }
      if (existing?.messageId || activity.status === 'completed' && !existing) continue;
      const updatedAt = activity.updatedAt ?? activity.createdAt;
      const rank = { queued: 0, running: 1, failed: 2, completed: 3 };
      if (existing?._syncActivityUpdatedAt > updatedAt || existing?._syncActivityUpdatedAt === updatedAt &&
        (rank[existing._syncActivityStatus] ?? -1) > (rank[activity.status] ?? -1)) continue;
      const pending = ['queued', 'running'].includes(activity.status);
      const awaitingAuthorization = activity.status === 'failed' && isAiAuthorizationFailure(activity.errorCode);
      const preparationNotAdmitted = activity.status === 'failed' &&
        ['AI_PREPARATION_TIMEOUT', 'AI_PREPARATION_CANCELLED'].includes(activity.errorCode);
      const additions = [];
      const previous = predecessors(activity.requestId);
      const identity = previous.length ? { retryOf: parents.get(activity.requestId), supersededRequestIds: previous } : {};
      if (!messages.value.some(item => item.role === 'user' && item.requestId === activity.requestId)) additions.push({
        id: runtime.nextId(), role: 'user', remote: true, requestId: activity.requestId, ...identity,
        text: activity.message, sentAt: activity.createdAt });
      additions.push({ id: runtime.nextId(), role: 'assistant', remote: true, requestId: activity.requestId, ...identity,
        _syncActivityUpdatedAt: updatedAt, _syncActivityStatus: activity.status,
        _requestNotAdmitted: preparationNotAdmitted,
        authorizationRequest: undefined, authorizationDenied: undefined, failure: undefined, continuedBy: undefined,
        pending, error: !pending && !awaitingAuthorization && activity.status !== 'completed',
        text: pending ? (STAGE_LABELS[activity.stage] || '另一台设备的请求正在处理中')
          : activity.status === 'completed' ? '另一台设备已完成，请等待结果同步。' : activity.notice || '这次请求已结束，请查看回执。',
        lastError: preparationNotAdmitted ? { code: activity.errorCode, providerCalls: 0, notAdmitted: true }
          : awaitingAuthorization ? { code: activity.errorCode } : undefined,
        sentAt: activity.createdAt, turn: { requestId: activity.requestId, message: activity.message,
          request: { message: activity.message, conversationId: snapshot.conversationId },
          state: pending ? 'remote' : awaitingAuthorization ? 'awaiting_authorization' : activity.status === 'completed' ? 'completed' : 'failed' } });
      messages.value = mergeSyncMessages(messages.value, additions);
    }
    syncCursor = Math.max(syncCursor, snapshot.lastId);
    if (wasNear && incoming.length) await bottom();
    return current();
  }

  function onRemoteConversationDeleted(id) {
    if (!isActive() || id !== conversationId.value) return;
    invalidateRequests(); stopAllPolling(); stopSyncPolling();
    clearConversation(id); clearLastConversation(id);
    releaseImages(); clearImage(); imageConsentRemembered.value = false;
    scrolledAway.value = false; messages.value = []; input.value = '';
    conversationId.value = undefined; resetSyncCursor();
    settingsNotice.value = '这段聊天已在另一台设备删除，请新建对话。';
  }

  const conversationSync = createConversationSync({
    subscribe: (...args) => subscribeAiConversation(...args),
    pull: (...args) => getAiConversationSync(...args),
    apply: applySyncSnapshot, deleted: onRemoteConversationDeleted,
    onError: (_error, id) => {
      if (!isActive() || id && id !== conversationId.value) return;
      syncConversation = '';
      settingsNotice.value = '聊天同步暂时无法恢复，已停止自动重连，请刷新页面重试。';
    },
    isVisible: () => typeof document === 'undefined' || document.visibilityState === 'visible',
  });
  function startSyncPolling() {
    if (typeof window === 'undefined' || typeof document === 'undefined' || !isActive() ||
      initializing.value || historyLoading.value || !conversationId.value ||
      document.visibilityState !== 'visible' || syncConversation === conversationId.value) return;
    syncConversation = conversationId.value;
    conversationSync.start(syncConversation, syncCursor);
  }
  function stopSyncPolling() { conversationSync.stop(); syncConversation = ''; }
  function onVisibilityChange() {
    if (document.visibilityState === 'visible') startSyncPolling(); else stopSyncPolling();
  }
  function initialize() {
    if (initialized || disposed) return;
    initialized = true;
    stopBusyWatch = watch(transferBusy, value => {
      if (!value && isActive() && syncConversation) void conversationSync.refresh();
    });
    if (typeof window !== 'undefined' && typeof document !== 'undefined')
      document.addEventListener('visibilitychange', onVisibilityChange);
    startSyncPolling();
  }
  function dispose() {
    if (disposed) return;
    disposed = true;
    stopBusyWatch?.();
    if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', onVisibilityChange);
    stopSyncPolling();
  }
  return { seedSyncCursor, resetSyncCursor, prepareEntityUpdates, applySyncSnapshot,
    onRemoteConversationDeleted, startSyncPolling, stopSyncPolling, onVisibilityChange,
    initialize, dispose };
}
