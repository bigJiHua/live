<template>
  <article :data-mid="item.id" class="chat-message" :class="[item.role, { 'reply-animated': hasRevealed, 'card-only': (item.draft || item.ocr || item.proposal || item.turn?.preview || item.financialCheck) && !item.pending && !item.error }]">
    <span v-if="item.role === 'assistant' && !item.continuation" class="avatar" aria-hidden="true"><van-icon name="chat-o" /></span>
    <div class="message-body">
      <span v-if="item.role === 'assistant' && item.continuation" class="speaker speaker-cont">财务助手 · 续 {{ item.continuation }}</span>
      <span v-else-if="item.role === 'assistant'" class="speaker">财务助手<button v-if="activitiesDone" type="button" class="act-toggle" :aria-label="item.activitiesOpen === true ? '收起处理过程' : '展开处理过程'" :aria-expanded="item.activitiesOpen === true" @click="emit('toggleActivities', item)">{{ item.activitiesOpen === true ? '收起' : item.activitiesTotal ? `展开 · ${item.activitiesTotal}` : '展开' }}</button></span>
      <ul v-if="item.activities?.length && (item.activitiesOpen === true || !activitiesDone)" class="activity-stream" aria-label="AI 处理过程">
        <li v-for="(act, i) in item.activities" :key="i" :class="{ done: act.done }">
          <span class="act-label">{{ act.label }}</span>
          <span v-if="act.done" class="act-dur">{{ act.duration ? `${(act.duration / 1000).toFixed(1)}s` : '' }}</span>
          <span v-else class="act-live">{{ Math.max(0, Math.round((activityClock - act.at) / 1000)) }}s</span>
        </li>
      </ul>
      <button v-if="item.imagePreview" type="button" class="chat-image-button" aria-label="查看已发送图片" @click="emit('previewImage', item.imagePreview)"><img :src="item.imagePreview" class="chat-image" alt="发送的支付截图或账单图片" /></button>
      <details v-if="showThinking" class="thinking-block" :class="{ 'thinking-active': thinkingActive }" :open="thinkingOpen" @toggle="toggleThinkingDetails">
        <summary><span>{{ thinkingLabel }}</span><span v-if="thinkingDuration" class="thinking-duration" aria-hidden="true">{{ thinkingDuration }}s</span></summary>
        <p v-if="thinkingProvisional" class="thinking-status thinking-provisional">{{ item.pending ? '思考内容正在生成，尚未最终核验。' : '中断前的思考内容，尚未最终核验。' }}</p>
        <div v-if="thinkingText" ref="thinkingTextRef" class="thinking-text" @scroll="onThinkingScroll">{{ thinkingText }}</div>
        <p v-else class="thinking-status" role="status">{{ thinkingNotice }}</p>
      </details>
      <div v-if="item.pending && !item.activities?.length" class="thinking" role="status" aria-label="正在回复"><i /><i /><i /></div>
      <!-- 2026-11-10 B2：连续逐笔明细行折叠为「前 3 行 + 展开内滚」，其余正文照常 markdown；
           分段在 Vue 层完成（details 是真实模板），不触碰 v-html/sanitize 管道 -->
      <div v-else-if="item.role === 'assistant' && item.text && !item.error" class="reply-content" :class="{ 'reply-revealing': isRevealing }" :aria-busy="isRevealing" aria-live="off">
        <template v-for="(seg, si) in displaySegments" :key="si">
          <div v-if="seg.flow" class="flow-wrap">
            <div class="flow-rows flow-head">{{ seg.flow.head.join('\n') }}</div>
            <details class="flow-block">
              <summary class="flow-summary">
                <span class="flow-badge">部分数据</span>
                <strong>逐笔明细</strong>
                <span class="flow-cap">{{ seg.flow.total }} 行 · {{ seg.flow.range }}</span>
                <span class="flow-more">展开全部 {{ seg.flow.total }} 行</span><span class="flow-less">收起明细</span>
                <van-icon name="arrow-down" class="flow-chev" />
              </summary>
              <div class="flow-rows flow-rest">{{ seg.flow.rest.join('\n') }}</div>
            </details>
          </div>
          <!-- Mermaid 正文图表独立渲染，与下方业务 AiChart 并存；失败保留代码 -->
          <AiMermaid v-else-if="seg.mermaid" :code="seg.mermaid" :active="!isRevealing && item.pending !== true" />
          <div v-else class="message-text markdown-body" v-html="seg.html"></div>
        </template>
        <div v-if="isRevealing" class="reply-reveal-controls"><span class="reply-cursor" aria-hidden="true"></span><button type="button" class="reply-show-all" @click="showAll">立即显示全部</button></div>
      </div>
      <!-- 2026-11-11：状态卡场景正文不再重复整段错误文案（与卡片主文案冲突），原因句收进「详情」 -->
      <p v-else-if="item.text && !((item.error || item.turn?.state === 'uncertain') && item.requestId)" class="message-text" :class="{ 'message-error': item.error }">{{ item.text }}</p>
      <p v-if="item.error && providerDetailText(item) && !item.requestId" class="image-retry-note provider-detail">供应商返回：{{ providerDetailText(item) }}</p>
      <p v-if="item.role === 'assistant' && item.operationOutcome && item.operationOutcome.state !== 'none' && item.operationOutcome.artifacts.length" class="operation-outcome" role="status"><span>生成时状态：</span>{{ item.operationOutcome.notice }}</p>
      <AiChart v-if="!isRevealing && item.chart && chartDataset(item)" class="reply-supplement" :chart="item.chart" :dataset="chartDataset(item)" />
      <p v-if="item.chartNotice" class="draft-notice" role="status">{{ item.chartNotice }}</p>
      <p v-if="item.draftNotice" class="draft-notice" role="status">{{ item.draftNotice }}</p>
      <AiEvidenceCard v-for="(evidence, index) in isRevealing ? [] : item.evidence || []" :key="index" class="reply-supplement" :evidence="evidence" />
      <AiProposalCard v-if="item.proposal" :proposal="item.proposal" :disabled="transferBusy"
        @change="emit('updateProposal', item, $event)" @working="emit('cardWorking', item, 'proposal', $event)" />
      <AiDraftCard v-if="item.draft" :draft="item.draft" :disabled="transferBusy"
        @change="emit('updateDraft', item, $event)" @working="emit('cardWorking', item, 'draft', $event)" />
      <AiOcrCard v-if="item.ocr" :ocr="item.ocr" :has-draft="Boolean(item.draft)" />
      <AiPendingTransactionCard v-if="pendingTaskVisible" :task="item.pendingTask"
        :completed-indices="item.pendingCompletedIndices || []"
        :disabled="busy || transferBusy" :unavailable="item.pendingTaskUnavailable"
        @resume="emit('resumePendingTask', item, $event)" />
      <AiFinancialCheckCard v-if="financialInputOpen" :financial-check="item.financialCheck"
        :fields="item.inputCandidate?.fields || {}" :choices="financialChoices"
        :conversation-id="conversationId" :source-candidate-id="item.inputCandidate?.sourceCandidateId || item.inputCandidate?.id"
        :facts-locked="Boolean(item.inputCandidate?.pendingTaskId)"
        :source-candidate-expires-at="item.inputCandidate?.expiresAt"
        :disabled="transferBusy || item.localDraftStatus === 'uncertain'"
        @checked="emit('applyFinancialCheck', item, $event)" @create="emit('createFinancialDraft', item, $event)"
        @cancel="emit('cancelFinancialCandidate', item)" @working="emit('cardWorking', item, 'financial', $event)"
        @resume="emit('resumePendingTask', item, item.inputCandidate.pendingTaskIndex ?? 0)" />
      <p v-if="item.financialError" class="draft-notice" role="alert">{{ item.financialError }}</p>
      <button v-if="item.localDraftStatus === 'uncertain'" class="retry" :disabled="transferBusy"
        @click="emit('recoverFinancialCandidate', item)">读取草稿状态</button>
      <!-- 2026-11-10 B3：失败/回执等待收敛为一张状态卡——图标+一行主文案+状态二选一主按钮，
           供应商 detail/错误码/限频说明收进「详情」；状态机与重试/回执逻辑不动 -->
      <template v-if="(item.error || item.turn?.state === 'uncertain') && item.requestId">
        <div class="turn-card" :class="item.turn?.state === 'failed' ? 'tc-fail' : 'tc-unc'">
          <span class="tc-ic"><van-icon :name="item.turn?.state === 'failed' ? 'warning-o' : 'clock-o'" /></span>
          <div class="tc-main">

            <div class="tc-grid">
              <div class="tc-left">
                <div class="tc-t">{{ item.turn?.state === 'failed' ? '这条回复没有成功' : item.receiptBusy ? '正在读取最终结果…' : '已停止，正在核对最终结果' }}</div>
                <div class="tc-d">{{ item.turn?.state === 'failed' ? '没有重新请求 AI，你的问题还在原处。' : 'AI 那边可能已经算完了，正在读取落档结果，不会重发请求。' }}</div>
              </div>
              <button v-if="item.turn?.state === 'failed' && item.turn.message" class="tc-btn tc-primary" :disabled="busy || item.receiptBusy" :title="item.turn.message" @click="emit('retryTurn', item)">重试此问题</button>
              <button v-else class="tc-btn" :disabled="busy || item.receiptBusy" @click="emit('recoverReply', item)">{{ item.receiptBusy ? '读取中…' : '立即读取' }}</button>
            </div>
            <p v-if="item.turn?.state === 'failed' && item.turn.message" class="image-retry-note">将重试：{{ item.turn.message.slice(0, 80) }}{{ item.turn.message.length > 80 ? '…' : '' }}</p>
            <div class="tc-detail-row">
              <button type="button" class="tc-detail" @click="detailsOpen = !detailsOpen">{{ detailsOpen ? '收起详情' : '详情' }}<van-icon :name="detailsOpen ? 'arrow-up' : 'arrow-down'" /></button>
            </div>
            <div v-if="detailsOpen" class="tc-more">
              <p v-if="item.text">原因：{{ item.text }}</p>
              <p v-if="providerDetailText(item)">供应商返回：{{ providerDetailText(item) }}</p>
              <p v-if="item.lastError?.code">错误码：{{ item.lastError.code }}<template v-if="item.lastError.traceId"> · trace {{ item.lastError.traceId }}</template></p>
              <p v-if="item.lastError?.code === 'AI_RATE_LIMIT'">请求频率超过当前窗口上限，稍候再点重试。</p>
              <p v-if="item.turn?.state === 'uncertain'">连接中断后会自动核对最终结果；服务恢复后也可点「立即读取」。</p>
            </div>
          </div>
        </div>
        <p v-if="item.receiptBusy" class="image-retry-note" role="status">正在读取回执，不会重新请求 AI。</p>
        <p v-else-if="item.receiptNotice" class="image-retry-note" role="status">{{ item.receiptNotice }}</p>
        <p v-else-if="item.turn?.state === 'uncertain'" class="image-retry-note">自动等待回执中，不会重新请求 AI。</p>
      </template>
      <div v-if="item.authorizationRequest && !item.authorizationDenied" class="authorization-card">
        <strong>{{ item.authorizationRequest.access === 'operate' ? '请求使用操作能力' : '请求读取数据' }}</strong>
        <p>{{ item.authorizationRequest.reason }}</p>
        <!-- 2026-11-12：卡片只留授权入口（拒绝易误触）；拒绝走授权弹窗内的「拒绝」按钮，语义与二次确认都更清晰 -->
        <button type="button" class="grant-cta" :disabled="busy" @click="emit('openGrantDialog', item)">选择有效期并授权</button>
      </div>
      <div v-else-if="item.lastError && !item.authorizationDenied && (item.lastError.code === 'AI_GRANT_REQUIRED' || item.lastError.code === 'AI_DOMAIN_NOT_GRANTED')" class="grant-chip">
        <button class="retry" :disabled="busy" @click="emit('openGrantDialog', item)">授权并继续</button>
      </div>
      <div v-if="!isRevealing && !item.pending && traceRows(item).length" class="trace-list reply-supplement">
        <template v-for="row in visibleTraceRows(item)" :key="row.key">
          <AiDatasetCard v-if="row.dataset" :dataset="row.dataset" />
          <div v-else class="trace-item">
            <span class="trace-badge choice">{{ row.badge || '候选' }}</span>
            <span class="trace-title">{{ row.title }}</span>
            <span v-if="Number.isInteger(row.records)" class="trace-records">{{ row.records }} 条</span>
          </div>
        </template>
        <button v-if="traceRows(item).length > 3" type="button" class="traces-more" :class="{ open: tracesOpen === true }" @click="tracesOpen = !tracesOpen">{{ tracesOpen === true ? '收起数据论据' : `展开全部 ${traceRows(item).length - 3} 条数据论据` }}<van-icon name="arrow-down" /></button>
      </div>

      <div v-if="item.role === 'user' && item.text" class="message-actions">
        <button type="button" class="message-action" :disabled="transferBusy" @click="copyMessage(item)">复制</button>
        <span v-if="item.sentAt" class="speaker-time">{{ timeLabel(item.sentAt) }}</span>
      </div>
      <div v-if="item.role === 'assistant' && !item.pending && !item.error && (item.text || item.usage || canRegenerate || canReask)" class="message-actions">
        <span v-if="item.sentAt" class="speaker-time">{{ timeLabel(item.sentAt) }}</span>
        <button v-if="item.text" type="button" class="message-action" :disabled="transferBusy" @click="copyMessage(item)">复制</button>
        <button v-if="canRegenerate" type="button" class="message-action" :disabled="busy || transferBusy" :title="item.turn?.message" @click="emit('regenerate', item)">{{ item.text ? '重新生成' : '重试此问题' }}</button>
        <button v-else-if="canReask" type="button" class="message-action" :disabled="busy || transferBusy" @click="emit('reaskTurn', item)">以此重问</button>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue';
