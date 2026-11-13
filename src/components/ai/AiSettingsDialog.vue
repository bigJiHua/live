<template>
  <svg style="display:none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <symbol id="logo-deepseek" viewBox="0 0 24 24">
      <path fill="#5786FE" d="M23.748 4.651c-.254-.124-.364.113-.512.233-.051.04-.094.09-.137.137-.372.397-.806.657-1.373.626-.829-.046-1.537.214-2.163.848-.133-.782-.575-1.248-1.247-1.548-.352-.155-.708-.311-.955-.65-.172-.24-.219-.509-.305-.774-.055-.16-.11-.323-.293-.35-.2-.031-.278.136-.356.276-.313.572-.434 1.202-.422 1.84.027 1.436.633 2.58 1.838 3.393.137.094.172.187.129.323-.082.28-.18.553-.266.833-.055.179-.137.218-.328.14a5.5 5.5 0 0 1-1.737-1.179c-.857-.828-1.631-1.743-2.597-2.46a12 12 0 0 0-.689-.47c-.985-.957.13-1.743.387-1.836.27-.098.094-.433-.778-.428-.872.003-1.67.295-2.687.685a3 3 0 0 1-.465.136 9.6 9.6 0 0 0-2.883-.101c-1.885.21-3.39 1.1-4.497 2.622C.082 8.776-.231 10.854.152 13.02c.403 2.284 1.568 4.175 3.36 5.653 1.857 1.533 3.997 2.284 6.438 2.14 1.482-.085 3.132-.284 4.994-1.86.47.234.962.328 1.78.398.629.058 1.235-.031 1.705-.129.735-.155.684-.836.418-.961-2.155-1.004-1.682-.595-2.112-.926 1.095-1.295 2.768-3.598 3.284-6.733.05-.346.115-.834.108-1.114-.004-.171.035-.238.23-.257a4.2 4.2 0 0 0 1.545-.475c1.397-.763 1.96-2.016 2.093-3.517.02-.23-.004-.467-.247-.588M11.58 18.168c-2.088-1.642-3.101-2.183-3.52-2.16-.39.024-.32.472-.234.763.09.288.207.487.371.74.114.167.192.416-.113.603-.673.416-1.842-.14-1.897-.168-1.361-.801-2.5-1.86-3.301-3.306-.775-1.393-1.225-2.888-1.299-4.482-.02-.385.094-.522.477-.592a4.7 4.7 0 0 1 1.53-.038c2.131.311 3.946 1.264 5.467 2.774.868.86 1.525 1.887 2.202 2.89.72 1.066 1.494 2.082 2.48 2.915.348.291.626.513.892.677-.802.09-2.14.109-3.055-.615zm1.001-6.44a.306.306 0 0 1 .415-.287.3.3 0 0 1 .113.074.3.3 0 0 1 .086.214c0 .17-.136.307-.308.307a.303.303 0 0 1-.306-.307m3.11 1.596c-.2.081-.4.151-.591.16a1.25 1.25 0 0 1-.798-.254c-.274-.23-.47-.358-.551-.758a1.7 1.7 0 0 1 .015-.588c.07-.327-.007-.537-.238-.727-.188-.156-.426-.199-.689-.199a.6.6 0 0 1-.254-.078.253.253 0 0 1-.114-.358 1 1 0 0 1 .192-.21c.356-.202.767-.136 1.146.016.352.144.618.408 1.001.782.392.451.462.576.685.915.176.264.336.536.446.848.066.194-.02.353-.25.45"/>
    </symbol>
  </svg>
  <van-popup :show="asPage || show" :overlay="!asPage" :transition="asPage ? '' : undefined" position="right" teleport="body" class="ai-settings-popup" :style="{ width: '100%', height: '100%' }" @update:show="close">
    <!-- 2026-11-12：asPage=路由页形态（/ai/model-settings），无遮罩、show 恒真、关闭=router.back()；
         弹窗形态保留给未来非聊天入口。返回语义由 vue-router 原生保证（返回即回聊天界面） -->
    <section role="dialog" aria-modal="true" aria-labelledby="ai-settings-title" class="settings-dialog" @keydown.esc.prevent="close">
      <div class="heading">
        <div><h2 id="ai-settings-title">模型设置</h2><p>{{ headingHint }}</p></div>
        <button aria-label="关闭模型设置" :disabled="busy" @click="close"><van-icon name="cross" /></button>
      </div>
      <div class="dialog-body">
      <p v-if="loading" class="note" role="status">正在读取设置…</p>
      <template v-else>
        <!-- 2026-11-11：横滑必带提示——未滚到尽头时右缘渐隐，滚到尽头渐隐消失（负责人规矩） -->
        <div ref="tabsEl" class="provider-tabs" :class="{ 'fade-end': tabsAtEnd }" @scroll="updateTabsFade">
          <button type="button" class="provider-tab add" :class="{ on: !editingProfile }" :disabled="busy || !profile?.enabled || profiles.length >= maxProfiles" @click="openNew()">
            <van-icon name="plus" /><span>添加供应商</span>
          </button>
          <button v-for="item in profiles" :key="item.connectionId" type="button" class="provider-tab" :class="{ on: editingProfile?.connectionId === item.connectionId }" :disabled="busy || !profile?.enabled" @click="openForm(item)">
            <svg v-if="hostOf(item.apiUrl) === 'api.deepseek.com'" class="brand-logo" viewBox="0 0 24 24" aria-hidden="true"><use href="#logo-deepseek" /></svg>
            <img v-else-if="hostOf(item.apiUrl) === 'open.bigmodel.cn'" :src="zhipuLogo" class="brand-logo brand-img" alt="" />
            <span v-else class="tab-dot" :class="{ on: item.active }" />{{ displayName(item) }}
          </button>
        </div>
        <form @submit.prevent="save">
          <p v-if="!editingProfile" class="group-hint">选官方预设只填 Key，或直接填写自定义接入商：</p>
          <div v-if="!editingProfile" class="preset-row">
            <button v-for="preset in OFFICIAL_PRESETS" :key="preset.id" type="button" class="preset-btn" :class="{ on: presetLocked?.id === preset.id }" :disabled="busy || !profile?.enabled" @click="openPreset(preset)">
              <svg v-if="preset.id === 'deepseek'" class="brand-logo" viewBox="0 0 24 24" aria-hidden="true"><use href="#logo-deepseek" /></svg>
              <img v-else :src="zhipuLogo" class="brand-logo brand-img" alt="" />
              {{ preset.name }}
            </button>
          </div>
          <!-- 2026-11-11：「当前使用中」徽标钉在「名称」标签行右端（不再单独占一行） -->
          <label>
            <span class="label-head">名称<span v-if="editingProfile?.active" class="active-flag"><span class="tab-dot on" />当前使用中</span></span>
            <input v-model="form.name" maxlength="60" autocomplete="off" :disabled="busy || !profile?.enabled" placeholder="如 商汤 / DeepSeek / 本地 Ollama" required />
          </label>
          <label>API 地址<span class="addr-hint">{{ presetLocked ? '官方固定地址' : 'OpenAI 兼容' }}</span><input v-model="form.apiUrl" type="url" autocomplete="off" spellcheck="false" :disabled="busy || !profile?.enabled || Boolean(presetLocked)" required /></label>
          <label>API Key
            <input :value="apiKeyField" :type="maskedKey ? 'text' : 'password'" autocomplete="off" spellcheck="false" maxlength="256" :disabled="busy || !profile?.enabled"
              :placeholder="editingProfile ? '留空使用已保存的 Key，填写则替换' : '输入 AI 接入商 API Key'" @input="onKeyInput" @focus="onKeyFocus" />
          </label>
          <label>模型
            <div class="model-row">
              <input v-model="form.model" autocomplete="off" spellcheck="false" maxlength="120" :disabled="busy || !profile?.enabled" :placeholder="presetLocked ? '填 Key 后点「获取模型」选择' : '如 sensenova-6.8-flash / deepseek-chat / gpt-4o-mini'" />
              <button type="button" class="fetch-models" :disabled="busy || !profile?.enabled || fetchingModels" @click="fetchModels">{{ fetchingModels ? '获取中…' : '获取模型' }}</button>
            </div>
          </label>
          <div v-if="modelOptions.length" class="model-list">
            <p class="group-hint">可选模型（点击选用）</p>
            <div class="model-scroll">
              <button v-for="option in modelOptions" :key="option" type="button" class="model-option" :class="{ on: form.model === option }" :disabled="busy || !profile?.enabled" @click="form.model = option">
                <span class="model-name">{{ option }}</span>
                <van-icon v-if="form.model === option" name="success" />
              </button>
            </div>
          </div>
          <!-- 2026-11-11：余额/用量是「当前接入商」的属性，紧贴字段就近展示，不再夹在删除与日志之间 -->
          <p v-if="balance" class="data-source balance-line">
            <span>{{ balanceName }} 用量余额：<strong :class="{ insufficient: !balance.isAvailable }">¥{{ balance.totalBalance }}</strong><template v-if="balance.grantedBalance !== '0.00'">（含赠送 ¥{{ balance.grantedBalance }}）</template></span>
            <span class="balance-state">{{ balance.isAvailable ? '✓ 可用' : '余额不足' }}</span>
          </p>
          <p v-else-if="balanceError" class="note" :class="{ 'balance-err': !balanceUnsupported }">{{ balanceError }}</p>
          <div class="actions">
            <!-- 2026-11-11：「···」打开操作面板，面板内才是删除项（删除仍走二次确认） -->
            <van-popover v-if="editingProfile" v-model:show="moreOpen" placement="top-start" trigger="click" :offset="[4, 8]">
              <template #reference>
                <button type="button" class="more-button" :disabled="busy" aria-label="更多操作"><van-icon name="ellipsis" /></button>
              </template>
              <!-- @click.stop：阻断面板内点击冒泡到 popover wrapper 的 toggle（否则 moreOpen=false 会被翻回 true，面板关不掉） -->
              <div class="more-pop" @click.stop>
                <button type="button" class="more-pop-item" :disabled="busy" @click="onDeleteClick"><van-icon name="delete-o" /><span>删除该接入商</span></button>
              </div>
            </van-popover>
            <button type="submit" class="primary" :disabled="busy || !profile?.enabled || (!editingProfile && !form.apiKey.trim())">{{ busy ? '正在检查连接…' : editingProfile ? '保存修改' : '添加并启用' }}</button>
          </div>
        </form>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <!-- 2026-11-11：调用/错误日志是全局审计入口，撤出接入商表单，全局入口只留在会话设置 -->
        <p v-if="profile && !profile.enabled" class="error">站点尚未启用 AI 或已锁定接入商设置。</p>
      </template>
      </div>
    </section>
  </van-popup>
