import request from '@/utils/request';
import { streamRequest, subscribeRequest } from '@/utils/request/streamRequest';
import { validateAiCapabilities, validateAiResult, validateAiDraft, validateAiSettings, validateAiBalance,
  validateAiConversations, validateAiModels, validateAiOcr } from '@/utils/aiContract';
import { validateAiImageDataUrl } from '@/utils/aiImage';
import { validateBudgetResponse } from '@/utils/aiBudgetSettings';
import { validateAiAgentResult, validateAiProposal, validateAiAgentSession, validateAiReceipt, validateAiBusinessMessages, validateAiConversationState, validateAiGrant, validateAiPendingTask } from '@/utils/aiBusinessContract';
import { validateAiFinancialEnvelope, validateAiFinancialCheck, validateAiTransactionPreflight, validateAiLocalDraftResult, validateAiLocalDraftReceipt } from '@/utils/aiFinancialCheck';

const noReplay = { skipAutomaticRetry: true };
const presentation = { allowUnavailableChart: true };
// 回执读取是只读恢复：失败由消息内联提示，不再弹全局 toast；超时交给页面转自动轮询。
const receiptRead = { ...noReplay, suppressToast: true, timeout: 12000 };
function checkedDraft(value) {
  const draft = validateAiDraft(value);
  if (draft.financialCheck != null) validateAiFinancialCheck(draft.financialCheck);
  return draft;
}

export async function getAiCapabilities() {
  const response = await request.get('/ai/capabilities');
  return validateAiCapabilities(response.data);
}

export async function recognizeAiImage({ image, message, consent, conversationId, connectionId, requestId, signal }) {
  if (consent !== true) throw new Error('请先同意将图片发送至 AI 服务进行识别');
  const response = await request.post('/ai/v3/ocr', { image: { dataUrl: validateAiImageDataUrl(image?.dataUrl) },
    ...(message ? { message } : {}), consent: true, ...(requestId ? { requestId } : {}), ...(conversationId ? { conversationId } : {}),
    ...(connectionId ? { connectionId } : {}) }, { ...noReplay, timeout: 120000, ...(signal ? { signal } : {}) });
  const result = validateAiFinancialEnvelope(validateAiResult(response.data));
  validateAiOcr(result.ocr);
  return result;
}

export async function getAiDraft(id) {
  return checkedDraft((await request.get(`/ai/v3/drafts/${encodeURIComponent(id)}`)).data);
}
// 追加163：入账确认——只携带事务 ID+版本，入账内容以后端库存档草稿为准
export async function commitAiDraft(draft) {
  return checkedDraft((await request.post(`/ai/v3/drafts/${encodeURIComponent(draft.id)}/commit`, { version: draft.version }, { ...noReplay, timeout: 60000 })).data);
}
export async function editAiDraft(draft, fields) {
  return checkedDraft((await request.put(`/ai/v3/drafts/${encodeURIComponent(draft.id)}`, { version: draft.version, fields }, noReplay)).data);
}
export async function actOnAiDraft(draft, action, riskAcknowledgement) {
  return checkedDraft((await request.post(`/ai/v3/drafts/${encodeURIComponent(draft.id)}/action`,
    { version: draft.version, action, ...(riskAcknowledgement ? { riskAcknowledgement } : {}) }, noReplay)).data);
}

