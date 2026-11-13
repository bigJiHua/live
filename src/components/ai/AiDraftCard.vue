<template>
  <section class="draft-card" :class="{ inactive: compact }" aria-label="记账草稿">
    <div class="card-heading"><span><van-icon name="notes-o" /> 记账草稿</span><span class="status" role="status">{{ statusLabel }}</span></div>
    <div v-if="compact && !detailsOpen" class="compact-summary"><span>{{ draft.direction === 0 ? '支出' : '收入' }} <strong>¥ {{ draft.amount }}</strong></span><span>{{ draft.categoryName || '未选分类' }} · {{ draft.transDate }}</span></div>
    <p v-if="compact && !detailsOpen && draft.status === 'committed'" class="compact-receipt">回执 {{ draft.committedRecordId || '' }} · <router-link class="flow-link" to="/finance/flow">在流水中查看</router-link></p>
    <template v-if="!compact || detailsOpen">
    <div class="amount"><span>{{ draft.direction === 0 ? '支出' : '收入' }}</span><strong>¥ {{ draft.amount }}</strong></div>
    <template v-if="!editing || compact">
      <dl class="fields"><div><dt>日期</dt><dd>{{ draft.transDate }}</dd></div><div><dt>分类</dt><dd>{{ draft.categoryName || '待选择' }}</dd></div><div><dt>账户</dt><dd>{{ draft.accountName || '待选择' }}</dd></div><div><dt>支付方式</dt><dd>{{ draft.payMethod || '待选择' }}</dd></div></dl>
      <p v-if="draft.remark" class="remark">{{ draft.remark }}</p>
      <p v-if="draft.missingFields.length && !expired && draft.status !== 'discarded'" class="missing">请补齐{{ draft.missingFields.join('、') }}后核对。</p>
      <AiFinancialCheckCard v-if="draft.status !== 'committed' && draft.status !== 'discarded'" :financial-check="financialCheck" :fields="draft" :editable="false" :disabled="locked" @recheck="refreshCheck" />
      <p v-if="draft.reviewInvalid || financialCheck?.reviewValid === false && draft.status === 'reviewed'" class="missing">资金或风险条件已变化，请重新核对。</p>
      <div v-if="needsNewReview && financialGate.ready" class="risk-confirmation">
        <label v-if="financialGate.requiresAcknowledgement"><input v-model="riskAccepted" type="checkbox" :disabled="locked" />我已核对本笔金额、账户及上述风险提示</label>
        <label v-if="financialGate.requiresAmountConfirmation">再次输入完整金额（元）<input v-model="confirmedAmount" aria-label="再次确认草稿金额" inputmode="decimal" maxlength="20" :disabled="locked" /></label>
      </div>
      <div v-if="!expired && draft.status !== 'discarded' && draft.status !== 'committed'" class="actions">
        <button :disabled="locked" @click="beginEdit"><van-icon name="edit" /> 修改</button>
        <button class="cancel" :disabled="locked" @click="act('discard')">取消草稿</button>
        <button v-if="needsNewReview" class="primary" :disabled="locked || draft.missingFields.length > 0 || !canReview" @click="act('review')">{{ draft.status === 'reviewed' ? '重新核对' : '核对完成' }}</button>
        <button v-if="draft.status === 'reviewed' && !needsNewReview" class="commit" :disabled="locked || !financialGate.ready" @click="commit">{{ committing ? '入账中…' : '去入账' }}</button>
      </div>
    </template>
    <form v-else class="edit-form" @submit.prevent="save">
      <div class="form-grid">
        <label>收支方向<select v-model="form.direction" :disabled="locked" @change="directionChanged"><option :value="0">支出</option><option :value="1">收入</option></select></label>
        <label>金额（元）<input v-model="form.amount" aria-label="草稿金额" inputmode="decimal" maxlength="15" :disabled="locked" required /></label>
        <label>日期<input v-model="form.transDate" type="date" :disabled="locked" required /></label>
        <label>分类<select v-model="form.categoryId" :disabled="locked"><option :value="null">待选择</option><option v-for="choice in categories" :key="choice.id" :value="choice.id">{{ choice.name }}</option></select></label>
        <label class="full">账户<select v-model="form.accountId" :disabled="locked"><option :value="null">待选择</option><option v-for="choice in accounts" :key="choice.id" :value="choice.id">{{ choice.name }}</option></select></label>
        <label class="full">支付方式<select v-model="form.payMethod" :disabled="locked"><option :value="null">待选择</option><option v-for="method in methods" :key="method" :value="method">{{ method }}</option></select></label>
        <label class="full">备注<input v-model="form.remark" maxlength="255" :disabled="locked" /></label>
      </div>
      <AiFinancialCheckCard :financial-check="financialCheck" :fields="form" :editable="false" :disabled="locked || editChecking" @recheck="checkEditedFields()" />
      <div class="actions"><button type="button" :disabled="locked" @click="returnToCard">返回卡片</button><button type="submit" class="primary" :disabled="locked || editChecking || !financialGate.ready">{{ busy ? '保存中…' : editChecking ? '核验中…' : '保存修改' }}</button></div>
    </form>
    <p v-if="notice && !error && !compact" class="notice" role="status">{{ notice }}</p>
    <div class="card-footer"><span>{{ draft.status === 'committed' ? '回执 ' + (draft.committedRecordId || '') + ' · ' : '' }}<router-link v-if="draft.status === 'committed'" class="flow-link" to="/finance/flow">在流水中查看</router-link><template v-if="draft.status !== 'committed'">{{ compact ? '未入账' : '未入账 · 核对不会改变余额' }}</template></span><span v-if="!compact">{{ display.validityLabel }}</span></div>
    </template>
    <button v-if="compact" class="detail-toggle" :aria-expanded="detailsOpen" @click="detailsOpen = !detailsOpen">{{ detailsOpen ? '收起详情' : '查看详情' }}<van-icon :name="detailsOpen ? 'arrow-up' : 'arrow-down'" /></button>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </section>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { showConfirmDialog } from 'vant';
