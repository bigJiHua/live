<template>
  <div class="personalization-page">
    <header class="page-head">
      <button type="button" aria-label="返回聊天" @click="back"><van-icon name="arrow-left" /></button>
      <div><h1>AI 个性化</h1><p>管理它记住的事、对你的了解和回答规则</p></div>
      <button type="button" aria-label="刷新个性化信息" :disabled="state.busy || state.loading" @click="load"><van-icon name="replay" /></button>
    </header>
    <nav class="tabs" aria-label="个性化分类"><button v-for="item in tabs" :key="item.id" type="button" :class="{ selected: tab === item.id }" :aria-pressed="tab === item.id" @click="tab = item.id">{{ item.label }}</button></nav>
    <main class="page-body" :aria-busy="state.busy || state.loading">
      <p v-if="state.error" class="feedback error" role="alert">{{ state.error }}</p>
      <p v-if="state.notice" class="feedback success" role="status">{{ state.notice }}</p>
      <div v-if="state.loading && !state.data" class="empty"><van-loading size="24" /><p>正在读取你的个性化设置…</p></div>
      <div v-else-if="!state.data" class="empty"><p>暂时无法读取个性化设置。</p><button class="primary" @click="load">重新读取</button></div>
      <template v-else>
        <section v-if="tab === 'memories'" aria-label="记忆管理">
          <div class="setting-row"><div><strong>使用记忆</strong><p>AI 按当前问题参考相关记忆。</p></div><van-switch :model-value="state.data.settings.useMemory" size="22" :disabled="state.busy" @update:model-value="setSetting('useMemory', $event)" /></div>
          <div class="section-actions"><span>{{ state.memories.length }} 条记忆</span><div><button class="text-button" :disabled="state.busy" @click="openHistory()">修改记录</button><button class="primary small" :disabled="state.busy" @click="newMemory"><van-icon name="plus" /> 添加记忆</button></div></div>
          <label class="search-box"><van-icon name="search" /><input v-model="search" type="search" aria-label="搜索记忆标题" placeholder="搜索标题或类型" /></label>
          <p v-if="state.warnings.length" class="hint warning">部分记忆暂时无法读取，请在修改记录中核查。</p>
          <p class="hint">也可以在聊天里让 AI 记住、修改或忘掉一件事。</p>
          <article v-for="memory in filteredMemories" :key="memory.path" class="content-card" :class="{ muted: isDisabled(memory.path) }">
            <div class="card-title"><h2>{{ memory.title }}</h2><span class="tag">{{ MEMORY_TYPES[memory.type] }}</span><span v-if="isDisabled(memory.path)" class="tag neutral">已停用</span></div>
            <p class="meta">更新于 {{ dateLabel(memory.updated) }}</p>
            <div class="card-actions"><button :disabled="state.busy" @click="editMemory(memory.path)">查看 / 编辑</button><button :disabled="state.busy" @click="toggleMemory(memory.path)">{{ isDisabled(memory.path) ? '启用' : '停用' }}</button><button :disabled="state.busy" @click="openHistory(memory.path)">记录</button><button class="danger" :disabled="state.busy" @click="deleteMemory(memory)">删除</button></div>
          </article>
          <div v-if="!filteredMemories.length" class="empty"><van-icon name="notes-o" /><p>{{ search ? '没有匹配的记忆。' : '还没有记忆，添加一件希望 AI 长期记住的事。' }}</p></div>
        </section>
        <section v-if="tab === 'profile'" aria-label="用户画像">
          <div class="setting-row"><div><strong>使用用户画像</strong><p>新会话和更换模型后，也能了解你的偏好。</p></div><van-switch :model-value="state.data.settings.useProfile" size="22" :disabled="state.busy" @update:model-value="setSetting('useProfile', $event)" /></div>
          <div class="setting-row"><div><strong>自动更新画像</strong><p>根据新增对话整理，保留你确认和修改的内容。</p></div><van-switch :model-value="state.data.settings.autoProfile" size="22" :disabled="state.busy" @update:model-value="setSetting('autoProfile', $event)" /></div>
          <div class="section-actions"><span>{{ state.data.profile.items.length }} 条了解</span><button class="primary small" :disabled="state.busy || state.data.profile.items.length >= 100" @click="editProfile()"><van-icon name="plus" /> 添加</button></div>
          <div class="generation-actions"><button class="primary" :disabled="state.busy" @click="generateProfile('incremental')">{{ state.busy ? '处理中…' : state.data.profile.items.length ? '根据新对话更新' : '根据历史对话生成' }}</button><button class="secondary" :disabled="state.busy" @click="rebuildProfile">重新整理历史</button></div>
          <p class="hint">AI 推断会标明依据。你可以纠正、确认或删除；你的明确规则优先于画像。</p>
          <p v-if="state.data.profile.updatedAt" class="meta">上次整理：{{ dateLabel(state.data.profile.updatedAt) }}</p>
          <template v-for="(label, category) in PROFILE_CATEGORIES" :key="category">
            <div v-if="profileItems(category).length" class="profile-group"><h2 class="group-title">{{ label }}</h2>
              <article v-for="item in profileItems(category)" :key="item.id" class="content-card">
                <div class="profile-badges"><span class="tag" :class="{ neutral: !item.confirmed }">{{ item.origin === 'user' ? '用户填写' : item.confirmed ? '用户已确认' : 'AI 推断 · 待核对' }}</span></div><p class="item-text">{{ item.text }}</p>
                <details v-if="item.evidence.length" class="evidence"><summary>查看依据（{{ item.evidence.length }}）</summary><blockquote v-for="(source, index) in item.evidence" :key="index"><p>{{ source.quote }}</p><footer v-if="source.sourceLabel || source.sourceTime">{{ source.sourceLabel || '历史对话' }}<span v-if="source.sourceTime"> · {{ dateLabel(source.sourceTime) }}</span></footer></blockquote></details>
                <div class="card-actions"><button :disabled="state.busy" @click="editProfile(item)">编辑</button><button v-if="!item.confirmed" :disabled="state.busy" @click="confirmProfile(item)">确认准确</button><button class="danger" :disabled="state.busy" @click="deleteProfile(item)">删除</button></div>
              </article>
            </div>
          </template>
          <div v-if="!state.data.profile.items.length" class="empty"><van-icon name="user-o" /><p>让 AI 从历史对话整理对你的了解，或直接告诉它你的偏好。</p></div>
        </section>
        <section v-if="tab === 'rules'" aria-label="自定义规则">
          <div class="intro"><h2>让 AI 按你的习惯回答</h2><p>明确写下你希望它怎样配合你。保存后，规则会随下一轮对话携带给 AI。</p></div><p class="hint">例如：先给结论，再解释原因；金额注明币种；不要主动推荐理财产品。</p>
          <div class="section-actions"><span>{{ state.ruleDrafts.length }} / 30 条规则<span v-if="rulesDirty" class="unsaved"> · 未保存</span></span><button class="secondary small" :disabled="state.busy || state.ruleDrafts.length >= 30" @click="addRule"><van-icon name="plus" /> 添加规则</button></div>
          <article v-for="(rule, index) in state.ruleDrafts" :key="rule.id" class="content-card rule-card"><div class="card-title"><label :for="`rule-${rule.id}`">规则 {{ index + 1 }}</label><span class="grow" /><span class="meta">{{ rule.enabled ? '启用' : '停用' }}</span><van-switch v-model="rule.enabled" size="20" :disabled="state.busy" /></div><textarea :id="`rule-${rule.id}`" v-model="rule.text" maxlength="500" rows="3" placeholder="写一条清楚、具体的要求" :disabled="state.busy" /><div class="rule-footer"><span class="meta">{{ rule.text.length }} / 500</span><button class="text-button danger" :disabled="state.busy" @click="state.ruleDrafts.splice(index, 1)">删除</button></div></article>
          <div v-if="!state.ruleDrafts.length" class="empty"><van-icon name="description" /><p>还没有规则，可以从回答方式或沟通习惯开始。</p></div>
          <div class="save-row"><button class="secondary" :disabled="state.busy || !rulesDirty" @click="resetRules">还原未保存修改</button><button class="primary" :disabled="state.busy || !rulesDirty" @click="saveRules">{{ state.busy ? '保存中…' : state.conflict ? '核对后保存规则' : '保存规则' }}</button></div>
          <details v-if="state.conflict" class="latest-content"><summary>查看服务器当前规则</summary><p v-for="rule in state.data.rules" :key="rule.id">{{ rule.text }}（{{ rule.enabled ? '启用' : '停用' }}）</p><p v-if="!state.data.rules.length">暂无规则</p></details>
        </section>
      </template>
    </main>
    <van-popup :show="editorOpen" position="right" teleport="body" :style="{ width: '100%', height: '100%' }" class="editor-popup" @update:show="closeEditor">
      <section class="editor-sheet" role="dialog" aria-modal="true" aria-labelledby="editor-title"><header class="editor-head"><h2 id="editor-title">{{ state.memoryEditor ? state.memoryEditor.path ? '编辑记忆' : '添加记忆' : '编辑用户画像' }}</h2><button type="button" aria-label="关闭编辑" :disabled="state.busy" @click="closeEditor"><van-icon name="cross" /></button></header>
        <form class="editor-body" @submit.prevent="state.memoryEditor ? saveMemory() : saveProfile()"><p v-if="state.error" class="feedback error" role="alert">{{ state.error }}</p>
          <template v-if="state.memoryEditor"><label class="field-label">标题<input v-model="state.memoryEditor.title" maxlength="80" required :disabled="state.busy" /></label><label class="field-label">类型<select v-model="state.memoryEditor.type" :disabled="state.busy"><option v-for="(label, key) in MEMORY_TYPES" :key="key" :value="key">{{ label }}</option></select></label><label class="field-label">内容<textarea v-model="state.memoryEditor.content" rows="12" maxlength="60000" required :disabled="state.busy" /></label><p v-if="state.memoryEditor.source" class="hint source">来源：{{ state.memoryEditor.source }}</p><p v-if="state.memoryEditor.created" class="meta">创建：{{ dateLabel(state.memoryEditor.created) }} · 更新：{{ dateLabel(state.memoryEditor.updated) }}</p><details v-if="state.latestMemory" open class="latest-content"><summary>最新内容（你的输入已保留在上方）</summary><strong>{{ state.latestMemory.title }}</strong><p>{{ state.latestMemory.content }}</p></details><p class="hint">保存稳定、需要长期参考的信息。删除的记忆可以从修改记录恢复。</p></template>
          <template v-else-if="state.profileEditor"><label class="field-label">分类<select v-model="state.profileEditor.category" :disabled="state.busy"><option v-for="(label, key) in PROFILE_CATEGORIES" :key="key" :value="key">{{ label }}</option></select></label><label class="field-label">内容<textarea v-model="state.profileEditor.text" maxlength="2000" rows="8" required :disabled="state.busy" /></label><p class="hint">你保存的内容会标为用户填写，自动整理不会覆盖。</p><details v-if="state.conflict" open class="latest-content"><summary>最新画像</summary><p v-for="item in state.data.profile.items" :key="item.id">{{ PROFILE_CATEGORIES[item.category] }}：{{ item.text }}</p></details></template>
          <div class="save-row"><button type="button" class="secondary" :disabled="state.busy" @click="closeEditor">取消</button><button type="submit" class="primary" :disabled="state.busy">{{ state.busy ? '保存中…' : state.conflict ? '核对后保存' : '保存' }}</button></div>
        </form>
      </section>
    </van-popup>
    <van-popup :show="state.historyOpen" position="right" teleport="body" :style="{ width: '100%', height: '100%' }" class="editor-popup" @update:show="closeHistory">
      <section class="editor-sheet" role="dialog" aria-modal="true" aria-labelledby="history-title"><header class="editor-head"><h2 id="history-title">记忆修改记录</h2><button type="button" aria-label="关闭修改记录" :disabled="state.busy" @click="state.historyOpen = false"><van-icon name="cross" /></button></header><div class="editor-body"><p v-if="state.error" class="feedback error" role="alert">{{ state.error }}</p><article v-for="entry in state.history" :key="entry.id || entry.historyId" class="content-card"><div class="card-title"><h2>{{ entry.snapshot?.title || entry.title || entry.path || state.historyPath || '记忆修改' }}</h2><span class="tag neutral">{{ historyAction(entry.action || entry.operation) }}</span></div><p class="meta">{{ dateLabel(entry.createdAt || entry.at || entry.updatedAt) }}</p><details v-if="entry.snapshot?.content || entry.content" class="evidence"><summary>查看保存的内容</summary><p class="item-text">{{ entry.snapshot?.content || entry.content }}</p></details><div class="card-actions"><button :disabled="state.busy || entry.restorable === false" @click="restoreMemory(entry)">恢复这个版本</button></div></article><div v-if="!state.history.length" class="empty"><p>暂无修改记录。</p></div></div></section>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue';