export async function askAiBusiness({ requestId, message, conversationId, consent, signal, mode, retryOf }) {
  if (consent !== true) throw new Error('请先授权将对话发送至 AI 服务');
  const result = validateAiFinancialEnvelope(validateAiAgentResult((await request.post('/ai/v3/chat',
    { requestId, message, ...(conversationId ? { conversationId } : {}), ...(mode ? { mode } : {}), ...(retryOf ? { retryOf } : {}), consent: true },
    { ...noReplay, timeout: 120000, ...(signal ? { signal } : {}) })).data, presentation));
  if (result.request.requestId !== requestId) throw Object.assign(new Error('本次 AI 响应与请求回执不一致'), { kind: 'protocol', code: 'AI_RESPONSE_INVALID' });
  return result;
}
export async function getAiBusinessReceipt(requestId) {
  const receipt = validateAiReceipt((await request.get(`/ai/v3/requests/${encodeURIComponent(requestId)}`, receiptRead)).data, presentation);
  if ([2, 3].includes(receipt.result?.contractVersion) && receipt.result.request.requestId !== requestId) throw Object.assign(new Error('请求回执编号不一致'), { kind: 'protocol', code: 'AI_RESPONSE_INVALID' });
  if (receipt.result) validateAiFinancialEnvelope(receipt.result);
  return receipt;
}
export async function getAiBusinessSession() {
  return validateAiAgentSession((await request.get('/ai/v3/session', { ...noReplay, params: { kind: 'default' } })).data, presentation);
}
export async function preflightAiTransaction(options) {
  return validateAiTransactionPreflight((await request.post('/ai/v3/transaction/preflight', options,
    { ...noReplay, timeout: 15000 })).data);
}
export async function createAiTransactionDraft(options) {
  const result = validateAiLocalDraftResult((await request.post('/ai/v3/transaction/draft', options,
    { ...noReplay, timeout: 30000 })).data, options.requestId);
  return checkedDraft(result.draft);
}
export async function getAiLocalDraftReceipt(requestId) {
  return validateAiLocalDraftReceipt((await request.get(`/ai/v3/requests/${encodeURIComponent(requestId)}`, receiptRead)).data, requestId);
}
export async function cancelAiTransactionCandidate(id) {
  const result = (await request.delete(`/ai/v3/transaction/candidates/${encodeURIComponent(id)}`,
    { ...noReplay, data: {} })).data;
  if (!result || result.cancelled !== true || result.sourceCandidateId !== id) throw new Error('候选取消未得到确认，请保留卡片重新核查。');
  return result;
}
export async function getAiTransactionCandidate(id) {
  const result = validateAiFinancialEnvelope((await request.get(`/ai/v3/transaction/candidates/${encodeURIComponent(id)}`, noReplay)).data);
  if ((result.inputCandidate?.sourceCandidateId || result.inputCandidate?.id) !== id) throw new Error('候选回执编号不一致，请重新核查。');
  if (result.draft) result.draft = checkedDraft(result.draft);
  return result;
}
export async function getAiPendingTransaction(conversationId) {
  const result = (await request.get('/ai/v3/transaction/pending', { ...receiptRead, params: { conversationId } })).data;
  if (!result || result.contractVersion !== 2 || !Object.hasOwn(result, 'pendingTask')) throw new Error('待补记账状态不完整，请稍后重新读取。');
  if (result.pendingTask != null) validateAiPendingTask(result.pendingTask);
  return result;
}
export async function resumeAiPendingTransaction(options) {
  const result = validateAiFinancialEnvelope((await request.post('/ai/v3/transaction/pending/resume', options,
    { ...noReplay, suppressToast: true, timeout: 30000 })).data, true);
  if (result.contractVersion !== 2) throw new Error('待补记账恢复响应不符合协议。');
  validateAiPendingTask(result.pendingTask);
  if (result.pendingTask.taskId !== options.taskId || result.pendingTask.revision !== options.revision ||
      !result.pendingTask.items.some(item => item.index === options.itemIndex)) throw new Error('待补记账恢复与原任务不一致，请重新读取。');
  if (result.draft) {
    result.draft = checkedDraft(result.draft);
    if (result.draft.conversationId !== options.conversationId) throw new Error('原草稿不属于本次聊天，请重新读取。');
  } else {
    if (result.inputCandidate?.pendingTaskId !== options.taskId || result.inputCandidate?.pendingTaskIndex !== options.itemIndex ||
        result.inputCandidate?.pendingTaskRevision !== options.revision) throw new Error('待补候选与原任务不一致，请重新读取。');
    validateAiTransactionPreflight(result);
  }
  return result;
}
export async function createAiBusinessSession() {
  return validateAiAgentSession((await request.post('/ai/v3/session', { kind: 'manual' }, noReplay)).data, presentation);
}
// ===== D-027：数据授权白名单（跟账号走，服务端强约束）=====
export async function getAiGrant(conversationId) {
  return validateAiGrant((await request.get('/ai/grant', { ...noReplay, params: conversationId ? { conversationId } : {} })).data);
}
export async function putAiGrant({ providerHost, domains, conversationId, lifetime, access }) {
  return validateAiGrant((await request.put('/ai/grant', { providerHost, ...(conversationId ? { conversationId, lifetime, access } : { domains }) }, { ...noReplay, timeout: 15000 })).data);
}
export async function deleteAiGrant(conversationId) {
  return validateAiGrant((await request.delete('/ai/grant', { ...noReplay, params: conversationId ? { conversationId } : {}, data: {} })).data);
}
// ===== 追加164：思考开关（用户偏好）=====
export async function getAiPreferences() {
  const value = (await request.get('/ai/preferences', noReplay)).data;
  if (!value || value.contractVersion !== 1 || typeof value.thinkingEnabled !== 'boolean' ||
      typeof value.thinkingSupported !== 'boolean') throw new Error('偏好响应不符合协议');
  return value;
}
export async function getAiBudgetSettings() {
  return validateBudgetResponse((await request.get('/ai/budget-settings', { ...noReplay, suppressToast: true })).data);
}
export async function putAiBudgetSettings(values) {
  return validateBudgetResponse((await request.put('/ai/budget-settings', values,
    { ...noReplay, suppressToast: true, timeout: 15000 })).data);
}
export async function putAiPreference(thinking, webSearch) {
  const payload = webSearch === undefined ? { thinking } : { thinking, webSearch };
  const value = (await request.put('/ai/preferences', payload, { ...noReplay, timeout: 15000 })).data;
  if (!value || value.contractVersion !== 1 || typeof value.thinkingEnabled !== 'boolean') throw new Error('偏好响应不符合协议');
  return value;
}
// ===== 追加164：全链路审计日志（本人 scoped）=====
export async function getAiLogs(limit = 100) {
  const value = (await request.get(`/ai/logs?limit=${Number(limit) || 100}`, noReplay)).data;
  if (!Array.isArray(value)) throw new Error('日志响应不符合协议');
  for (const row of value) {
    if (!row || typeof row.id !== 'string' || typeof row.lane !== 'string' ||
        !['provider', 'tool', 'gate', 'preview', 'meta', 'commit', 'run', 'claim',
          'tool-recovery', 'query-evidence-recovery', 'language-recovery', 'grounded-done',
          'admission-wait', 'audit-write', 'settings-warmup', 'conversation-activity-prune',
          'numeric-recovery', 'numeric-tool-limit', 'refresh', 'v3-http'].includes(row.phase) ||
        !['ok', 'error'].includes(row.status)) throw new Error('日志行不符合协议');
  }
  return value;
}
// One user send starts one bounded agent run. Only its validated receipt renders a final card.
export async function streamAiBusiness({ requestId, message, conversationId, consent = true, onEvent, signal, mode, retryOf, turnTimeoutMs, queueWaitMs }) {
  if (consent !== true) throw new Error('请先授权将对话发送至 AI 服务');
  let sawDone = false;
  // Allow the backend's bounded run to finish and deliver its final receipt.
  const waitMs = Number.isInteger(queueWaitMs) && queueWaitMs >= 0 && queueWaitMs <= 60000 ? queueWaitMs : 0;
  const timeoutMs = Number.isInteger(turnTimeoutMs) && turnTimeoutMs >= 30000 && turnTimeoutMs <= 300000
    ? turnTimeoutMs + waitMs + 10000 : undefined;
  await streamRequest({ url: '/ai/v3/chat/stream', data: { requestId, message, ...(conversationId ? { conversationId } : {}), ...(mode ? { mode } : {}), ...(retryOf ? { retryOf } : {}), consent: true }, signal,
    timeoutMs, onEvent: ({ event, data }) => {
      if (event === 'done') {
        const result = validateAiFinancialEnvelope(validateAiAgentResult(data.data, presentation));
        if (result.request.requestId !== requestId) throw Object.assign(new Error('本次 AI 响应与请求回执不一致'), { kind: 'protocol', code: 'AI_RESPONSE_INVALID' });
        sawDone = true;
        onEvent?.({ event, data: { status: 200, data: result } });
        return;
      }
      onEvent?.({ event, data });
    } });
  if (!sawDone) throw new Error('流式回执不完整，请读取状态');
}
export async function getAiProposal(id, options = {}) {
  return validateAiProposal((await request.get(`/ai/v3/proposals/${encodeURIComponent(id)}`, { ...noReplay, ...options })).data);
}
export async function editAiProposal(proposal, fields) {
  return validateAiProposal((await request.put(`/ai/v3/proposals/${encodeURIComponent(proposal.id)}`, { version: proposal.version, fields }, noReplay)).data);
}
export async function actOnAiProposal(proposal, action) {
  return validateAiProposal((await request.post(`/ai/v3/proposals/${encodeURIComponent(proposal.id)}/action`, { version: proposal.version, action }, noReplay)).data);
}
export async function executeAiProposal(proposal) {
  return validateAiProposal((await request.post(`/ai/v3/proposals/${encodeURIComponent(proposal.id)}/execute`, { version: proposal.version }, { ...noReplay, timeout: 60000 })).data);
}

