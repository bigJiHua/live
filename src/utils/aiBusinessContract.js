import { AI_DOMAINS } from './aiTurn.js';
import { validateAiResult as legacyResult, validateAiDraft as legacyDraft, validateAiMessages as legacyMessages } from './aiContract.js';
import { validateAiFinancialEnvelope, validateAiFinancialCheck, validateAiTransactionPreflight } from './aiFinancialCheck.js';

const uuid = value => typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
const text = (value, length = 2000) => typeof value === 'string' && value.length <= length;
const object = value => value && typeof value === 'object' && !Array.isArray(value);
const scalar = value => value === null || typeof value === 'boolean' || text(value) || (typeof value === 'number' && Number.isFinite(value));
const fail = message => { throw Object.assign(new Error(message || '业务助手响应不符合安全协议，已停止展示'),
  { code: 'AI_RESPONSE_INVALID', kind: 'protocol' }); };
const checkedLegacy = (validate, value) => {
  try { return validate(value); }
  catch (error) { throw Object.assign(error, { code: 'AI_RESPONSE_INVALID', kind: 'protocol' }); }
};
const validateAiResult = value => checkedLegacy(legacyResult, value);
const validateAiDraft = value => checkedLegacy(legacyDraft, value);
const validateAiMessages = value => checkedLegacy(legacyMessages, value);
const queryId = value => typeof value === 'string' && /^q_[a-f\d]{24}$/.test(value);
const count = value => value === null || Number.isSafeInteger(value) && value >= 0;
const calendarDate = value => typeof value === 'string' && /^20\d{2}-\d{2}-\d{2}$/.test(value) &&
  Number.isFinite(Date.parse(`${value}T00:00:00Z`)) && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value;
const queryScope = value => object(value) && Object.keys(value).every(key => ['startDate', 'endDate'].includes(key)) &&
  calendarDate(value.startDate) && calendarDate(value.endDate) && value.startDate <= value.endDate;
const queryCardTypes = ['credit', 'debit', 'virtual_cash', 'virtual_balance', 'virtual_wechat', 'virtual_alipay'];
const queryCursor = value => text(value, 2048) && /^lq1\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]{43}$/.test(value);

function validateQueryPage(page, meta) {
  if (!object(page) || Object.keys(page).length !== 4 || Object.keys(page).some(key => !['offset', 'limit', 'hasMore', 'nextCursor'].includes(key)) ||
      !Number.isSafeInteger(page.offset) || page.offset < 0 || page.offset > 100000 ||
      !Number.isSafeInteger(page.limit) || page.limit < 1 || page.limit > 50 || typeof page.hasMore !== 'boolean' ||
      !(page.nextCursor === null || queryCursor(page.nextCursor)) || page.hasMore !== (page.nextCursor !== null) ||
      meta.returnedRowCount > page.limit || meta.rowsComplete && page.offset !== 0 ||
      page.hasMore && meta.returnedRowCount === 0 ||
      meta.matchedRowCount === null && page.hasMore ||
      meta.matchedRowCount !== null && (page.offset + meta.returnedRowCount > meta.matchedRowCount ||
        page.hasMore !== (page.offset + meta.returnedRowCount < meta.matchedRowCount))) fail('查询分页或覆盖数量不符合协议');
}

