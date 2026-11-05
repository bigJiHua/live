import request from '@/utils/request';
import { validateAiCapabilities, validateAiResult, validateAiDraft } from '@/utils/aiContract';

export async function getAiCapabilities() {
  const response = await request.get('/ai/capabilities');
  return validateAiCapabilities(response.data);
}

export async function askAi({ message, scene = 'chat', consent, conversationId }) {
  const response = await request.post('/ai/chat', { message, scene, consent, ...(conversationId ? { conversationId } : {}) }, { timeout: 120000 });
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