import { useReplyReveal } from '../composables/useReplyReveal.js';
import AiChart from '@/components/ai/AiChart.vue';
import AiMermaid from '@/components/ai/AiMermaid.vue';
import AiDatasetCard from '@/components/ai/AiDatasetCard.vue';
import AiEvidenceCard from '@/components/ai/AiEvidenceCard.vue';
import AiProposalCard from '@/components/ai/AiProposalCard.vue';
import AiDraftCard from '@/components/ai/AiDraftCard.vue';
import AiOcrCard from '@/components/ai/AiOcrCard.vue';
import AiFinancialCheckCard from '@/components/ai/AiFinancialCheckCard.vue';
import AiPendingTransactionCard from '@/components/ai/AiPendingTransactionCard.vue';
import { renderAiMarkdown, getAiMarkdownCodeLines, getAiMarkdownMermaidBlocks } from '@/utils/aiMarkdown';
import { copyText } from '@/utils/clipboard';
import { aiDatasetIdentity, findAiQueryDataset } from '@/utils/aiDatasetDisplay';
import { AI_DOMAIN_LABELS } from '@/utils/aiTurn';

const props = defineProps({
  item: { type: Object, required: true },
  busy: Boolean,
  transferBusy: Boolean,
  activityClock: { type: Number, default: () => Date.now() },
  canRegenerate: Boolean,
  canReask: Boolean,
  conversationId: String,
  financialInputOpen: Boolean,
  pendingTaskVisible: Boolean,
  financialChoices: { type: Object, default: () => ({ accounts: [], categories: [] }) },
});
const emit = defineEmits(['toggleActivities', 'previewImage', 'retryTurn', 'recoverReply',
  'openGrantDialog', 'regenerate', 'reaskTurn', 'updateProposal', 'updateDraft', 'cardWorking',
  'applyFinancialCheck', 'createFinancialDraft', 'cancelFinancialCandidate', 'recoverFinancialCandidate',
  'resumePendingTask',
  'revealProgress', 'revealComplete']);
