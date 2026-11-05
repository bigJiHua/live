import request from '@/utils/request';
import { validateAiCapabilities, validateAiResult, validateAiDraft, validateAiSettings } from '@/utils/aiContract';

export async function getAiCapabilities() {
  const response = await request.get('/ai/capabilities');
  return validateAiCapabilities(response.data);
}

export async function askAi({ message, scene = 'chat', consent, conversationId, connectionId }) {
  const response = await request.post('/ai/chat', { message, scene, consent, ...(conversationId ? { conversationId } : {}), ...(connectionId ? { connectionId } : {}) }, { timeout: 120000 });
  return validateAiResult(response.data);
}

export async function getAiDraft(id) {
  return validateAiDraft((await request.get(`/ai/drafts/${encodeURIComponent(id)}`)).data);
}
export async function editAiDraft(draft, fields) {
  return validateAiDraft((await request.put(`/ai/drafts/${encodeURIComponent(draft.id)}`, { version: draft.version, fields })).data);
}
export async function actOnAiDraft(draft, action) {
  return validateAiDraft((await request.post(`/ai/drafts/${encodeURIComponent(draft.id)}/action`, { version: draft.version, action })).data);
}

export async function getAiSettings() { return validateAiSettings((await request.get('/ai/settings')).data); }
export async function configureAi(settings) {
  return validateAiSettings((await request.put('/ai/settings', settings, { timeout: 30000 })).data);
}
export async function clearAiSettings() { return validateAiSettings((await request.delete('/ai/settings', { data: {} })).data); }
