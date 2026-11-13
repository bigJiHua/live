<template>
  <section class="financial-card" :class="check?.status" aria-label="财务核查">
    <div class="heading"><strong>{{ editable ? '记账前核查' : '资金与风险核验' }}</strong><span role="status">{{ statusLabel }}</span></div>
    <!-- 2026-11-10 C2：readonly 且核验通过时折叠为一行；有风险提示/核验过期/待补充时自动展开（安全信息不折叠） -->
    <button v-if="collapsible" type="button" class="check-line" :aria-expanded="!collapsed" @click="collapsed = !collapsed">
      <van-icon name="shield-o" class="cl-icon" />
      <span class="cl-t">资金核验 · {{ statusLabel }} · {{ shortFacts }}<template v-if="shortTime"> · {{ shortTime }}</template></span>
      <span class="cl-tg">{{ collapsed ? '展开' : '收起' }}</span>
      <van-icon :name="collapsed ? 'arrow-down' : 'arrow-up'" class="cl-chev" />
    </button>
    <template v-if="!collapsible || !collapsed">
    <dl v-if="check" class="facts">
      <div v-if="check.requestedAmount != null"><dt>拟登记金额</dt><dd>{{ check.currency || 'CNY' }} {{ check.requestedAmount }}</dd></div>
      <div v-if="check.availableAmount != null"><dt>{{ isCredit ? '账本可用额度' : '账本可用余额' }}</dt><dd>{{ check.currency || 'CNY' }} {{ check.availableAmount }}</dd></div>
      <div v-if="check.shortfall != null"><dt>不足金额</dt><dd>{{ check.currency || 'CNY' }} {{ check.shortfall }}</dd></div>
    </dl>
    <ul v-if="reasons.length" class="reasons"><li v-for="message in reasons" :key="message">{{ message }}</li></ul>
    <p v-if="check && !gate.fresh" class="stale" role="status">核验已过期，请重新核验；不会再次请求 AI。</p>
    <p v-if="check" class="source">依据本系统账本 · {{ checkedTime }}<span v-if="check.coverage === 'incomplete'"> · 数据不完整</span></p>
    <button v-if="!editable && !gate.fresh" class="read-refresh" type="button" :disabled="disabled" @click="emit('recheck')">重新核验</button>
    </template>
    <form v-if="editable" class="form" @submit.prevent="checkNow">
      <p v-if="sourceEnded" class="stale" role="status">这份待补候选已过期，请继续补充原交易，重新核验账户。</p>
      <button v-if="sourceEnded" type="button" class="read-refresh" :disabled="disabled || checking" @click="emit('resume')">继续补充原交易</button>
      <div class="grid">
        <label>收支方向<select v-model="form.direction" :disabled="disabled || factsLocked" @change="directionChanged"><option :value="null">待明确</option><option :value="0">支出</option><option :value="1">收入</option></select></label>
        <label>金额（人民币）<input v-model="form.amount" aria-label="核查金额" inputmode="decimal" maxlength="40" :disabled="disabled || factsLocked" /></label>
        <label>日期<input v-model="form.transDate" type="date" :disabled="disabled || factsLocked" /></label>
        <label>分类<select v-model="form.categoryId" :disabled="disabled"><option :value="null">待选择</option><option v-for="choice in categories" :key="choice.id" :value="choice.id">{{ choice.name }}</option></select></label>
        <label class="full">资金账户<select v-model="form.accountId" aria-label="核查账户" :disabled="disabled"><option :value="null">待选择</option><option v-for="choice in accounts" :key="choice.id" :value="choice.id">{{ choice.name }}</option></select></label>
        <label class="full">支付方式<select v-model="form.payMethod" :disabled="disabled"><option :value="null">待选择</option><option v-for="method in methods" :key="method" :value="method">{{ method }}</option></select></label>
        <label class="full">备注<input v-model="form.remark" maxlength="255" :disabled="disabled" /></label>
      </div>
      <div class="actions">
        <button type="submit" :disabled="disabled || checking || sourceEnded">{{ checking ? '核验中…' : '重新核验' }}</button>
        <button type="button" class="cancel" :disabled="disabled || checking" @click="cancel">取消</button>
        <button v-if="createEnabled" type="button" class="primary" :disabled="disabled || checking || sourceEnded || !gate.ready || missingFields.length > 0" @click="create">生成记账草稿</button>
      </div>
      <p v-if="missingFields.length" class="footnote" role="status">还需补齐{{ missingFields.join('、') }}。</p>
      <p v-if="factsLocked" class="footnote">本次只补账户、分类和支付方式，保留原交易的金额与日期。</p>
      <p class="footnote">补充和核验仅查询本站数据，不调用 AI；生成草稿不会入账。</p>
    </form>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </section>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { preflightAiTransaction } from '@/utils/api/ai';