const { displayedText, isRevealing, hasRevealed, showAll } = useReplyReveal(() => props.item, {
  onProgress: (item, progress, owner) => emit('revealProgress', item, { progress, ...owner }),
  onComplete: (item, reason, owner) => emit('revealComplete', item, { reason, ...owner }),
});
const displaySegments = computed(() => messageSegments(displayedText.value));
const thinkingOpen = ref(false);
const thinkingTextRef = ref(null);
let thinkingTouched = false, thinkingOpened = false, thinkingFollowing = true, thinkingLastTop = 0;
watch(() => props.item.requestId || props.item.id, () => {
  thinkingOpen.value = false; thinkingTouched = false; thinkingOpened = false;
  thinkingFollowing = true; thinkingLastTop = 0;
});
const thinkingState = computed(() => props.item.thinkingState || null);
const thinkingText = computed(() => props.item.thinkingLive || props.item.thinking || '');
const thinkingProvisional = computed(() => Boolean(props.item.thinkingLive));
watch(thinkingText, () => {
  const node = thinkingTextRef.value;
  if (!props.item.thinkingLive || !thinkingFollowing || node && node.scrollHeight - node.scrollTop - node.clientHeight > 24) return;
  const owner = props.item.requestId || props.item.id;
  nextTick(() => {
    if (!props.item.thinkingLive || !thinkingFollowing || owner !== (props.item.requestId || props.item.id)) return;
    const text = thinkingTextRef.value;
    if (text) { text.scrollTop = text.scrollHeight; thinkingLastTop = text.scrollTop; }
  });
});
function onThinkingScroll(event) {
  const node = event.target;
  if (node.scrollTop < thinkingLastTop - 3) thinkingFollowing = false;
  else if (node.scrollTop > thinkingLastTop + 3) thinkingFollowing = node.scrollHeight - node.scrollTop - node.clientHeight < 24;
  thinkingLastTop = node.scrollTop;
}
watch(() => [props.item.pending, props.item.thinkingLive, props.item.thinkingState], () => {
  if (props.item.pending && (props.item.thinkingLive || props.item.thinkingState) && !thinkingTouched && !thinkingOpened) {
    thinkingOpened = true; thinkingOpen.value = true;
  }
  if (!props.item.pending && !thinkingTouched && !props.item.thinkingLive) thinkingOpen.value = false;
}, { immediate: true });
function toggleThinkingDetails(event) {
  if (event.target.open === thinkingOpen.value) return;
  thinkingTouched = true; thinkingOpen.value = event.target.open;
}
const thinkingActive = computed(() => props.item.pending === true && ['started', 'working'].includes(thinkingState.value?.state));
const showThinking = computed(() => props.item.role === 'assistant' && (thinkingText.value || thinkingState.value?.observedAny === true || thinkingState.value?.observed === true || props.item.pending && thinkingState.value));
const thinkingLabel = computed(() => thinkingState.value?.state === 'interrupted' ? '思考已中断'
  : thinkingActive.value ? '深度思考中…'
    : thinkingState.value?.state === 'completed' ? props.item.thinking || thinkingState.value.observedAny === true || thinkingState.value.observed === true ? '已完成深度思考' : '处理完成'
      : props.item.pending ? '深度思考中…' : '已深度思考');
