<template>
  <section class="proposal-card" :aria-label="title">
    <div class="heading">
      <template v-if="doneCompact">
        <strong class="done-head"><span class="done-check" aria-hidden="true">{{ doneIcon }}</span>{{ title }}<span v-if="terminalNote" class="done-note">{{ terminalNote }}</span></strong>
        <button type="button" class="detail-toggle" :aria-expanded="detailOpen ? 'true' : 'false'" @click="detailOpen = !detailOpen">{{ detailOpen ? '收起' : '详细' }}<van-icon :name="detailOpen ? 'arrow-up' : 'arrow-down'" /></button>
      </template>
      <template v-else><strong>{{ title }}</strong><span :class="{ 'status-failed': proposal.status === 'failed' }">{{ status }}</span></template>
    </div>
    <div v-if="doneCompact && !detailOpen" class="compact">
      <div v-for="key in compactKeys" :key="key" class="compact-row"><span>{{ schema.find(field => field.key === key)?.label || fieldLabels[key] || key }}</span><p>{{ key === 'targetId' || key === 'fundId' ? targetLabel(key) : displayValue(fields[key], key) }}</p></div>
      <div v-if="proposal.kind === 'fund.returnBatch' && fields.entries?.length" class="compact-row"><span>登记</span><p>{{ fields.entries.length }} 笔 · {{ batchDateRange }}</p></div>
    </div>
    <template v-if="!doneCompact || detailOpen">
    <p v-if="proposal.actionReason" class="notice">{{ proposal.actionReason }}</p>
    <p v-if="proposal.kind === 'system.backup'" class="notice">固定范围：系统数据库完整备份。确认后启动后台任务，任务完成情况以服务器回执为准。</p>
    <div v-if="snapshot.length" class="snapshot"><strong>当前记录</strong><dl><div v-for="field in snapshot" :key="field.key" :class="{full: ['content','remark'].includes(field.key)}"><dt>{{ field.label }}</dt><dd>{{ displayValue(field.value, field.key) }}</dd></div></dl></div>
    <div v-if="changes.length && !editing" class="changes"><strong>本次修改</strong><div v-for="field in changes" :key="field.key"><span>{{ field.label }}</span><p><del>{{ displayValue(field.oldValue, field.key) }}</del><span aria-label="改为">→</span><b>{{ displayValue(field.newValue, field.key) }}</b></p></div></div>
    <template v-if="!editing">
      <dl class="fields"><div v-for="field in schema" :key="field.key" :class="{full: field.type === 'textarea'}"><dt>{{ field.label }}</dt><dd>{{ field.key === 'targetId' || field.key === 'fundId' ? targetLabel(field.key) : displayValue(fields[field.key], field.key) }}</dd></div></dl>
      <div v-if="proposal.kind === 'fund.returnBatch'" class="batch"><p class="notice">只登记你明确提供的日收益；重复日期或已有历史会整批停止。</p><table><thead><tr><th>日期</th><th>收益（元）</th></tr></thead><tbody><tr v-for="(entry,index) in fields.entries || []" :key="index"><td>{{ entry.date }}</td><td>{{ entry.amount }}</td></tr></tbody></table></div>
    </template>
    <form v-else @submit.prevent="save">
      <div class="form-grid"><label v-for="field in schema" :key="field.key" :class="{full: field.type === 'textarea', 'checkbox-field': field.type === 'checkbox'}">{{ field.label }}
        <select v-if="field.options || ['targetId','fundId'].includes(field.key)" v-model="form[field.key]" :disabled="locked"><option :value="null">请选择</option><option v-for="choice in options(field)" :key="choice.value" :value="choice.value">{{ choice.label }}</option></select>
        <input v-else-if="field.type === 'checkbox'" v-model="form[field.key]" type="checkbox" :disabled="locked" />
        <textarea v-else-if="field.type === 'textarea'" v-model="form[field.key]" rows="2" :maxlength="field.max || 3000" :disabled="locked" />
        <input v-else v-model="form[field.key]" :type="field.type === 'date' ? 'date' : 'text'" :inputmode="field.type === 'money' || field.type === 'number' ? 'decimal' : undefined" :maxlength="field.max || 255" :disabled="locked" />
      </label></div>
      <div v-if="proposal.kind === 'fund.returnBatch'" class="batch"><div v-for="(entry,index) in form.entries" :key="index" class="batch-row"><input v-model="entry.date" type="date" :disabled="locked" aria-label="收益日期" /><input v-model="entry.amount" inputmode="decimal" maxlength="12" :disabled="locked" aria-label="日收益金额" /><button type="button" :disabled="locked" @click="form.entries.splice(index,1)">移除</button></div><button type="button" :disabled="locked || form.entries.length >= 31" @click="form.entries.push({date:'',amount:''})">添加一行</button></div>
      <div class="actions"><button type="button" :disabled="locked" @click="editing=false">返回卡片</button><button type="submit" class="primary" :disabled="locked">保存修改</button></div>
    </form>
    <p v-if="proposal.missingFields.length && !terminal" class="notice">待补齐：{{ proposal.missingFields.map(key => fieldLabels[key] || key).join('、') }}</p>
    <div v-if="!editing && !terminal" class="actions">
      <button :disabled="locked" @click="beginEdit">修改</button>
      <button :disabled="locked" @click="act('discard')">取消草稿</button>
      <template v-if="proposal.actionUnavailable && proposal.kind === 'fixedAsset.create'">
        <button v-if="operations?.openOriginal" type="button" class="original-link" :disabled="locked" @click="operations.openOriginal(proposal)">查看预填示例</button>
        <router-link v-else class="original-link" :to="{ path: '/finance/fixed-asset', query: { aiProposal: proposal.id } }">在原入口查看预填信息</router-link>
      </template>
      <button v-if="proposal.status === 'pending' && !proposal.actionUnavailable" class="primary" :disabled="locked || proposal.missingFields.length > 0" @click="act('review')">核对完成</button>
      <button v-if="proposal.status === 'reviewed' && !proposal.actionUnavailable" class="execute" :disabled="locked" @click="execute">确认执行</button>
    </div>
    <p v-if="proposal.receipt && !(doneCompact && isBackup)" class="receipt" :class="`receipt-${receiptTone}`" role="status">{{ receiptText }}</p>
    <small>{{ terminal ? '以服务器状态和回执为准' : '核对不会执行业务操作 · 编辑后需重新核对' }}</small>
    </template>
    <p v-if="doneCompact && isBackup" class="receipt" :class="`receipt-${receiptTone}`" role="status">{{ receiptText }}</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <button v-if="uncertain || (doneCompact && isBackup && receiptTone !== 'success')" type="button" class="refresh" :disabled="busy || disabled" @click="refresh">读取最新状态</button>
  </section>
