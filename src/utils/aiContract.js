export function validateAiCapabilities(value) {
  if (!value || value.contractVersion !== 1 || value.formalWriteEnabled !== false ||
      !['disabled', 'mock', 'deepseek'].includes(value.mode) || typeof value.enabled !== 'boolean' ||
      typeof value.requiresConsent !== 'boolean' || value.chatEnabled !== true || value.draftEditingEnabled !== true) {
    throw new Error('AI 接口版本或安全能力不匹配，请更新对应测试分支');
  }
  if (value.ocrEnabled != null && (typeof value.ocrEnabled !== 'boolean' ||
      (value.ocrEnabled && (typeof value.ocrModel !== 'string' || !value.ocrModel.trim())))) {
    throw new Error('图片识别能力不符合协议');
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
  if (value.ocr != null) validateAiOcr(value.ocr);
  return value;
}

function isUuid(value) { return typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value); }
export function validateAiDraft(value) {
  const committed = value?.status === 'committed';
  if (!value || value.formalWriteEnabled !== false || value.needsReview !== !committed || !isUuid(value.id) ||
      !Number.isInteger(value.version) || value.version < 1 || !Number.isFinite(value.expiresAt) ||
      !['pending', 'reviewed', 'discarded', 'committed'].includes(value.status) || !Array.isArray(value.missingFields) ||
      !Array.isArray(value.choices?.accounts) || !Array.isArray(value.choices?.categories) ||
      (committed && (typeof value.committedRecordId !== 'string' || !value.committedRecordId.trim()))) {
    throw new Error('草稿安全标记不完整，本次结果已停止展示');
  }
  return value;
}

export function validateAiSettings(value) {
  if (!value || typeof value.enabled !== 'boolean' || typeof value.configured !== 'boolean' ||
      typeof value.apiUrl !== 'string' || !/^https:\/\/|^http:\/\/(localhost|127\.0\.0\.1)/.test(value.apiUrl) ||
      typeof value.model !== 'string' || value.model.length > 120 ||
      (value.configured ? !isUuid(value.connectionId) : value.connectionId !== null) ||
      (value.expiresAt !== null && !Number.isFinite(value.expiresAt)) || Object.hasOwn(value, 'apiKey')) {
    throw new Error('连接设置响应不符合协议');
  }
  return value;
}

// 追加163 扩展：历史记录 + 多供应商模型列表
export function validateAiConversations(value) {
  if (!Array.isArray(value) || value.some(item => !item || !isUuid(item.id) ||
      typeof item.title !== 'string' || typeof item.messageCount !== 'number')) {
    throw new Error('历史列表不符合协议');
  }
  return value;
}
export function validateAiMessages(value) {
  if (!Array.isArray(value) || value.some(item => !item ||
      !['user', 'assistant'].includes(item.role) || typeof item.content !== 'string' ||
      (item.draftId != null && !isUuid(item.draftId)) ||
      (item.trace != null && (!Array.isArray(item.trace) || item.trace.some(trace => !trace || trace.readOnly !== true))))) {
    throw new Error('历史消息不符合协议');
  }
  for (const item of value) if (item.ocr != null) validateAiOcr(item.ocr);
  return value;
}

export function validateAiOcr(value) {
  const nullableText = (text, length) => text === null || (typeof text === 'string' && text.length <= length);
  const validAmount = amount => amount === null || (typeof amount === 'string' &&
    /^(0|[1-9]\d{0,9})(\.\d{1,4})?$/.test(amount) && Number(amount) > 0);
  const validDate = date => {
    if (date === null) return true;
    if (typeof date !== 'string' || !/^20\d{2}-\d{2}-\d{2}$/.test(date)) return false;
    const parsed = new Date(`${date}T00:00:00Z`);
    return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === date;
  };
  if (!value || !['payment', 'statement', 'unknown'].includes(value.documentType) ||
      typeof value.truncated !== 'boolean' || !Array.isArray(value.items) || value.items.length > 20 ||
      !Array.isArray(value.warnings) || value.warnings.length > 10 ||
      value.warnings.some(warning => typeof warning !== 'string' || warning.length > 200) ||
      value.items.some(item => !item || ![0, 1, null].includes(item.direction) ||
        !validAmount(item.amount) || !validDate(item.transDate) ||
        !(item.currency === null || (typeof item.currency === 'string' && /^[A-Z]{3}$/.test(item.currency))) ||
        !['现金', '余额', '支付宝', '微信', '银行卡', null].includes(item.payMethod) ||
        !nullableText(item.merchant, 100) || !nullableText(item.accountHint, 80) ||
        !nullableText(item.categoryHint, 80) || !nullableText(item.remark, 255) ||
        !['high', 'medium', 'low'].includes(item.confidence) ||
        !['completed', 'pending', 'failed', 'refunded', 'unknown'].includes(item.transactionStatus) ||
        !['ordinary', 'transfer', 'repayment', 'refund', 'installment', 'investment', 'balance', 'unknown'].includes(item.transactionKind) ||
        !item.evidence || !['dateText', 'amountText', 'statusText', 'kindText'].every(field =>
          nullableText(item.evidence[field], 80)))) {
    throw new Error('图片识别结果不符合协议，本次结果已停止展示');
  }
  return value;
}
export function validateAiModels(value) {
  if (!value || !Array.isArray(value.models) ||
      value.models.some(id => typeof id !== 'string' || id.length > 120) || Object.hasOwn(value, 'apiKey')) {
    throw new Error('模型列表响应不符合协议');
  }
  return value;
}

// 追加163：DeepSeek 官方账户余额（非系统账本余额）
const isMoney = (value) => typeof value === 'string' && /^\d+(\.\d{1,2})?$/.test(value);
export function validateAiBalance(value) {
  if (!value || typeof value.isAvailable !== 'boolean' || !['CNY', 'USD'].includes(value.currency) ||
      !isMoney(value.totalBalance) || !isMoney(value.grantedBalance) || !isMoney(value.toppedUpBalance) ||
      Object.hasOwn(value, 'apiKey')) {
    throw new Error('余额响应不符合协议');
  }
  return value;
}

// 追加163 扩展：能力声明里的供应商主机名，用于界面文案；缺省或未知回退 DeepSeek（旧后端只支持官方）。
export function getAiProviderLabel(value) {
  if (!value?.enabled) return '未启用';
  if (value.mode === 'mock') return '模拟测试';
  const provider = typeof value.provider === 'string' ? value.provider.trim() : '';
  if (!provider || /^unknown$/i.test(provider)) return 'DeepSeek';
  return /(^|\.)deepseek\.com$/i.test(provider) ? 'DeepSeek' : provider;
}
