<template>
  <van-popup :show="show" round teleport="body" :close-on-click-overlay="!busy" class="ai-settings-popup" @update:show="close">
    <section role="dialog" aria-modal="true" aria-labelledby="ai-settings-title" class="settings-dialog" @keydown.esc.prevent="close">
      <div class="heading"><div><h2 id="ai-settings-title">连接设置</h2><p>DeepSeek</p></div><button aria-label="关闭设置" :disabled="busy" @click="close"><van-icon name="cross" /></button></div>
      <p v-if="loading" class="note" role="status">正在读取设置…</p>
      <form v-else @submit.prevent="save">
        <label>API 地址<span class="addr-hint">OpenAI 兼容（https，本机可用 http://localhost）</span><input ref="apiInput" v-model="apiUrl" type="url" autocomplete="off" spellcheck="false" :disabled="busy || !profile?.enabled" required /></label>
        <label>API Key <span v-if="profile?.configured" class="configured">已设置</span><input v-model="apiKey" type="password" autocomplete="off" spellcheck="false" :placeholder="profile?.configured ? '留空使用当前 Key，填写则替换' : '输入 DeepSeek API Key'" :disabled="busy || !profile?.enabled" maxlength="256" /></label>
        <label>模型（可手填，或点「获取模型」拉取厂商列表）
          <div class="model-row">
            <input v-model="model" autocomplete="off" spellcheck="false" maxlength="120" :disabled="busy || !profile?.enabled" placeholder="如 deepseek-flash / gpt-4o-mini / 自定义" />
            <button type="button" class="fetch-models" :disabled="busy || !profile?.enabled || fetchingModels" @click="fetchModels">{{ fetchingModels ? '获取中…' : '获取模型' }}</button>
          </div>
        </label>
        <p class="note">Key 临时存于后端内存，1小时后失效，服务重启后清除。不写入浏览器存储或代码仓库。</p>
        <p v-if="balance" class="data-source balance-line">
          <span>DeepSeek 余额：<strong :class="{ insufficient: !balance.isAvailable }">¥{{ balance.totalBalance }}</strong><template v-if="balance.grantedBalance !== '0.00'">（含赠送 ¥{{ balance.grantedBalance }}）</template></span>
          <span class="balance-state">{{ balance.isAvailable ? '✓ 可用' : '余额不足' }}</span>
        </p>
        <p v-else-if="balanceError" class="note balance-err">{{ balanceError }}</p>
        <p class="data-source">账本数据：{{ dataSource === 'synthetic' ? '测试样例' : '当前用户的只读账本' }}</p>
        <p v-if="profile && !profile.enabled" class="error">后端尚未开启临时连接设置。</p>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <div class="actions"><button type="submit" class="primary" :disabled="busy || !profile?.enabled || (!apiKey.trim() && !profile?.configured)">{{ busy ? '正在检查连接…' : '检查连接并应用' }}</button><button v-if="profile?.configured" type="button" :disabled="busy" @click="clear">清除 Key</button></div>
      </form>
    </section>
    <van-popup v-model:show="pickerOpen" position="bottom" round teleport="body">
      <van-picker title="选择模型（厂商列表）" :columns="modelColumns" @confirm="onModelConfirm" @cancel="pickerOpen = false" />
    </van-popup>
  </van-popup>