import { editAiDraft, actOnAiDraft, getAiDraft, commitAiDraft, preflightAiTransaction } from '@/utils/api/ai';
import { getAiDraftDisplay } from '@/utils/aiDraftState';
import AiFinancialCheckCard from '@/components/ai/AiFinancialCheckCard.vue';
import { getAiFinancialGate, getAiRiskAcknowledgement, getAiTransactionFields, getAiMissingTransactionFields, createAiPreflightController, aiPreflightKey } from '@/utils/aiFinancialCheck';
const props = defineProps({ draft: { type: Object, required: true }, disabled: Boolean });
const emit = defineEmits(['change', 'working']);
const editing = ref(false);
const detailsOpen = ref(false);
const notice = ref('');
const busy = ref(false);
const committing = ref(false);
const error = ref('');
const clock = ref(Date.now());
const financialCheck = ref(props.draft.financialCheck || null);
const riskAccepted = ref(false), confirmedAmount = ref(''), editChecking = ref(false);
const financialGate = computed(() => getAiFinancialGate(financialCheck.value, clock.value, props.draft));
const riskAcknowledgement = computed(() => getAiRiskAcknowledgement(financialCheck.value, riskAccepted.value, confirmedAmount.value, clock.value, props.draft));
const canReview = computed(() => Boolean(riskAcknowledgement.value) && props.draft.missingFields.length===0 &&
  getAiMissingTransactionFields(props.draft,props.draft.choices).length===0);
const needsNewReview = computed(() => props.draft.status === 'pending' || props.draft.reviewInvalid === true ||
  (props.draft.status === 'reviewed' && financialCheck.value?.reviewValid === false));