import { getAiTransactionFields, getAiMissingTransactionFields, getAiFinancialGate, getAiFinancialReasons, createAiPreflightController, aiPreflightKey } from '@/utils/aiFinancialCheck';

const props = defineProps({ financialCheck: Object, fields: { type: Object, default: () => ({}) },
  choices: { type: Object, default: () => ({ accounts: [], categories: [] }) }, conversationId: String,
  sourceCandidateId: String, sourceCandidateExpiresAt: Number, disabled: Boolean, factsLocked: Boolean, editable: { type: Boolean, default: true }, createEnabled: { type: Boolean, default: true } });
const emit = defineEmits(['checked', 'create', 'cancel', 'working', 'recheck', 'resume']);
const form = reactive(getAiTransactionFields(props.fields));
const check = ref(props.financialCheck || null), currentChoices = ref(props.choices);
const latest = ref(null), checking = ref(false), error = ref(''), clock = ref(Date.now());
const sourceExpiredError = ref(false);
const sourceEnded = computed(() => props.factsLocked && (sourceExpiredError.value || Number.isFinite(props.sourceCandidateExpiresAt) && props.sourceCandidateExpiresAt <= clock.value));
watch(() => props.sourceCandidateExpiresAt, () => { sourceExpiredError.value = false; });
let live = true, clockTimer;
const methods = ['现金', '余额', '支付宝', '微信', '银行卡'];
const gate = computed(() => getAiFinancialGate(check.value, clock.value, form));
const reasons = computed(() => getAiFinancialReasons(check.value));
const statusLabel = computed(() => checking.value ? '核验中' : !check.value ? '请补充并核验' :
  ({ pass: '核验通过', warn: '请核对', needs_input: '待补充', block: '暂不可生成' }[check.value.status] || '待核验'));