</template>
<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue';
import { showConfirmDialog } from 'vant';
import { editAiProposal, actOnAiProposal, executeAiProposal, getAiProposal } from '@/utils/api/ai';
import { AI_PROPOSAL_LABELS } from '@/utils/aiBusinessContract';
import { getAiProposalSnapshot, getAiProposalChanges, formatAiProposalValue } from '@/utils/aiProposalDisplay';
const props = defineProps({ proposal: { type: Object, required: true }, disabled: Boolean, startExpanded: Boolean, operations: Object });
const emit = defineEmits(['change', 'working']);
const busy = ref(false), editing = ref(false), error = ref(''), uncertain = ref(false), clock = ref(Date.now());
const form = reactive({}); let timer, live = true;
const f = (key,label,type='text',options) => ({key,label,type,options});
const target = f('targetId','目标记录');
const todo = [f('content','事项','textarea'),f('eventType','类型','text',['schedule','birthday','anniversary','countdown']),f('happenDate','日期','date'),f('priority','优先级','number',[1,2,3]),f('needRemind','需要提醒','checkbox'),f('remindDays','提前天数','number'),f('isRecurring','每年重复','checkbox'),f('remark','备注','textarea')];
const schemas = {
  'budget.create':[f('title','预算标题'),f('route','路线'),f('budgetType','预算类型','text',['吃','买','行']),f('budgetAmount','预算金额','money'),f('cycle','周期','text',['月','季','年']),f('planDate','计划日期','date'),f('notes','备注','textarea')],
  'todo.create':todo,'todo.update':[target,...todo],'todo.complete':[target,f('status','状态','text',['待完成','已完成'])],'todo.delete':[target],
  'diary.create':[f('content','今天的日记','textarea'),f('mood','心情','text',['开心','平静','难过','累','兴奋','郁闷']),f('date','日期','date')],
  'card.metadata':[target,f('alias','别名'),f('status','状态','text',['正常','挂失','注销'])],
  'fixedAsset.create':[f('info','资产名称'),f('tag','品类'),f('buyPrice','购置价','money'),f('useYears','使用年限','number'),f('residualRate','残值率','number'),f('buyDate','购买日期','date')],
  'fixedAsset.update':[target,f('info','资产名称'),f('tag','品类')], 'fixedAsset.delete':[target],'fixedAsset.restore':[target],'fixedAsset.permanentDelete':[target],
  'fund.returnBatch':[f('fundId','基金')], 'system.backup':[],
};
const schema = computed(() => schemas[props.proposal.kind] || []);
const fieldLabels = { ...Object.fromEntries(Object.values(schemas).flat().map(field => [field.key,field.label])), alias:'别名',name:'名称',status:'状态',amount:'金额',date:'日期',info:'资产名称' };
const fields = computed(() => props.proposal.fields || props.proposal.payload || {});
const title = computed(() => AI_PROPOSAL_LABELS[props.proposal.kind]);
const expired = computed(() => clock.value >= props.proposal.expiresAt);
const terminalStatuses = ['committed','executed','discarded','expired','failed'];
const terminal = computed(() => expired.value || terminalStatuses.includes(props.proposal.status));
const locked = computed(() => busy.value || props.disabled || terminal.value || uncertain.value);
const status = computed(() => expired.value && !terminalStatuses.includes(props.proposal.status) ? '已过期' : ({pending:'待核对',reviewed:'已核对',committed:'已执行',executed:'已执行',discarded:'已取消',expired:'已过期',failed:'已失败'}[props.proposal.status] || props.proposal.status));
const snapshot = computed(() => getAiProposalSnapshot(props.proposal));
const changes = computed(() => getAiProposalChanges(props.proposal));
// 已执行态紧凑卡：各类型只露关键字段，回执与全部字段收进「详细」
const COMPACT_KEYS = {
  'budget.create':['title','budgetAmount','cycle'],'todo.create':['content','happenDate','remark'],'todo.update':['targetId','content','happenDate','remark'],
  'todo.complete':['targetId','status'],'todo.delete':['targetId'],'diary.create':['content','mood','date'],'card.metadata':['targetId','alias','status'],
  'fixedAsset.create':['info','buyPrice','buyDate'],'fixedAsset.update':['targetId','info','tag'],'fixedAsset.delete':['targetId'],'fixedAsset.restore':['targetId'],
  'fixedAsset.permanentDelete':['targetId'],'fund.returnBatch':['fundId'],'system.backup':[],
};
const doneCompact = computed(() => {
  // 2026-11-10 C1 终态统一紧凑：除已执行外，已取消/已过期/已失败也折叠为一行，
  // 与草稿卡 committed 紧凑对齐；字段与回执收进「详细」。
  return ['committed', 'executed', 'discarded', 'expired', 'failed'].includes(props.proposal.status) || expired.value;
});
const terminalNote = computed(() => doneCompact.value && !['committed', 'executed'].includes(props.proposal.status) ? status.value : '');
const isBackup = computed(() => props.proposal.kind === 'system.backup');
const receiptTone = computed(() => {
  if (!isBackup.value) return props.proposal.status === 'failed' ? 'failed' : 'success';
  const state = props.proposal.receipt?.status;
  return ['succeeded','completed'].includes(state) ? 'success' : ['failed','cancelled'].includes(state) ? 'failed' : 'pending';
});
// 终态状态符：取消/过期=🚫、失败=⚠️；已执行沿用 ✅/⏳/❌（备份任务按回执状态）。
const doneIcon = computed(() => ['discarded', 'expired'].includes(props.proposal.status) || (expired.value && !['committed', 'executed', 'failed'].includes(props.proposal.status)) ? '🚫'
  : props.proposal.status === 'failed' ? '⚠️'
  : !isBackup.value || receiptTone.value === 'success' ? '✅' : receiptTone.value === 'failed' ? '❌' : '⏳');