</template>
<script setup>
import { ref, reactive, computed, watch, nextTick, onBeforeUnmount } from 'vue';
import { showConfirmDialog } from 'vant';
import { getAiSettings, configureAi, getAiBalance, fetchAiModels,
  activateAiProvider, deleteAiProvider } from '@/utils/api/ai';
import { aiProviderNameFromHost } from '@/utils/aiContract';
import { resolveSavedProvider } from '@/utils/aiProviderSelection';
import { useRouter } from 'vue-router';
import zhipuLogo from '@/assets/img/zhipu-logo.png';
const props = defineProps({
  show: Boolean,
  // 路由页形态（/ai/model-settings）：无遮罩、show 恒真、关闭=router.back()
  asPage: Boolean,
});
const emit = defineEmits(['update:show', 'configured', 'working']);
const router = useRouter();
const maxProfiles = 20;
const profile = ref(null);
const loading = ref(false);
const busy = ref(false);
const error = ref('');
// 2026-11-11：「···」操作面板开合
const moreOpen = ref(false);
const balance = ref(null);
const balanceError = ref('');
const balanceUnsupported = ref(false);

const editing = ref(null);
const form = reactive({ name: '', apiUrl: 'https://api.deepseek.com', model: '', apiKey: '' });
const keyDirty = ref(false);
const OFFICIAL_PRESETS = [
  { id: 'deepseek', name: 'DeepSeek', apiUrl: 'https://api.deepseek.com' },
  { id: 'glm', name: '智谱 GLM', apiUrl: 'https://open.bigmodel.cn/api/paas/v4' },
];
const presetLocked = ref(null);
const modelOptions = ref([]);
const fetchingModels = ref(false);
// 2026-11-11：全局追溯日志入口撤出接入商表单（留在会话设置），本组件不再持有抽屉状态
const profiles = computed(() => profile.value?.profiles || []);
const editingProfile = computed(() => (editing.value && editing.value !== 'new') ? editing.value : null);
const activeProfile = computed(() => profiles.value.find(item => item.active) || null);
const activeName = computed(() => activeProfile.value?.name || '当前接入商');
// 余额行跟随详情态：查看哪家接入商就查哪家的余额
const balanceName = computed(() => editingProfile.value ? displayName(editingProfile.value) : '当前接入商');
const hostOf = value => { try { return new URL(value).host; } catch (_) { return value; } };
// 迁移过来的旧设置没有自定义名称（name=host）时，列表用品牌名展示，但编辑框仍保留可改的原始名称。
function displayName(item) {
  const host = hostOf(item.apiUrl);
  return item.name === host ? (aiProviderNameFromHost(host) || item.name) : item.name;
}
const headingHint = computed(() => {
  if (!profile.value?.enabled) return '站点尚未启用 AI 或已锁定接入商设置';
  if (activeProfile.value) {
    const host = hostOf(activeProfile.value.apiUrl);
    const name = displayName(activeProfile.value);
    return name === host ? host : `${name} · ${host}`;
  }
  return profiles.value.length ? '点上方供应商编辑详情，或添加新的接入商' : '添加你的第一个接入商';
});
// 数据库已有 Key 时只展示掩码；聚焦或输入后进入编辑态，绝不回传明文。
const maskedKey = computed(() => !keyDirty.value && Boolean(editingProfile.value));
const apiKeyField = computed(() => (maskedKey.value ? '*********' : form.apiKey));
function onKeyFocus() {
  if (!keyDirty.value && editing.value) { keyDirty.value = true; form.apiKey = ''; }
}
function onKeyInput(event) { keyDirty.value = true; form.apiKey = event.target.value; }