const thinkingDuration = computed(() => {
  const state = thinkingState.value;
  if (!state || state.durationKnown === false) return '';
  const stored = Number.isFinite(state.totalElapsedMs) ? Math.max(0, state.totalElapsedMs) : state.elapsedMs || 0;
  const live = thinkingActive.value && Number.isFinite(state.startedAt) ? Math.max(0, props.activityClock - state.startedAt) : 0;
  return (Math.max(stored, (state.completedElapsedMs || 0) + live) / 1000).toFixed(1);
});
const thinkingNotice = computed(() => thinkingState.value?.state === 'interrupted' ? '本轮思考已中断，请查看请求状态。'
  : thinkingActive.value ? '正在整理思路，完成后继续处理回复。'
    : props.item.pending ? '本轮思考已结束，正在继续处理结果。' : '本轮处理已完成。');
// Details and evidence expansion belong to this rendered message, not its business payload.
const detailsOpen = ref(props.item.tcDetailOpen === true);
const tracesOpen = ref(props.item.tracesOpen === true);
const activitiesDone = computed(() => Boolean(props.item.activities?.length && props.item.activities.every(act => act.done)));

function toolTraceLabel(trace, item) {
  const contextTools = { memory_list: '记忆目录', memory_read: '读取记忆', memory_search: '搜索记忆',
    memory_write: '保存记忆', memory_delete: '删除记忆', history_search: '查找历史对话', history_read: '读取历史对话' };
  return findAiQueryDataset(item.datasets || [], trace)?.title ||
    contextTools[trace.tool] || AI_DOMAIN_LABELS[trace.domain] || (trace.tool === 'list_choices' ? '记账候选' : '业务查询');
}