export async function getAiSettings() { return validateAiSettings((await request.get('/ai/settings')).data); }
// 追加163：供应商官方账户余额（非系统账本余额）；带 connectionId 查指定接入商（查看哪家查哪家）
export async function getAiBalance(connectionId) {
  return validateAiBalance((await request.get('/ai/balance',
    { ...noReplay, timeout: 15000, ...(connectionId ? { params: { connectionId } } : {}) })).data);
}
// 追加163 扩展：历史聊天记录
export async function listAiConversations() {
  return validateAiConversations((await request.get('/ai/conversations')).data);
}
export async function getAiConversationMessages(id, options = {}) {
  return validateAiBusinessMessages((await request.get(`/ai/conversations/${encodeURIComponent(id)}/messages`, options)).data, presentation);
}
export async function deleteAiConversation(id) {
  return (await request.delete(`/ai/conversations/${encodeURIComponent(id)}`, { data: {} })).data;
}
export async function getAiConversationSync(id, since, options = {}) {
  return validateAiConversationState((await request.get(`/ai/conversations/${encodeURIComponent(id)}/sync`,
    { ...noReplay, suppressToast: true, timeout: 12000, ...options, params: { since } })).data, presentation);
}
export async function subscribeAiConversation(id, since, { signal, onEvent } = {}) {
  return subscribeRequest({ url: `/ai/conversations/${encodeURIComponent(id)}/events?since=${since}`, signal,
    onEvent: ({ event, data }) => onEvent?.({ event, data: event === 'sync' ? validateAiConversationState(data, presentation) : data }) });
}
// 多供应商：拉取厂商模型列表（Key 只取本次输入或已保存的接入商，不回退 .env）
export async function fetchAiModels({ connectionId, apiUrl, apiKey } = {}) {
  return validateAiModels((await request.post('/ai/models',
    { ...(connectionId ? { connectionId } : {}), ...(apiUrl ? { apiUrl } : {}), ...(apiKey ? { apiKey } : {}) },
    { ...noReplay, timeout: 15000 })).data);
}
export async function configureAi(settings) {
  return validateAiSettings((await request.put('/ai/settings', settings, { ...noReplay, timeout: 30000 })).data);
}
export async function activateAiProvider(connectionId) {
  return validateAiSettings((await request.post(`/ai/settings/${encodeURIComponent(connectionId)}/activate`, {}, noReplay)).data);
}
export async function deleteAiProvider(connectionId) {
  return validateAiSettings((await request.delete(`/ai/settings/${encodeURIComponent(connectionId)}`, { ...noReplay, data: {} })).data);
}