</template>
<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue';
import { getAiSettings, configureAi, clearAiSettings, getAiBalance, fetchAiModels } from '@/utils/api/ai';
const props = defineProps({ show: Boolean, dataSource: String });
const emit = defineEmits(['update:show', 'configured', 'working']);
const profile = ref(null);
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const balance = ref(null);
const balanceError = ref('');
const apiUrl = ref('https://api.deepseek.com');
const apiKey = ref('');
const model = ref('deepseek-flash');
const pickerOpen = ref(false);
const fetchingModels = ref(false);
const modelColumns = ref([]);
function onModelConfirm({ selectedValues }) {
  if (selectedValues?.length) model.value = selectedValues[0];
  pickerOpen.value = false;
}
// 追加163 扩展：拉取厂商模型列表（OpenAI 兼容 /models），Key 优先用输入框里的（首配场景）
async function fetchModels() {
  if (busy.value || fetchingModels.value) return;
  fetchingModels.value = true; error.value = '';
  try {
    const result = await fetchAiModels({ apiUrl: apiUrl.value.trim() || undefined,
      ...(apiKey.value.trim() ? { apiKey: apiKey.value.trim() } : {}) });
    if (!result.models.length) { error.value = '厂商未返回任何模型'; return; }
    modelColumns.value = result.models.map(item => ({ text: item, value: item }));
    pickerOpen.value = true;
  } catch (exception) { error.value = exception?.message || '模型列表获取失败'; }
  finally { fetchingModels.value = false; }
}
const apiInput = ref(null);
let previouslyFocused;
watch(() => props.show, async visible => {
  apiKey.value = ''; error.value = '';
  if (!visible) return;
  previouslyFocused = document.activeElement;
  loading.value = true;
  try {
    profile.value = await getAiSettings(); apiUrl.value = profile.value.apiUrl; model.value = profile.value.model;
    loadBalance();
  } catch (exception) { profile.value = null; error.value = exception?.message || '无法读取连接设置'; }
  finally { loading.value = false; await nextTick(); apiInput.value?.focus(); }
});
// 追加163：已配置连接时拉取 DeepSeek 官方余额（非系统账本余额），每次打开弹窗刷新
async function loadBalance() {
  balance.value = null; balanceError.value = '';
  // 追加163：跟随生效模式而非会话配置——.env 全局 Key 下 configured=false 但照样有真实余额
  if (!profile.value?.enabled) return;
  try { balance.value = await getAiBalance(); }
  catch (exception) { balanceError.value = '余额查询失败：' + (exception?.message || '未知错误'); }
}
function close() {
  if (busy.value) return;
  apiKey.value = ''; emit('update:show', false); nextTick(() => previouslyFocused?.focus?.());
}
async function save() {
  if (busy.value) return;
  busy.value = true; emit('working', true); error.value = '';
  try {
    await configureAi({ apiUrl: apiUrl.value.trim(), model: model.value,
      ...(apiKey.value.trim() ? { apiKey: apiKey.value.trim() } : {}) });
    apiKey.value = ''; emit('configured', '已连接 DeepSeek，可以开始对话。'); emit('update:show', false);
    loadBalance();
  } catch (exception) { error.value = exception?.message || '连接失败，请检查 API 和 Key'; }
  finally { busy.value = false; emit('working', false); }
}
async function clear() {
  busy.value = true; emit('working', true); error.value = '';
  try { profile.value = await clearAiSettings(); apiKey.value = ''; emit('configured', '临时 Key 已清除。'); emit('update:show', false); }
  catch (exception) { error.value = exception?.message || '清除失败'; }
  finally { busy.value = false; emit('working', false); }
}
onBeforeUnmount(() => { apiKey.value = ''; });
</script>
<style scoped>
.ai-settings-popup{width:min(440px,calc(100vw - 28px));max-height:90dvh;background:var(--theme-bg-primary);color:var(--theme-text-primary)}.settings-dialog{padding:24px;box-sizing:border-box}.heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}.heading h2{font-size:19px;margin:0}.heading p{font-size:12px;color:var(--theme-text-secondary);margin:5px 0 0}.heading button{border:0;background:none;color:var(--theme-text-secondary);padding:8px;font-size:18px;cursor:pointer}label{display:block;font-size:12px;margin:18px 0;color:var(--theme-text-secondary)}input,select{display:block;box-sizing:border-box;width:100%;min-width:0;margin-top:8px;padding:12px;border:1px solid var(--theme-border);border-radius:9px;background:var(--theme-bg-secondary);color:var(--theme-text-primary);font:inherit;font-size:13px}
.model-row{display:flex;gap:8px;align-items:stretch;margin-top:8px}.model-row input{margin-top:0;flex:1;min-width:0}.fetch-models{flex-shrink:0;border:1px solid var(--theme-border);background:var(--theme-bg-secondary);color:var(--van-primary-color);border-radius:9px;padding:0 12px;font:inherit;font-size:12px;cursor:pointer;white-space:nowrap}.fetch-models:disabled{opacity:.45;cursor:default}
.addr-hint{font-size:10px;color:var(--theme-text-secondary);float:right;font-weight:400}
.model-select{display:flex;align-items:center;justify-content:space-between;gap:8px;box-sizing:border-box;width:100%;min-width:0;margin-top:8px;padding:12px;border:1px solid var(--theme-border);border-radius:9px;background:var(--theme-bg-secondary);color:var(--theme-text-primary);font:inherit;font-size:13px;cursor:pointer;text-align:left}.model-select:disabled{opacity:.45;cursor:default}.model-select .van-icon{color:var(--theme-text-secondary)}.model-select:focus{outline:1px solid var(--van-primary-color)}input:focus,select:focus{outline:1px solid var(--van-primary-color)}.note,.data-source{font-size:11px;line-height:1.8;color:var(--theme-text-secondary)}.data-source{padding:10px 12px;border-radius:8px;background:var(--theme-bg-secondary)}.configured{color:var(--van-primary-color);float:right}.error{color:var(--van-danger-color);font-size:12px;line-height:1.7}.actions{display:flex;gap:10px;margin-top:22px}.actions button{border:1px solid var(--theme-border);background:transparent;color:var(--theme-text-primary);border-radius:9px;padding:11px 12px;font:inherit;font-size:12px;cursor:pointer}.actions .primary{background:var(--van-primary-color);color:white;border-color:var(--van-primary-color);flex:1}button:disabled{opacity:.45;cursor:default}.balance-line{display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap}.balance-line strong{font-size:14px}.balance-line strong.insufficient{color:var(--van-danger-color)}.balance-state{font-size:11px;color:var(--van-success-color,#07c160);white-space:nowrap}.balance-err{color:var(--van-danger-color)}
@media(max-width:380px){.settings-dialog{padding:20px}}
</style>
