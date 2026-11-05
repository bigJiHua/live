<template>
  <section class="draft-card" :class="{ inactive: draft.status === 'discarded' || expired }" aria-label="记账草稿">
    <div class="card-heading"><span><van-icon name="notes-o" /> 记账草稿</span><span class="status">{{ statusLabel }}</span></div>
    <div class="amount"><span>{{ draft.direction === 0 ? '支出' : '收入' }}</span><strong>¥ {{ draft.amount }}</strong></div>
    <template v-if="!editing">
      <dl class="fields"><div><dt>日期</dt><dd>{{ draft.transDate }}</dd></div><div><dt>分类</dt><dd>{{ draft.categoryName || '待选择' }}</dd></div><div><dt>账户</dt><dd>{{ draft.accountName || '待选择' }}</dd></div><div><dt>支付方式</dt><dd>{{ draft.payMethod || '待选择' }}</dd></div></dl>
      <p v-if="draft.remark" class="remark">{{ draft.remark }}</p>
      <p v-if="draft.missingFields.length && !expired && draft.status !== 'discarded'" class="missing">请补齐{{ draft.missingFields.join('、') }}后核对。</p>
      <div v-if="!expired && draft.status !== 'discarded'" class="actions">
        <button :disabled="locked" @click="beginEdit"><van-icon name="edit" /> 修改</button>
        <button class="primary" :disabled="locked || draft.missingFields.length > 0 || draft.status === 'reviewed'" @click="act('review')">{{ draft.status === 'reviewed' ? '已核对' : '核对完成' }}</button>
        <button class="cancel" :disabled="locked" @click="act('discard')">取消草稿</button>
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
      <div class="actions"><button type="submit" class="primary" :disabled="locked">{{ busy ? '保存中…' : '保存修改' }}</button><button type="button" :disabled="locked" @click="editing = false; error = ''">返回卡片</button></div>
    </form>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div class="card-footer"><span>未入账 · 核对不会改变余额</span><span>{{ expired ? '已过期' : '15分钟内有效' }}</span></div>
  </section>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue';
import { editAiDraft, actOnAiDraft, getAiDraft } from '@/utils/api/ai';
const props = defineProps({ draft: { type: Object, required: true }, disabled: Boolean });
const emit = defineEmits(['change', 'feedback', 'working']);
const editing = ref(false);
const busy = ref(false);
const error = ref('');
const clock = ref(Date.now());
let timer;
const expired = computed(() => clock.value >= props.draft.expiresAt);
const locked = computed(() => props.disabled || busy.value || expired.value || props.draft.status === 'discarded');
const statusLabel = computed(() => expired.value ? '已过期' : ({ pending: '待核对', reviewed: '已核对', discarded: '已取消' }[props.draft.status]));
const methods = ['现金', '余额', '支付宝', '微信', '银行卡'];
const form = reactive({});
const categories = computed(() => props.draft.choices.categories.filter(choice => choice.type === (form.direction === 0 ? 'expense' : 'income')));
const accounts = computed(() => props.draft.choices.accounts.filter(choice => form.direction === 0 || choice.type !== 'credit'));
function beginEdit() {
  if (locked.value) return;
  const { direction, amount, transDate, categoryId, accountId, payMethod, remark } = props.draft;
  Object.assign(form, { direction, amount, transDate, categoryId, accountId, payMethod, remark });
  editing.value = true; error.value = '';
}
function directionChanged() {
  if (!categories.value.some(choice => choice.id === form.categoryId)) form.categoryId = null;
  if (!accounts.value.some(choice => choice.id === form.accountId)) form.accountId = null;
}
async function handleError(exception) {
  error.value = exception?.message || '草稿操作失败，请重试';
  // Refresh server state on errors, but never resubmit stale edits automatically.
  try {
    const current = await getAiDraft(props.draft.id);
    if (current.version !== props.draft.version) {
      emit('change', current); editing.value = false;
      error.value = '草稿已变化，已加载最新版本，请重新核对后操作。';
    }
  } catch (_) { clock.value = Date.now(); }
}
async function save() {
  clock.value = Date.now(); if (locked.value) return;
  busy.value = true; emit('working', true); error.value = '';
  try {
    const updated = await editAiDraft(props.draft, { ...form, amount: String(form.amount).trim(), currency: 'CNY' });
    emit('change', updated); editing.value = false;
    emit('feedback', '草稿已更新，请核对卡片上的金额和账户。');
  } catch (exception) { await handleError(exception); }
  finally { busy.value = false; emit('working', false); }
}
async function act(action) {
  clock.value = Date.now(); if (locked.value) return;
  busy.value = true; emit('working', true); error.value = '';
  try {
    emit('change', await actOnAiDraft(props.draft, action));
    emit('feedback', action === 'review' ? '这份草稿已标记为核对完成，仍未入账。' : '这份草稿已取消。');
  } catch (exception) { await handleError(exception); }
  finally { busy.value = false; emit('working', false); }
}
onMounted(() => { timer = setInterval(() => { clock.value = Date.now(); }, 15000); });
onBeforeUnmount(() => clearInterval(timer));
</script>

