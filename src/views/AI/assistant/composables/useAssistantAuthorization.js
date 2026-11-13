// Authorization owns its revision and expiry clock. Continuation is an explicit
// callback to the turn runner, so authorization never starts a second run itself.
export function useAssistantAuthorization({ state, services, runtime, actions }) {
  const { capability, conversationId, transferBusy, grantBusy, grant, grantClock,
    authDialogOpen, authDialogMode, authError, pendingGrantEntry, authGranted,
    aiMode, settingsNotice } = state;
  const { getAiGrant, putAiGrant, deleteAiGrant, setCachedAiGrant, clearCachedAiGrant } = services;
  const { retryTurn, finishTurn } = actions;
  let revision = 0;
  const timer = setInterval(() => { grantClock.value = Date.now(); }, 1000);
  function current(id, version) {
    return runtime.isActive() && id === conversationId.value && version === revision;
  }
  async function refreshGrant(id) {
    const version = ++revision;
    try {
      const value = await getAiGrant(id);
      if (current(id, version)) { grant.value = value; setCachedAiGrant(value); }
    } catch { if (current(id, version)) grant.value = { granted: false }; }
  }
  async function grantAuth({ access, lifetime }) {
    if (transferBusy.value || !capability.value?.provider || !conversationId.value) return;
    if (pendingGrantEntry.value?.authorizationRequest?.expiresAt <= Date.now()) {
      authError.value = '这次授权请求已过期，请重新发送问题。'; return;
    }
    const id = conversationId.value, version = ++revision;
    grantBusy.value = true; authError.value = '';
    try {
      const value = await putAiGrant({ providerHost: capability.value.provider, conversationId: id, access, lifetime });
      if (!current(id, version)) return;
      grant.value = value; setCachedAiGrant(value); authDialogOpen.value = false;
      if (access === 'operate') aiMode.value = 'operate';
      else if (aiMode.value === 'operate') aiMode.value = 'analysis';
      settingsNotice.value = '本会话已授权，有效期内可按需使用，不会重复询问。';
    } catch (error) {
      if (current(id, version)) authError.value = error?.message || '授权未保存，请稍后重试。';
      return;
    } finally { grantBusy.value = false; }
    if (!current(id, version)) return;
    const pending = pendingGrantEntry.value;
    pendingGrantEntry.value = null;
    if (pending?.turn) {
      await retryTurn(pending);
    }
  }
  async function revokeAuth() {
    if (transferBusy.value) return;
    const id = conversationId.value, version = ++revision;
    grantBusy.value = true; authError.value = '';
    try {
      const value = await deleteAiGrant(id);
      if (!current(id, version)) return;
      grant.value = value; clearCachedAiGrant(); authDialogOpen.value = false;
      settingsNotice.value = '已撤回本会话授权；下次需要使用能力时会重新询问。';
    } catch (error) { if (current(id, version)) authError.value = error?.message || '撤回未得到确认，请稍后重试。'; }
    finally { grantBusy.value = false; }
  }
  function openGrantDialog(entry) {
    authError.value = ''; pendingGrantEntry.value = entry?.turn ? entry : null;
    authDialogMode.value = !entry && authGranted.value ? 'edit' : 'create'; authDialogOpen.value = true;
  }
  function rejectAuthorization(entry) {
    authDialogOpen.value = false; pendingGrantEntry.value = null;
    if (!entry?.turn) return;
    entry.authorizationDenied = true; entry.turn.state = 'cancelled';
    entry.error = false; entry.pending = false;
    entry.text = '这次授权已拒绝，没有执行请求的能力。可以继续聊不需要读取数据的部分。';
    delete entry.lastError; finishTurn(entry);
  }
  function dispose() { revision++; clearInterval(timer); }
  return { refreshGrant, grantAuth, revokeAuth, openGrantDialog, rejectAuthorization, dispose };
}
