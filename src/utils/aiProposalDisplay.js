const SNAPSHOT_LABELS = { label:'名称', name:'名称', alias:'别名', status:'状态', content:'事项',
  info:'资产名称', tag:'品类', eventType:'类型', happenDate:'日期', date:'日期', priority:'优先级',
  needRemind:'需要提醒', remindDays:'提前天数', isRecurring:'每年重复', remark:'备注', isDeleted:'已删除' };
const primitive = value => value === null || ['string','number','boolean'].includes(typeof value);
const VALUE_LABELS = {
  eventType: { schedule:'普通日程', birthday:'生日', anniversary:'纪念日', countdown:'倒数日' },
  priority: { 1:'高优先级', 2:'普通优先级', 3:'低优先级' },
  budgetType: { '吃':'餐饮', '买':'购物', '行':'出行' },
  cycle: { '月':'月度', '季':'季度', '年':'年度' },
  status: { using:'使用中', scrapped:'已报废', sold:'已出售', lost:'已遗失' },
  backupStatus: { queued:'排队中', pending:'等待处理', started:'已启动', running:'进行中', processing:'处理中', succeeded:'已完成', completed:'已完成', failed:'失败', unknown:'状态待确认' },
};

export function formatAiProposalValue(key, value) {
  if (value == null || value === '') return '待补充';
  if (typeof value === 'boolean') return value ? '是' : '否';
  return VALUE_LABELS[key]?.[value] || String(value);
}

export function getAiProposalSnapshot(proposal) {
  const snapshot = proposal.targetSnapshot || {};
  return Object.entries(snapshot).filter(([key,value]) => Object.hasOwn(SNAPSHOT_LABELS,key) && primitive(value))
    .map(([key,value]) => ({key,label:SNAPSHOT_LABELS[key],value}));
}

export function getAiProposalChanges(proposal) {
  const snapshot = proposal.targetSnapshot || {}, fields = proposal.fields || proposal.payload || {};
  const old = snapshot.old || snapshot.metadata || snapshot;
  return Object.entries(fields).filter(([key,value]) => Object.hasOwn(SNAPSHOT_LABELS,key) && Object.hasOwn(old,key) &&
    primitive(value) && primitive(old[key]) && old[key] !== value).map(([key,value]) => ({key,label:SNAPSHOT_LABELS[key],oldValue:old[key],newValue:value}));
}