export function validateAiQueryMeta(value, dataset) {
  const fields = ['version', 'queryId', 'datasetId', 'requestedScope', 'effectiveScope', 'filters', 'sort',
    'matchedRowCount', 'returnedRowCount', 'sourceRecordCount', 'rowsComplete', 'summaryComplete', 'rankingComplete',
    'returnedDateRange', 'asOf', 'groupBy', 'resolution', 'page'];
  if (!object(value) || Object.keys(value).some(key => !fields.includes(key)) || value.version !== 1 || !queryId(value.queryId) ||
      value.datasetId !== dataset.datasetId || !queryScope(value.requestedScope) || !queryScope(value.effectiveScope) ||
      !object(value.filters) || Object.keys(value.filters).some(key => !['direction', 'text', 'cardTypes'].includes(key)) ||
      !['income', 'expense', 'both'].includes(value.filters.direction) || !text(value.filters.text, 80) ||
      !['dateDesc', 'amountDesc', 'expenseDesc', 'incomeDesc', 'monthAsc', 'categoryAmountDesc'].includes(value.sort) ||
      !count(value.matchedRowCount) || !count(value.sourceRecordCount) || !Number.isSafeInteger(value.returnedRowCount) ||
      value.returnedRowCount !== dataset.rows.length || typeof value.rowsComplete !== 'boolean' ||
      typeof value.summaryComplete !== 'boolean' || typeof value.rankingComplete !== 'boolean' || !text(value.asOf, 80) ||
      !Number.isFinite(Date.parse(value.asOf)) ||
      value.matchedRowCount !== null && value.matchedRowCount < value.returnedRowCount ||
      value.rowsComplete && (value.matchedRowCount === null || value.matchedRowCount !== value.returnedRowCount) ||
      value.returnedDateRange !== null && (!queryScope(value.returnedDateRange) ||
        value.returnedDateRange.startDate < value.effectiveScope.startDate ||
        value.returnedDateRange.endDate > value.effectiveScope.endDate)) fail('查询范围或覆盖数量不符合协议');
  if (Object.hasOwn(value, 'groupBy') && (value.datasetId !== 'card-monthlyConsumption' ||
      !['card', 'cardMonth'].includes(value.groupBy))) fail('查询范围或覆盖数量不符合协议');
  if (Object.hasOwn(value, 'resolution') && !['detail', 'month', 'year'].includes(value.resolution)) fail('查询粒度不符合协议');
  if (Object.hasOwn(value, 'page')) validateQueryPage(value.page, value);
  if (Object.hasOwn(value.filters, 'cardTypes') && (!['card-counts', 'card-monthlyConsumption'].includes(value.datasetId) ||
      !Array.isArray(value.filters.cardTypes) || !value.filters.cardTypes.length ||
      value.filters.cardTypes.some(type => !queryCardTypes.includes(type)) ||
      new Set(value.filters.cardTypes).size !== value.filters.cardTypes.length)) fail('查询范围或覆盖数量不符合协议');
  return value;
}

export const AI_PROPOSAL_LABELS = {
  'budget.create': '预算草稿', 'todo.create': '新增待办', 'todo.update': '修改待办',
  'todo.complete': '完成待办', 'todo.delete': '删除待办', 'diary.create': '工作日记',
  'card.metadata': '信用卡资料', 'fixedAsset.create': '新增固定资产',
  'fixedAsset.update': '修改固定资产', 'fixedAsset.delete': '删除固定资产',
  'fixedAsset.restore': '恢复固定资产', 'fixedAsset.permanentDelete': '彻底删除固定资产',
  'fund.returnBatch': '基金收益登记', 'system.backup': '数据备份',
  'transaction.create': '记账草稿',
};

const OPERATION_NOTICES = Object.freeze({
  none: '本轮未提交正式业务操作。',
  needs_input: '本轮尚有信息需要补充，未提交正式业务操作。',
  awaiting_confirmation: '本轮已准备待核对草稿，仍需你确认提交。',
});

function operationArtifacts(source, history) {
  const entries = new Map();
  const add = (type, id, value) => {
    if (id == null) {
      if (value) fail('操作成果缺少存档标识');
      return;
    }
    if (!uuid(id)) fail('操作成果存档标识不符合协议');
    const key = `${type}:${id}`, previous = entries.get(key);
    entries.set(key, { type, id, kind: type === 'proposal' ? value?.kind || previous?.kind : 'transaction.create',
      value: value || previous?.value });
  };
  for (const item of [source, source?.business].filter(object)) {
    for (const key of ['drafts', 'candidates', ...(history ? ['draftIds', 'candidateIds'] : [])])
      if (item[key] != null && (!Array.isArray(item[key]) || item[key].length > 3)) fail('操作成果卡片集合不符合协议');
    add('draft', item.draft?.id, item.draft);
    for (const draft of item.drafts || []) {
      if (!object(draft)) fail('操作成果草稿不符合协议');
      add('draft', draft.id, draft);
    }
    add('proposal', item.proposal?.id, item.proposal);
    add('candidate', item.inputCandidate?.sourceCandidateId || item.inputCandidate?.id, item.inputCandidate);
    for (const candidate of item.candidates || []) {
      if (!object(candidate?.inputCandidate)) fail('操作成果候选不符合协议');
      add('candidate', candidate.inputCandidate.sourceCandidateId || candidate.inputCandidate.id, candidate.inputCandidate);
    }
    if (history) {
      add('draft', item.draftId);
      add('proposal', item.proposalId);
      for (const id of item.draftIds || []) add('draft', id);
      for (const id of item.candidateIds || []) add('candidate', id);
    }
  }
  return entries;
}