<style scoped>
.draft-card{margin-top:16px;border:1px solid var(--theme-border);border-radius:16px;padding:18px;background:var(--theme-bg-secondary);overflow:hidden}.card-heading{display:flex;justify-content:space-between;align-items:center;gap:8px;font-size:12px}.card-heading .van-icon{color:var(--van-primary-color);margin-right:4px}.status{font-size:10px;background:var(--van-primary-color-light);color:var(--van-primary-color);padding:4px 8px;border-radius:6px;white-space:nowrap}.amount{display:flex;align-items:baseline;gap:10px;margin:20px 0 16px}.amount span{color:var(--theme-text-secondary);font-size:12px}.amount strong{font-size:30px;letter-spacing:-1px;font-variant-numeric:tabular-nums;overflow-wrap:anywhere;min-width:0}.fields{margin:0;display:grid;grid-template-columns:1fr 1fr;gap:14px 16px;font-size:12px}.fields dt{color:var(--theme-text-secondary);font-size:11px;margin-bottom:5px}.fields dd{margin:0;overflow-wrap:anywhere;line-height:1.5}.remark{font-size:12px;line-height:1.7;color:var(--theme-text-secondary);white-space:pre-wrap;overflow-wrap:anywhere;margin:16px 0 0}.missing{color:var(--van-primary-color);font-size:11px;line-height:1.7;margin:14px 0 0}.actions{display:flex;flex-wrap:wrap;gap:8px;margin-top:18px}.actions button{border:1px solid var(--theme-border);background:var(--theme-bg-primary);border-radius:8px;padding:9px 12px;color:var(--theme-text-primary);font:inherit;font-size:12px;cursor:pointer}.actions .primary{background:var(--van-primary-color);border-color:var(--van-primary-color);color:white}.actions .cancel{background:transparent;border-color:transparent;color:var(--theme-text-secondary);padding-left:4px;padding-right:0}.actions button:disabled{opacity:.45;cursor:default}.card-footer{display:flex;justify-content:space-between;gap:8px;border-top:1px solid var(--theme-border);margin-top:16px;padding-top:11px;font-size:10px;color:var(--theme-text-secondary);line-height:1.5}.inactive .amount,.inactive .fields{opacity:.6}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.form-grid label{display:flex;flex-direction:column;gap:6px;min-width:0;font-size:11px;color:var(--theme-text-secondary)}.form-grid .full{grid-column:1/-1}input,select{box-sizing:border-box;width:100%;min-width:0;padding:10px 8px;border:1px solid var(--theme-border);border-radius:8px;background:var(--theme-bg-primary);color:var(--theme-text-primary);font:inherit;font-size:13px;min-height:38px}input:focus,select:focus{outline:1px solid var(--van-primary-color)}.error{color:var(--van-danger-color);font-size:12px;line-height:1.7;margin:12px 0 0}.edit-form{margin-top:18px}@media(max-width:380px){.draft-card{padding:14px}.amount strong{font-size:27px}.actions{gap:6px}.actions button{padding:9px 10px;font-size:11px}.card-footer{font-size:9px}}
</style>
