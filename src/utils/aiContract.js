export function validateAiCapabilities(value) {
  if (!value || value.contractVersion !== 1 || value.formalWriteEnabled !== false ||
      !['disabled', 'mock', 'deepseek'].includes(value.mode) || typeof value.enabled !== 'boolean' ||
      typeof value.requiresConsent !== 'boolean') {
    throw new Error('AI 接口版本或安全能力不匹配，请更新对应测试分支');
  }
  return value;
}

export function validateAiResult(value) {
  if (!value || value.contractVersion !== 1 || value.formalWriteEnabled !== false ||
      !['mock', 'deepseek'].includes(value.mode) || typeof value.answer !== 'string' ||
      !Array.isArray(value.trace) || !value.trace.every(item => item.readOnly === true)) {
    throw new Error('AI 响应与测试协议不匹配，本次结果已停止展示');
  }
  if (value.draft && (value.draft.formalWriteEnabled !== false || value.draft.needsReview !== true ||
      typeof value.draft.id !== 'string' || !Array.isArray(value.draft.missingFields))) {
    throw new Error('草稿安全标记不完整，本次结果已停止展示');
  }
  return value;
}