const isCredit = computed(() => check.value?.accountType === 'credit');
const checkedTime = computed(() => {
  const value = check.value?.asOf, date = new Date(value);
  return Number.isNaN(date.getTime()) ? '时间待核验' : date.toLocaleString('zh-CN', { hour12: false });
});
// 2026-11-10 C2：折叠单行的摘要（仅 readonly 且通过时展示）
const collapsed = ref(true);
const collapsible = computed(() => !props.editable && Boolean(check.value) && gate.value.fresh && check.value.status === 'pass' && reasons.value.length === 0);
const shortFacts = computed(() => {
  const c = check.value; if (!c) return '';
  const money = value => value != null ? `${c.currency || 'CNY'} ${value}` : '';
  if (c.requestedAmount != null && c.availableAmount != null) return `拟登记 ${money(c.requestedAmount)} · 可用 ${money(c.availableAmount)}`;
  if (c.availableAmount != null) return `可用 ${money(c.availableAmount)}`;
  if (c.requestedAmount != null) return `拟登记 ${money(c.requestedAmount)}`;
  return '已通过核验';
});
const shortTime = computed(() => {
  const date = check.value?.asOf ? new Date(check.value.asOf) : null;
  return date && !Number.isNaN(date.getTime()) ? `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}` : '';
});
const categories = computed(() => (currentChoices.value.categories || []).filter(choice => choice.type === (form.direction === 1 ? 'income' : 'expense')));
const accounts = computed(() => (currentChoices.value.accounts || []).filter(choice => form.direction !== 1 || choice.type !== 'credit'));
const missingFields = computed(() => getAiMissingTransactionFields(form,currentChoices.value));
function options() {
  return { fields: getAiTransactionFields(form), ...(props.conversationId ? { conversationId: props.conversationId } : {}),
    ...(props.sourceCandidateId ? { sourceCandidateId: props.sourceCandidateId } : {}) };
}
let lastKey = aiPreflightKey(options());
const controller = createAiPreflightController(preflightAiTransaction, { onBusy(value) {
  if (!live) return;
  checking.value = value; emit('working', value);
} });
watch(() => props.financialCheck, value => { check.value = value || null; });
watch(() => props.choices, value => { currentChoices.value = value; });
watch(() => props.fields, value => { Object.assign(form,getAiTransactionFields(value));lastKey=aiPreflightKey(options()); });
watch(() => aiPreflightKey(options()), key => {
  if (!props.editable || key === lastKey || !live) return;
  lastKey = key; check.value = null; latest.value = null; error.value = '';
  void runCheck(false);
});
function directionChanged() {
  if (!categories.value.some(choice => choice.id === form.categoryId)) form.categoryId = null;
  if (!accounts.value.some(choice => choice.id === form.accountId)) form.accountId = null;
}
async function runCheck(immediate) {
  if (sourceEnded.value) return null;
  error.value = '';
  try {
    const result = await controller.schedule(options(), { immediate });
    if (!result || !live) return null;
    latest.value = result; check.value = result.financialCheck; currentChoices.value = result.choices;
    // Canonical server fields update the form once; this response does not cause a new request.
    Object.assign(form, getAiTransactionFields(result.fields));
    lastKey = aiPreflightKey(options());
    clock.value = Date.now();
    emit('checked', result);
    await nextTick();
    return result;
  } catch (exception) {
    if (['AI_CANDIDATE_EXPIRED', 'AI_CANDIDATE_ENDED'].includes(exception?.code)) sourceExpiredError.value = true;
    if (live) { check.value = null; latest.value = null; error.value = exception?.message || '核验暂时失败，请手动重试。'; }
    return null;
  }
}
function checkNow() { if (!props.disabled && !checking.value && !sourceEnded.value) void runCheck(true); }
function create() {
  clock.value = Date.now();
  if (props.disabled || checking.value || sourceEnded.value || !gate.value.ready || missingFields.value.length) return;
  emit('create', { fields: getAiTransactionFields(latest.value?.fields || form),
    ...(props.sourceCandidateId ? { sourceCandidateId: props.sourceCandidateId } : {}) });
}
function cancel() { controller.cancel(); emit('cancel'); }
onMounted(() => { clockTimer = setInterval(() => { clock.value = Date.now(); }, 15000); });
onBeforeUnmount(() => { live = false; clearInterval(clockTimer); controller.dispose(); emit('working',false); });
</script>