// Outcome describes the original server-created artifacts, independently of
// model prose and of a later review/commit. IDs must belong to this result or
// its persisted message; restoring current cards must not rewrite the snapshot.
export function validateAiOperationOutcome(value, source, { history = false } = {}) {
  if (!object(value) || Object.keys(value).some(key => !['version', 'state', 'formalWriteExecuted', 'artifacts', 'notice'].includes(key)) ||
      value.version !== 1 || !Object.hasOwn(OPERATION_NOTICES, value.state) || value.formalWriteExecuted !== false ||
      !Array.isArray(value.artifacts) || value.artifacts.length > 3 || !text(value.notice, 200) ||
      value.notice !== OPERATION_NOTICES[value.state]) fail('操作成果不符合安全协议');
  const expected = source ? operationArtifacts(source, history) : null, ids = new Set();
  for (const entry of value.artifacts) {
    if (!object(entry) || Object.keys(entry).some(key => !['type', 'id', 'kind', 'status', 'readyForReview'].includes(key)) ||
        !['draft', 'proposal', 'candidate'].includes(entry.type) || !uuid(entry.id) || ids.has(entry.id) ||
        typeof entry.readyForReview !== 'boolean' ||
        !(entry.type === 'candidate' ? entry.status === 'pending' && entry.readyForReview === false : ['pending', 'reviewed'].includes(entry.status)) ||
        !(entry.type === 'proposal' ? Object.hasOwn(AI_PROPOSAL_LABELS, entry.kind) && entry.kind !== 'transaction.create'
          : entry.kind === 'transaction.create')) fail('操作成果条目不符合安全协议');
    ids.add(entry.id);
    if (expected) {
      const match = expected.get(`${entry.type}:${entry.id}`);
      if (!match || match.kind && match.kind !== entry.kind || match.value?.status != null && match.value.status !== entry.status)
        fail('操作成果必须绑定本轮存档卡片');
      if (match.value && entry.type !== 'candidate') {
        const ready = ['pending', 'reviewed'].includes(match.value.status) && match.value.actionUnavailable !== true &&
          (match.value.missingFields == null || Array.isArray(match.value.missingFields) && match.value.missingFields.length === 0);
        if (entry.readyForReview !== ready) fail('操作成果核对状态与卡片不一致');
      }
    }
  }
  const state = !value.artifacts.length ? 'none' : value.artifacts.some(entry => !entry.readyForReview) ? 'needs_input' : 'awaiting_confirmation';
  if (value.state !== state || expected && expected.size !== value.artifacts.length) fail('操作成果与本轮卡片数量不一致');
  return value;
}