const batchDateRange = computed(() => {
  const dates = (fields.value.entries || []).map(entry => entry.date).filter(Boolean).sort();
  return dates.length ? dates[0] === dates[dates.length - 1] ? dates[0] : `${dates[0]} 至 ${dates[dates.length - 1]}` : '日期待补充';
});
const detailOpen = ref(props.startExpanded === true);
const compactKeys = computed(() => {
  const keys = COMPACT_KEYS[props.proposal.kind] || schema.value.slice(0, 3).map(field => field.key);
  return keys.filter(key => {
    if (key === 'targetId' || key === 'fundId') return true;
    const value = fields.value[key];
    return !(value === undefined || value === null || value === '');
  });
});
const receiptText = computed(() => {
  const receipt = props.proposal.receipt;
  if (isBackup.value) {
    const labels = {queued:'排队中',pending:'等待处理',started:'已启动',running:'进行中',processing:'进行中',succeeded:'已完成',completed:'已完成',failed:'失败',cancelled:'已取消',unknown:'状态待确认'};
    return `备份任务${receipt?.jobRef ? ` ${receipt.jobRef}` : ''} · ${labels[receipt?.status] || '状态待确认'}`;
  }
  return receipt?.recordId ? `操作已完成 · 回执 ${receipt.recordId}` : '服务器已确认操作完成';
});
const defaultOperations = { edit: editAiProposal, act: actOnAiProposal, execute: executeAiProposal, get: getAiProposal };
function operation(name, ...args) { return (props.operations?.[name] || defaultOperations[name])(...args); }
function displayValue(value, key) { return formatAiProposalValue(key, value); }
function choices(key) {
  const groups = props.proposal.choices || {};
  return key === 'fundId' ? groups.funds || [] : props.proposal.kind.startsWith('card.') ? groups.cards || [] : props.proposal.kind.startsWith('todo.') ? groups.todos || [] : groups.fixedAssets || [];
}
function options(field) {
  if (field.options) return field.options.map(value => ({value,label:formatAiProposalValue(field.key, value)}));
  return choices(field.key).map(choice => ({value:choice.id,label:choice.name || choice.alias || choice.info || choice.content || '本人记录'}));
}
function targetLabel(key) { const item = choices(key).find(choice => String(choice.id) === String(fields.value[key])); return item?.name || item?.alias || item?.info || item?.content || (fields.value[key] ? props.proposal.targetSnapshot?.label || '已匹配本人记录（见当前记录）' : '待选择'); }
function beginEdit() {
  if (locked.value) return;
  for (const key of Object.keys(form)) delete form[key];
  Object.assign(form, JSON.parse(JSON.stringify(fields.value)));
  if (props.proposal.kind === 'fund.returnBatch') form.entries ||= [];
  editing.value=true; error.value='';
}
async function run(operation) {
  if (!live || locked.value) return;
  busy.value=true; emit('working',true); error.value=''; uncertain.value=false;
  try { const result = await operation(); if (live) { emit('change',result); editing.value=false; } }
  catch(exception) { if (live) { error.value=exception?.message || '操作未得到确认，请读取最新状态后再操作。'; uncertain.value=true; } }
  finally { busy.value=false; emit('working',false); }
}
async function save() {
  const data = {};
  for (const field of schema.value) if (Object.hasOwn(form,field.key)) data[field.key] = field.type === 'number' ? Number(form[field.key]) : form[field.key];
  if (props.proposal.kind === 'fund.returnBatch') data.entries = form.entries.map(entry=>({date:entry.date,amount:String(entry.amount).trim()}));
  await run(()=>operation('edit',props.proposal,data));
}
async function act(action) { await run(()=>operation('act',props.proposal,action)); }
async function refresh() {
  if (!live || busy.value || props.disabled) return; busy.value=true; emit('working',true);
  try { const result = await operation('get',props.proposal.id); if (live) { emit('change',result); uncertain.value=false; error.value=''; editing.value=false; } }
  catch(exception) { if (live) error.value=exception?.message || '状态暂时无法读取'; }
  finally { busy.value=false; emit('working',false); }
}
async function execute() {
  if (!live || locked.value || props.proposal.status !== 'reviewed' || props.proposal.actionUnavailable) return;
  // Lock before opening confirmation so repeated clicks cannot create concurrent submissions.
  busy.value=true; emit('working',true);
  const consequence = props.proposal.kind === 'system.backup' ? '将启动系统数据库备份任务。' : props.proposal.kind === 'fixedAsset.permanentDelete' ? '将永久删除此资产，无法恢复。' : /delete/.test(props.proposal.kind) ? '将删除对应记录。' : props.proposal.kind === 'fund.returnBatch' ? '将整批登记明确提供的收益，更新基金历史。' : '将保存到对应业务并立即生效。';
  try { await showConfirmDialog({title:`确认${title.value}`,message:`${consequence}\n\n请确认卡片所示字段和目标记录。`,confirmButtonText:'确认执行',cancelButtonText:'返回核对'}); }
  catch { busy.value=false; emit('working',false); return; }
  if (!live) { busy.value=false; return; }
  error.value=''; uncertain.value=false;
  try { const result = await operation('execute',props.proposal); if (live) emit('change',result); }
  catch(exception) { if (live) { error.value=exception?.message || '执行未得到确认，请读取回执。'; uncertain.value=true; } }
  finally { busy.value=false; emit('working',false); }
}
onMounted(()=>{ timer=setInterval(()=>{clock.value=Date.now();},15000); });
onBeforeUnmount(()=>{ live=false; clearInterval(timer); emit('working',false); });
</script>
<style scoped>
.proposal-card{margin-top:var(--ai-card-gap,6px);padding:var(--ai-card-padding,10px);border:1px solid var(--theme-border);border-radius:10px;background:var(--theme-bg-secondary);min-width:0;font-size:var(--ai-body-size,11px);line-height:1.55}
button,input,select,textarea,summary,a{font:inherit}
button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible,summary:focus-visible,a:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}
button:disabled,input:disabled,select:disabled,textarea:disabled{opacity:.5;cursor:default}
input,select,textarea{box-sizing:border-box;width:100%;min-width:0;border:1px solid var(--theme-border);border-radius:7px;padding:6px 8px;background:var(--theme-bg-primary);color:var(--theme-text-primary);font-size:var(--ai-body-size,11px);min-height:34px}
input[type=checkbox]{width:18px;height:18px;min-height:0;padding:0;accent-color:var(--van-primary-color)}
.actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:var(--ai-card-gap,6px);margin-top:var(--ai-card-gap,6px)}
.actions button,.read-refresh,.original-link{box-sizing:border-box;min-height:34px;border:1px solid var(--theme-border);border-radius:7px;padding:5px 9px;background:var(--theme-bg-primary);color:var(--theme-text-primary);font-size:var(--ai-body-size,11px);cursor:pointer;text-decoration:none;line-height:1.4}
.actions .primary{background:var(--van-primary-color);border-color:var(--van-primary-color);color:#fff}
@media(max-width:600px){.actions button,.read-refresh,.original-link{min-height:44px;min-width:44px;display:inline-flex;align-items:center;justify-content:center;padding:5px 9px}input,select,textarea{font-size:16px}input:not([type=checkbox]),select{min-height:44px}textarea{line-height:1.5}}

.heading{display:flex;align-items:center;justify-content:space-between;gap:var(--ai-card-gap,6px);font-size:var(--ai-title-size,11px)}.heading>strong,.done-head{min-width:0;overflow-wrap:anywhere}.heading span{font-size:var(--ai-label-size,10px);color:var(--van-primary-color)}.heading .status-failed{color:var(--van-danger-color)}
.done-head{display:inline-flex;align-items:center;gap:4px}.done-note{font-size:var(--ai-label-size,10px);color:var(--theme-text-secondary);font-weight:400;white-space:nowrap}.done-check,.detail-toggle{flex-shrink:0}.detail-toggle{margin-left:auto;display:inline-flex;align-items:center;justify-content:center;gap:3px;min-height:32px;border:0;background:transparent;padding:2px 6px;color:var(--van-primary-color);font-size:var(--ai-body-size,11px);cursor:pointer}.detail-toggle .van-icon{font-size:var(--ai-label-size,10px)}
.compact{margin:var(--ai-card-gap,6px) 0 0;display:flex;flex-direction:column;gap:4px}.compact-row{display:flex;align-items:baseline;gap:var(--ai-card-gap,6px);font-size:var(--ai-body-size,11px);line-height:1.5}.compact-row>span{flex-shrink:0;font-size:var(--ai-label-size,10px);color:var(--theme-text-secondary)}.compact-row>p{margin:0;min-width:0;white-space:pre-wrap;overflow-wrap:anywhere}
.fields,dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--ai-card-gap,6px);font-size:var(--ai-body-size,11px);margin:var(--ai-card-gap,6px) 0}dl>div{min-width:0}dt{color:var(--theme-text-secondary);font-size:var(--ai-label-size,10px);margin-bottom:2px}dd{margin:0;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.5}
.snapshot{border-bottom:1px solid var(--theme-border);margin:var(--ai-card-gap,6px) 0;padding-bottom:var(--ai-card-gap,6px)}.snapshot>strong{font-size:var(--ai-label-size,10px)}
.changes{margin:var(--ai-card-gap,6px) 0;padding:var(--ai-card-gap,6px);border:1px solid var(--theme-border);border-radius:7px;background:var(--theme-bg-primary);font-size:var(--ai-body-size,11px)}.changes>strong,.changes>div>span{font-size:var(--ai-label-size,10px)}.changes>div{margin-top:var(--ai-card-gap,6px)}.changes>div>span,.changes del{color:var(--theme-text-secondary)}.changes p{display:flex;align-items:center;gap:var(--ai-card-gap,6px);flex-wrap:wrap;margin:3px 0 0;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.5}.changes b{font-weight:550;color:var(--van-primary-color)}
.notice,small{color:var(--theme-text-secondary);font-size:var(--ai-label-size,10px);line-height:1.55;overflow-wrap:anywhere}.notice{margin:var(--ai-card-gap,6px) 0}small{display:block;margin-top:var(--ai-card-gap,6px)}
.actions{margin:var(--ai-card-gap,6px) 0}.execute{background:var(--van-success-color);border-color:var(--van-success-color);color:#fff}
.original-link{display:inline-flex;align-items:center;justify-content:center}
.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--ai-card-gap,6px);margin-top:var(--ai-card-gap,6px)}.form-grid label{display:flex;flex-direction:column;gap:3px;font-size:var(--ai-label-size,10px);color:var(--theme-text-secondary);min-width:0}.full{grid-column:1/-1}input[type=checkbox]{align-self:flex-start}
.batch{overflow-x:auto}.batch-row{display:flex;gap:var(--ai-card-gap,6px);margin:var(--ai-card-gap,6px) 0;min-width:0}.batch-row input{flex:1;min-width:0}.batch-row button{flex-shrink:0;border:1px solid var(--theme-border);border-radius:7px;padding:5px 9px;min-height:34px;background:var(--theme-bg-primary);color:var(--theme-text-primary)}
table{width:100%;font-size:var(--ai-body-size,11px);border-collapse:collapse}th,td{text-align:left;padding:5px var(--ai-card-gap,6px);border-bottom:1px solid var(--theme-border);font-variant-numeric:tabular-nums;overflow-wrap:anywhere}
.error,.receipt{font-size:var(--ai-body-size,11px);line-height:1.55;margin:var(--ai-card-gap,6px) 0;overflow-wrap:anywhere;white-space:pre-wrap}.error,.receipt-failed{color:var(--van-danger-color)}.receipt{color:var(--van-success-color);font-weight:500}.receipt-failed{color:var(--van-danger-color)}.receipt-pending{color:var(--theme-text-secondary)}.refresh{margin-bottom:var(--ai-card-gap,6px)}
@media(max-width:600px){.original-link{flex-basis:100%;text-align:center}.form-grid .checkbox-field{min-height:44px;flex-direction:row;align-items:center;justify-content:space-between}.checkbox-field input{align-self:center;margin:0}.detail-toggle{min-height:36px}.batch-row{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:var(--ai-card-gap,6px)}.batch-row input[type=date]{grid-column:1/-1}.batch-row button{min-height:44px;min-width:44px}}
@media(max-width:380px){.form-grid{grid-template-columns:minmax(0,1fr)}}
</style>