let timer;
let live = true, lastEditKey = '';
const display = computed(() => getAiDraftDisplay(props.draft, clock.value));
const expired = computed(() => display.value.expired);
// 2026-11-10 C1 终态统一紧凑：已入账与已取消/已过期一样折叠为一行（回执与流水入口在收起态常驻），
// 字段与核查卡收进「查看详情」。
const compact = computed(() => props.draft.status === 'discarded' || props.draft.status === 'committed' || expired.value);
const locked = computed(() => props.disabled || busy.value || committing.value || expired.value || props.draft.status === 'discarded' || props.draft.status === 'committed');
const statusLabel = computed(() => display.value.statusLabel);
const methods = ['现金', '余额', '支付宝', '微信', '银行卡'];
const form = reactive({});
const categories = computed(() => props.draft.choices.categories.filter(choice => choice.type === (form.direction === 0 ? 'expense' : 'income')));
const accounts = computed(() => props.draft.choices.accounts.filter(choice => form.direction === 0 || choice.type !== 'credit'));
const editChecks = createAiPreflightController(preflightAiTransaction, { onBusy(value) { if (live) { editChecking.value = value; emit('working', value); } } });
watch(() => [props.draft.id, props.draft.version, props.draft.financialCheck?.fieldsDigest, props.draft.financialCheck?.riskDigest].join('|'), () => {
  financialCheck.value = props.draft.financialCheck || null; riskAccepted.value = false; confirmedAmount.value = '';
});
watch(() => props.draft.financialCheck, value => { if (!editing.value) financialCheck.value = value || null; });
watch(() => editing.value ? aiPreflightKey(getAiTransactionFields(form)) : '', key => {
  if (!key || key === lastEditKey || !live) return;
  lastEditKey = key; financialCheck.value = null; riskAccepted.value = false; confirmedAmount.value = '';
  void checkEditedFields(false);
});
function beginEdit() {
  if (locked.value) return;
  const { direction, amount, transDate, categoryId, accountId, payMethod, remark } = props.draft;
  Object.assign(form, { direction, amount, transDate, categoryId, accountId, payMethod, remark });
  lastEditKey = aiPreflightKey(getAiTransactionFields(form));
  editing.value = true; error.value = ''; notice.value = '';
}
function returnToCard() { editChecks.cancel(); editing.value = false; error.value = ''; financialCheck.value = props.draft.financialCheck || null; }
async function checkEditedFields(immediate = true) {
  if (!editing.value || !live) return;
  try {
    const result = await editChecks.schedule({ fields: getAiTransactionFields(form),
      ...(props.draft.conversationId ? { conversationId: props.draft.conversationId } : {}) }, { immediate: immediate === true });
    if (result && live && editing.value) { financialCheck.value = result.financialCheck; clock.value = Date.now(); error.value = ''; }
  } catch (exception) { if (live) { financialCheck.value = null; error.value = exception?.message || '金额或账户暂时无法核验。'; } }
}
async function refreshCheck() {
  clock.value = Date.now(); if (locked.value) return;
  busy.value = true; emit('working', true); error.value = '';
  try { const current = await getAiDraft(props.draft.id); financialCheck.value = current.financialCheck || null; emit('change', current); }
  catch (exception) { await handleError(exception); }
  finally { busy.value = false; emit('working', false); }
}
function directionChanged() {
  if (!categories.value.some(choice => choice.id === form.categoryId)) form.categoryId = null;
  if (!accounts.value.some(choice => choice.id === form.accountId)) form.accountId = null;
}
async function handleError(exception) {
  const previousVersion=props.draft.version;
  error.value = exception?.message || '草稿操作失败，请重试';
  // Refresh server state on errors, but never resubmit stale edits automatically.
  try {
    const current = await getAiDraft(props.draft.id);
    financialCheck.value = current.financialCheck || null; emit('change', current);
    if (current.version !== previousVersion) {
      editing.value = false;
      error.value = '草稿已变化，已加载最新版本，请重新核对后操作。';
    }
  } catch (_) { clock.value = Date.now(); }
}
async function save() {
  clock.value = Date.now(); if (locked.value || editChecking.value || !financialGate.value.ready || getAiMissingTransactionFields(form,props.draft.choices).length) return;
  busy.value = true; emit('working', true); error.value = ''; notice.value = '';
  try {
    const updated = await editAiDraft(props.draft, { ...form, amount: String(form.amount).trim(), currency: 'CNY' });
    emit('change', updated); editing.value = false;
    notice.value = '修改已保存';
  } catch (exception) { await handleError(exception); }
  finally { busy.value = false; emit('working', false); }
}
async function act(action) {
  clock.value = Date.now(); if (locked.value) return;
  if (action === 'review' && !canReview.value) return;
  busy.value = true; emit('working', true); error.value = ''; notice.value = '';
  try {
    emit('change', await actOnAiDraft(props.draft, action, action === 'review' ? riskAcknowledgement.value : undefined));
  } catch (exception) { await handleError(exception); }
  finally { busy.value = false; emit('working', false); }
}
// 追加163：去入账——二次确认回显全部字段，确认后仅携带事务 ID 由后端全权入库
async function commit() {
  clock.value = Date.now(); if (locked.value || props.draft.status !== 'reviewed' || needsNewReview.value || !financialGate.value.ready) return;
  committing.value = true; emit('working', true);
  try {
    await showConfirmDialog({
      title: '确认入账',
      message: `¥${props.draft.amount} ${props.draft.direction === 0 ? '支出' : '收入'} · ${props.draft.categoryName || '未分类'}\n${props.draft.accountName || '未选账户'} · ${props.draft.payMethod} · ${props.draft.transDate}\n\n入账后立即生效并计入收支统计，确认执行？`,
      confirmButtonText: '确认入账', cancelButtonText: '再想想',
    });
  } catch { committing.value = false; emit('working', false); return; }
  committing.value = true; emit('working', true); error.value = ''; notice.value = '';
  try {
    const committed = await commitAiDraft(props.draft);
    emit('change', committed);
  } catch (exception) {
    error.value = exception?.message || '入账失败，请重试';
    try {
      const previousVersion=props.draft.version;
      const current = await getAiDraft(props.draft.id);
      financialCheck.value = current.financialCheck || null; emit('change', current);
      if (current.version !== previousVersion) error.value = '草稿状态已变化，已加载最新版本。';
    } catch { clock.value = Date.now(); }
  }
  finally { committing.value = false; emit('working', false); }
}
onMounted(() => { timer = setInterval(() => { clock.value = Date.now(); }, 15000); });
onBeforeUnmount(() => { live = false; clearInterval(timer); editChecks.dispose(); emit('working',false); });
</script>

