import { computed } from 'vue';
// 相对路径：controller 测试 harness 用 node 原生 ESM 直接导入本文件，@/ Vite 别名不可解析
import { clearDialogGuard } from '../../../../composables/useDialogBackGuard.js';

// ===== 2026-11-11 模型列表缓存：模块级、跨挂载存活。不进 Pinia——消费方仅 composer 浮层一处，
// 且 controller 测试 harness 在无 pinia 的 node 环境执行（store 调用会炸）。带 TTL 与连接键：
// 换接入商/换端自动失效；新鲜期内打开零请求，过期打开走静默刷新。
const MODEL_LIST_TTL = 5 * 60 * 1000;
const modelListCache = { key: '', models: [], fetchedAt: 0 };
const modelListKey = profile => `${profile?.connectionId || ''}|${(profile?.apiUrl || '').trim()}`;
function cachedModelList(key, requireFresh) {
  if (modelListCache.key !== key || !modelListCache.models.length) return null;
  if (requireFresh && Date.now() - modelListCache.fetchedAt >= MODEL_LIST_TTL) return null;
  return modelListCache.models;
}
function rememberModelList(key, models) {
  modelListCache.key = key;
  modelListCache.models = models;
  modelListCache.fetchedAt = Date.now();
}

export function useAssistantProfile({ state, services, runtime, actions }) {
  const {
    capability, providerLabel, profile, modelPickerOpen, modelOptions, modelLoading,
    modelError, thinkingEnabled, thinkingSupported, thinkingRequired, webSearchEnabled, webSearchAvailable, busy,
    transferBusy, settingsBusy, settingsNotice, sessionSettingsOpen,
  } = state;
  const { getAiSettings, getAiPreferences, fetchAiModels, configureAi, putAiPreference, router } = services;

  const currentModel = computed(() => profile.value?.model || '');
  const modelLabel = computed(() => currentModel.value || '选择模型');
  const providerName = computed(() => {
    const name = profile.value?.name;
    return name && name !== (capability.value?.provider || '') ? name : providerLabel.value;
  });

  // 2026-11-11：模型列表预取/静默刷新——缓存新鲜期（5 分钟）内直接命中零请求，
  // 过期才拉取；失败静默，打开浮层时仍有可见加载态兜底。
  async function preloadModelOptions() {
    if (!runtime.isActive() || modelLoading.value) return;
    const key = modelListKey(profile.value);
    const fresh = cachedModelList(key, true);
    if (fresh) {
      if (modelOptions.value !== fresh) modelOptions.value = fresh;
      return;
    }
    try {
      const result = await fetchAiModels({
        connectionId: profile.value?.connectionId || undefined,
        apiUrl: profile.value?.apiUrl || undefined,
      });
      if (!runtime.isActive()) return;
      if (Array.isArray(result.models) && result.models.length) {
        rememberModelList(key, result.models);
        modelOptions.value = result.models;
      }
    } catch { /* 预取/静默刷新失败不打扰用户 */ }
  }

  async function loadComposerProfile() {
    if (!runtime.isActive()) return;
    if (!capability.value?.enabled) {
      profile.value = null;
      thinkingSupported.value = false;
      thinkingRequired.value = false;
      return;
    }
    try {
      const settings = await getAiSettings();
      if (!runtime.isActive()) return;
      profile.value = settings;
      // 换接入商/换端：立即回填该连接的缓存列表（无则清空），过期由预取静默刷新
      modelOptions.value = cachedModelList(modelListKey(settings), false) || [];
      void preloadModelOptions();
    } catch {
      if (!runtime.isActive()) return;
      profile.value = null;
    }
    try {
      const prefs = await getAiPreferences();
      if (!runtime.isActive()) return;
      thinkingEnabled.value = prefs.thinkingEnabled;
      thinkingSupported.value = prefs.thinkingSupported;
      thinkingRequired.value = prefs.thinkingRequired === true;
      webSearchEnabled.value = prefs.webSearch === true;
      webSearchAvailable.value = prefs.webSearchAvailable === true;
    } catch {
      if (!runtime.isActive()) return;
      thinkingEnabled.value = false;
      thinkingSupported.value = false;
      thinkingRequired.value = false;
      webSearchEnabled.value = false;
      webSearchAvailable.value = false;
    }
  }

  async function openModelPicker() {
    if (!runtime.isActive() || busy.value || modelLoading.value) return;
    modelPickerOpen.value = !modelPickerOpen.value;
    if (!modelPickerOpen.value) return;
    modelError.value = '';
    // 2026-11-11：已有列表时走预取通道——缓存新鲜期内零请求（点开不再每次拉取），
    // 过期则静默刷新；无列表才走可见加载态。
    if (modelOptions.value.length) { void preloadModelOptions(); return; }
    modelLoading.value = true;
    try {
      const result = await fetchAiModels({
        connectionId: profile.value?.connectionId || undefined,
        apiUrl: profile.value?.apiUrl || undefined,
      });
      if (!runtime.isActive()) return;
      modelOptions.value = result.models;
    } catch (error) {
      if (runtime.isActive()) modelError.value = error?.message || '模型列表获取失败';
    } finally {
      if (runtime.isActive()) modelLoading.value = false;
    }
  }

  // 复用连接设置端点，只替换模型；当前会话及在途请求保持原有上下文。
  async function switchModel(model) {
    if (!runtime.isActive()) return;
    if (busy.value || !model || model === currentModel.value) {
      modelPickerOpen.value = false;
      return;
    }
    settingsBusy.value = true;
    try {
      const settings = await configureAi({
        connectionId: profile.value?.connectionId || undefined,
        apiUrl: (profile.value?.apiUrl || '').trim(),
        model,
      });
      if (!runtime.isActive()) return;
      profile.value = settings;
      capability.value = {
        ...capability.value,
        connectionId: profile.value.connectionId,
        sessionConfigured: profile.value.configured,
      };
      modelPickerOpen.value = false;
      settingsNotice.value = `已切换模型 ${model}，下一轮对话生效。`;
      await loadComposerProfile();
    } catch (error) {
      if (runtime.isActive()) modelError.value = error?.message || '模型切换失败';
    } finally {
      if (runtime.isActive()) settingsBusy.value = false;
    }
  }

  // §3b：联网开关（默认关；付费能力显式开启）。开关只控制触发权，费用仍受站点预算与授权卡约束。
  async function toggleWebSearch() {
    if (!runtime.isActive() || transferBusy.value || !webSearchAvailable.value) return;
    const next = !webSearchEnabled.value;
    webSearchEnabled.value = next;
    try {
      const prefs = await putAiPreference(undefined, next);
      if (!runtime.isActive()) return;
      webSearchEnabled.value = prefs.webSearch === true;
    } catch (error) {
      if (!runtime.isActive()) return;
      webSearchEnabled.value = !next;
      settingsNotice.value = error?.message || '联网开关保存失败';
    }
  }

  async function toggleThinking() {
    if (!runtime.isActive() || transferBusy.value || thinkingRequired.value) return;
    const next = !thinkingEnabled.value;
    thinkingEnabled.value = next;
    try {
      const prefs = await putAiPreference(next);
      if (!runtime.isActive()) return;
      thinkingEnabled.value = prefs.thinkingEnabled;
    } catch (error) {
      if (!runtime.isActive()) return;
      thinkingEnabled.value = !next;
      settingsNotice.value = error?.message || '思考开关保存失败';
    }
  }

  // 模型设置 = 独立路由页（/ai/model-settings）：全屏形态天然占历史条目，
  // 返回（手机手势/浏览器返回/X 按钮）由 vue-router 原生回退到聊天界面，无需弹窗哨兵 hack
  function openModelSettings() {
    if (!runtime.isActive()) return;
    modelPickerOpen.value = false;
    router?.push('/ai/model-settings');
  }

  function openModelSettingsFromSession() {
    if (!runtime.isActive()) return;
    // 跳转前同步洗白会话设置弹窗的哨兵：否则其关闭延迟消费（setTimeout back）会抢在
    // 动态 import(ModelSettings) 完成前执行，误判哨兵仍在而 back 打断本次导航（进不去路由页）
    sessionSettingsOpen.value = false;
    clearDialogGuard();
    router?.push('/ai/model-settings');
  }

  function openPersonalizationFromSession() {
    if (!runtime.isActive() || busy.value || transferBusy.value) return;
    sessionSettingsOpen.value = false;
    clearDialogGuard();
    router?.push('/ai/personalization');
  }

  function openBudgetSettingsFromSession() {
    if (!runtime.isActive() || busy.value || transferBusy.value) return;
    sessionSettingsOpen.value = false;
    clearDialogGuard();
    router?.push('/ai/budget-settings');
  }

  // 配置随能力加载或设置变更刷新，本模块没有挂载资源。
  function initialize() {}
  function dispose() {}

  return {
    currentModel, modelLabel, providerName, loadComposerProfile, openModelPicker, webSearchEnabled, webSearchAvailable,
    switchModel, toggleThinking, toggleWebSearch, openModelSettings, openModelSettingsFromSession, openPersonalizationFromSession, openBudgetSettingsFromSession,
    initialize, dispose,
  };
}