export function validateAiDatasets(datasets) {
  if (!Array.isArray(datasets) || datasets.length > 40 || JSON.stringify(datasets).length > 65536) fail();
  const ids = new Set();
  for (const data of datasets) {
    if (!object(data) || !text(data.datasetId, 120) || !data.datasetId ||
        !AI_DOMAINS.includes(data.domain) || !text(data.title, 160) || !Array.isArray(data.columns) ||
        data.columns.length > 30 || !Array.isArray(data.rows) || data.rows.length > 500 ||
        !object(data.summary) || !Object.values(data.summary).every(scalar) || !text(data.policy, 2000) ||
        !text(data.asOf, 80) || typeof data.isEstimated !== 'boolean' || typeof data.truncated !== 'boolean') fail();
    if (data.queryMeta != null) validateAiQueryMeta(data.queryMeta, data);
    const identity = data.queryMeta?.queryId || data.datasetId;
    if (ids.has(identity)) fail('查询结果标识重复');
    ids.add(identity);
    const keys = new Set();
    for (const column of data.columns) {
      if (!object(column) || !text(column.key, 80) || !/^[a-zA-Z][\w]*$/.test(column.key) ||
          keys.has(column.key) || !text(column.label, 100) ||
          !['money', 'decimal', 'number', 'date', 'text'].includes(column.type)) fail();
      keys.add(column.key);
    }
    for (const row of data.rows) {
      const references = ['ref', 'cardRef', 'categoryRef', 'fundRef', 'jobRef', 'poolRef'];
      if (!object(row) || Object.keys(row).some(key => !keys.has(key) && !references.includes(key)) || !Object.values(row).every(scalar)) fail();
      for (const key of references) if (row[key] != null && !/^r_[a-f\d]{32}$/i.test(row[key])) fail('数据引用不符合匿名协议');
      for (const column of data.columns) if (['money', 'decimal'].includes(column.type) && row[column.key] != null &&
          (typeof row[column.key] !== 'string' || !/^-?\d+(\.\d{1,4})?$/.test(row[column.key]))) fail('金额必须以精确小数字符串返回');
    }
  }
  return datasets;
}

export function validateAiChart(chart, datasets) {
  if (chart == null) return null;
  if (!object(chart) || Object.keys(chart).some(key => !['datasetId', 'queryId', 'type', 'xKey', 'yKeys', 'title'].includes(key)) ||
      (chart.queryId != null && !queryId(chart.queryId)) ||
      !['bar', 'line', 'pie'].includes(chart.type) || !text(chart.title, 160) ||
      !Array.isArray(chart.yKeys) || chart.yKeys.length < 1 || chart.yKeys.length > 4) fail('图表协议不符合要求');
  const matching = datasets.filter(item => item.datasetId === chart.datasetId &&
    (chart.queryId == null || item.queryMeta?.queryId === chart.queryId));
  const data = matching.length === 1 ? matching[0] : null;
  if (!data || !data.columns.some(column => column.key === chart.xKey) ||
      chart.yKeys.some(key => !data.columns.some(column => column.key === key && ['money', 'decimal', 'number'].includes(column.type)))) fail('图表必须绑定本次返回的数据');
  if (chart.type === 'pie' && chart.yKeys.length !== 1) fail('饼图只能绑定一个数值指标');
  if (chart.type === 'pie' && data.rows.some(row => row[chart.yKeys[0]] != null && Number(row[chart.yKeys[0]]) < 0)) fail('包含负数的数据不能绘制饼图');
  return chart;
}

function chartPresentation(value, { allowUnavailableChart = false } = {}) {
  try { validateAiChart(value.chart, value.datasets || []); }
  catch (error) {
    if (!allowUnavailableChart) throw error;
    // Optional chart rendering must not invalidate an otherwise checked result.
    // Never guess a metric, borrow another query, or modify persisted data.
    return { ...value, chart: null, chartNotice: '这张图表暂时无法显示，查询数据已保留。' };
  }
  return value;
}

export function validateAiChoices(value) {
  if (!object(value)) fail('业务候选不符合协议');
  for (const [group, choices] of Object.entries(value)) {
    if (!['cards', 'categories', 'funds', 'todos', 'fixedAssets', 'moods'].includes(group) || !Array.isArray(choices) || choices.length > 100) fail('业务候选超出开放范围');
    if (group === 'moods') { if (choices.some(choice => !text(choice, 30))) fail(); continue; }
    for (const choice of choices) if (!object(choice) || !text(choice.name, 1500) ||
        (choice.ref != null && !/^r_[a-f\d]{32}$/i.test(choice.ref)) ||
        (choice.id == null && choice.ref == null)) fail('业务候选缺少受控标识');
  }
  return value;
}