function openForm(item = null) {
  editing.value = item || null;
  presetLocked.value = null;
  form.name = item?.name || '';
  form.apiUrl = item?.apiUrl || 'https://api.deepseek.com';
  form.model = item?.model || '';
  form.apiKey = '';
  keyDirty.value = false;
  error.value = '';
  modelOptions.value = [];
  loadBalance(item?.connectionId);
  nextTick(updateTabsFade);
}
// 2026-11-11：取消按钮移除（落位即当前接入商，改错字段重新进入即可），cancelEdit 一并退役
// 2026-11-11：供应商 chip 条横滑提示——未滚到尽头时右缘渐隐
const tabsEl = ref(null);
const tabsAtEnd = ref(false);
function updateTabsFade() {
  const el = tabsEl.value;
  if (!el) { tabsAtEnd.value = false; return; }
  tabsAtEnd.value = el.scrollWidth - el.scrollLeft - el.clientWidth < 8;
}
function openNew() {
  editing.value = 'new';
  presetLocked.value = null;
  form.name = ''; form.apiUrl = 'https://api.deepseek.com'; form.model = ''; form.apiKey = '';
  keyDirty.value = false;
  error.value = '';
  loadBalance(null);
  modelOptions.value = [];
}
function openPreset(preset) {
  editing.value = 'new';
  presetLocked.value = preset;
  form.name = preset.name;
  form.apiUrl = preset.apiUrl;
  form.model = '';
  form.apiKey = '';
  keyDirty.value = false;
  error.value = '';
  loadBalance(null);
  modelOptions.value = [];
}
async function fetchModels() {
  if (busy.value || fetchingModels.value) return;
  fetchingModels.value = true; error.value = '';
  try {
    const result = await fetchAiModels({ connectionId: editingProfile.value?.connectionId || undefined,
      apiUrl: form.apiUrl.trim() || undefined,
      ...(keyDirty.value && form.apiKey.trim() ? { apiKey: form.apiKey.trim() } : {}) });
    if (!result.models.length) { error.value = '厂商未返回任何模型'; return; }
    modelOptions.value = result.models;
  } catch (exception) { error.value = exception?.message || '模型列表获取失败'; }
  finally { fetchingModels.value = false; }
}


