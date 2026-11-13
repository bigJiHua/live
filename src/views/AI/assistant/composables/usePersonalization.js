import { reactive, computed } from 'vue';
import { clonePersonalization, newPersonalizationId, isPersonalizationConflict } from '../../../../utils/aiPersonalization.js';

export function usePersonalization(api) {
  const state = reactive({ data: null, memories: [], warnings: [], loading: false, busy: false, error: '', notice: '',
    conflict: false, ruleDrafts: [], memoryEditor: null, profileEditor: null, history: [], historyPath: null, historyOpen: false,
    latestMemory: null });
  let active = true;
  const rulesDirty = computed(() => JSON.stringify(state.ruleDrafts) !== JSON.stringify(state.data?.rules || []));
  function acceptData(data, keepDrafts = false) {
    state.data = data;
    if (!keepDrafts) state.ruleDrafts = clonePersonalization(data.rules);
  }
  async function refresh({ keepDrafts = false } = {}) {
    const [data, memories] = await Promise.all([api.getPersonalization(), api.listMemories()]);
    if (!active) return;
    acceptData(data, keepDrafts);
    state.memories = memories.files;
    state.warnings = memories.warnings || [];
  }
  async function load() {
    if (!active || state.loading || state.busy) return;
    state.loading = true; state.error = '';
    try { await refresh({ keepDrafts: rulesDirty.value }); }
    catch (error) { if (active) state.error = error?.message || '个性化信息读取失败'; }
    finally { if (active) state.loading = false; }
  }
  async function refreshAfterSaved(label) {
    try { await refresh({ keepDrafts: rulesDirty.value }); }
    catch { if (active) state.error = `${label}已完成，但列表暂时无法刷新。请重新读取后核查。`; }
  }
  async function action(work, notice) {
    if (!active || state.busy || state.loading) return false;
    state.busy = true; state.error = ''; state.notice = ''; state.conflict = false;
    try {
      await work();
      if (!active) return false;
      state.notice = notice; return true;
    } catch (error) {
      if (!active) return false;
      if (isPersonalizationConflict(error)) {
        state.conflict = true;
        state.error = '内容已在其他地方修改。你的输入已保留，请核对最新内容后再保存。';
        try {
          await refresh({ keepDrafts: true });
          if (active && state.memoryEditor?.path) {
            const latest = await api.readMemory(state.memoryEditor.path);
            if (active) state.latestMemory = latest;
          }
        } catch { if (active) state.error += ' 最新内容暂时无法读取，请稍后重试。'; }
      } else state.error = error?.message || '操作未得到确认，请重新读取后核查';
      return false;
    } finally { if (active) state.busy = false; }
  }
  async function updateData(patch, notice) {
    if (!state.data) return false;
    return action(async () => {
      const data = await api.updatePersonalization({ expectedRevision: state.data.revision, ...patch });
      if (active) acceptData(data, patch.rules == null && rulesDirty.value);
    }, notice);
  }
  function addRule() {
    if (state.busy || state.ruleDrafts.length >= 30) return;
    state.ruleDrafts.push({ id: newPersonalizationId('rule'), text: '', enabled: true });
  }
  function resetRules() { if (!state.busy && state.data) state.ruleDrafts = clonePersonalization(state.data.rules); }
  async function saveRules() {
    if (state.ruleDrafts.some(rule => !rule.text.trim())) { state.error = '请填写规则内容，或删除空白规则。'; return false; }
    return updateData({ rules: clonePersonalization(state.ruleDrafts) }, '规则已保存，下一轮对话生效。');
  }
  const setSetting = (key, value) => updateData({ settings: { ...state.data.settings, [key]: value } }, '偏好已保存，下一轮对话生效。');
  const toggleMemory = path => {
    const disabled = state.data.disabledMemories.includes(path);
    return updateData({ disabledMemories: disabled ? state.data.disabledMemories.filter(item => item !== path)
      : [...state.data.disabledMemories, path] }, disabled ? '记忆已启用。' : '记忆已停用，AI 不再参考这条内容。');
  };
  function newMemory() {
    state.profileEditor = null;
    state.memoryEditor = { path: null, revision: null, title: '', type: 'note', content: '' };
    state.latestMemory = null; state.error = ''; state.conflict = false;
  }
  async function editMemory(path) {
    return action(async () => { const memory = await api.readMemory(path); if (active) {
      state.profileEditor = null;
      state.memoryEditor = clonePersonalization(memory); state.latestMemory = null;
    } }, '');
  }
  async function saveMemory() {
    const draft = state.memoryEditor;
    if (!draft?.title.trim() || !draft.content.trim()) { state.error = '请填写记忆标题和内容。'; return false; }
    return action(async () => {
      const body = { title: draft.title.trim(), type: draft.type, content: draft.content };
      if (draft.path) {
        const expectedRevision = state.latestMemory?.revision || draft.revision;
        await api.updateMemory(draft.path, { ...body, expectedRevision });
      } else await api.createMemory(body);
      if (active) { state.memoryEditor = null; state.latestMemory = null; }
      await refreshAfterSaved('记忆保存');
    }, '记忆已保存。');
  }
  async function removeMemory(memory) {
    return action(async () => {
      const result = await api.deleteMemory(memory.path, { expectedRevision: memory.revision });
      if (!result || result.deleted !== true) throw new Error('删除未得到确认，请重新读取记忆列表。');
      await refreshAfterSaved('记忆删除');
    }, '记忆已删除，可从修改记录恢复。');
  }
  async function openHistory(path = null) {
    return action(async () => { const history = await api.memoryHistory(path); if (active) {
      state.history = history; state.historyPath = path; state.historyOpen = true;
    } }, '');
  }
  async function restoreMemory(entry) {
    const path = entry.path || entry.snapshot?.path || state.historyPath;
    if (!path) { state.error = '这条修改记录缺少记忆文件信息。'; return false; }
    return action(async () => {
      const current = state.memories.find(item => item.path === path);
      await api.restoreMemory(path, { historyId: entry.id || entry.historyId,
        ...(current ? { expectedRevision: current.revision } : {}) });
      await refreshAfterSaved('记忆恢复');
      if (active) state.historyOpen = false;
    }, '记忆已恢复。');
  }
  function editProfile(item) {
    state.memoryEditor = null;
    state.profileEditor = item ? clonePersonalization(item) : {
      id: newPersonalizationId('profile'), category: 'communication', text: '', origin: 'user', confirmed: true, evidence: [], updatedAt: null,
    };
    state.error = ''; state.conflict = false;
  }
  async function saveProfile() {
    const draft = state.profileEditor;
    if (!draft?.text.trim()) { state.error = '请填写画像内容。'; return false; }
    const item = { ...clonePersonalization(draft), text: draft.text.trim(), origin: 'user', confirmed: true };
    const items = state.data.profile.items.filter(entry => entry.id !== item.id);
    items.push(item);
    const saved = await updateData({ profile: { ...state.data.profile, items } }, '画像已保存，AI 会优先采用你的修改。');
    if (saved && active) state.profileEditor = null;
    return saved;
  }
  const confirmProfile = item => updateData({ profile: { ...state.data.profile,
    items: state.data.profile.items.map(entry => entry.id === item.id ? { ...entry, confirmed: true } : entry) } }, '已确认这条画像。');
  const removeProfile = item => updateData({ profile: { ...state.data.profile,
    items: state.data.profile.items.filter(entry => entry.id !== item.id) } }, '画像已删除，自动整理时不会重新补回。');
  async function generateProfile(mode = 'incremental') {
    return action(async () => { const data = await api.generateProfile({ expectedRevision: state.data.revision, mode });
      if (active) acceptData(data, rulesDirty.value);
    }, '画像整理完成，你可以检查依据并修改。');
  }
  function dispose() { active = false; }
  return { state, rulesDirty, load, addRule, resetRules, saveRules, setSetting, toggleMemory, newMemory, editMemory,
    saveMemory, removeMemory, openHistory, restoreMemory, editProfile, saveProfile, confirmProfile, removeProfile, generateProfile, dispose };
}