export function validateAiProposal(value) {
  if (!object(value) || !uuid(value.id) || !Object.hasOwn(AI_PROPOSAL_LABELS, value.kind) ||
      !Number.isInteger(value.version) || value.version < 1 || !Number.isFinite(value.expiresAt) ||
      !['pending', 'reviewed', 'discarded', 'executed', 'committed', 'expired', 'failed'].includes(value.status) ||
      !Array.isArray(value.missingFields) || value.missingFields.some(field => !text(field, 100)) ||
      !object(value.fields || value.payload)) fail('业务草稿种类或版本不符合开放边界');
  if (value.kind === 'fixedAsset.create' && value.actionUnavailable !== true) fail('固定资产登记须在原入口核对');
  if (value.choices) validateAiChoices(value.choices);
  return value;
}

export function validateAiToolTrace(value) {
  if (!Array.isArray(value) || value.length > 8 || JSON.stringify(value).length > 65536 ||
      value.some(item => !object(item) || !text(item.tool, 100) || !item.tool.trim() ||
        Object.keys(item).some(key => !['tool', 'datasetId', 'queryId', 'domain', 'records', 'bytes', 'digest', 'previewId'].includes(key)) ||
        (item.queryId != null && !queryId(item.queryId)) ||
        (item.domain != null && !AI_DOMAINS.includes(item.domain)) ||
        (item.datasetId != null && !text(item.datasetId, 120)) ||
        (item.records != null && (!Number.isInteger(item.records) || item.records < 0 || item.records > 500)) ||
        (item.bytes != null && (!Number.isInteger(item.bytes) || item.bytes < 0 || item.bytes > 65536)) ||
        (item.digest != null && !/^[a-f\d]{64}$/i.test(item.digest)) ||
        (item.previewId != null && !uuid(item.previewId)))) fail('按需取数记录不符合协议');
  return value;
}

export function validateAiBusinessResult(value, options) {
  const v3 = value?.contractVersion === 3;
  if (!object(value) || ![2, 3].includes(value.contractVersion) || !text(value.answer, 12000) || !uuid(value.conversationId) ||
      !object(value.request) || !uuid(value.request.requestId) || value.request.maxProviderCalls !== (v3 ? 5 : 1) ||
      !Number.isInteger(value.request.providerCalls) || value.request.providerCalls < (v3 ? 1 : 0) ||
      value.request.providerCalls > value.request.maxProviderCalls) fail(v3 ? 'AI 处理预算或回执不完整' : '单次 AI 调用安全回执不完整');
  if (value.toolTrace != null) validateAiToolTrace(value.toolTrace);
  if (value.promptVersion != null && !text(value.promptVersion, 100)) fail('AI 提示版本不符合协议');
  validateAiDatasets(value.datasets || []);
  value = chartPresentation(value, options);
  if (value.proposal) validateAiProposal(value.proposal);
  if (value.draft) validateAiDraft(value.draft);
  if (value.pendingTask != null) validateAiPendingTask(value.pendingTask);
  // A multi-entry result can mix ready drafts and candidates needing input.
  // The 2-3 entry limit applies to the combined result, not each group.
  const multi = value.drafts != null || value.candidates != null;
  if (value.drafts != null) {
    if (!Array.isArray(value.drafts) || value.drafts.length < 1 || value.drafts.length > 3) fail('多笔草稿数组不符合协议');
    for (const draft of value.drafts) {
      validateAiDraft(draft);
      if (draft.financialCheck != null) validateAiFinancialCheck(draft.financialCheck);
    }
  }
  // 追加165：多笔候选数组（需补字段的核查卡，逐张独立）
  if (value.candidates != null) {
    if (!Array.isArray(value.candidates) || value.candidates.length < 1 || value.candidates.length > 3) fail('多笔候选数组不符合协议');
    for (const item of value.candidates) {
      const id = item?.inputCandidate?.sourceCandidateId || item?.inputCandidate?.id;
      if (!object(item) || !uuid(id) || (item.inputCandidate.id != null && item.inputCandidate.id !== id)) fail('多笔候选不符合协议');
      validateAiFinancialEnvelope(item, true);
      validateAiTransactionPreflight({ contractVersion: 2, ...item,
        fields: item.inputCandidate.fields, choices: item.financialChoices || item.inputCandidate.choices });
    }
  }
  if (multi) {
    const ids = [...(value.drafts || []).map(draft => draft.id),
      ...(value.candidates || []).map(item => item.inputCandidate.sourceCandidateId || item.inputCandidate.id)];
    if (ids.length < 2 || ids.length > 3 || new Set(ids).size !== ids.length) fail('多笔记账必须包含二至三份独立卡片');
    if (value.draft || value.proposal || value.inputCandidate) fail('多笔记账不得同时携带单笔卡片');
  }
  if (Object.hasOwn(value, 'operationOutcome')) {
    if (!v3) fail('操作成果需要新版 AI 协议');
    validateAiOperationOutcome(value.operationOutcome, value);
  }
  return value;
}