let previouslyFocused;
watch(() => props.show, async visible => {
  form.apiKey = ''; keyDirty.value = false; error.value = ''; presetLocked.value = null;
  if (!visible) return;
  previouslyFocused = document.activeElement;
  loading.value = true;
  try {
    profile.value = await getAiSettings();
    // 2026-11-11：打开配置落在当前启用的接入商详情（原为空白「添加供应商」表单，看不出当前配置）；
    // 无任何接入商时才落新建。
    const activeProfile = (profile.value.profiles || []).find(item => item.active);
    if (activeProfile) openForm(activeProfile); else openNew();
    nextTick(updateTabsFade);

  } catch (exception) { profile.value = null; error.value = exception?.message || '无法读取连接设置'; }
  finally { loading.value = false; nextTick(updateTabsFade); }
}, { immediate: true });
// 余额跟随详情态正在查看的接入商（查看哪家查哪家）；后端按各家契约分发（仅 DeepSeek 官方
// 提供 /user/balance，GLM 未开放该 API），说明文案带实际 host，就近内联展示，不弹失败提示。
async function loadBalance(connectionId) {
  balance.value = null; balanceError.value = ''; balanceUnsupported.value = false;
  if (!profile.value?.enabled || !connectionId) return;
  try { balance.value = await getAiBalance(connectionId); }
  catch (exception) {
    balanceUnsupported.value = exception?.code === 'AI_BALANCE_UNSUPPORTED';
    balanceError.value = balanceUnsupported.value
      ? (exception?.message || '当前接入商未提供用量余额查询。')
      : '接入商余额查询失败：' + (exception?.message || '未知错误');
  }
}
function close() {
  if (busy.value) return;
  form.apiKey = '';
  // 页面形态：关闭=返回上一路由（聊天界面），返回语义由 vue-router 原生保证
  if (props.asPage) { router.back(); return; }
  emit('update:show', false); nextTick(() => previouslyFocused?.focus?.());
}
async function save() {
  if (busy.value) return;
  error.value = '';
  const name = form.name.trim(), apiUrl = form.apiUrl.trim(), model = form.model.trim();
  const typedKey = keyDirty.value ? form.apiKey.trim() : '';
  if (!name) { error.value = '请填写接入商名称'; return; }
  if (!apiUrl) { error.value = '请填写 API 地址'; return; }
  if (!model) { error.value = '请填写模型名称'; return; }
  if (!editingProfile.value && !typedKey) { error.value = '请填写 API Key'; return; }
  busy.value = true; emit('working', true);
  try {
    const target = editingProfile.value;
    profile.value = await configureAi({ name, apiUrl, model,
      ...(typedKey ? { apiKey: typedKey } : {}),
      ...(target ? { connectionId: target.connectionId } : {}) });
    // 后端在地址/模型/Key 变化时会轮换 connectionId；按名称定位新编号，
    // 不能继续使用提交前的 target.connectionId。
    let { saved, activateId } = resolveSavedProvider(profile.value, {
      name,
      previousConnectionId: target?.connectionId || null,
      wasActive: target ? target.active === true : true,
    });
    if (activateId) {
      profile.value = await activateAiProvider(activateId);
      saved = (profile.value.profiles || []).find(item => item.connectionId === activateId) || saved;
    }
    const message = target ? (activateId ? `已保存并启用 ${name}。` : `已保存 ${name}。`) : `已添加 ${name}，可以开始对话。`;
    // 2026-11-10 D4：保存后落在刚保存的接入商详情上（余额行就近刷新），不再清成空白新建表单
    if (saved) openForm(saved); else openNew();
    emit('configured', message);

  } catch (exception) { error.value = exception?.message || '连接失败，请检查 API 和 Key'; }
  finally { busy.value = false; emit('working', false); }
}

