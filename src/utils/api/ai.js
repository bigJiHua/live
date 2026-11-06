import request from '@/utils/request';
import { validateAiCapabilities, validateAiResult, validateAiDraft, validateAiSettings, validateAiBalance,
  validateAiConversations, validateAiMessages, validateAiModels, validateAiOcr } from '@/utils/aiContract';
import { validateAiImageDataUrl } from '@/utils/aiImage';

export async function getAiCapabilities() {
  const response = await request.get('/ai/capabilities');
  return validateAiCapabilities(response.data);
}

export async function askAi({ message, scene = 'chat', consent, conversationId, connectionId }) {
  const response = await request.post('/ai/chat', { message, scene, consent, ...(conversationId ? { conversationId } : {}), ...(connectionId ? { connectionId } : {}) }, { timeout: 120000 });
  return validateAiResult(response.data);
}

export async function recognizeAiImage({ image, message, consent, conversationId, connectionId }) {
  if (consent !== true) throw new Error('请先同意将图片发送至 AI 服务进行识别');
  const response = await request.post('/ai/ocr', { image: { dataUrl: validateAiImageDataUrl(image?.dataUrl) },
    ...(message ? { message } : {}), consent: true, ...(conversationId ? { conversationId } : {}),
    ...(connectionId ? { connectionId } : {}) }, { timeout: 120000 });
  const result = validateAiResult(response.data);
  validateAiOcr(result.ocr);
  return result;
}

export async function getAiDraft(id) {
  return validateAiDraft((await request.get(`/ai/drafts/${encodeURIComponent(id)}`)).data);
}
// 追加163：入账确认——只携带事务 ID+版本，入账内容以后端库存档草稿为准
export async function commitAiDraft(draft) {
  return validateAiDraft((await request.post(`/ai/drafts/${encodeURIComponent(draft.id)}/commit`, { version: draft.version }, { timeout: 60000 })).data);
}
export async function editAiDraft(draft, fields) {
  return validateAiDraft((await request.put(`/ai/drafts/${encodeURIComponent(draft.id)}`, { version: draft.version, fields })).data);
}
export async function actOnAiDraft(draft, action) {
  return validateAiDraft((await request.post(`/ai/drafts/${encodeURIComponent(draft.id)}/action`, { version: draft.version, action })).data);
}

export async function getAiSettings() { return validateAiSettings((await request.get('/ai/settings')).data); }
// 追加163：DeepSeek 官方账户余额（非系统账本余额）
export async function getAiBalance() {
  return validateAiBalance((await request.get('/ai/balance', { timeout: 15000 })).data);
}
// 追加163 扩展：历史聊天记录
export async function listAiConversations() {
  return validateAiConversations((await request.get('/ai/conversations')).data);
}
export async function getAiConversationMessages(id) {
  return validateAiMessages((await request.get(`/ai/conversations/${encodeURIComponent(id)}/messages`)).data);
}
export async function deleteAiConversation(id) {
  return (await request.delete(`/ai/conversations/${encodeURIComponent(id)}`, { data: {} })).data;
}
// 多供应商：拉取厂商模型列表（Key 优先级 = 传入 > 会话级 > .env）
export async function fetchAiModels({ apiUrl, apiKey } = {}) {
  return validateAiModels((await request.post('/ai/models',
    { ...(apiUrl ? { apiUrl } : {}), ...(apiKey ? { apiKey } : {}) }, { timeout: 15000 })).data);
}
export async function configureAi(settings) {
  return validateAiSettings((await request.put('/ai/settings', settings, { timeout: 30000 })).data);
}
export async function clearAiSettings() { return validateAiSettings((await request.delete('/ai/settings', { data: {} })).data); }