export function validateAiAgentResult(value, options) {
  if (value?.contractVersion !== 3) fail('当前聊天需要新版 AI 协议，请更新服务端');
  return validateAiBusinessResult(value, options);
}

export function validateAiPendingTask(value) {
  const keys = ['taskId', 'revision', 'status', 'expiresAt', 'items'];
  if (!object(value) || Object.keys(value).some(key => !keys.includes(key)) || !uuid(value.taskId) ||
      !Number.isSafeInteger(value.revision) || value.revision < 1 || !['awaiting_input', 'expired'].includes(value.status) ||
      !Number.isFinite(value.expiresAt) || !Array.isArray(value.items) || !value.items.length || value.items.length > 3) fail('待补记账任务不符合协议');
  const indexes = new Set();
  for (const item of value.items) {
    const fields = item?.fields;
    if (!object(item) || Object.keys(item).some(key => !['index', 'fields', 'missingFields'].includes(key)) ||
        !Number.isSafeInteger(item.index) || item.index < 0 || item.index > 2 || indexes.has(item.index) ||
        !object(fields) || Object.keys(fields).some(key => !['direction', 'amount', 'currency', 'transDate', 'remark', 'payMethod'].includes(key)) ||
        ![0, 1].includes(fields.direction) || fields.currency !== 'CNY' || !text(fields.amount, 20) ||
        !/^(0|[1-9]\d{0,9})(\.\d{1,2})?$/.test(fields.amount) || !/[1-9]/.test(fields.amount) ||
        !calendarDate(fields.transDate) || !text(fields.remark, 255) ||
        fields.payMethod != null && !['现金', '余额', '支付宝', '微信', '银行卡'].includes(fields.payMethod) ||
        !Array.isArray(item.missingFields) || item.missingFields.some(field => !['账户', '分类', '支付方式'].includes(field))) fail('待补记账来源或缺项不符合协议');
    indexes.add(item.index);
  }
  return value;
}

function validateRetryGraph(links) {
  const parents = new Map();
  for (const { requestId, retryOf } of links) {
    if (!uuid(requestId) || !uuid(retryOf) || requestId === retryOf ||
      parents.has(requestId) && parents.get(requestId) !== retryOf) fail('继续请求关联不符合协议');
    parents.set(requestId, retryOf);
  }
  const checked = new Set();
  for (const id of parents.keys()) {
    const path = new Set(); let current = id;
    while (parents.has(current) && !checked.has(current)) {
      if (path.has(current)) fail('继续请求关联存在循环');
      path.add(current); current = parents.get(current);
    }
    for (const item of path) checked.add(item);
  }
}

