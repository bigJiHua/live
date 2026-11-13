import { nextTick } from 'vue';

// Conversation transitions commit only after the destination has been restored.
// A failed transition keeps the current messages, draft and image attachment.
export function useAssistantConversation({ state, services, runtime, actions }) {
  const { capability, initializing, connectionError, businessEnabled, modelOptions,
    scrolledAway, messages, conversationId, input, consent, imageConsentRemembered,
    settingsNotice, busy, chatBusy, historyOpen, historyLoading, historyError, historyList } = state;
  const { getAiCapabilities, getAiConversationMessages, getAiBusinessSession,
    createAiBusinessSession, listAiConversations, deleteAiConversation, showConfirmDialog,
    readLastConversation, clearLastConversation, clearConversation } = services;
  const { loadComposerProfile, requestedConversationId, restoreBusinessHistory,
    seedSyncCursor, resetSyncCursor, appendJournal, bottom, startSyncPolling,
    stopAllPolling, stopSyncPolling, releaseImages, clearImage, resizeInput, invalidateRequests,
    discardDraft } = actions;
  let conversationRevision = 0;
  let historyRevision = 0;
  let disposed = false;
  const isActive = () => !disposed && runtime.isActive();

  async function loadCapabilities() {
    if (!isActive()) return;
    const revision = ++conversationRevision;
    let expectedId = conversationId.value;
    const valid = () => isActive() && revision === conversationRevision;
    const current = () => valid() && expectedId === conversationId.value;
    initializing.value = true;
    connectionError.value = '';
    try {
      const capabilities = await getAiCapabilities();
      if (!current()) return;
      capability.value = capabilities;
      if (capability.value.enabled && !businessEnabled.value) throw new Error('聊天服务需要更新，请启动新版 API。');
      loadComposerProfile();
      if (businessEnabled.value) {
        const requested = requestedConversationId() || readLastConversation();
        let destination;
        if (requested) {
          try {
            const list = await getAiConversationMessages(requested, { suppressToast: true });
            if (!current()) return;
            const restored = await restoreBusinessHistory(list, requested);
            if (!current()) return;
            destination = { id: requested, list, restored };
          } catch (error) {
            if (!current()) return;
            if (![404, 410].includes(Number(error?.status))) throw error;
            // Expired links quietly fall back to the default conversation.
            clearLastConversation(requested);
          }
        }
        if (!destination) {
          const session = await getAiBusinessSession();
          if (!current()) return;
          const list = session.messages || [];
          const restored = await restoreBusinessHistory(list, session.conversationId);
          if (!current()) return;
          destination = { id: session.conversationId, list, restored };
        }
        stopSyncPolling();
        seedSyncCursor(destination.list);
        messages.value = destination.restored.map(entry => ({ ...entry, id: runtime.nextId() }));
        expectedId = destination.id;
        conversationId.value = destination.id;
        await appendJournal();
        if (current()) await bottom();
      }
    } catch (error) {
      if (!current()) return;
      capability.value = null;
      connectionError.value = error?.message || '助手连接失败';
    } finally {
      if (valid()) { initializing.value = false; startSyncPolling(); }
    }
  }

  async function settingsChanged(notice) {
    if (!isActive()) return;
    invalidateRequests();
    stopAllPolling(); stopSyncPolling(); resetSyncCursor();
    modelOptions.value = [];
    releaseImages(); scrolledAway.value = false;
    messages.value = []; conversationId.value = undefined; input.value = ''; consent.value = false;
    clearImage(); imageConsentRemembered.value = false;
    settingsNotice.value = notice;
    await loadCapabilities();
    nextTick(() => { if (isActive()) resizeInput(); });
  }

  async function newConversation() {
    if (!isActive() || busy.value) return;
    const revision = ++conversationRevision;
    const originalId = conversationId.value;
    const valid = () => isActive() && revision === conversationRevision;
    const current = () => valid() && originalId === conversationId.value;
    let id;
    if (businessEnabled.value) {
      chatBusy.value = true;
      try {
        const session = await createAiBusinessSession();
        if (!current()) return;
        id = session.conversationId;
      } catch (error) {
        if (current()) settingsNotice.value = error?.message || '新对话创建失败';
        return;
      } finally { if (valid()) chatBusy.value = false; }
    }
    if (!current()) return;
    stopAllPolling(); stopSyncPolling(); resetSyncCursor();
    releaseImages(); scrolledAway.value = false;
    conversationId.value = id;
    messages.value = []; consent.value = false; input.value = ''; imageConsentRemembered.value = false;
    clearImage(); settingsNotice.value = '';
    nextTick(() => { if (valid() && conversationId.value === id) resizeInput(); });
    startSyncPolling();
  }

  async function openHistory() {
    if (!isActive()) return;
    const revision = ++historyRevision;
    const current = () => isActive() && revision === historyRevision;
    historyOpen.value = true; historyLoading.value = true; historyError.value = '';
    try {
      const list = await listAiConversations();
      if (current()) historyList.value = list;
    } catch (error) {
      if (current()) historyError.value = error?.message || '历史对话读取失败，请稍后重试';
    } finally { if (current()) { historyLoading.value = false; startSyncPolling(); } }
  }

  async function resumeConversation(item) {
    if (!isActive() || busy.value) return;
    if (item.id === conversationId.value) { historyOpen.value = false; return; }
    const revision = ++conversationRevision;
    let expectedId = conversationId.value;
    const valid = () => isActive() && revision === conversationRevision;
    const current = () => valid() && expectedId === conversationId.value;
    stopAllPolling(); stopSyncPolling();
    historyLoading.value = true; historyError.value = '';
    try {
      const list = await getAiConversationMessages(item.id);
      if (!current()) return;
      const restored = await restoreBusinessHistory(list, item.id);
      if (!current()) return;
      releaseImages(); scrolledAway.value = false;
      messages.value = restored.map(entry => ({ ...entry, id: runtime.nextId() }));
      seedSyncCursor(list);
      expectedId = item.id;
      conversationId.value = item.id;
      clearImage(); imageConsentRemembered.value = false;
      consent.value = capability.value?.requiresConsent ? consent.value : false;
      historyOpen.value = false;
      await appendJournal();
      nextTick(() => { if (current()) { bottom(); resizeInput(); } });
    } catch (error) {
      if (current()) historyError.value = error?.message || '无法恢复这段对话，请稍后重试';
    } finally { if (valid()) { historyLoading.value = false; startSyncPolling(); } }
  }

  async function removeConversation(item) {
    if (!isActive() || busy.value) return;
    const revision = ++conversationRevision;
    const originalId = conversationId.value;
    const valid = () => isActive() && revision === conversationRevision;
    const current = () => valid() && originalId === conversationId.value;
    historyLoading.value = true;
    try {
      await showConfirmDialog({ title: '删除对话', message: `删除「${item.title}」？不可恢复。`, confirmButtonText: '删除', cancelButtonText: '取消' });
      if (!current()) return;
      await deleteAiConversation(item.id);
      if (!current()) return;
      if (conversationId.value === item.id) discardDraft(item.id);
      clearConversation(item.id);
      clearLastConversation(item.id);
      historyList.value = historyList.value.filter(entry => entry.id !== item.id);
      if (conversationId.value === item.id) {
        stopAllPolling(); stopSyncPolling(); resetSyncCursor();
        releaseImages(); scrolledAway.value = false;
        conversationId.value = undefined; messages.value = [];
        clearImage(); imageConsentRemembered.value = false;
      }
    } catch { /* Cancellation or deletion failure keeps the list and current view. */ }
    finally { if (valid()) historyLoading.value = false; }
  }

  function initialize() { return loadCapabilities(); }
  function dispose() { disposed = true; conversationRevision++; historyRevision++; }
  return { loadCapabilities, settingsChanged, newConversation, openHistory,
    resumeConversation, removeConversation, initialize, dispose };
}
