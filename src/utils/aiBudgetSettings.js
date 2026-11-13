export const BUDGET_FIELDS = [
  { key: 'dailyProviderCalls', label: '每日模型调用额度', unit: '次', scale: 1,
    description: '按北京时间每天重置，统计实际发起的模型调用。一条消息可能调用模型多次；提高后可进行更多对话，不会增加每轮最多 5 次调用的限制。' },
  { key: 'maxUsageTokens', label: '单轮累计 Token 上限', unit: 'Token', scale: 1,
    description: '按接入商返回的用量统计一次对话的累计用量，包含输入、输出及计入用量的思考内容。达到上限后停止下一次调用，单次调用可能超过剩余额度；接入商不返回用量时无法准确判断。提高后可能增加费用。Token 不等于字数。' },
  { key: 'providerTimeoutMs', label: '单次模型请求超时', unit: '秒', scale: 1000,
    description: '从发起请求到接收完整响应的最长时间，包含持续输出的流式响应。提高后允许较慢的模型完成响应；超过此时间请求会中止，不自动重试。' },
  { key: 'turnTimeoutMs', label: '整轮处理超时', unit: '秒', scale: 1000,
    description: '一次对话从处理开始到结束的总时限，包含模型与工具调用，排队等待另计。提高后给多步处理更多时间；整轮时限仍会提前终止尚未完成的单次请求。' },
];
export const BUDGET_KEYS = ['dailyProviderCalls', 'maxUsageTokens', 'maxOutputTokens', 'providerTimeoutMs', 'turnTimeoutMs'];
export function validateBudgetResponse(value) {
  if (!value || value.contractVersion !== 1 || BUDGET_KEYS.some(key => {
    const limit = value.limits?.[key];
    if (!limit || !Number.isInteger(limit.min) || !Number.isInteger(limit.max) || limit.min > limit.max) return true;
    return ['values', 'defaults', 'overrides'].some(group => {
      const field = value[group]?.[key];
      if (field === null) return group !== 'overrides' && key !== 'maxOutputTokens';
      return !(key === 'maxOutputTokens' && field === 'max')
        && (!Number.isInteger(field) || field < limit.min || field > limit.max);
    });
  })) throw new Error('额度与超时设置响应不完整，请重新读取。');
  return value;
}
export function budgetPayload(form, outputMode, outputTokens) {
  const payload = Object.fromEntries(BUDGET_FIELDS.map(field => [field.key,
    field.scale === 1000 ? Math.round(Number(form[field.key]) * field.scale) : Number(form[field.key])]));
  payload.maxOutputTokens = outputMode === 'default' ? null : outputMode === 'max' ? 'max' : Number(outputTokens);
  return payload;
}