function providerDetailText(item) {
  const detail = item?.providerDetail;
  if (!detail || typeof detail !== 'object') return '';
  const head = [Number.isInteger(detail.status) ? `HTTP ${detail.status}` : '', detail.code, detail.cause].filter(Boolean).join(' · ');
  const message = typeof detail.message === 'string' ? detail.message : '';
  return [head, message].filter(Boolean).join(head && message ? '：' : '');
}

const AI_FLOW_LINE_RE = /^\d{4}-\d{2}-\d{2}｜/;
function messageSegments(value) {
  const text = String(value || '').replace(/\r\n|\r/g, '\n');
  const lines = text.split('\n');
  const codeLines = getAiMarkdownCodeLines(text);
  const segments = [];
  // 追加172：```mermaid 围栏整体抽成独立段交给 AiMermaid（白名单渲染/失败回退代码块）；
  // 提取用与 codeLines 同一套 lexer 计行法，围栏内外行号不会互相串
  const mermaidByStart = new Map(), mermaidCovered = new Set();
  for (const block of getAiMarkdownMermaidBlocks(text)) {
    mermaidByStart.set(block.start, block);
    for (let index = block.start; index < block.end; index++) mermaidCovered.add(index);
  }
  let markdown = [], flow = [];
  const flushMarkdown = () => {
    if (!markdown.length) return;
    const html = renderAiMarkdown(markdown.join('\n'));
    if (html) segments.push({ html });
    markdown = [];
  };
  const flushFlow = () => {
    if (!flow.length) return;
    if (flow.length < 4) { markdown.push(...flow); flow = []; return; }
    flushMarkdown();
    const dates = flow.map(line => line.slice(0, 10));
    const range = dates[0] === dates[dates.length - 1] ? dates[0] : `${dates[0]} 至 ${dates[dates.length - 1]}`;
    segments.push({ flow: { head: flow.slice(0, 3), rest: flow.slice(3), total: flow.length, range } });
    flow = [];
  };
  lines.forEach((line, index) => {
    if (mermaidByStart.has(index)) { flushFlow(); flushMarkdown(); segments.push({ mermaid: mermaidByStart.get(index).code }); return; }
    if (mermaidCovered.has(index)) { flushFlow(); return; }
    if (!codeLines.has(index) && AI_FLOW_LINE_RE.test(line)) flow.push(line);
    else { flushFlow(); markdown.push(line); }
  });
  flushFlow();
  flushMarkdown();
  return segments;
}
// 图表直接跟着答复正文展示，所属数据集下方卡片继续承担明细/口径
function chartDataset(item) {
  return item.chart ? findAiQueryDataset(item.datasets || [], item.chart) : null;
}
// 轨迹行与数据卡合并渲染（2026-11-08）：query_data 行内嵌对应数据集（点击逐卡独立展开），
// list_choices 行仅披露不可展开；开合状态由 AiDatasetCard 自持，不再有整组同步。
function traceRows(item) {
  const datasets = item.datasets || [];
  const used = new Set();
  const rows = (item.toolTrace || []).map((trace, index) => {
    if (trace.tool !== 'query_data') return { key: `t${index}`, dataset: null, title: toolTraceLabel(trace, item), records: trace.records,
      badge: trace.tool.startsWith('memory_') ? '记忆' : trace.tool.startsWith('history_') ? '历史' : '候选' };
    const matching = findAiQueryDataset(datasets, trace);
    const dataset = matching && !used.has(aiDatasetIdentity(matching)) ? matching : null;
    if (dataset) used.add(aiDatasetIdentity(dataset));
    return { key: trace.queryId || `t${index}`, dataset, title: dataset?.title || toolTraceLabel(trace, item), records: dataset?.rows.length ?? trace.records };
  });
  datasets.forEach((dataset, index) => {
    if (!used.has(aiDatasetIdentity(dataset))) rows.push({ key: dataset.queryMeta?.queryId || `d${index}`, dataset, title: dataset.title, records: dataset.rows.length });
  });
  return rows;
}
function visibleTraceRows(item) {
  const rows = traceRows(item);
  return tracesOpen.value ? rows : rows.slice(0, 3);
}
function copyMessage(item) { copyText(item.text || '', '已复制'); }
function timeLabel(value) {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return '';
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}
</script>

