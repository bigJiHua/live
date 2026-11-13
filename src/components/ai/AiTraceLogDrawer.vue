<template>
  <van-popup :show="show" position="bottom" round teleport="body" :style="{ maxHeight: '80%' }" @update:show="value => emit('update:show', value)" @open="load">
    <div class="trace-sheet">
      <div class="trace-head">
        <strong>全链路追溯日志</strong>
        <span class="trace-sub">思考 / 调用 / 工具 / 门控 · 最近 {{ rows.length }} 条</span>
        <button aria-label="关闭日志" @click="emit('update:show', false)"><van-icon name="cross" /></button>
      </div>
      <!-- 2026-11-10 D3：本地过滤胶囊（纯前端，不改变审计顺序） -->
      <div class="trace-filters">
        <button v-for="option in TRACE_FILTERS" :key="option.value" type="button" class="trace-filter" :class="{ on: filter === option.value, 'trace-filter-err': option.value === 'err' }" :aria-pressed="filter === option.value" @click="filter = option.value">{{ option.label }}</button>
      </div>
      <p v-if="loading" class="trace-note" role="status">加载中…</p>
      <p v-else-if="error" class="trace-note trace-error" role="alert">{{ error }}</p>
      <p v-else-if="!filteredRows.length" class="trace-note">{{ rows.length ? '这个筛选下没有记录。' : '还没有审计记录。' }}</p>
      <ul v-else class="trace-list">
        <li v-for="row in filteredRows" :key="row.id">
          <details>
            <summary>
              <span class="trace-time">{{ shortTime(row.createdAt) }}</span>
              <span class="trace-phase" :class="{ 'trace-phase-error': row.status === 'error' }">{{ phaseLabel(row) }}</span>
              <span v-if="row.toolName" class="trace-meta">{{ row.toolName }}</span>
              <span class="trace-meta">{{ row.durationMs != null ? `${row.durationMs}ms` : '' }}</span>
              <span v-if="row.errorCode" class="trace-error">{{ row.errorCode }}</span>
            </summary>
            <div class="trace-detail">
              <p v-if="row.requestId" class="trace-line">请求编号：{{ row.requestId }}</p>
              <p class="trace-line">{{ row.model || row.providerHost || '本地操作' }}<template v-if="row.thinkingEnabled"> · 思考开</template><template v-if="row.usage"> · {{ row.usage.totalTokens ?? `${row.usage.promptTokens ?? '?'}+${row.usage.completionTokens ?? '?'}` }} tokens</template></p>
              <p class="trace-line">请求</p>
              <pre class="trace-json">{{ pretty(row.request) }}</pre>
              <p class="trace-line">响应</p>
              <pre class="trace-json">{{ pretty(row.response) }}</pre>
            </div>
          </details>
        </li>
      </ul>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, computed } from 'vue';
import { getAiLogs } from '@/utils/api/ai';
import { useDialogBackGuard } from '@/composables/useDialogBackGuard';

defineOptions({ name: 'AiTraceLogDrawer' });
const props = defineProps({ show: { type: Boolean, default: false } });
const emit = defineEmits(['update:show']);
// 返回拦截：日志抽屉开着时按返回只关抽屉、路由留在聊天界面（不退 /ai 回主页）
useDialogBackGuard(() => props.show, () => emit('update:show', false));
const rows = ref([]);
const loading = ref(false);
const error = ref('');
// 2026-11-10 D3：胶囊过滤——全部 / 仅错误 / 工具调用 / 门控拦截
const filter = ref('all');
const TRACE_FILTERS = [
  { value: 'all', label: '全部' },
  { value: 'err', label: '仅错误' },
  { value: 'tool', label: '工具' },
  { value: 'gate', label: '门控' },
];
const filteredRows = computed(() => filter.value === 'all' ? rows.value :
  rows.value.filter(row => filter.value === 'err' ? row.status === 'error' : row.phase === filter.value));

