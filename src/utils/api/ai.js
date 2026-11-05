import request from '@/utils/request';
import { validateAiCapabilities, validateAiResult } from '@/utils/aiContract';

export async function getAiCapabilities() {
  const response = await request.get('/ai/capabilities');
  return validateAiCapabilities(response.data);
}

export async function askAi({ message, scene, consent }) {
  const response = await request.post('/ai/chat', { message, scene, consent }, { timeout: 120000 });
  return validateAiResult(response.data);
}