export function validateAiBusinessMessages(list, options) {
  const originalList = list;
  validateAiMessages(list);
  list = options?.allowUnavailableChart ? [...list] : list;
  for (const [index, item] of list.entries()) {
    if (item.proposal) validateAiProposal(item.proposal);
    if (item.draft) validateAiDraft(item.draft);
    if (item.pendingTask != null) validateAiPendingTask(item.pendingTask);
    if (item.datasets) validateAiDatasets(item.datasets);
    if (item.chart) list[index] = chartPresentation(item, options);
    if (item.toolTrace) validateAiToolTrace(item.toolTrace);
    // 服务端失败轮次：必须能定位到原请求，才能恢复「重试/读取回执」。
    if (item.failure != null && (item.role !== 'assistant' || !object(item.failure) || item.failure.status !== 'failed' ||
      !text(item.requestId, 64) || !/^[A-Za-z0-9_-]{1,64}$/.test(item.requestId) ||
      (item.failure.code != null && !text(item.failure.code, 80)))) fail('历史未完成轮次不符合协议');
    if (item.failure?.providerDetail != null) validateAiProviderDetail(item.failure.providerDetail);
    if (item.authorizationRequest != null) validateAiAuthorizationRequest(item.authorizationRequest);
    if (item.retryOf != null && (!uuid(item.retryOf) || item.retryOf === item.requestId)) fail('历史继续请求关联不符合协议');
    if (item.continuedBy != null && (!uuid(item.continuedBy) || item.continuedBy === item.requestId)) fail('历史继续请求关联不符合协议');
    for (const key of ['draftIds', 'candidateIds']) if (item[key] != null &&
      (!Array.isArray(item[key]) || item[key].length > 3 || item[key].some(id => !uuid(id)) ||
        new Set(item[key]).size !== item[key].length)) fail('历史卡片编号不符合协议');
    const nestedOutcome = object(item.business) && Object.hasOwn(item.business, 'operationOutcome');
    if ((Object.hasOwn(item, 'operationOutcome') || nestedOutcome) && item.role !== 'assistant') fail('操作成果只能属于助手结果');
    if (Object.hasOwn(item, 'operationOutcome')) validateAiOperationOutcome(item.operationOutcome, item, { history: true });
    if (nestedOutcome) {
      validateAiOperationOutcome(item.business.operationOutcome, item, { history: true });
      if (item.operationOutcome && (item.operationOutcome.state !== item.business.operationOutcome.state ||
          item.operationOutcome.artifacts.some(entry => !item.business.operationOutcome.artifacts.some(other =>
            entry.type === other.type && entry.id === other.id && entry.kind === other.kind &&
            entry.status === other.status && entry.readyForReview === other.readyForReview)))) fail('历史操作成果快照不一致');
      if (!Object.hasOwn(item, 'operationOutcome')) {
        if (list === originalList) list = [...list];
        list[index] = { ...list[index], operationOutcome: item.business.operationOutcome };
      }
    }
  }
  validateRetryGraph(list.flatMap(item => [
    ...(item.retryOf ? [{ requestId: item.requestId, retryOf: item.retryOf }] : []),
    ...(item.continuedBy ? [{ requestId: item.continuedBy, retryOf: item.requestId }] : []),
  ]));
  return list;
}

// 追加166：跨端同步增量响应。lastId 是本会话当前最大消息编号，前端以它续拉；每条增量必须带 messageId。
export function validateAiConversationSync(value, options) {
  if (!object(value) || !Array.isArray(value.messages) ||
      !Number.isInteger(value.lastId) || value.lastId < 0) fail('同步响应不符合协议');
  for (const item of value.messages) if (!Number.isInteger(item.messageId) || item.messageId < 1) fail('同步消息编号不符合协议');
  const messages = validateAiBusinessMessages(value.messages.map(item => ({ ...item, content: item.content ?? item.text })), options);
  return options?.allowUnavailableChart ? { ...value, messages } : value;
}

export function validateAiConversationState(value, options) {
  value = validateAiConversationSync(value, options);
  if (!uuid(value.conversationId) || !Array.isArray(value.activities) || !object(value.entities)) fail('会话状态不符合协议');
  for (const activity of value.activities) if (!uuid(activity.requestId) || !text(activity.message, 1500) ||
    !['queued','running','completed','failed'].includes(activity.status) || !text(activity.stage, 32)) fail('会话活动不符合协议');
  if (value.retryLinks != null && (!Array.isArray(value.retryLinks) || value.retryLinks.length > 1000 ||
    value.retryLinks.some(link => !object(link) || !uuid(link.requestId) || !uuid(link.retryOf) || link.requestId === link.retryOf)))
    fail('会话继续请求关联不符合协议');
  validateRetryGraph([...(value.retryLinks || []), ...value.messages.flatMap(item => [
    ...(item.retryOf ? [{ requestId: item.requestId, retryOf: item.retryOf }] : []),
    ...(item.continuedBy ? [{ requestId: item.continuedBy, retryOf: item.requestId }] : []),
  ])]);
  for (const kind of ['drafts','proposals','candidates']) {
    if (!Array.isArray(value.entities[kind]) || value.entities[kind].length > 200) fail('会话卡片状态不符合协议');
    for (const entity of value.entities[kind]) if (!uuid(entity.id) || !text(entity.status, 20) ||
      entity.version != null && (!Number.isInteger(entity.version) || entity.version < 1)) fail('会话卡片版本不符合协议');
  }
  return value;
}

