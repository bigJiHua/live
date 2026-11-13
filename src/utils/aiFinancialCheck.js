import { validateAiDraft } from './aiContract.js';

const isObject = value => value && typeof value === 'object' && !Array.isArray(value);
const isText = (value, limit = 240) => typeof value === 'string' && value.length <= limit;
const isMoney = value => value === null || (typeof value === 'string' && /^-?\d{1,14}(?:\.\d{1,4})?$/.test(value));
const isDigest = value => typeof value === 'string' && /^[a-f\d]{64}$/i.test(value);
const timeOf = value => typeof value === 'number' ? value : Date.parse(value);
const statuses = ['pass', 'warn', 'needs_input', 'block'];
const fields = ['direction', 'amount', 'currency', 'transDate', 'categoryId', 'accountId', 'payMethod', 'remark'];
const fail = () => { throw new Error('财务核验响应不完整，已停止继续操作，请重新核验。'); };

export function validateAiFinancialCheck(value) {
  if (!isObject(value) || !statuses.includes(value.status) ||
      !isText(value.policyVersion, 100) || !value.policyVersion ||
      !isDigest(value.fieldsDigest) || !isDigest(value.riskDigest) ||
      !Number.isFinite(timeOf(value.asOf)) ||
      (value.expiresAt != null && !Number.isFinite(timeOf(value.expiresAt))) ||
      !['requestedAmount', 'availableAmount', 'shortfall'].every(key => value[key] == null || isMoney(value[key])) ||
      (value.currency != null && !/^[A-Z]{3}$/.test(value.currency)) ||
      (value.direction != null && ![0,1].includes(value.direction)) ||
      (value.reasonCodes != null && (!Array.isArray(value.reasonCodes) || value.reasonCodes.length > 30 ||
        value.reasonCodes.some(code => !isText(code, 100) || !/^[A-Z0-9_]+$/.test(code)))) ||
      (value.reasons != null && (!Array.isArray(value.reasons) || value.reasons.length > 30 ||
        value.reasons.some(reason => typeof reason === 'string' ? !isText(reason, 500) :
          !isObject(reason) || (reason.message != null && !isText(reason.message, 500)) ||
          (reason.code != null && !isText(reason.code, 100))))) ||
      ['requiresAcknowledgement', 'requiresAmountConfirmation', 'reviewValid'].some(key =>
        value[key] != null && typeof value[key] !== 'boolean')) fail();
  return value;
}

export function getAiTransactionFields(value = {}) {
  const result = {};
  for (const key of fields) {
    const item = value[key];
    if (item == null) result[key] = null;
    else if (key === 'direction') {
      if (![0, 1].includes(item)) fail();
      result[key] = item;
    } else {
      if (!isText(item, key === 'remark' ? 255 : 160)) fail();
      result[key] = item;
    }
  }
  result.currency ??= 'CNY';
  result.remark ??= '';
  return result;
}

export function getAiMissingTransactionFields(value = {}, choices = {}) {
  const missing=[];
  if (![0,1].includes(value.direction)) missing.push('收支方向');
  const amount=canonicalAmount(value.amount);
  if (!amount || amount==='0' || (amount.split('.')[1]||'').length>2) missing.push('金额');
  const date=typeof value.transDate==='string'&&/^20\d{2}-\d{2}-\d{2}$/.test(value.transDate)?new Date(value.transDate+'T00:00:00Z'):null;
  if (!date || Number.isNaN(date.getTime()) || date.toISOString().slice(0,10)!==value.transDate) missing.push('日期');
  if (!(choices.accounts||[]).some(choice=>choice.id===value.accountId && (value.direction!==1||choice.type!=='credit'))) missing.push('账户');
  if (!(choices.categories||[]).some(choice=>choice.id===value.categoryId&&choice.type===(value.direction===0?'expense':'income'))) missing.push('分类');
  if (!['现金','余额','支付宝','微信','银行卡'].includes(value.payMethod)) missing.push('支付方式');
  return missing;
}