<style scoped>
.draft-card{margin-top:var(--ai-card-gap,6px);padding:var(--ai-card-padding,10px);border:1px solid var(--theme-border);border-radius:10px;background:var(--theme-bg-secondary);min-width:0;font-size:var(--ai-body-size,11px);line-height:1.55}
button,input,select,textarea,summary,a{font:inherit}
button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible,summary:focus-visible,a:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}
button:disabled,input:disabled,select:disabled,textarea:disabled{opacity:.5;cursor:default}
input,select,textarea{box-sizing:border-box;width:100%;min-width:0;border:1px solid var(--theme-border);border-radius:7px;padding:6px 8px;background:var(--theme-bg-primary);color:var(--theme-text-primary);font-size:var(--ai-body-size,11px);min-height:34px}
input[type=checkbox]{width:18px;height:18px;min-height:0;padding:0;accent-color:var(--van-primary-color)}
.actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:var(--ai-card-gap,6px);margin-top:var(--ai-card-gap,6px)}
.actions button,.read-refresh,.original-link{box-sizing:border-box;min-height:34px;border:1px solid var(--theme-border);border-radius:7px;padding:5px 9px;background:var(--theme-bg-primary);color:var(--theme-text-primary);font-size:var(--ai-body-size,11px);cursor:pointer;text-decoration:none;line-height:1.4}
.actions .primary{background:var(--van-primary-color);border-color:var(--van-primary-color);color:#fff}
@media(max-width:600px){.actions button,.read-refresh,.original-link{min-height:44px;min-width:44px;display:inline-flex;align-items:center;justify-content:center;padding:5px 9px}input,select,textarea{font-size:16px}input:not([type=checkbox]),select{min-height:44px}textarea{line-height:1.5}}

.card-heading{display:flex;justify-content:space-between;align-items:center;gap:var(--ai-card-gap,6px);font-size:var(--ai-title-size,11px)}
.card-heading>span{min-width:0;overflow-wrap:anywhere}.card-heading .van-icon{color:var(--van-primary-color);margin-right:3px}
.status{flex-shrink:0;font-size:var(--ai-label-size,10px);background:var(--van-primary-color-light);color:var(--van-primary-color);padding:2px 5px;border-radius:5px;white-space:nowrap}
.amount{display:flex;align-items:baseline;gap:var(--ai-card-gap,6px);margin:var(--ai-card-gap,6px) 0}.amount span{color:var(--theme-text-secondary);font-size:var(--ai-label-size,10px)}
.amount strong{font-size:var(--ai-money-size,22px);font-weight:650;line-height:1.25;font-variant-numeric:tabular-nums;min-width:0;overflow-wrap:anywhere}
.fields{margin:0;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--ai-card-gap,6px);font-size:var(--ai-body-size,11px)}
.fields>div{min-width:0}.fields dt{color:var(--theme-text-secondary);font-size:var(--ai-label-size,10px);margin-bottom:2px}.fields dd{margin:0;overflow-wrap:anywhere;line-height:1.5}
.remark,.missing,.notice,.error{font-size:var(--ai-body-size,11px);line-height:1.55;white-space:pre-wrap;overflow-wrap:anywhere;margin:var(--ai-card-gap,6px) 0 0}
.remark{color:var(--theme-text-secondary)}.missing,.notice{color:var(--van-primary-color)}.error{color:var(--van-danger-color)}
.actions .commit{background:var(--van-success-color,#07c160);border-color:var(--van-success-color,#07c160);color:#fff}
.actions .cancel{background:transparent;border-color:transparent;color:var(--theme-text-secondary)}
.committed-badge{display:inline-flex;align-items:center;gap:4px;background:var(--van-success-color,#07c160);color:#fff;border-radius:7px;padding:5px 9px;font-size:var(--ai-body-size,11px)}
.committed-badge .van-icon{font-size:12px}.flow-link{color:var(--van-primary-color);text-decoration:none}
.card-footer{display:flex;flex-wrap:wrap;justify-content:space-between;gap:3px var(--ai-card-gap,6px);border-top:1px solid var(--theme-border);margin-top:var(--ai-card-gap,6px);padding-top:var(--ai-card-gap,6px);font-size:var(--ai-label-size,10px);color:var(--theme-text-secondary);line-height:1.5}
.card-footer>span{min-width:0;overflow-wrap:anywhere}.inactive .amount,.inactive .fields{opacity:1}
.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--ai-card-gap,6px)}.form-grid label{display:flex;flex-direction:column;gap:3px;min-width:0;font-size:var(--ai-label-size,10px);color:var(--theme-text-secondary)}.form-grid .full{grid-column:1/-1}
.edit-form{margin-top:var(--ai-card-gap,6px)}.risk-confirmation{display:flex;flex-direction:column;gap:var(--ai-card-gap,6px);margin-top:var(--ai-card-gap,6px);font-size:var(--ai-body-size,11px);line-height:1.55}
.risk-confirmation label{display:flex;align-items:center;gap:var(--ai-card-gap,6px);min-height:34px}.risk-confirmation label:has(input:not([type=checkbox])){flex-direction:column;align-items:stretch}.risk-confirmation input[type=checkbox]{flex-shrink:0;margin:0}
.compact-summary{display:flex;flex-direction:column;gap:3px;margin-top:var(--ai-card-gap,6px);color:var(--theme-text-secondary);font-size:var(--ai-body-size,11px);line-height:1.5}.compact-summary span{min-width:0;overflow-wrap:anywhere}
.compact-summary strong{color:var(--theme-text-primary);font-size:calc(var(--ai-money-size,22px) * .8);font-variant-numeric:tabular-nums;margin-left:4px}
.compact-receipt{margin:3px 0 0;font-size:var(--ai-label-size,10px);color:var(--theme-text-secondary);line-height:1.5}
.detail-toggle{display:flex;align-items:center;gap:4px;margin-top:2px;min-height:24px;padding:2px 0;border:0;background:none;color:var(--van-primary-color);font-size:var(--ai-label-size,10px);cursor:pointer}
.detail-toggle .van-icon{font-size:var(--ai-label-size,10px)}
@media(max-width:600px){.detail-toggle{min-height:26px}.risk-confirmation label{min-height:44px}}
</style>
