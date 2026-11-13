import { validateAiChart } from './aiBusinessContract.js';

const monthPattern = /^20\d{2}-(?:0[1-9]|1[0-2])$/;
const yearPattern = /^20\d{2}$/;
const monthIndex = month => Number(month.slice(0, 4)) * 12 + Number(month.slice(5, 7)) - 1;
const monthText = index => `${Math.floor(index / 12)}-${String(index % 12 + 1).padStart(2, '0')}`;

function cardMonthSeries(chart, dataset, label) {
  const scope = dataset.queryMeta?.effectiveScope;
  const annual = dataset.queryMeta?.resolution === 'year';
  const pattern = annual ? yearPattern : monthPattern;
  const periodIndex = annual ? year => Number(year) : monthIndex;
  const periodText = annual ? index => String(index) : monthText;
  const periodLabel = annual ? '年度' : '月度';
  const start = scope?.startDate?.slice(0, annual ? 4 : 7), end = scope?.endDate?.slice(0, annual ? 4 : 7);
  let months;
  if (pattern.test(start || '') && pattern.test(end || '') && periodIndex(start) <= periodIndex(end)) {
    const count = periodIndex(end) - periodIndex(start) + 1;
    // A ten-year date interval can touch eleven calendar years or 121 months.
    if (count > (annual ? 11 : 121)) throw new Error(`卡片${periodLabel}图表范围超出限制`);
    months = Array.from({ length: count }, (_, index) => periodText(periodIndex(start) + index));
  } else months = [...new Set(dataset.rows.map(row => row.month))].sort();

  const cards = new Map();
  for (const row of dataset.rows) {
    if (!pattern.test(row.month || '') || !months.includes(row.month)) throw new Error(`卡片${periodLabel}图表日期不符合查询范围`);
    const ref = typeof row.cardRef === 'string' && /^r_[a-f\d]{32}$/i.test(row.cardRef) ? row.cardRef : null;
    const name = typeof row.name === 'string' && row.name ? row.name : '未知卡片';
    // Historical DTOs may have names only. References always take precedence
    // so two real cards sharing a display name never become one series.
    const identity = ref ? `ref:${ref}` : `name:${name}`;
    if (!cards.has(identity)) cards.set(identity, { name, months: new Map() });
    const card = cards.get(identity);
    if (card.months.has(row.month)) throw new Error(`卡片${periodLabel}图表存在重复的卡片${annual ? '年份' : '月份'}，无法确定序列`);
    card.months.set(row.month, row);
  }
  const moneyKeys = new Set(dataset.columns.filter(column => column.type === 'money').map(column => column.key));
  const series = [...cards.values()].flatMap(card => chart.yKeys.map(key => ({
    type: chart.type,
    name: chart.yKeys.length === 1 ? card.name : `${card.name} · ${label(key)}`,
    data: months.map(month => {
      const row = card.months.get(month);
      if (!row || row[key] == null || moneyKeys.has(key) && Number(row.unpricedCount) > 0) return null;
      // Numeric conversion affects chart pixels only; dataset strings stay exact.
      return Number(row[key]);
    }),
  })));
  return { months, series };
}

function cardBillLabels(dataset) {
  const base = dataset.rows.map(row => String(row.name || '未知卡片') + (row.month ? ' · ' + String(row.month) : ''));
  const counts = new Map(), seen = new Map();
  for (const label of base) counts.set(label, (counts.get(label) || 0) + 1);
  return base.map(label => { const index = (seen.get(label) || 0) + 1; seen.set(label, index);
    return counts.get(label) > 1 ? label + '（' + index + '）' : label; });
}

export function buildAiChartOptions(chart, dataset, { textColor } = {}) {
  validateAiChart(chart, [dataset]);
  const label = key => dataset.columns.find(column => column.key === key)?.label || key;
  const decimalTooltip = key => {
    if (dataset.columns.find(column => column.key === key)?.type !== 'decimal') return {};
    return { tooltip: { valueFormatter: (value, dataIndex) => {
      if (value == null) return '—';
      const raw = dataset.rows[dataIndex]?.[key];
      if (typeof raw === 'string' && /^-?\d+(?:\.\d{1,4})?$/.test(raw) && Number(raw) === Number(value)) return raw;
      return value == null ? '—' : String(value);
    } } };
  };

  const cardMonths = dataset.datasetId === 'card-monthlyConsumption' && dataset.queryMeta?.groupBy === 'cardMonth'
    && chart.xKey === 'month' && ['bar', 'line'].includes(chart.type);
  const cardData = cardMonths ? cardMonthSeries(chart, dataset, label) : null;
  const billLabels = dataset.datasetId === 'card-bills' && chart.xKey === 'name' && ['bar', 'line'].includes(chart.type) ? cardBillLabels(dataset) : null;
  const series = cardData?.series || (chart.type === 'pie' ? [{ type: 'pie', center: ['50%', '54%'], radius: ['24%', '56%'],
    label: { show: true, position: 'inside', formatter: '{d}%', fontSize: 9, color: '#fff' }, labelLine: { show: false },
    data: dataset.rows.filter(row => row[chart.yKeys[0]] != null)
      .map(row => ({ name: String(row[chart.xKey] ?? '未知'), value: Number(row[chart.yKeys[0]]) })) }] :
    chart.yKeys.map(key => ({ type: chart.type, name: label(key), ...decimalTooltip(key),
      data: dataset.rows.map(row => row[key] == null ? null : Number(row[key])) })));
  return { animation: false, title: { text: chart.title, left: 'center', top: 2, textStyle: { fontSize: 11, ...(textColor ? { color: textColor } : {}) } },
    tooltip: { trigger: chart.type === 'pie' ? 'item' : 'axis', renderMode: 'richText' },
    legend: { bottom: 0, textStyle: { fontSize: 10, ...(textColor ? { color: textColor } : {}) } },
    ...(chart.type !== 'pie' ? { grid: { left: 40, right: 12, top: 34, bottom: 44 },
      xAxis: { type: 'category', data: cardData?.months || billLabels || dataset.rows.map(row => String(row[chart.xKey] ?? '')) },
      yAxis: { type: 'value' } } : {}), series };
}