<style scoped>
.chat-image-button{display:block;padding:0;border:0;background:none;cursor:zoom-in;margin-bottom:8px;max-width:100%}.chat-image{display:block;max-width:min(100%,320px);max-height:300px;width:auto;height:auto;object-fit:contain;border-radius:10px;border:1px solid var(--theme-border)}.image-retry-note{font-size:9px;line-height:1.6;color:var(--theme-text-secondary);margin:0 0 8px}
.card-only .message-body :deep(.draft-card){margin-top:0}.card-only .message-body :deep(.evidence-card + .draft-card){margin-top:10px}
.draft-notice{margin:0 0 8px;font-size:11px;line-height:1.7;color:var(--theme-text-secondary)}
.operation-outcome{margin:0 0 8px;padding:7px 10px;border:1px solid var(--theme-border);border-radius:8px;background:var(--theme-bg-secondary);font-size:12px;line-height:1.6;color:var(--theme-text-secondary)}
button{font:inherit;cursor:pointer}button:disabled{opacity:.45;cursor:default}
.chat-message{display:flex;align-items:flex-start;gap:10px;margin-bottom:18px}.avatar{flex-shrink:0;display:grid;place-items:center;width:26px;height:26px;border-radius:9px;background:var(--van-primary-color-light);color:var(--van-primary-color);font-size:15px}.message-body{min-width:0;flex:1}.speaker{display:flex;align-items:center;font-size:12px;color:var(--theme-text-secondary);margin:2px 0 6px}.speaker-time{margin-left:auto;font-size:10px;font-variant-numeric:tabular-nums;opacity:.85}.act-toggle{display:inline-flex;align-items:center;margin-left:7px;padding:2px 8px;border:1px solid var(--theme-border);border-radius:7px;background:var(--theme-bg-secondary);color:var(--theme-text-secondary);font:inherit;font-size:10px;line-height:1.4;cursor:pointer;vertical-align:middle}.act-toggle:hover{color:var(--van-primary-color);border-color:var(--van-primary-color)}.message-text{margin:0;font-size:14px;line-height:1.75;white-space:pre-wrap;overflow-wrap:anywhere}.chat-message.user{justify-content:flex-end}.user .message-body{flex:0 1 auto;max-width:85%}.user .speaker{text-align:right}.user .message-text{padding:10px 14px;border:1px solid rgba(var(--theme-primary-rgb,58,102,224),.22);border-radius:14px 14px 4px 14px;background:rgba(var(--theme-primary-rgb,58,102,224),.12);box-shadow:0 1px 4px rgba(0,0,0,.05)}
.assistant .message-text{padding:10px 14px;border:1px solid var(--theme-border);border-radius:4px 14px 14px 14px;background:var(--theme-bg-secondary)}.grant-chip{margin:2px 0 6px}.retry{background:none;border:0;padding:6px 0;color:var(--van-primary-color);font-size:11px}.thinking{display:flex;gap:5px;padding:8px 0}.thinking i{width:6px;height:6px;background:var(--theme-text-secondary);border-radius:50%;animation:pulse 1.1s infinite}.thinking i:nth-child(2){animation-delay:.15s}.thinking i:nth-child(3){animation-delay:.3s}@keyframes pulse{50%{opacity:.25;transform:translateY(-3px)}}
.activity-stream{list-style:none;margin:0 0 8px;padding:0;display:flex;flex-direction:column;gap:2px}
.activity-stream li{display:flex;align-items:baseline;gap:6px;font-size:10px;line-height:1.7;color:var(--theme-text-secondary);min-width:0}
.activity-stream li::before{content:'';width:4px;height:4px;border-radius:50%;background:currentColor;opacity:.5;flex-shrink:0;align-self:center}
.activity-stream li.done::before{content:'✓';width:auto;height:auto;background:none;border-radius:0;font-size:9px;opacity:.6}
.activity-stream .act-label{min-width:0;overflow-wrap:anywhere}
.activity-stream .act-dur,.activity-stream .act-live{font-variant-numeric:tabular-nums;opacity:.65;flex-shrink:0}
.thinking-block{margin:0 0 8px;border:1px solid var(--theme-border);border-radius:10px;background:var(--theme-bg-secondary);overflow:hidden}
.thinking-block summary{cursor:pointer;user-select:none;list-style:none;padding:7px 10px;font-size:10px;color:var(--theme-text-secondary)}
.thinking-block summary::-webkit-details-marker{display:none}
.thinking-block summary{display:flex;align-items:center;gap:6px}
.thinking-duration{margin-left:auto;font-size:10px;font-variant-numeric:tabular-nums;opacity:.72}
.thinking-active{border-color:rgba(var(--theme-primary-rgb,58,102,224),.25)}
.thinking-status{margin:0;padding:2px 10px 9px;font-size:11px;line-height:1.8;color:var(--theme-text-secondary)}
.reply-content{min-width:0}
.reply-revealing{animation:reply-arrive .18s ease-out}
.reply-reveal-controls{display:flex;align-items:center;gap:8px;padding:4px 2px 0;min-height:20px}
.reply-cursor{display:inline-block;width:2px;height:14px;background:var(--van-primary-color);border-radius:1px;animation:reply-cursor 1s ease-in-out infinite}
.reply-show-all{border:0;background:none;padding:3px 5px;color:var(--theme-text-secondary);font-size:10px;line-height:1.4;border-radius:4px}
.reply-show-all:hover,.reply-show-all:focus-visible{color:var(--van-primary-color);background:var(--theme-bg-secondary)}
.reply-animated .reply-supplement{animation:reply-arrive .18s ease-out}
@keyframes reply-arrive{from{opacity:.35;transform:translateY(3px)}to{opacity:1;transform:translateY(0)}}
@keyframes reply-cursor{50%{opacity:.2}}
.thinking-block summary::before{content:'▸ ';font-size:9px}
.thinking-block[open] summary::before{content:'▾ '}
.thinking-text{padding:2px 10px 8px;font-size:11px;line-height:1.8;color:var(--theme-text-secondary);white-space:pre-wrap;overflow-wrap:anywhere;max-height:220px;overflow-y:auto}
.trace-list{margin:2px 0 0}
.trace-list .trace-item:not(.data-card){display:flex;align-items:center;gap:5px;padding:1px 0;font-size:10px;color:var(--theme-text-tertiary)}
.trace-badge{flex-shrink:0;font-size:9px;line-height:1;padding:3px 5px;border-radius:3px}
.trace-badge.data{color:var(--van-primary-color);background:var(--theme-primary-light,rgba(25,137,250,.08))}
.trace-badge.choice{color:var(--van-success-color,#07c160);background:rgba(7,193,96,.08)}
.trace-title{min-width:0;flex:1;overflow-wrap:anywhere}
.trace-records{font-variant-numeric:tabular-nums;opacity:.8;flex-shrink:0;font-size:9.5px;color:var(--theme-text-tertiary)}
.traces-more{display:flex;align-items:center;gap:4px;min-height:26px;padding:2px 0;border:0;background:none;font:inherit;font-size:10.5px;color:var(--theme-text-tertiary);text-align:left;cursor:pointer}
.traces-more .van-icon{font-size:9px;transition:transform .2s}
.traces-more.open .van-icon{transform:rotate(180deg)}
.message-actions{display:flex;align-items:center;gap:10px;margin-top:2px}
.user .message-actions,.assistant .message-actions{justify-content:flex-end}
.assistant .message-actions .speaker-time{margin-right:auto}
.message-actions .speaker-time{margin-left:0}
.message-action{border:0;background:none;padding:2px 0;color:var(--theme-text-secondary);font-size:10px;cursor:pointer}
.message-action:hover{color:var(--van-primary-color)}
.turn-card{display:flex;gap:10px;border:1px solid;border-radius:10px;padding:9px 11px;margin:8px 0 4px;align-items:flex-start}
.turn-card .tc-ic{flex-shrink:0;display:grid;place-items:center;width:26px;height:26px;border-radius:8px;margin-top:2px;font-size:14px}
.turn-card .tc-main{flex:1;min-width:0}
.turn-card .tc-t{font-size:12.5px;font-weight:600;line-height:1.5}
.turn-card .tc-d{font-size:11px;color:var(--theme-text-secondary);margin-top:2px;line-height:1.55}
.turn-card .tc-head{display:flex;gap:8px;align-items:center;justify-content:space-between}
.turn-card .tc-head .tc-t{flex:1;min-width:0}
.turn-card .tc-head .tc-btn{flex-shrink:0}
.turn-card .tc-detail-row{margin-top:1px}
.tc-btn{min-height:30px;border:1px solid var(--theme-border);border-radius:8px;padding:4px 12px;background:var(--theme-bg-primary);color:var(--theme-text-primary);font:inherit;font-size:12px;cursor:pointer}
.tc-btn:disabled{opacity:.5;cursor:default}
.tc-btn.tc-primary{background:var(--van-primary-color);border-color:var(--van-primary-color);color:#fff;font-weight:600}
.tc-detail{display:inline-flex;align-items:center;gap:3px;border:0;background:none;padding:4px;color:var(--theme-text-secondary);font:inherit;font-size:11px;cursor:pointer}
.tc-more{font-size:10.5px;color:var(--theme-text-secondary);margin-top:6px;border-top:1px dashed var(--theme-border);padding-top:6px;line-height:1.7}
.tc-more p{margin:0;white-space:pre-wrap;overflow-wrap:anywhere;font-family:ui-monospace,Consolas,monospace}
.tc-fail{border-color:var(--van-danger-color);background:var(--van-danger-color-light,rgba(238,46,46,.06))}
.tc-fail .tc-ic{background:rgba(238,46,46,.12);color:var(--van-danger-color)}
.tc-unc{border-color:var(--van-warning-color,#ee9a01);background:rgba(238,154,1,.05)}
.tc-unc .tc-ic{background:rgba(238,154,1,.12);color:var(--van-warning-color,#ee9a01)}
.message-error{color:var(--van-danger-color)}
.flow-wrap{margin:2px 0 4px}
.flow-rows{font-family:var(--mono);font-size:11px;line-height:1.8;color:var(--theme-text-secondary);white-space:pre;overflow-x:auto;padding:2px 0}
.flow-head{padding-bottom:2px}
.flow-block{border:1px solid var(--theme-border);border-radius:8px;background:var(--theme-bg-secondary);margin:2px 0;overflow:hidden}
.flow-summary{display:flex;align-items:center;gap:6px;min-height:28px;padding:4px 10px;font-size:10px;color:var(--theme-text-secondary);cursor:pointer;list-style:none;user-select:none}
.flow-summary::-webkit-details-marker{display:none}
.flow-badge{flex-shrink:0;font-size:8px;line-height:1;padding:2px 4px;border-radius:3px;color:var(--van-warning-color,#ff976a);background:rgba(255,151,106,.1)}
.flow-summary strong{font-size:10.5px;color:var(--theme-text-primary);font-weight:500;flex-shrink:0}
.flow-cap{font-size:10px;color:var(--theme-text-tertiary);min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.flow-more,.flow-less{flex-shrink:0;color:var(--van-primary-color)}
.flow-block:not([open]) .flow-less{display:none}
.flow-block[open] .flow-more{display:none}
.flow-chev{margin-left:auto;flex-shrink:0;font-size:10px;color:var(--theme-text-tertiary);transition:transform .2s}
.flow-block[open] .flow-chev{transform:rotate(180deg)}
.flow-rest{max-height:220px;overflow-y:auto;padding:4px 10px;border-top:1px solid var(--theme-border);overscroll-behavior:contain}
.speaker-cont{opacity:.55;font-size:10px;margin:0 0 2px}
.message-text.markdown-body{white-space:normal}
.markdown-body :deep(p){margin:0 0 6px}.markdown-body :deep(p:last-child){margin-bottom:0}
.markdown-body :deep(ul),.markdown-body :deep(ol){margin:3px 0 6px;padding-left:18px}
.markdown-body :deep(li){margin:2px 0}
.markdown-body :deep(h1),.markdown-body :deep(h2),.markdown-body :deep(h3),.markdown-body :deep(h4){font-size:14px;margin:10px 0 6px}
.markdown-body :deep(blockquote){margin:6px 0;padding:2px 10px;border-left:3px solid var(--theme-border);color:var(--theme-text-secondary)}
.markdown-body :deep(code){font-size:12px;background:var(--theme-bg-secondary);padding:1px 5px;border-radius:4px}
.markdown-body :deep(pre){margin:6px 0;padding:10px 12px;border-radius:8px;background:var(--theme-bg-secondary);overflow-x:auto}
.markdown-body :deep(pre code){padding:0;background:none}
.markdown-body :deep(table){display:block;overflow-x:auto;border-collapse:collapse;margin:6px 0;font-size:12px;max-width:100%}
.markdown-body :deep(th),.markdown-body :deep(td){border:1px solid var(--theme-border);padding:4px 8px;text-align:left}
.markdown-body :deep(a){color:var(--van-primary-color)}
@media(max-width:600px){
  .chat-message{gap:6px;margin-bottom:10px}
  .avatar{width:20px;height:20px;border-radius:6px;font-size:12px}
  .speaker{font-size:11px;margin:1px 0 4px}
  .message-text{font-size:var(--ai-body-size);line-height:1.55}
  .user .message-body{max-width:92%}
  .user .message-text{padding:7px 10px;border-radius:10px 10px 3px 10px}
  .assistant .message-text{padding:7px 10px;border-radius:3px 10px 10px 10px}
  .message-actions{gap:12px;margin-top:0}
  .message-action{min-height:28px;padding:4px 0;font-size:11px}
  .markdown-body :deep(h1),.markdown-body :deep(h2),.markdown-body :deep(h3),.markdown-body :deep(h4){font-size:13px;margin:8px 0 4px}
  .tc-btn{min-height:40px;min-width:40px}
  .tc-detail{min-height:36px}
  .turn-card .tc-ic{margin-top:7px}
  .flow-summary{min-height:32px}
  .flow-rest{max-height:200px}
}
.authorization-card{padding:12px;margin-top:8px;border:1px solid var(--theme-border);border-radius:10px;background:var(--theme-bg-secondary);font-size:12px;line-height:1.6}.authorization-card p{margin:6px 0}
/* 2026-11-12：授权卡唯一主操作（拒绝收进授权弹窗），实心按钮替代并排文字链接 */
.grant-cta{display:block;width:100%;min-height:36px;margin-top:8px;border:1px solid var(--van-primary-color);border-radius:8px;background:var(--van-primary-color);color:#fff;font:inherit;font-size:12px;cursor:pointer}
.grant-cta:disabled{opacity:.5;cursor:default}
@media(max-width:600px){.grant-cta{min-height:44px}}
button:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}
@media(prefers-reduced-motion:reduce){.thinking i,.reply-revealing,.reply-cursor,.reply-animated .reply-supplement{animation:none}}
.turn-card .tc-grid{display:flex;gap:8px;align-items:center;justify-content:space-between}
.turn-card .tc-left{flex:1;min-width:0}
.turn-card .tc-grid .tc-btn{flex-shrink:0}
</style>