export function validateAiFinancialEnvelope(value, required = false) {
  if (!isObject(value)) fail();
  if (value.financialCheck != null) validateAiFinancialCheck(value.financialCheck);
  else if (required) fail();
  if (value.inputCandidate != null) {
    if (!isObject(value.inputCandidate) || !isObject(value.inputCandidate.fields) ||
        (value.inputCandidate.sourceCandidateId != null && !isText(value.inputCandidate.sourceCandidateId, 200))) fail();
    getAiTransactionFields(value.inputCandidate.fields);
  }
  if (value.draft?.financialCheck != null) validateAiFinancialCheck(value.draft.financialCheck);
  return value;
}

export function validateAiTransactionPreflight(value) {
  validateAiFinancialEnvelope(value, true);
  if (value.contractVersion !== 2 || !isObject(value.fields) || !isObject(value.choices) ||
      !['accounts', 'categories'].every(key => Array.isArray(value.choices[key]) && value.choices[key].length <= 100)) fail();
  getAiTransactionFields(value.fields);
  for (const key of ['accounts', 'categories']) for (const choice of value.choices[key]) {
    if (!isObject(choice) || !isText(choice.id, 160) || !choice.id || !isText(choice.name, 200)) fail();
  }
  return value;
}
export function validateAiLocalDraftResult(value, requestId) {
  validateAiFinancialEnvelope(value, true);
  if (value.contractVersion !== 2 || value.answer !== '' || !Array.isArray(value.datasets) || value.datasets.length ||
      (value.chart != null) || !value.request || value.request.providerCalls !== 0 || value.request.maxProviderCalls !== 0 ||
      (requestId && value.request.requestId !== requestId) || !value.draft) fail();
  validateAiDraft(value.draft);
  return value;
}
export function validateAiLocalDraftReceipt(value, requestId) {
  if (!isObject(value) || !['running', 'processing', 'pending', 'completed', 'succeeded', 'failed', 'unknown'].includes(value.status)) fail();
  if (value.result) validateAiLocalDraftResult(value.result, requestId);
  return value;
}

export function getAiFinancialGate(check, now = Date.now(), context = {}) {
  try { validateAiFinancialCheck(check); }
  catch { return { ready: false, fresh: false, status: 'needs_input', requiresAcknowledgement: false, requiresAmountConfirmation: false }; }
  const expiresAt = check.expiresAt == null ? timeOf(check.asOf) + 30000 : timeOf(check.expiresAt);
  const fresh = now < expiresAt;
  const amount=canonicalAmount(check.requestedAmount),income=(check.direction??context.direction)===1;
  const trusted=check.coverage==='complete' && ['authoritative-application-ledger','authoritative-credit-core'].includes(check.source) &&
    check.currency==='CNY' && amount && amount!=='0' && (amount.split('.')[1]||'').length<=2 &&
    (typeof check.availableAmount==='string' || income && check.availableAmount===null);
  return { status: check.status, fresh, ready: Boolean(fresh && trusted && ['pass', 'warn'].includes(check.status)),
    requiresAcknowledgement: check.requiresAcknowledgement ?? check.status === 'warn',
    requiresAmountConfirmation: check.requiresAmountConfirmation === true };
}

function canonicalAmount(value) {
  if (typeof value !== 'string' || !/^\d{1,14}(?:\.\d{1,4})?$/.test(value.trim())) return null;
  const [integer, fraction = ''] = value.trim().split('.');
  const decimals = fraction.replace(/0+$/, '');
  return integer.replace(/^0+(?=\d)/, '') + (decimals ? '.' + decimals : '');
}

export function getAiRiskAcknowledgement(check, acknowledged = false, confirmedAmount = '', now = Date.now(), context = {}) {
  const gate = getAiFinancialGate(check, now,context);
  if (!gate.ready || (gate.requiresAcknowledgement && !acknowledged)) return null;
  if (gate.requiresAmountConfirmation &&
      (!canonicalAmount(confirmedAmount) || canonicalAmount(confirmedAmount) !== canonicalAmount(check.requestedAmount))) return null;
  return { fieldsDigest: check.fieldsDigest, policyVersion: check.policyVersion, riskDigest: check.riskDigest,
    ...(gate.requiresAmountConfirmation ? { confirmedAmount: confirmedAmount.trim() } : {}) };
}