// 2026-11-11：面板内删除项——关面板后仍走 removeProvider 的二次确认，双保险不变
function onDeleteClick() {
  moreOpen.value = false;
  const target = editingProfile.value;
  if (target) void removeProvider(target);
}

async function removeProvider(item) {
  if (busy.value) return;
  try {
    await showConfirmDialog({ title: '删除接入商', message: `删除「${item.name}」后需重新填写 API Key，确定删除？`,
      confirmButtonText: '删除', cancelButtonText: '取消' });
  } catch (_) { return; }
  busy.value = true; emit('working', true); error.value = '';
  try {
    profile.value = await deleteAiProvider(item.connectionId);
    if (editingProfile.value?.connectionId === item.connectionId) openNew();
    emit('configured', `已删除 ${item.name}。`);

  } catch (exception) { error.value = exception?.message || '删除失败'; }
  finally { busy.value = false; emit('working', false); }
}
onBeforeUnmount(() => { form.apiKey = ''; });
</script>
<style scoped>
.ai-settings-popup{width:100%;height:100%;background:var(--theme-bg-primary);color:var(--theme-text-primary)}.settings-dialog{display:flex;flex-direction:column;height:100%;box-sizing:border-box}.heading{flex-shrink:0;width:100%;max-width:560px;margin:0 auto;display:flex;align-items:center;justify-content:space-between;gap:10px;padding:calc(env(safe-area-inset-top,0px) + 14px) 16px 12px;border-bottom:1px solid var(--theme-border);box-sizing:border-box}.heading h2{font-size:17px;margin:0}.heading p{font-size:12px;color:var(--theme-text-secondary);margin:4px 0 0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.heading button{border:0;background:none;color:var(--theme-text-secondary);padding:6px;font-size:17px;cursor:pointer}.dialog-body{flex:1;overflow-y:auto;width:100%;max-width:560px;margin:0 auto;padding:14px 16px 28px;box-sizing:border-box}label{display:block;font-size:11px;margin:12px 0;color:var(--theme-text-secondary)}input,select{display:block;box-sizing:border-box;width:100%;min-width:0;margin-top:7px;padding:10px;border:1px solid var(--theme-border);border-radius:9px;background:var(--theme-bg-secondary);color:var(--theme-text-primary);font:inherit;font-size:13px}
.model-row{display:flex;gap:8px;align-items:stretch;margin-top:7px}.model-row input{margin-top:0;flex:1;min-width:0}.fetch-models{flex-shrink:0;border:1px solid var(--theme-border);background:var(--theme-bg-secondary);color:var(--van-primary-color);border-radius:9px;padding:0 11px;font:inherit;font-size:11px;cursor:pointer;white-space:nowrap}.fetch-models:disabled{opacity:.45;cursor:default}
.addr-hint{font-size:10px;color:var(--theme-text-secondary);margin-left:6px;font-weight:400}
.model-list{margin-top:10px}
.model-scroll{max-height:180px;overflow-y:auto;border:1px solid var(--theme-border);border-radius:9px;background:var(--theme-bg-secondary)}
.model-option{display:flex;align-items:center;justify-content:space-between;gap:8px;width:100%;min-height:36px;padding:8px 12px;border:0;border-bottom:1px solid var(--theme-border);background:none;color:var(--theme-text-primary);font:inherit;font-size:12px;cursor:pointer;text-align:left}
.model-option:last-child{border-bottom:0}
.model-option.on{color:var(--van-primary-color);background:var(--van-primary-color-light,var(--theme-bg-secondary))}
.model-name{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.model-option:focus{outline:1px solid var(--van-primary-color)}input:focus,select:focus{outline:1px solid var(--van-primary-color)}.note,.data-source{font-size:11px;line-height:1.7;color:var(--theme-text-secondary)}.data-source{padding:8px 10px;border-radius:8px;background:var(--theme-bg-secondary)}.error{color:var(--van-danger-color);font-size:11px;line-height:1.7}.actions{display:flex;gap:8px;margin-top:14px}.actions button{border:1px solid var(--theme-border);background:transparent;color:var(--theme-text-primary);border-radius:9px;padding:10px 12px;font:inherit;font-size:11px;cursor:pointer}.actions .primary{background:var(--van-primary-color);color:white;border-color:var(--van-primary-color);flex:1}button:disabled{opacity:.45;cursor:default}.balance-line{display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap}.balance-line strong{font-size:13px}.balance-line strong.insufficient{color:var(--van-danger-color)}.balance-state{font-size:10px;color:var(--van-success-color,#07c160);white-space:nowrap}.balance-err{color:var(--van-danger-color)}
/* 2026-11-11 供应商 chip 条（+添加 / 已有）+ 详情态，参考桌面双栏设计的移动端上下形态；
   横滑必带提示：未滚到尽头右缘渐隐（mask），滚到尽头移除（.fade-end） */
.provider-tabs{display:flex;gap:8px;overflow-x:auto;margin:4px 0 10px;padding-bottom:2px;scrollbar-width:none;-webkit-mask-image:linear-gradient(90deg,#000 86%,rgba(0,0,0,0) 100%);mask-image:linear-gradient(90deg,#000 86%,rgba(0,0,0,0) 100%);transition:-webkit-mask-image .2s;transition:mask-image .2s}
.provider-tabs.fade-end{-webkit-mask-image:none;mask-image:none}
.provider-tabs::-webkit-scrollbar{display:none}
.provider-tab{flex-shrink:0;display:inline-flex;align-items:center;gap:6px;min-height:36px;padding:0 14px;border:1px solid var(--theme-border);border-radius:999px;background:var(--theme-bg-secondary);color:var(--theme-text-primary);font:inherit;font-size:12px;cursor:pointer;white-space:nowrap}
.provider-tab.add{border-style:dashed;color:var(--theme-text-secondary)}
.provider-tab.on{border-color:var(--van-primary-color);color:var(--van-primary-color);background:var(--van-primary-color-light,var(--theme-bg-secondary))}
.tab-dot{width:6px;height:6px;border-radius:50%;background:var(--theme-text-placeholder);flex-shrink:0}
.tab-dot.on{background:var(--van-success-color,#07c160)}
.group-hint{font-size:11px;color:var(--theme-text-secondary);margin:12px 0 8px}
/* 2026-11-11：徽标内联到「名称」标签行右端（label-head 两端对齐），不再单独占行 */
.label-head{display:flex;align-items:center;justify-content:space-between;gap:8px}
.active-flag{display:inline-flex;align-items:center;gap:6px;font-size:10px;color:var(--van-primary-color);border:1px solid currentColor;border-radius:999px;padding:2px 8px;margin:0}
/* 2026-11-11：删除入口收进「···」图标按钮，点击弹操作面板（与 composer 模型浮层同语言） */
.more-button{flex-shrink:0;display:grid;place-items:center;min-width:42px;border:1px solid var(--theme-border);border-radius:9px;background:transparent;color:var(--theme-text-secondary);font-size:18px;cursor:pointer}
.more-button .van-icon{font-size:18px}
.more-pop{width:180px;background:var(--theme-bg-secondary);border:1px solid var(--theme-border);border-radius:12px;box-shadow:0 6px 24px rgba(0,0,0,.4);overflow:hidden}
.more-pop-item{display:flex;align-items:center;gap:8px;width:100%;min-height:40px;padding:0 12px;border:0;background:none;font:inherit;font-size:12px;color:var(--van-danger-color);text-align:left;cursor:pointer}
.more-pop-item:active{background:var(--theme-bg-tertiary)}
.more-pop-item .van-icon{font-size:14px}
.preset-row{display:flex;gap:8px;margin:0 0 4px}
.brand-logo{width:15px;height:15px;flex-shrink:0}
.brand-img{border-radius:4px}
.provider-tab .brand-logo{width:14px;height:14px}
.preset-btn{flex:1;display:flex;align-items:center;justify-content:center;gap:6px;min-height:38px;border:1px solid rgba(var(--theme-primary-rgb,58,102,224),.35);border-radius:9px;background:var(--van-primary-color-light,var(--theme-bg-secondary));color:var(--van-primary-color);font:inherit;font-size:11px;cursor:pointer}
.preset-btn:disabled{opacity:.45;cursor:default}
.preset-btn:hover:not(:disabled){border-color:var(--van-primary-color)}

/* 全屏面板：移动端贴边距 + 输入字号 16px 规避 iOS 聚焦自动放大 + 按钮加大触控热区 */
.model-scroll{max-height:160px}
@media(max-width:600px){.heading{padding:calc(env(safe-area-inset-top,0px) + 12px) 14px 10px}.heading h2{font-size:15px}.heading p{font-size:11px}.dialog-body{padding:12px 14px max(24px,env(safe-area-inset-bottom))}label{margin:8px 0;line-height:1.5}input,select{font-size:16px;min-height:38px;line-height:20px;margin-top:5px;padding:8px 10px}.model-row{gap:6px;margin-top:5px}.model-row input{margin-top:0}.note,.data-source,.error{line-height:1.55}.actions{gap:6px;margin-top:10px}.actions button{min-height:42px;padding:9px 10px;font-size:12px}.provider-tab{min-height:38px}.more-button{min-width:44px;padding:0}}
button:focus-visible,input:focus-visible,select:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}
</style>