const PHASE_LABELS = { provider: 'AI 调用', tool: '工具', gate: '门控拦截', preview: '预览', meta: '辅助', commit: '入账',
  run: '运行', claim: '认领', 'tool-recovery': '工具恢复', 'query-evidence-recovery': '证据恢复', 'language-recovery': '语言恢复',
  'grounded-done': '生成完成', 'admission-wait': '排队', 'audit-write': '审计写入', 'settings-warmup': '设置恢复', 'conversation-activity-prune': '会话清理',
  'numeric-recovery': '数字恢复', 'numeric-tool-limit': '工具超限', 'refresh': '画像刷新', 'v3-http': 'HTTP 错误' };
function phaseLabel(row) { return PHASE_LABELS[row.phase] || row.phase; }
function shortTime(value) {
  const d = new Date(value);
  return isNaN(d) ? '' : `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`;
}
function pretty(value) { return value == null ? '（无）' : typeof value === 'string' ? value : JSON.stringify(value, null, 2); }
async function load() {
  if (loading.value) return;
  loading.value = true; error.value = '';
  try { rows.value = await getAiLogs(100); }
  catch (cause) { error.value = cause?.message || '日志读取失败，请稍后重试'; }
  finally { loading.value = false; }
}
</script>

<style scoped>
.trace-sheet{padding:14px 14px max(12px,env(safe-area-inset-bottom))}
.trace-head{display:flex;align-items:center;gap:8px;margin-bottom:8px}
.trace-head strong{font-size:14px}
.trace-sub{font-size:9px;color:var(--theme-text-secondary);flex:1}
.trace-head button{border:0;background:none;color:var(--theme-text-secondary);padding:6px;font-size:15px;cursor:pointer}
.trace-filters{display:flex;gap:6px;margin:0 0 8px;flex-wrap:wrap}
.trace-filter{font-size:11px;padding:4px 12px;border-radius:999px;border:1px solid var(--theme-border);background:var(--theme-bg-secondary);color:var(--theme-text-secondary);font:inherit;cursor:pointer;min-height:30px}
.trace-filter.on{color:var(--van-primary-color);border-color:var(--van-primary-color);background:var(--van-primary-color-light)}
.trace-filter-err.on{color:var(--van-danger-color);border-color:var(--van-danger-color);background:transparent}
@media(max-width:600px){.trace-filter{min-height:34px}}
.trace-note{font-size:11px;color:var(--theme-text-secondary);text-align:center;padding:16px 0;margin:0}
.trace-error{color:var(--van-danger-color)}
.trace-list{list-style:none;margin:0;padding:0;max-height:60vh;overflow-y:auto}
.trace-list li{border-bottom:1px solid var(--theme-border)}
.trace-list summary{display:flex;align-items:center;gap:8px;flex-wrap:wrap;cursor:pointer;padding:8px 2px;font-size:10px;list-style:none}
.trace-list summary::-webkit-details-marker{display:none}
.trace-time{color:var(--theme-text-secondary);font-variant-numeric:tabular-nums}
.trace-phase{color:var(--van-primary-color);background:var(--van-primary-color-light);padding:1px 6px;border-radius:4px}
.trace-phase-error{color:var(--van-danger-color);background:transparent;border:1px solid var(--van-danger-color)}
.trace-meta{color:var(--theme-text-secondary)}
.trace-detail{padding:2px 2px 10px}
.trace-line{margin:4px 0 2px;font-size:10px;color:var(--theme-text-secondary)}
.trace-json{margin:0 0 6px;padding:8px 10px;border-radius:8px;background:var(--theme-bg-secondary);font-size:9px;line-height:1.6;overflow-x:auto;white-space:pre-wrap;overflow-wrap:anywhere;max-height:200px;overflow-y:auto}
@media(max-width:600px){.trace-sheet{padding:10px 10px max(10px,env(safe-area-inset-bottom))}.trace-head strong{font-size:13px}.trace-sub{font-size:11px;line-height:1.4}.trace-head{gap:6px}.trace-head button{min-width:32px;min-height:32px}.trace-list summary{font-size:11px;gap:6px;min-height:32px;padding:6px 2px}.trace-line,.trace-json{font-size:11px}.trace-json{padding:6px 8px;line-height:1.5}}
button:focus-visible,summary:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}
</style>