const labels = {
  INSUFFICIENT_FUNDS: '账本可用余额不足，请核对金额或账户。',
  INSUFFICIENT_CREDIT: '可用信用额度不足，请在信用卡页面核查。',
  ACCOUNT_REQUIRED: '请选择本人的实际资金账户。',
  ACCOUNT_AMBIGUOUS: '有多个匹配账户，请明确选择。',
  AMOUNT_REQUIRED: '请填写明确的交易金额。',
  CATEGORY_REQUIRED: '请选择与收支方向一致的分类。',
  PAY_METHOD_REQUIRED: '请选择支付方式。',
  DATA_QUALITY_UNKNOWN: '账本数据不完整，暂时无法可靠核验。',
  DATA_QUALITY_INVALID: '相关账本数据存在异常，请先核查来源。',
  BALANCE_UNAVAILABLE: '暂时无法取得可信的账本余额。',
  LARGE_AMOUNT: '本笔金额较大，请核对金额与账户。',
  VERY_LARGE_AMOUNT: '本笔需要再次确认完整金额。',
  HIGH_BALANCE_USAGE: '本笔将使用账户大部分可用余额，请核对。',
  POSSIBLE_DUPLICATE: '发现相似流水，请确认是否为另一笔交易。',
  HISTORICAL_TRANSACTION: '这是一笔补记交易，显示的是当前账本余额影响。',
};

export function getAiFinancialReasons(check) {
  const messages = (check?.reasons || []).map(reason => typeof reason === 'string' ? reason :
    reason.message || labels[reason.code]).filter(Boolean);
  if (!messages.length) messages.push(...(check?.reasonCodes || []).map(code => labels[code] || '这项数据需要进一步核对。'));
  return [...new Set(messages)];
}

export function aiPreflightKey(options) {
  const sorted = value => {
    if (Array.isArray(value)) return value.map(sorted);
    if (!isObject(value)) return value;
    return Object.fromEntries(Object.keys(value).sort().map(key => [key, sorted(value[key])]));
  };
  return JSON.stringify(sorted(options));
}

// Explicit input changes schedule local checks. A response never schedules another request.
export function createAiPreflightController(send, { delay = 300, now = Date.now, onBusy = () => {} } = {}) {
  let revision = 0, timer = null, pending = null, running = false, disposed = false;
  let cache = null;
  const start = async () => {
    if (disposed || running || !pending?.ready) return;
    const task = pending; pending = null; running = true; onBusy(true);
    try {
      const result = validateAiTransactionPreflight(await send(task.options));
      if (disposed || task.revision !== revision) task.resolve(null);
      else {
        cache = { keys: [task.key, aiPreflightKey({ ...task.options,fields:result.fields })], result };
        task.resolve(result);
      }
    } catch (error) {
      if (disposed || task.revision !== revision) task.resolve(null);
      else task.reject(error);
    } finally {
      running = false; onBusy(false);
      if (pending?.ready && !disposed) void start();
    }
  };
  const cancel = () => {
    revision++; clearTimeout(timer); timer = null;
    pending?.resolve(null); pending = null;
  };
  return {
    schedule(options, { immediate = false } = {}) {
      if (disposed) return Promise.resolve(null);
      cancel();
      const key = aiPreflightKey(options);
      if (cache?.keys.includes(key) && getAiFinancialGate(cache.result.financialCheck, now()).fresh) return Promise.resolve(cache.result);
      return new Promise((resolve, reject) => {
        pending = { options: structuredClone(options), key, revision, resolve, reject, ready: immediate };
        if (immediate) void start();
        else timer = setTimeout(() => { timer = null; if (pending) { pending.ready = true; void start(); } }, delay);
      });
    },
    cancel,
    dispose() { cancel(); disposed = true; cache = null; },
  };
}

