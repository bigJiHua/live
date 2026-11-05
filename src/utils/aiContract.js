export function validateAiCapabilities(value) {
  if (!value || value.contractVersion !== 1 || value.formalWriteEnabled !== false ||
      !['disabled', 'mock', 'deepseek'].includes(value.mode) || typeof value.enabled !== 'boolean' ||
      typeof value.requiresConsent !== 'boolean' || value.chatEnabled !== true || value.draftEditingEnabled !== true) {
    throw new Error('AI 接口版本或安全能力不匹配，请更新对应测试分支');
  }
  return value;
}

export function validateAiResult(value) {
  if (!value || value.contractVersion !== 1 || value.formalWriteEnabled !== false ||
      !['mock', 'deepseek'].includes(value.mode) || typeof value.answer !== 'string' ||
      !Array.isArray(value.trace) || !value.trace.every(item => item.readOnly === true) ||
      !isUuid(value.conversationId)) {
    throw new Error('AI 响应与测试协议不匹配，本次结果已停止展示');
  }
  if (value.draft) validateAiDraft(value.draft);
  return value;
}

function isUuid(value) { return typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value); }
export function validateAiDraft(value) {
  if (!value || value.formalWriteEnabled !== false || value.needsReview !== true || !isUuid(value.id) ||
      !Number.isInteger(value.version) || value.version < 1 || !Number.isFinite(value.expiresAt) ||
      !['pending', 'reviewed', 'discarded'].includes(value.status) || !Array.isArray(value.missingFields) ||
      !Array.isArray(value.choices?.accounts) || !Array.isArray(value.choices?.categories)) {
    throw new Error('草稿安全标记不完整，本次结果已停止展示');
  }
  return value;
}

export function validateAiSettings(value) {
  if (!value || typeof value.enabled !== 'boolean' || typeof value.configured !== 'boolean' ||
      value.apiUrl !== 'https://api.deepseek.com' || typeof value.model !== 'string' ||
      !Array.isArray(value.models) || !value.models.includes(value.model) ||
      (value.configured ? !isUuid(value.connectionId) : value.connectionId !== null) ||
      (value.expiresAt !== null && !Number.isFinite(value.expiresAt)) || Object.hasOwn(value, 'apiKey')) {
    throw new Error('连接设置响应不符合协议');
  }
  return value;
}