<style scoped>
.financial-card{margin-top:var(--ai-card-gap,6px);padding:var(--ai-card-padding,10px);border:1px solid var(--theme-border);border-radius:10px;background:var(--theme-bg-secondary);min-width:0;font-size:var(--ai-body-size,11px);line-height:1.55}
button,input,select,textarea,summary,a{font:inherit}
button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible,summary:focus-visible,a:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}
button:disabled,input:disabled,select:disabled,textarea:disabled{opacity:.5;cursor:default}
input,select,textarea{box-sizing:border-box;width:100%;min-width:0;border:1px solid var(--theme-border);border-radius:7px;padding:6px 8px;background:var(--theme-bg-primary);color:var(--theme-text-primary);font-size:var(--ai-body-size,11px);min-height:34px}
input[type=checkbox]{width:18px;height:18px;min-height:0;padding:0;accent-color:var(--van-primary-color)}
.actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:var(--ai-card-gap,6px);margin-top:var(--ai-card-gap,6px)}
.actions button,.read-refresh,.original-link{box-sizing:border-box;min-height:34px;border:1px solid var(--theme-border);border-radius:7px;padding:5px 9px;background:var(--theme-bg-primary);color:var(--theme-text-primary);font-size:var(--ai-body-size,11px);cursor:pointer;text-decoration:none;line-height:1.4}
.actions .primary{background:var(--van-primary-color);border-color:var(--van-primary-color);color:#fff}
@media(max-width:600px){.actions button,.read-refresh,.original-link{min-height:44px;min-width:44px;display:inline-flex;align-items:center;justify-content:center;padding:5px 9px}input,select,textarea{font-size:16px}input:not([type=checkbox]),select{min-height:44px}textarea{line-height:1.5}}

.heading{display:flex;justify-content:space-between;align-items:center;gap:var(--ai-card-gap,6px);font-size:var(--ai-title-size,11px)}.heading strong{min-width:0;overflow-wrap:anywhere}.heading span{font-size:var(--ai-label-size,10px);color:var(--van-primary-color);flex-shrink:0}
.facts{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--ai-card-gap,6px);margin:var(--ai-card-gap,6px) 0 0;font-size:var(--ai-body-size,11px)}.facts>div{min-width:0}.facts dt{font-size:var(--ai-label-size,10px);color:var(--theme-text-secondary);margin-bottom:2px}.facts dd{margin:0;overflow-wrap:anywhere;font-weight:550;font-variant-numeric:tabular-nums}
.reasons{padding-left:15px;margin:var(--ai-card-gap,6px) 0 0;color:var(--theme-text-primary);font-size:var(--ai-body-size,11px);line-height:1.55}.reasons li+li{margin-top:3px}
.source,.footnote{font-size:var(--ai-label-size,10px);color:var(--theme-text-secondary);line-height:1.55;margin:var(--ai-card-gap,6px) 0 0;overflow-wrap:anywhere}
.stale,.error{font-size:var(--ai-body-size,11px);color:var(--van-danger-color);line-height:1.55;margin:var(--ai-card-gap,6px) 0 0;overflow-wrap:anywhere}
.form{margin-top:var(--ai-card-gap,6px)}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--ai-card-gap,6px)}.grid label{display:flex;flex-direction:column;gap:3px;font-size:var(--ai-label-size,10px);min-width:0;color:var(--theme-text-secondary)}.grid .full{grid-column:1/-1}
.actions .cancel{background:transparent;border-color:transparent;color:var(--theme-text-secondary)}.read-refresh{display:flex;margin:var(--ai-card-gap,6px) 0 0 auto}
.check-line{display:flex;align-items:center;gap:6px;width:100%;min-height:34px;margin:var(--ai-card-gap,6px) 0 0;padding:6px 8px;border:1px solid var(--theme-border);border-radius:8px;background:var(--theme-bg-primary);color:var(--theme-text-secondary);font:inherit;font-size:var(--ai-body-size,11px);cursor:pointer;text-align:left}
.check-line .cl-icon{flex-shrink:0;color:var(--van-success-color,#07c160)}
.check-line .cl-t{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.check-line .cl-tg{flex-shrink:0;font-size:var(--ai-label-size,10px)}
.check-line .cl-chev{flex-shrink:0;font-size:10px;color:var(--theme-text-tertiary)}
@media(max-width:600px){.check-line{min-height:44px}}
.block .heading span{color:var(--van-danger-color)}.warn .heading span{color:var(--van-primary-color)}
</style>
