<template>
  <van-popup
    :show="show"
    :position="isNarrow ? 'bottom' : 'right'"
    teleport="body"
    :round="isNarrow"
    :style="isNarrow ? { width: '100%', maxHeight: '76%' } : { width: 'min(86vw, 380px)', height: '100%' }"
    @update:show="emit('update:show', $event)"
  >
    <div class="history-sheet" :class="isNarrow ? 'history-sheet-bottom' : 'history-sheet-side'">
      <div class="history-head">
        <strong>历史对话</strong>
        <button type="button" class="history-new" :disabled="busy" @click="newFromHistory">
          <van-icon name="plus" /><span class="btn-text">新对话</span>
        </button>
        <button type="button" aria-label="关闭历史" @click="emit('update:show', false)"><van-icon name="cross" /></button>
      </div>
      <div class="history-search">
        <van-icon name="search" />
        <input v-model="query" type="search" placeholder="搜索对话标题…" aria-label="搜索对话标题" />
      </div>
      <p v-if="loading" class="history-note" role="status">加载中…</p>
      <p v-else-if="error" class="history-note" role="alert">{{ error }}</p>
      <p v-else-if="!list.length" class="history-note">还没有历史对话。</p>
      <p v-else-if="!filteredHistory.length" class="history-note">没有匹配的对话。</p>
      <div v-else class="history-body">
        <template v-for="group in groupedHistory" :key="group.label">
          <p v-if="group.items.length" class="history-grp">{{ group.label }}</p>
          <ul v-if="group.items.length" class="history-list">
            <li v-for="item in group.items" :key="item.id" :class="{ active: item.id === currentConversationId }">
              <button type="button" class="history-item" :aria-current="item.id === currentConversationId ? 'true' : undefined" @click="emit('resume', item)">
                <span class="history-title">{{ item.title }}</span>
                <span class="history-meta"><span v-if="item.id === currentConversationId" class="history-current">当前</span>{{ item.messageCount }} 条 · {{ shortTime(item.updatedAt) }}</span>
              </button>
              <button type="button" class="history-del" aria-label="删除对话" @click="emit('remove', item)"><van-icon name="delete-o" /></button>
            </li>
          </ul>
        </template>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { computed, ref, watch } from 'vue';

const props = defineProps({
  show: Boolean,
  isNarrow: Boolean,
  list: { type: Array, default: () => [] },
  loading: Boolean,
  error: { type: String, default: '' },
  currentConversationId: { type: String, default: undefined },
  busy: Boolean,
});
const emit = defineEmits(['update:show', 'newConversation', 'resume', 'remove']);
const query = ref('');
watch(() => props.show, show => { if (show) query.value = ''; });

const filteredHistory = computed(() => {
  const keyword = query.value.trim().toLowerCase();
  return keyword ? props.list.filter(item => (item.title || '').toLowerCase().includes(keyword)) : props.list;
});
const groupedHistory = computed(() => {
  const startOfDay = value => { const date = new Date(value); date.setHours(0, 0, 0, 0); return date.getTime(); };
  const today = startOfDay(Date.now());
  const groups = [{ label: '今天', items: [] }, { label: '昨天', items: [] }, { label: '更早', items: [] }];
  for (const item of filteredHistory.value) {
    const time = Number(item.updatedAt) || Date.parse(item.updatedAt) || 0;
    const day = time ? startOfDay(time) : 0;
    if (day >= today) groups[0].items.push(item);
    else if (day >= today - 86400000) groups[1].items.push(item);
    else groups[2].items.push(item);
  }
  return groups;
});

function newFromHistory() {
  if (props.busy) return;
  emit('update:show', false);
  emit('newConversation');
}
function shortTime(value) {
  const date = new Date(value);
  return isNaN(date) ? '' : `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}
</script>

<style scoped>
button{font:inherit;cursor:pointer}button:disabled{opacity:.45;cursor:default}
button:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}
.history-sheet{padding:14px 14px max(12px,env(safe-area-inset-bottom));color:var(--theme-text-primary)}
.history-sheet-side{display:flex;flex-direction:column;height:100%;box-sizing:border-box;padding-top:max(14px,env(safe-area-inset-top))}
.history-sheet-side .history-note{flex:1;display:flex;align-items:center;justify-content:center}
.history-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px}
.history-head strong{font-size:14px}
.history-head button{border:0;background:none;color:var(--theme-text-secondary);padding:6px;font-size:15px;cursor:pointer}
.history-note{font-size:11px;color:var(--theme-text-secondary);text-align:center;padding:16px 0;margin:0}
.history-list{list-style:none;margin:0;padding:0}
.history-list li{display:flex;align-items:center;gap:6px;margin:2px 0;padding:0;border-bottom:1px solid var(--theme-border);border-radius:8px;transition:background .15s}
.history-list li:hover{background:var(--theme-bg-tertiary)}
.history-list li.active{background:var(--theme-primary-light,var(--theme-bg-tertiary))}
.history-list li.active .history-title{color:var(--van-primary-color);font-weight:550}
.history-current{display:inline-block;margin-right:4px;padding:0 4px;border-radius:4px;background:var(--van-primary-color);color:var(--theme-button-primary-text,#fff);font-size:9px;line-height:14px}
.history-item{flex:1;min-width:0;display:flex;flex-direction:column;gap:2px;padding:12px 10px;border:0;background:none;color:var(--theme-text-primary);font:inherit;text-align:left;cursor:pointer}
.history-title{font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.history-meta{font-size:9px;color:var(--theme-text-secondary)}
.history-del{border:0;background:none;color:var(--theme-text-secondary);padding:12px;font-size:17px;cursor:pointer;flex-shrink:0}
.history-search{display:flex;align-items:center;gap:6px;margin:0 0 8px;padding:0 10px;border:1px solid var(--theme-border);border-radius:9px;background:var(--theme-bg-secondary);min-height:36px}
.history-search .van-icon{color:var(--theme-text-tertiary);font-size:13px;flex-shrink:0}
.history-search input{flex:1;min-width:0;border:0;background:none;color:inherit;font:inherit;font-size:12px;outline:none;padding:8px 0}
.history-new{display:inline-flex;align-items:center;gap:4px;margin-left:auto;margin-right:6px;border:1px solid var(--van-primary-color);border-radius:999px;background:var(--van-primary-color-light);color:var(--van-primary-color);font:inherit;font-size:11px;padding:4px 10px;min-height:30px;cursor:pointer}
.history-new:disabled{opacity:.5;cursor:default}
.history-grp{font-size:10px;color:var(--theme-text-tertiary);margin:8px 2px 2px;letter-spacing:1px}
.history-sheet-bottom{display:flex;flex-direction:column;box-sizing:border-box;padding-bottom:max(12px,env(safe-area-inset-bottom));max-height:76vh}
.history-body{flex:1;min-height:0;overflow-y:auto}
@media(max-width:600px){
  .history-sheet{padding:12px 12px max(10px,env(safe-area-inset-bottom))}
  .history-sheet-side{padding-top:max(12px,env(safe-area-inset-top))}
  .history-head strong{font-size:13px}
  .history-meta{font-size:10px}
  .history-search input{font-size:16px}
  .history-search{min-height:44px}
  .history-new{min-height:40px;min-width:44px;justify-content:center}
  .btn-text{display:none}
}
</style>