import { useRouter, onBeforeRouteLeave } from 'vue-router';
import { showConfirmDialog } from 'vant';
import { personalizationApi } from '@/utils/api/aiPersonalization';
import { PROFILE_CATEGORIES, MEMORY_TYPES } from '@/utils/aiPersonalization';
import { usePersonalization } from './assistant/composables/usePersonalization.js';
import { usePersonalizationBackGuard } from './assistant/composables/usePersonalizationBackGuard.js';

const router = useRouter(), controller = usePersonalization(personalizationApi);
const { state, rulesDirty, load, addRule, resetRules, saveRules, setSetting, toggleMemory, newMemory, editMemory, saveMemory, removeMemory, openHistory, restoreMemory, editProfile, saveProfile, confirmProfile, removeProfile, generateProfile } = controller;
const tab = ref('memories'), search = ref('');
const tabs = [{ id: 'memories', label: '记忆' }, { id: 'profile', label: 'AI 对我的了解' }, { id: 'rules', label: '自定义规则' }];
const isDisabled = path => state.data?.disabledMemories.includes(path);
const filteredMemories = computed(() => state.memories.filter(item => !search.value.trim() || `${item.title} ${MEMORY_TYPES[item.type]}`.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase())));
const profileItems = category => state.data.profile.items.filter(item => item.category === category);
const editorOpen = computed(() => Boolean(state.memoryEditor || state.profileEditor));
function closeEditor() { if (!state.busy) { state.memoryEditor = null; state.profileEditor = null; state.latestMemory = null; state.conflict = false; state.error = ''; } }
function closeHistory() { if (!state.busy) state.historyOpen = false; }
usePersonalizationBackGuard(() => editorOpen.value || state.historyOpen, () => state.busy, () => {
  if (state.historyOpen) closeHistory(); else closeEditor();
});
function dateLabel(value) {
  if (!value) return '尚未更新'; const date = new Date(value);
  return Number.isFinite(date.getTime()) ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date) : '时间未知';
}
const historyAction = value => ({ create: '新增', update: '修改', replace: '修改', append: '追加', patch: '局部修改', delete: '删除', remove: '删除', restore: '恢复', 'before-update': '修改前版本', 'before-restore': '恢复前版本' })[value] || '修改';
async function deleteMemory(memory) {
  try { await showConfirmDialog({ title: '删除记忆', message: `删除“${memory.title}”后，AI 不再参考这条记忆。可以在修改记录中恢复。`, confirmButtonText: '删除', cancelButtonText: '保留' }); } catch { return; }
  await removeMemory(memory);
}
async function deleteProfile(item) {
  try { await showConfirmDialog({ title: '删除这条画像', message: '删除后，自动整理历史时不会重新补回这条结论。', confirmButtonText: '删除', cancelButtonText: '保留' }); } catch { return; }
  await removeProfile(item);
}
async function rebuildProfile() {
  try { await showConfirmDialog({ title: '重新整理历史', message: 'AI 会再次整理历史对话。你已确认、直接填写和删除的内容会保留其处理结果。', confirmButtonText: '开始整理', cancelButtonText: '取消' }); } catch { return; }
  await generateProfile('rebuild');
}
function back() { if (window.history.state?.back) router.back(); else router.replace('/ai'); }
onBeforeRouteLeave(async () => {
  if (state.busy) return false;
  if (!rulesDirty.value && !editorOpen.value) return true;
  try { await showConfirmDialog({ title: '还有未保存的修改', message: '离开会丢弃本页未保存的输入。', confirmButtonText: '放弃并离开', cancelButtonText: '继续编辑' }); return true; } catch { return false; }
});
onMounted(load); onBeforeUnmount(controller.dispose);
</script>

