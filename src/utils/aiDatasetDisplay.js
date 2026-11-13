const LABELS = {
  income: '收入', expense: '支出', net: '结余', count: '数量', incomeCount: '收入笔数', expenseCount: '支出笔数', total: '合计', balance: '余额',
  unpricedCount: '未计价笔数', estimatedTotal: '估算总额', pending: '未完成', completed: '已完成',
  currency: '币种', startDate: '开始日期', endDate: '结束日期', incomplete: '数据不完整',
  // 2026-11-10 C3：此前 deficit/surplus 以英文原样显示在中文网格里
  deficit: '超支金额', surplus: '盈余金额',
  profit: '收益增量合计', capitalDelta: '本金增量合计', amount: '登记金额合计',
  planned: '计划金额合计', registeredUsed: '登记使用额合计', remaining: '登记剩余额合计',
  daySalary: '基本工资合计', subsidy: '补贴合计', deductions: '扣款合计', hours: '登记工时',
};
const MONEY_KEYS = new Set(['income', 'expense', 'net', 'total', 'balance', 'estimatedTotal']);
const DECIMAL_MONEY_KEYS = new Set(['amount', 'profit']);
const countValue = value => /^(?:0|[1-9]\d*)$/.test(String(value)) && Number.isSafeInteger(Number(value)) ? Number(value) : 0;
const displayValue = value => typeof value === 'boolean' ? (value ? '是' : '否') : value ?? '未提供';
const knownCurrency = value => typeof value === 'string' && /^[A-Z]{3}$/.test(value);
const queryUnits = { 'finance-monthly': '个月', 'finance-daily': '天', 'finance-categories': '组', 'finance-transactions': '笔',
  'account-balances': '个账户', 'card-cards': '张卡片', 'card-counts': '类', 'asset-composition': '组' };
const savedStateDatasets = new Set(['account-balances', 'card-cards', 'card-counts', 'card-pools', 'work-rules',
  'asset-composition', 'fixedAsset-saved', 'fund-holdings']);
const querySourceUnits = { 'account-balances': '个账户', 'asset-composition': '个账户', 'card-cards': '张卡片', 'card-counts': '张卡片',
  'work-salary': '条工资登记', 'fund-history': '条收益登记', 'budget-plans': '个预算计划', 'todo-events': '个发生事件',
  'asset-snapshots': '条快照记录', 'asset-registers': '条资产登记', 'card-repayments': '条还款记录',
  'card-monthlyConsumption': '笔消费流水' };
function queryUnit(dataset, meta) {
  if (['month', 'year'].includes(meta?.resolution)) {
    if (['finance-monthly', 'finance-daily'].includes(dataset.datasetId)) return meta.resolution === 'year' ? '年' : '个月';
    // Card/fund/work/status dimensions can produce multiple groups per period.
    // A group count must not be described as a number of saved records or years.
    return '组';
  }
  return meta ? queryUnits[dataset.datasetId] || '条' : '条';
}

export function aiDatasetIdentity(dataset) {
  return dataset.queryMeta?.queryId || dataset.datasetId;
}

// Legacy history has only a dataset type. It is safe to use it only when there
// is one matching result; new results bind the exact query instance.
export function findAiQueryDataset(datasets = [], reference = {}) {
  const matching = datasets.filter(dataset => dataset.datasetId === reference.datasetId &&
    (reference.queryId == null || dataset.queryMeta?.queryId === reference.queryId));
  return matching.length === 1 ? matching[0] : null;
}

export function getAiQueryDisplay(dataset) {
  const rows = dataset.rows || [], meta = dataset.queryMeta;
  const unit = queryUnit(dataset, meta);
  const recordsLabel = `${rows.length} ${unit}`;
  const knownEmpty = meta ? meta.rowsComplete && meta.matchedRowCount === 0 :
    !dataset.truncated && dataset.summary?.count != null && Number(dataset.summary.count) === 0;
  const emptyMessage = knownEmpty ? '这个查询范围内没有匹配数据。' : '本次未返回匹配记录，尚不能确认整个查询范围为空。';
  if (!meta) return { recordsLabel, countLabel: '', scopeLabel: '', emptyMessage };
  const range = savedStateDatasets.has(dataset.datasetId) ? '当前已保存状态'
    : dataset.datasetId === 'installment-plans'
      ? `当前保存计划；期次状态范围 ${meta.effectiveScope.startDate} 至 ${meta.effectiveScope.endDate}`
      : `查询范围 ${meta.effectiveScope.startDate} 至 ${meta.effectiveScope.endDate}`;
  const filterLabel = typeof meta.filters?.text === 'string' && meta.filters.text
    ? `；文字筛选“${meta.filters.text.replace(/\s+/g, ' ')}”` : '';
  const scopeLabel = range + filterLabel;
  const matching = meta.matchedRowCount === null ? '匹配总数未确定' : `范围内匹配 ${meta.matchedRowCount} ${unit}`;
  const source = dataset.datasetId !== 'finance-transactions' && meta.sourceRecordCount !== null
    ? `，涉及 ${meta.sourceRecordCount} ${querySourceUnits[dataset.datasetId] || '笔流水'}` : '';
  const countLabel = `${matching}，已返回 ${meta.returnedRowCount} ${unit}${source}`;
  return { recordsLabel, countLabel, scopeLabel, emptyMessage };
}

