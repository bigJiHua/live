import { nextTick } from 'vue';

export const STAGE_LABELS = {
  queued: '等待上一条 AI 请求完成', prepared: '核验授权', selecting: 'AI 选择能力',
  reading: '读取必要数据', reasoning: 'AI 结合数据分析', thinking: 'AI 处理原话',
  finalizing: '整理最终答复', validating: '核验模型输出', parsing: '核验模型输出',
  preflight: '核验资金与来源', drafting: '生成待确认草稿',
};
const TOOL_LABELS = {
  memory_list: '记忆目录', memory_read: '读取记忆', memory_search: '搜索记忆',
  memory_write: '保存记忆', memory_delete: '删除记忆', history_search: '查找历史对话', history_read: '读取历史对话',
  query_data: '业务数据', list_choices: '记账候选',
  calculate: '确定性计算', web_search: '联网搜索',
};

export function useAssistantActivities({ state, services, runtime, actions }) {
  const { activityClock } = state;
  const { hasFocusEntry, tryFocusSent } = actions;
  let activityTimer = null;

  function startActivityTicker() {
    if (!runtime.isActive() || activityTimer) return;
    activityTimer = setInterval(() => {
      if (runtime.isActive()) activityClock.value = Date.now();
    }, 1000);
  }

  function stopActivityTicker() {
    clearInterval(activityTimer);
    activityTimer = null;
  }

  function sealActivities(entry) {
    const list = entry.activities || [];
    const last = list[list.length - 1];
    if (last && !last.done) {
      last.done = true;
      last.duration = Date.now() - last.at;
    }
    if (!list.length || list.every(act => act.done)) {
      stopActivityTicker();
      if (!entry.activitiesUserTouched) entry.activitiesOpen = false;
    }
  }

  function toggleActivities(entry) {
    entry.activitiesUserTouched = true;
    entry.activitiesOpen = entry.activitiesOpen !== true;
  }

  function pushActivity(entry, label) {
    sealActivities(entry);
    (entry.activities || (entry.activities = [])).push({ label, at: Date.now() });
    startActivityTicker();
  }

  function trackStage(entry, data) {
    const stage = data?.stage;
    if (!stage) return;
    pushActivity(entry, stage === 'thinking' && data.model
      ? `${STAGE_LABELS.thinking} · ${data.model}`
      : (STAGE_LABELS[stage] || 'AI 正在处理'));
    if (hasFocusEntry()) nextTick(() => {
      if (runtime.isActive()) tryFocusSent();
    });
  }

  function trackTool(entry, data) {
    const label = TOOL_LABELS[data?.tool];
    if (!label) return;
    const records = Number(data?.records);
    pushActivity(entry, `读取数据 · ${label}${records > 0 ? ` · ${records} 条` : ''}`);
  }

  // Lifecycle facts and provisional text snapshots are separate channels.
  // Only the completed verified channel can become saved reasoning details.
  function completedElapsed(state) {
    if (Number.isSafeInteger(state?.completedElapsedMs) && state.completedElapsedMs >= 0) return state.completedElapsedMs;
    if (Number.isSafeInteger(state?.totalElapsedMs) && state.totalElapsedMs >= state.elapsedMs)
      return state.totalElapsedMs - state.elapsedMs;
    return 0;
  }

  function trackReasoningStatus(entry, data) {
    if (!runtime.isActive() || !entry || !data || typeof data !== 'object' || Array.isArray(data)
      || typeof data.requestId !== 'string' || !data.requestId || data.requestId !== entry.requestId
      || entry.role && entry.role !== 'assistant'
      || !Number.isInteger(data.round) || data.round < 1 || data.round > 5
      || !['started', 'working', 'completed'].includes(data.state)
      || !Number.isSafeInteger(data.elapsedMs) || data.elapsedMs < 0 || data.elapsedMs > 86400000
      || typeof data.observed !== 'boolean'
      || state.messages?.value && !state.messages.value.includes(entry)) return false;
    const previous = entry.thinkingState;
    // A delayed stream cannot reopen a sealed/error/finished message.
    if ((!entry.pending || entry.error) && data.state !== 'completed') return false;
    if (previous?.state === 'interrupted') return false;
    if (previous && (data.round < previous.round || data.round === previous.round
      && ({ started: 0, working: 1, completed: 2 }[data.state] < { started: 0, working: 1, completed: 2 }[previous.state]))) return false;
    const sameRound = previous?.round === data.round;
    const elapsedMs = Math.max(data.elapsedMs, sameRound ? previous.elapsedMs : 0);
    const observed = data.observed || Boolean(sameRound && previous.observed);
    const observedAny = observed || Boolean(previous?.observedAny || previous?.observed);
    // Same-round progress replaces its elapsed value. Only a newer round moves
    // the previous round's known duration into the completed-round subtotal.
    const completedElapsedMs = completedElapsed(previous) + (sameRound ? 0 : previous?.elapsedMs || 0);
    const totalElapsedMs = completedElapsedMs + elapsedMs;
    if (sameRound && previous.state === data.state && previous.elapsedMs === elapsedMs && previous.observed === observed
      && previous.observedAny === observedAny && previous.completedElapsedMs === completedElapsedMs && previous.totalElapsedMs === totalElapsedMs) return false;
    entry.thinkingState = {
      state: data.state, round: data.round, elapsedMs, observed, observedAny, completedElapsedMs, totalElapsedMs,
      durationKnown: previous?.durationKnown !== false,
      startedAt: sameRound ? previous.startedAt : Date.now() - elapsedMs,
    };
    if (entry.pending && data.state !== 'completed') startActivityTicker();
    return true;
  }

  // Receipt/sync completion can precede the last lifecycle frame. Freeze its
  // clock locally; interruption must never claim successful thought completion.
  function sealReasoningStatus(entry, interrupted = false) {
    if (!interrupted) clearLiveReasoning(entry);
    const previous = entry?.thinkingState;
    if (!previous) return;
    const elapsedMs = previous.state === 'completed' || previous.state === 'interrupted'
      ? previous.elapsedMs : Math.max(previous.elapsedMs, Math.min(86400000, Math.max(0, Date.now() - previous.startedAt)));
    const completedElapsedMs = completedElapsed(previous);
    entry.thinkingState = { ...previous, state: interrupted ? 'interrupted' : 'completed', elapsedMs,
      observedAny: Boolean(previous.observedAny || previous.observed), completedElapsedMs, totalElapsedMs: completedElapsedMs + elapsedMs };
  }

  function clearLiveReasoning(entry) {
    if (!entry) return;
    delete entry.thinkingDraftRounds;
    delete entry.thinkingLive;
  }
  function rebuildLiveReasoning(entry) {
    const drafts = entry.thinkingDraftRounds || {};
    if (!Object.keys(drafts).length) { delete entry.thinkingLive; return; }
    const rounds = new Set([...Object.keys(entry.thinkingRounds || {}), ...Object.keys(drafts)]);
    entry.thinkingLive = [...rounds].sort((a, b) => Number(a) - Number(b))
      .map(round => entry.thinkingRounds?.[round] ?? drafts[round]?.text ?? '').filter(Boolean).join('\n\n');
  }
  function trackReasoningDelta(entry, data) {
    if (!runtime.isActive() || !entry || !entry.pending || entry.error || !data || typeof data !== 'object' || Array.isArray(data)
      || entry.role && entry.role !== 'assistant' || data.requestId !== entry.requestId
      || typeof data.requestId !== 'string' || !data.requestId
      || data.provisional !== true || data.mode !== 'snapshot'
      || !Number.isInteger(data.round) || data.round < 1 || data.round > 5
      || !Number.isSafeInteger(data.sequence) || data.sequence < 1 || data.sequence > 1000000
      || typeof data.text !== 'string' || data.text.length > 12000
      || state.messages?.value && !state.messages.value.includes(entry)
      || entry.thinkingState?.state === 'interrupted' || data.round < (entry.thinkingState?.round || 0)
      || Object.hasOwn(entry.thinkingRounds || {}, data.round)) return false;
    const previous = entry.thinkingDraftRounds?.[data.round];
    if (previous && data.sequence <= previous.sequence) return false;
    const drafts = { ...(entry.thinkingDraftRounds || {}), [data.round]: { text: data.text, sequence: data.sequence } };
    const roundKeys = new Set([...Object.keys(entry.thinkingRounds || {}), ...Object.keys(drafts)]);
    const totalLength = [...roundKeys].reduce((total, round) => total + (entry.thinkingRounds?.[round] ?? drafts[round]?.text ?? '').length, 0)
      + Math.max(0, roundKeys.size - 1) * 2;
    if (totalLength > 24000) return false;
    entry.thinkingDraftRounds = drafts;
    rebuildLiveReasoning(entry);
    const thought = entry.thinkingState;
    if (thought) entry.thinkingState = { ...thought, observedAny: true,
      ...(thought.round === data.round ? { observed: true } : {}) };
    else entry.thinkingState = { state: 'working', round: data.round, observed: true, observedAny: true,
      elapsedMs: 0, completedElapsedMs: 0, totalElapsedMs: 0, durationKnown: false, startedAt: Date.now() };
    return true;
  }

  // 仅展示成功终态后核验过的每轮思考全文。
  function trackReasoning(entry, data) {
    const text = typeof data?.text === 'string' ? data.text : '';
    const round = data?.round;
    if (!runtime.isActive() || data?.verified !== true || !text || text.length > 12000 ||
      !Number.isInteger(round) || round < 1 || round > 5) return;
    entry.thinkingRounds ||= {};
    entry.thinkingRounds[round] = text;
    if (entry.thinkingDraftRounds) delete entry.thinkingDraftRounds[round];
    entry.thinkingRound = round;
    entry.thinking = Object.keys(entry.thinkingRounds).sort((a, b) => Number(a) - Number(b))
      .map(key => entry.thinkingRounds[key]).join('\n\n');
    rebuildLiveReasoning(entry);
    const previous = entry.thinkingState;
    // A verified text event proves reasoning was observed, not how long it took.
    if (previous) entry.thinkingState = { ...previous, observedAny: true,
      ...(previous.round === round ? { observed: true } : {}) };
    else entry.thinkingState = { state: 'completed', round, elapsedMs: 0, observed: true, observedAny: true, durationKnown: false,
      startedAt: Date.now(), completedElapsedMs: 0, totalElapsedMs: 0 };
  }

  function usageText(item) {
    const usage = item?.usage;
    if (!usage) return '';
    const total = Number(usage.totalTokens ??
      ((Number(usage.promptTokens) || 0) + (Number(usage.completionTokens) || 0)));
    return Number.isFinite(total) && total > 0 ? `${total.toLocaleString()} tokens` : '';
  }

  function finishActivities(entry, usage) {
    sealReasoningStatus(entry);
    if (!entry.activities?.length) return;
    sealActivities(entry);
    const total = Date.now() - entry.activities[0].at;
    const tokens = usage ? usageText({ usage }) : '';
    entry.activitiesTotal = `${(total / 1000).toFixed(1)}s`;
    entry.activities.push({
      label: `完成 · ${(total / 1000).toFixed(1)}s${tokens ? ` · ${tokens}` : ''}`,
      at: Date.now(), done: true,
    });
    stopActivityTicker();
  }

  function initialize() {}
  function dispose() { stopActivityTicker(); }

  return {
    startActivityTicker, stopActivityTicker, sealActivities, toggleActivities,
    pushActivity, trackStage, trackTool, trackReasoning, trackReasoningDelta, clearLiveReasoning,
    trackReasoningStatus, sealReasoningStatus, finishActivities, usageText,
    initialize, dispose,
  };
}
