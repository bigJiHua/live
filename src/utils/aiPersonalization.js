export const PROFILE_CATEGORIES = Object.freeze({
  background: '背景', likes: '喜欢', dislikes: '不喜欢', goals: '长期目标', communication: '沟通偏好', workflow: '协作习惯',
});
export const MEMORY_TYPES = Object.freeze({ preference: '偏好', profile: '背景', workflow: '工作约定', note: '笔记' });
const revision = value => typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
const record = value => value && typeof value === 'object' && !Array.isArray(value);
const text = (value, limit, empty = false) => typeof value === 'string' && value.length <= limit && (empty || value.trim().length > 0);
const fail = () => { throw Object.assign(new Error('个性化响应不完整，请重新读取。'), { code: 'AI_PERSONALIZATION_RESPONSE_INVALID' }); };

export function validatePersonalization(value) {
  if (!record(value) || value.contractVersion !== 1 || !revision(value.revision) || !record(value.settings) ||
    ['useMemory', 'useProfile', 'autoProfile'].some(key => typeof value.settings[key] !== 'boolean') ||
    !record(value.profile) || !Array.isArray(value.profile.items) || value.profile.items.length > 100 ||
    !Array.isArray(value.rules) || value.rules.length > 30 || !Array.isArray(value.disabledMemories)) fail();
  const ids = new Set();
  for (const item of value.profile.items) {
    if (!record(item) || !text(item.id, 100) || ids.has(item.id) || !Object.hasOwn(PROFILE_CATEGORIES, item.category) ||
      !text(item.text, 2000) || !['user', 'ai'].includes(item.origin) || typeof item.confirmed !== 'boolean' ||
      !Array.isArray(item.evidence) || item.evidence.some(source => !record(source) || !text(source.quote, 6000))) fail();
    ids.add(item.id);
  }
  ids.clear();
  for (const rule of value.rules) {
    if (!record(rule) || !text(rule.id, 100) || ids.has(rule.id) || !text(rule.text, 500) || typeof rule.enabled !== 'boolean') fail();
    ids.add(rule.id);
  }
  if (value.disabledMemories.some(path => !text(path, 80))) fail();
  return value;
}

export function validateMemory(value, { content = false } = {}) {
  if (!record(value) || !text(value.path, 80) || !value.path.endsWith('.md') || /[\\/:]/.test(value.path) ||
    !text(value.title, 80) || !Object.hasOwn(MEMORY_TYPES, value.type) || !revision(value.revision) ||
    !text(value.updated, 40) || content && !text(value.content, 65536, true)) fail();
  return value;
}
export function validateMemories(value) {
  if (!record(value) || !Array.isArray(value.files) || value.files.length > 100) fail();
  value.files.forEach(item => validateMemory(item));
  return value;
}
export function validateMemoryHistory(value) {
  const entries = Array.isArray(value) ? value : value?.entries || value?.history;
  if (!Array.isArray(entries) || entries.length > 100) fail();
  for (const entry of entries) if (!record(entry) || !text(entry.id || entry.historyId, 100)) fail();
  return entries;
}
function checkedMemoryIdentity(value, path) {
  validateMemory(value, { content: true });
  if (value.path !== path) fail();
  return value;
}
export const clonePersonalization = value => JSON.parse(JSON.stringify(value));
export function newPersonalizationId(prefix = 'item') {
  return `${prefix}_${globalThis.crypto?.randomUUID?.() || `${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}`}`;
}
export function isPersonalizationConflict(error) {
  return error?.status === 409 || error?.httpStatus === 409 || /CONFLICT|REVISION_REQUIRED/.test(error?.code || '');
}

// Explicit actions make exactly one request. All errors are handled by the page.
export function createPersonalizationApi(request) {
  const options = { skipAutomaticRetry: true, suppressToast: true, timeout: 15000 };
  const fileUrl = path => `/ai/memories/${encodeURIComponent(path)}`;
  return {
    getPersonalization: async () => validatePersonalization((await request.get('/ai/personalization', options)).data),
    updatePersonalization: async body => validatePersonalization((await request.put('/ai/personalization', body, options)).data),
    generateProfile: async body => validatePersonalization((await request.post('/ai/personalization/profile/generate', body,
      // Allow the configured provider deadline plus queueing and persistence.
      { ...options, timeout: 180000 })).data),
    listMemories: async () => validateMemories((await request.get('/ai/memories', options)).data),
    readMemory: async path => checkedMemoryIdentity((await request.get(fileUrl(path), options)).data, path),
    createMemory: async body => validateMemory((await request.post('/ai/memories', body, options)).data, { content: true }),
    updateMemory: async (path, body) => checkedMemoryIdentity((await request.put(fileUrl(path), body, options)).data, path),
    deleteMemory: async (path, body) => {
      const result = (await request.delete(fileUrl(path), { ...options, data: body })).data;
      if (!record(result) || result.deleted !== true || result.path !== path || result.revision !== body.expectedRevision) fail();
      return result;
    },
    memoryHistory: async path => validateMemoryHistory((await request.get('/ai/memories/history',
      { ...options, params: path ? { path } : {} })).data),
    restoreMemory: async (path, body) => checkedMemoryIdentity((await request.post(`${fileUrl(path)}/restore`, body, options)).data, path),
  };
}