// This describes saved server data only. It never adds amounts or infers a currency/rate.
export function getAiDatasetDisplay(dataset) {
  const rows = dataset.rows || [], columns = dataset.columns || [], summary = dataset.summary || {};
  const moneyColumns = columns.filter(column => column.type === 'money' ||
    column.type === 'decimal' && DECIMAL_MONEY_KEYS.has(column.key));
  const moneyKeys = new Set([...MONEY_KEYS, ...moneyColumns.map(column => column.key)]);
  const moneySummary = Object.entries(summary).filter(([key]) => moneyKeys.has(key));
  const currency = knownCurrency(summary.currency) ? summary.currency : null;
  const rowCurrencies = rows.map(row => row.currency).filter(knownCurrency);
  const mixedCurrency = new Set(rowCurrencies).size > 1 && !currency;
  const unverifiedCurrency = summary.currency === 'unverified' || rows.some(row => row.currency === 'unverified');
  const hasMoney = moneySummary.length > 0 || moneyColumns.length > 0 && rows.length > 0;
  const missingCurrency = hasMoney && !currency && (moneySummary.length > 0 ||
    rows.some(row => !knownCurrency(row.currency)));
  const unpricedCount = Object.hasOwn(summary, 'unpricedCount') ? countValue(summary.unpricedCount) :
    rows.reduce((count, row) => count + countValue(row.unpricedCount), 0);
  const missingAmountCount = rows.filter(row => moneyColumns.some(column => row[column.key] == null)).length;
  const missingSummaryAmount = moneySummary.some(([, value]) => value == null);
  const incomplete = summary.incomplete === true || unpricedCount > 0 || missingAmountCount > 0 || missingSummaryAmount;
  const notes = [];
  const queryDisplay = getAiQueryDisplay(dataset);
  if (unpricedCount) notes.push(`未计价 ${unpricedCount} 笔，未计入金额`);
  if (summary.incomplete === true && !unpricedCount) notes.push(moneySummary.length ?
    '数据不完整，金额仅含已计入部分' : '数据不完整，请展开核对');
  if (missingAmountCount) notes.push(`${missingAmountCount} 条数据含未提供金额`);
  if (missingSummaryAmount) notes.push('汇总金额未提供');
  if (unverifiedCurrency) notes.push('币种未核验，不能视为人民币总额');
  else if (missingCurrency) notes.push('金额币种未注明，不能视为确定总额');
  if (mixedCurrency) notes.push('包含多币种，金额不可直接相加');
  if (dataset.queryMeta) {
    const meta = dataset.queryMeta;
    // 「明细未全部返回」由 coverage 行（已返回 N 条）表达，不再重复进警告；汇总覆盖语义保留在排名提示。
    if (meta.rankingComplete && ['amountDesc', 'expenseDesc', 'incomeDesc', 'categoryAmountDesc'].includes(meta.sort)) notes.push('排名基于完整查询范围');
  } else if (dataset.truncated) notes.push(`结果已截取（${rows.length} 条），请缩小范围查看`);

  const summaryItems = Object.entries(summary).map(([key, value]) => {
    const asNumber = typeof value === 'number' ? value :
      typeof value === 'string' && /^-?\d+(?:\.\d+)?$/.test(value) ? Number(value) : NaN;
    return {
      key,
      label: key === 'count' && dataset.domain === 'finance' ? '流水笔数' : key === 'estimatedTotal' ? '估算总额' : moneyKeys.has(key) ?
        `${dataset.isEstimated ? '估算' : '已计入'}${LABELS[key] || key}` : LABELS[key] || key,
      value: key === 'currency' && value === 'unverified' ? '未核验' : moneyKeys.has(key) ?
        `${currency || '币种待核验'} ${displayValue(value)}` : displayValue(value),
      // 2026-11-10 C3：结余类主指标为负时在卡片网格中警示
      neg: moneyKeys.has(key) && Number.isFinite(asNumber) && asNumber < 0,
    };
  });
  const picked = [];
  for (const key of ['expense', 'income', 'balance', 'total', 'estimatedTotal', 'net', 'count', 'pending', 'completed']) {
    const item = summaryItems.find(item => item.key === key);
    if (!item || moneyKeys.has(key) && (!currency || unverifiedCurrency)) continue;
    picked.push(`${item.label} ${item.value}`);
    if (picked.length === 2) break;
  }
  if (!picked.length) picked.push(`${rows.length} 条数据`);
  if (moneySummary.length && (!currency || unverifiedCurrency)) picked.push('金额币种待核验');
  const badge = unverifiedCurrency || missingCurrency ? '待核验' : incomplete ? '数据不完整' :
    mixedCurrency ? '多币种数据' : dataset.truncated ? '部分数据' : dataset.isEstimated ? '估算' : '账本数据';
  // 2026-11-10 C3：数据时间此前是未格式化的 ISO 字符串，改为本地「月-日 时:分」
  const asOfDate = typeof dataset.asOf === 'string' && !Number.isNaN(Date.parse(dataset.asOf)) ? new Date(dataset.asOf) : null;
  const dataTimeLabel = asOfDate ? `${String(asOfDate.getMonth() + 1).padStart(2, '0')}-${String(asOfDate.getDate()).padStart(2, '0')} ${String(asOfDate.getHours()).padStart(2, '0')}:${String(asOfDate.getMinutes()).padStart(2, '0')}` : '';
  return { compactSummary: picked.join(' · '), summaryItems, notes, badge, dataTimeLabel, ...queryDisplay };
}