<style scoped>
.personalization-page{height:100vh;height:100dvh;display:flex;flex-direction:column;overflow:hidden;background:var(--theme-bg-primary);color:var(--theme-text-primary);font-size:13px}
button,input,textarea,select{font:inherit}button{cursor:pointer}button:disabled{opacity:.45;cursor:default}button:focus-visible,input:focus-visible,textarea:focus-visible,select:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}
.page-head{display:flex;align-items:center;gap:12px;padding:calc(env(safe-area-inset-top,0px) + 14px) 18px 12px;border-bottom:1px solid var(--theme-border);flex-shrink:0}.page-head>div{flex:1;min-width:0}.page-head h1{font-size:17px;margin:0}.page-head p{font-size:11px;color:var(--theme-text-secondary);margin:4px 0 0}.page-head>button,.editor-head>button{border:0;background:none;color:var(--theme-text-secondary);font-size:20px;width:34px;height:34px;flex-shrink:0}
.tabs{display:flex;gap:4px;width:100%;max-width:720px;align-self:center;padding:12px 16px 0;box-sizing:border-box;flex-shrink:0}.tabs button{flex:1;border:0;border-radius:10px;padding:11px 4px;background:transparent;color:var(--theme-text-secondary);font-size:12px;white-space:nowrap}.tabs button.selected{background:var(--theme-bg-secondary);color:var(--van-primary-color);font-weight:600;box-shadow:0 1px 4px rgba(0,0,0,.04)}
.page-body{flex:1;min-height:0;overflow-y:auto;overscroll-behavior:contain;width:100%;max-width:720px;margin:0 auto;padding:16px 18px max(28px,env(safe-area-inset-bottom));box-sizing:border-box}.setting-row{display:flex;gap:18px;align-items:center;justify-content:space-between;padding:14px 0;border-bottom:1px solid var(--theme-border)}.setting-row strong{font-size:13px;font-weight:600}.setting-row p{font-size:11px;line-height:1.5;color:var(--theme-text-secondary);margin:4px 0 0}.setting-row .van-switch{flex-shrink:0}
.section-actions{display:flex;align-items:center;justify-content:space-between;gap:8px;margin:18px 0 12px;color:var(--theme-text-secondary);font-size:11px}.section-actions>div{display:flex;align-items:center;gap:8px}.primary,.secondary{border:1px solid var(--van-primary-color);border-radius:9px;padding:10px 14px;font-size:12px}.primary{color:#fff;background:var(--van-primary-color)}.secondary{border-color:var(--theme-border);color:var(--theme-text-primary);background:var(--theme-bg-secondary)}.small{padding:7px 10px;font-size:11px}.text-button{border:0;background:none;color:var(--van-primary-color);padding:5px 0;font-size:11px}
.search-box{display:flex;align-items:center;gap:8px;padding:10px 12px;border:1px solid var(--theme-border);border-radius:10px;background:var(--theme-bg-secondary);color:var(--theme-text-secondary)}.search-box input{width:100%;border:0;background:none;color:var(--theme-text-primary);font-size:12px;outline:0}.hint{font-size:11px;color:var(--theme-text-secondary);line-height:1.7;margin:12px 0}.warning{color:var(--van-warning-color,#ee9a01)}
.content-card{border:1px solid var(--theme-border);background:var(--theme-bg-secondary);border-radius:12px;padding:14px;margin:10px 0;overflow-wrap:anywhere}.content-card.muted{opacity:.65}.card-title{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.card-title h2{font-size:13px;font-weight:600;margin:0;flex:1;min-width:0}.tag{font-size:10px;line-height:1.5;padding:2px 7px;border-radius:6px;background:var(--van-primary-color-light,rgba(58,102,224,.08));color:var(--van-primary-color)}.tag.neutral{color:var(--theme-text-secondary);background:var(--theme-bg-tertiary)}.meta{font-size:10px;line-height:1.6;color:var(--theme-text-tertiary);margin:7px 0}.card-actions{display:flex;gap:14px;align-items:center;flex-wrap:wrap;margin-top:10px;padding-top:10px;border-top:1px solid var(--theme-border)}.card-actions button{border:0;background:none;color:var(--van-primary-color);font-size:11px;padding:3px 0}.danger,.card-actions button.danger{color:var(--van-danger-color,#ee0a24)}
.empty{display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:38px 14px;color:var(--theme-text-secondary);font-size:12px;line-height:1.7}.empty>.van-icon{font-size:30px;color:var(--theme-text-tertiary)}.empty p{max-width:320px}.feedback{padding:11px 12px;border-radius:9px;font-size:12px;line-height:1.6;margin:0 0 12px;overflow-wrap:anywhere}.feedback.error{background:rgba(238,10,36,.07);color:var(--van-danger-color,#ee0a24)}.feedback.success{background:rgba(7,193,96,.08);color:var(--van-success-color,#07c160)}
.generation-actions{display:flex;gap:8px;flex-wrap:wrap}.group-title{font-size:12px;font-weight:600;color:var(--theme-text-secondary);margin:22px 2px 8px}.item-text{white-space:pre-wrap;line-height:1.8;font-size:13px;margin:10px 0}.evidence{font-size:11px;line-height:1.7;color:var(--theme-text-secondary)}.evidence summary{cursor:pointer;color:var(--van-primary-color)}.evidence blockquote{margin:10px 0;padding:2px 12px;border-left:2px solid var(--theme-border);white-space:pre-wrap}.evidence blockquote p{margin:6px 0}.evidence footer{font-size:10px;color:var(--theme-text-tertiary)}.intro h2{font-size:15px;margin:12px 0 8px}.intro p{font-size:12px;line-height:1.8;color:var(--theme-text-secondary)}.grow{flex:1}.rule-card textarea{width:100%;box-sizing:border-box;resize:vertical;min-height:76px;border:1px solid var(--theme-border);border-radius:8px;background:var(--theme-bg-primary);color:var(--theme-text-primary);font-size:12px;line-height:1.7;padding:9px 10px;margin-top:12px}.rule-footer{display:flex;align-items:center;justify-content:space-between}.unsaved{color:var(--van-warning-color,#ee9a01)}.save-row{display:flex;justify-content:flex-end;gap:8px;margin-top:20px}
.editor-popup{background:var(--theme-bg-primary);color:var(--theme-text-primary)}.editor-sheet{height:100%;display:flex;flex-direction:column;max-width:720px;margin:auto}.editor-head{display:flex;align-items:center;justify-content:space-between;padding:calc(env(safe-area-inset-top,0px) + 14px) 18px 12px;border-bottom:1px solid var(--theme-border);flex-shrink:0}.editor-head h2{font-size:16px;margin:0}.editor-body{flex:1;min-height:0;overflow:auto;padding:18px 18px max(30px,env(safe-area-inset-bottom));box-sizing:border-box}.field-label{display:flex;flex-direction:column;gap:8px;margin-bottom:18px;font-size:12px;color:var(--theme-text-secondary)}.field-label input,.field-label select,.field-label textarea{width:100%;box-sizing:border-box;min-height:40px;border:1px solid var(--theme-border);border-radius:9px;background:var(--theme-bg-secondary);color:var(--theme-text-primary);padding:10px 12px;line-height:1.7;font-size:13px}.field-label textarea{resize:vertical}.latest-content{border:1px solid var(--theme-border);border-radius:9px;padding:12px;font-size:12px;line-height:1.7;color:var(--theme-text-secondary);margin:14px 0}.latest-content p{white-space:pre-wrap;overflow-wrap:anywhere}.latest-content summary{cursor:pointer;margin-bottom:8px}.source{white-space:pre-wrap;overflow-wrap:anywhere}
@media(max-width:600px){.page-head{padding:calc(env(safe-area-inset-top,0px) + 10px) 12px 10px;gap:8px}.page-head h1{font-size:15px}.page-head p{font-size:10px}.page-body{padding:12px 14px max(24px,env(safe-area-inset-bottom))}.tabs{padding:10px 12px 0}.tabs button{font-size:11px}.content-card{padding:12px}.editor-body{padding:14px 14px max(28px,env(safe-area-inset-bottom))}.save-row{flex-wrap:wrap}.generation-actions button{font-size:11px}}
</style>
