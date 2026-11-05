<template>
  <van-popup :show="show" round teleport="body" :close-on-click-overlay="!busy" class="ai-settings-popup" @update:show="close">
    <section role="dialog" aria-modal="true" aria-labelledby="ai-settings-title" class="settings-dialog" @keydown.esc.prevent="close">
      <div class="heading"><div><h2 id="ai-settings-title">连接设置</h2><p>DeepSeek</p></div><button aria-label="关闭设置" :disabled="busy" @click="close"><van-icon name="cross" /></button></div>
      <p v-if="loading" class="note" role="status">正在读取设置…</p>
      <form v-else @submit.prevent="save">
        <label>API 地址<input ref="apiInput" v-model="apiUrl" type="url" autocomplete="off" spellcheck="false" :disabled="busy || !profile?.enabled" required /></label>
        <label>API Key <span v-if="profile?.configured" class="configured">已设置</span><input v-model="apiKey" type="password" autocomplete="off" spellcheck="false" :placeholder="profile?.configured ? '留空使用当前 Key，填写则替换' : '输入 DeepSeek API Key'" :disabled="busy || !profile?.enabled" maxlength="256" /></label>
        <label>模型<select v-model="model" :disabled="busy || !profile?.enabled"><option v-for="item in profile?.models || ['deepseek-flash', 'deepseek-v4-pro']" :key="item" :value="item">{{ item }}</option></select></label>
        <p class="note">Key 临时存于后端内存，1小时后失效，服务重启后清除。不写入浏览器存储或代码仓库。</p>
        <p class="data-source">账本数据：{{ dataSource === 'synthetic' ? '测试样例' : '当前用户的只读账本' }}</p>
        <p v-if="profile && !profile.enabled" class="error">后端尚未开启临时连接设置。</p>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <div class="actions"><button type="submit" class="primary" :disabled="busy || !profile?.enabled || (!apiKey.trim() && !profile?.configured)">{{ busy ? '正在检查连接…' : '检查连接并应用' }}</button><button v-if="profile?.configured" type="button" :disabled="busy" @click="clear">清除 Key</button></div>
      </form>
    </section>
  </van-popup>
</template>
<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from 'vue';
import { getAiSettings, configureAi, clearAiSettings } from '@/utils/api/ai';
const props = defineProps({ show: Boolean, dataSource: String });
const emit = defineEmits(['update:show', 'configured', 'working']);
const profile = ref(null);
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const apiUrl = ref('https://api.deepseek.com');
const apiKey = ref('');
const model = ref('deepseek-flash');
const apiInput = ref(null);
let previouslyFocused;
watch(() => props.show, async visible => {
  apiKey.value = ''; error.value = '';
  if (!visible) return;
  previouslyFocused = document.activeElement;
  loading.value = true;
  try {
    profile.value = await getAiSettings(); apiUrl.value = profile.value.apiUrl; model.value = profile.value.model;
  } catch (exception) { profile.value = null; error.value = exception?.message || '无法读取连接设置'; }
  finally { loading.value = false; await nextTick(); apiInput.value?.focus(); }
});
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
.ai-settings-popup{width:min(440px,calc(100vw - 28px));max-height:90dvh;background:var(--theme-bg-primary);color:var(--theme-text-primary)}.settings-dialog{padding:24px;box-sizing:border-box}.heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}.heading h2{font-size:19px;margin:0}.heading p{font-size:12px;color:var(--theme-text-secondary);margin:5px 0 0}.heading button{border:0;background:none;color:var(--theme-text-secondary);padding:8px;font-size:18px;cursor:pointer}label{display:block;font-size:12px;margin:18px 0;color:var(--theme-text-secondary)}input,select{display:block;box-sizing:border-box;width:100%;min-width:0;margin-top:8px;padding:12px;border:1px solid var(--theme-border);border-radius:9px;background:var(--theme-bg-secondary);color:var(--theme-text-primary);font:inherit;font-size:13px}input:focus,select:focus{outline:1px solid var(--van-primary-color)}.note,.data-source{font-size:11px;line-height:1.8;color:var(--theme-text-secondary)}.data-source{padding:10px 12px;border-radius:8px;background:var(--theme-bg-secondary)}.configured{color:var(--van-primary-color);float:right}.error{color:var(--van-danger-color);font-size:12px;line-height:1.7}.actions{display:flex;gap:10px;margin-top:22px}.actions button{border:1px solid var(--theme-border);background:transparent;color:var(--theme-text-primary);border-radius:9px;padding:11px 12px;font:inherit;font-size:12px;cursor:pointer}.actions .primary{background:var(--van-primary-color);color:white;border-color:var(--van-primary-color);flex:1}button:disabled{opacity:.45;cursor:default}@media(max-width:380px){.settings-dialog{padding:20px}}
</style>
