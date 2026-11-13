<template>
  <section class="budget-settings" :class="{ standalone }" :aria-labelledby="standalone ? 'ai-budget-page-title' : 'ai-budget-title'">
    <h3 v-if="!standalone" id="ai-budget-title">额度与超时</h3>
    <p class="budget-note">仅调整当前账号，所有接入商共用。保存后用于后续请求，也可恢复站点默认设置。</p>
    <p v-if="loading" role="status">正在读取设置…</p>
    <form v-else-if="settings" @submit.prevent="save(false)">
      <label v-for="field in BUDGET_FIELDS" :key="field.key" class="budget-field">
        <strong>{{ field.label }} <span>（{{ field.unit }}）</span></strong>
        <input v-model="form[field.key]" type="number" :min="settings.limits[field.key].min / field.scale"
          :max="settings.limits[field.key].max / field.scale" :step="field.scale === 1000 ? 0.001 : 1" :disabled="saving" required />
        <span class="budget-range">范围 {{ settings.limits[field.key].min / field.scale }}～{{ settings.limits[field.key].max / field.scale }} {{ field.unit }}；站点默认 {{ settings.defaults[field.key] / field.scale }}</span>
        <span class="budget-description">{{ field.description }}</span>
      </label>
      <label class="budget-field">
        <strong>每次模型输出上限 <span>（Token）</span></strong>
        <select v-model="outputMode" :disabled="saving">
          <option value="default">跟随站点默认（{{ settings.defaults.maxOutputTokens ?? '自动' }}）</option>
          <option value="max">使用当前模型最大值（max）</option>
          <option value="number">自定义数量</option>
        </select>
        <input v-if="outputMode === 'number'" v-model="outputTokens" type="number" :min="settings.limits.maxOutputTokens.min"
          :max="settings.limits.maxOutputTokens.max" step="1" :disabled="saving" aria-label="自定义输出 Token 数量" required />
        <span class="budget-range">自定义范围 {{ settings.limits.maxOutputTokens.min }}～{{ settings.limits.maxOutputTokens.max }} Token</span>
        <span class="budget-description">限制每次模型调用的输出长度。max 会按当前接入商和模型上限自动确定；自定义值也会按模型能力收窄。提高后允许更长回答，可能增加费用与等待时间；整轮累计预算和超时仍会限制后续调用。</span>
        <span v-if="settings.effectiveMaxOutputTokens != null" class="budget-range">当前接入商和模型解析后的输出上限：{{ settings.effectiveMaxOutputTokens }} Token</span>
      </label>
      <div class="budget-actions">
        <button type="button" :disabled="saving" @click="save(true)">恢复默认</button>
        <button type="submit" class="budget-save" :disabled="saving">{{ saving ? '正在保存…' : '保存额度与超时' }}</button>
      </div>
    </form>
    <p v-if="error" class="budget-error" role="alert">{{ error }}</p>
    <button v-if="!settings && !loading" type="button" @click="load">重新读取</button>
    <p v-if="notice" class="budget-success" role="status">{{ notice }}</p>
  </section>
</template>
<script setup>
import { reactive, ref, watch, onBeforeUnmount } from 'vue';
import { getAiBudgetSettings, putAiBudgetSettings } from '@/utils/api/ai';
import { BUDGET_FIELDS, BUDGET_KEYS, budgetPayload } from '@/utils/aiBudgetSettings';
const props = defineProps({ active: Boolean, standalone: Boolean });
const settings = ref(null), loading = ref(false), saving = ref(false), error = ref(''), notice = ref('');
const form = reactive({}), outputMode = ref('max'), outputTokens = ref(4096);
let revision = 0;
function apply(value) {
  settings.value = value;
  for (const field of BUDGET_FIELDS) form[field.key] = value.values[field.key] / field.scale;
  const output = value.overrides.maxOutputTokens;
  outputMode.value = output === null ? 'default' : output === 'max' ? 'max' : 'number';
  outputTokens.value = typeof value.values.maxOutputTokens === 'number' ? value.values.maxOutputTokens : 4096;
}
async function load() {
  const current = ++revision;
  loading.value = true; error.value = ''; notice.value = '';
  try { const value = await getAiBudgetSettings(); if (current === revision) apply(value); }
  catch (exception) { if (current === revision) error.value = exception?.message || '设置读取失败'; }
  finally { if (current === revision) loading.value = false; }
}
async function save(reset) {
  if (saving.value) return;
  const current = revision;
  saving.value = true; error.value = ''; notice.value = '';
  try {
    const payload = reset ? Object.fromEntries(BUDGET_KEYS.map(key => [key, null])) : budgetPayload(form, outputMode.value, outputTokens.value);
    const value = await putAiBudgetSettings(payload);
    if (current !== revision) return;
    apply(value); notice.value = reset ? '已恢复站点默认设置。' : '已保存，后续请求使用新的额度与超时。';
  } catch (exception) { if (current === revision) error.value = exception?.message || '设置保存失败，请重试'; }
  finally { if (current === revision) saving.value = false; }
}
watch(() => props.active, active => {
  if (active) { saving.value = false; void load(); }
  else { revision++; loading.value = false; saving.value = false; }
}, { immediate: true });
onBeforeUnmount(() => { revision++; });
</script>
<style scoped>
.budget-settings{margin-top:24px;padding-top:20px;border-top:1px solid var(--theme-border-color,#e5e7eb);text-align:left}
.budget-settings.standalone{margin-top:0;padding-top:0;border-top:0}
.budget-settings h3{margin:0 0 8px;font-size:16px}.budget-note,.budget-range,.budget-description{font-size:12px;line-height:1.65;color:var(--theme-text-secondary,#64748b)}
.budget-note{margin:0 0 18px}.budget-field{display:flex;flex-direction:column;gap:7px;margin-bottom:20px}.budget-field strong{font-size:14px}.budget-field strong span{font-size:12px;font-weight:400}
.budget-field input,.budget-field select{box-sizing:border-box;width:100%;min-height:44px;padding:10px 12px;font:inherit;font-size:14px;color:var(--theme-text-primary,#1f2937);background:var(--theme-bg-primary,#fff);border:1px solid var(--theme-border-color,#d1d5db);border-radius:10px}
.budget-field input:focus,.budget-field select:focus{outline:2px solid var(--theme-primary,#1989fa);outline-offset:1px}.budget-actions{display:flex;flex-wrap:wrap;gap:10px;margin-top:16px}
.budget-actions button,.budget-settings>button{min-height:44px;border:1px solid var(--theme-border-color,#d1d5db);border-radius:10px;padding:10px 14px;background:var(--theme-bg-primary,#fff);color:var(--theme-text-primary,#1f2937);font:inherit;font-size:14px;cursor:pointer}
.budget-actions .budget-save{flex:1;background:var(--theme-primary,#1989fa);border-color:var(--theme-primary,#1989fa);color:white}.budget-actions button:disabled{opacity:.5;cursor:wait}.budget-error{font-size:13px;color:var(--theme-danger,#d93025)}.budget-success{font-size:13px;color:var(--theme-success,#198754)}
</style>