// D-027：数据授权白名单（跟账号走）； granted:false 时 providerHost/domains/grantedAt 为空
export function validateAiGrant(value) {
  if (!object(value) || ![1, 2].includes(value.contractVersion) || typeof value.granted !== 'boolean') fail('授权响应不符合协议');
  if (value.contractVersion === 2 && (!uuid(value.conversationId) || value.granted &&
    (!['read', 'operate', 'web_search'].includes(value.access) || !['conversation', '15m', '1h', '24h'].includes(value.lifetime)
      || value.expiresAt !== null && (!Number.isSafeInteger(value.expiresAt) || value.expiresAt <= value.grantedAt)))) fail('会话授权不符合协议');
  if (!value.granted) {
    if (value.providerHost != null || value.grantedAt != null || (Array.isArray(value.domains) && value.domains.length)) fail('已撤回授权不得携带白名单残留');
    return value;
  }
  if (typeof value.providerHost !== 'string' || !value.providerHost || value.providerHost.length > 120 ||
      !Number.isFinite(value.grantedAt) || !Array.isArray(value.domains) || !value.domains.length ||
      value.domains.length > AI_DOMAINS.length || value.domains.some(domain => !AI_DOMAINS.includes(domain)) ||
      new Set(value.domains).size !== value.domains.length) fail('授权白名单不符合协议');
  return value;
}

export function validateAiAuthorizationRequest(value) {
  if (!object(value) || value.version !== 1 || !uuid(value.conversationId) || !text(value.providerHost, 120)
    || !value.providerHost || !['read', 'operate', 'web_search'].includes(value.access) || !text(value.reason, 200)
    || !Number.isSafeInteger(value.expiresAt)) fail('能力授权请求不符合协议');
  return value;
}

export function validateAiSession(value, options) {
  if (!object(value) || ![2, 3].includes(value.contractVersion) || !uuid(value.conversationId) ||
      (value.messages != null && (!Array.isArray(value.messages) || value.messages.some(item =>
        !['user', 'assistant'].includes(item.role) || !text(item.content ?? item.text, 12000))))) fail('会话响应不符合协议');
  const messages = validateAiBusinessMessages((value.messages || []).map(item => ({...item,content:item.content ?? item.text})), options);
  return options?.allowUnavailableChart ? { ...value, messages } : value;
}

export function validateAiAgentSession(value, options) {
  if (value?.contractVersion !== 3) fail('当前会话需要新版 AI 协议，请更新服务端');
  return validateAiSession(value, options);
}

// 供应商真实错误摘要（HTTP 状态/错误码/一句话原话），服务端已脱敏截断。
export function validateAiProviderDetail(value) {
  if (value == null) return value;
  if (!object(value) || (value.status != null && !Number.isInteger(value.status)) ||
      (value.code != null && !text(value.code, 80)) || (value.message != null && !text(value.message, 240)) ||
      (value.cause != null && !text(value.cause, 80))) fail('供应商错误摘要不符合协议');
  return value;
}

export function validateAiReceipt(value, options) {
  if (!object(value) || !['running', 'processing', 'pending', 'completed', 'succeeded', 'failed', 'unknown'].includes(value.status)) fail('请求回执不符合协议');
  if (value.providerDetail != null) validateAiProviderDetail(value.providerDetail);
  if (value.result) {
    const result = value.result.contractVersion === 1 ? validateAiResult(value.result) : validateAiBusinessResult(value.result, options);
    if (result !== value.result) return { ...value, result };
  }
  return value;
}
