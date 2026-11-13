import { nextTick, watch } from 'vue';

export function useAssistantViewport({ state, services, runtime, actions }) {
  const {
    scrollArea, composerRef, scrolledAway, isNarrow, replyAnnouncement,
    chatActivity, pendingJumpVisible, messages, replyReveals,
  } = state;
  let focusEntry = null;
  let narrowQuery = null;
  let lastScrollTop = 0, followQueued = false, followGeneration = 0;
  const stopActivityWatch = watch(chatActivity, (value, previous) => {
    if (!runtime.isActive()) return;
    if (value) replyAnnouncement.value = '';
    else if (previous && messages.value.length) replyAnnouncement.value = messages.value.some(item => item.turn?.state === 'uncertain')
      ? '本轮已停止，正在核对最终结果。' : '本轮回复已结束。';
    else replyAnnouncement.value = '';
  }, { flush: 'post' });
  const stopRevealWatch = watch(() => messages.value.map(item => [item.id, item.pending, item.error]), () => {
    if (!replyReveals) return;
    for (const id of Object.keys(replyReveals.value)) {
      const item = messages.value.find(entry => String(entry.id) === id);
      if (!item || item.pending || item.error || replyReveals.value[id] !== (item.requestId || String(item.id))) delete replyReveals.value[id];
    }
  });

  function nearBottom() {
    const el = scrollArea.value;
    return !el || el.scrollHeight - el.scrollTop - el.clientHeight < 160;
  }

  function onScrollAreaScroll() {
    const top = scrollArea.value?.scrollTop || 0;
    if (chatActivity.value && top < lastScrollTop - 3) scrolledAway.value = true;
    else if (top > lastScrollTop + 3 || !chatActivity.value) scrolledAway.value = !nearBottom();
    lastScrollTop = top;
  }

  async function bottom() {
    await nextTick();
    if (!runtime.isActive()) return;
    if (scrollArea.value) {
      scrollArea.value.scrollTop = scrollArea.value.scrollHeight;
      lastScrollTop = scrollArea.value.scrollTop;
    }
    scrolledAway.value = false;
  }

  function resizeInput() {
    if (runtime.isActive()) composerRef.value?.resizeInput();
  }

  function revealProgress(item, info) {
    if (!runtime.isActive() || !messages.value.includes(item) || item.pending || item.error) return;
    if (info?.requestId != null && info.requestId !== item.requestId) return;
    if (replyReveals) replyReveals.value[item.id] = item.requestId || String(item.id);
    if (info?.immediate || scrolledAway.value || !nearBottom() || followQueued) return;
    followQueued = true;
    const generation = followGeneration;
    nextTick(() => {
      followQueued = false;
      if (generation !== followGeneration || !runtime.isActive() || !messages.value.includes(item) || scrolledAway.value
        || info?.requestId != null && info.requestId !== item.requestId) return;
      const el = scrollArea.value;
      if (el) {
        const end = Math.max(0, el.scrollHeight - el.clientHeight);
        const step = Math.max(24, Math.min(96, el.clientHeight * 0.2 || 96));
        el.scrollTop = Math.min(end, el.scrollTop + step);
        lastScrollTop = el.scrollTop;
      }
    });
  }
  function revealComplete(item, info) {
    if (info?.reason && info.reason !== 'complete') followGeneration++;
    const owner = info?.requestId || item.requestId || String(item.id);
    if (replyReveals && replyReveals.value[item.id] === owner) delete replyReveals.value[item.id];
  }

  function setFocusEntry(entry) { focusEntry = entry; }
  function hasFocusEntry() { return Boolean(focusEntry); }

  function tryFocusSent(force = false) {
    if (!runtime.isActive() || !focusEntry || !messages.value.includes(focusEntry)) {
      focusEntry = null;
      return;
    }
    if (!force && !nearBottom()) {
      focusEntry = null;
      return;
    }
    const el = scrollArea.value;
    const node = el?.querySelector(`[data-mid="${focusEntry.id}"]`);
    if (!el || !node) return;
    const want = Math.max(0,
      node.getBoundingClientRect().top - el.getBoundingClientRect().top + el.scrollTop - 10);
    el.scrollTop = want;
    if (el.scrollTop >= want - 2) focusEntry = null;
    else el.scrollTop = el.scrollHeight;
    lastScrollTop = el.scrollTop;
  }

  function onNarrowChange(event) {
    if (runtime.isActive()) isNarrow.value = event.matches;
  }

  function initialize() {
    if (!runtime.isActive() || narrowQuery || typeof window === 'undefined') return;
    if (typeof window.matchMedia === 'function') {
      narrowQuery = window.matchMedia('(max-width: 600px)');
      isNarrow.value = narrowQuery.matches;
      if (typeof narrowQuery.addEventListener === 'function') {
        narrowQuery.addEventListener('change', onNarrowChange);
      }
    }
  }

  function dispose() {
    followGeneration++;
    stopActivityWatch();
    stopRevealWatch();
    if (replyReveals) replyReveals.value = {};
    if (narrowQuery && typeof narrowQuery.removeEventListener === 'function') {
      narrowQuery.removeEventListener('change', onNarrowChange);
    }
    narrowQuery = null;
    focusEntry = null;
  }

  return {
    pendingJumpVisible, onScrollAreaScroll, nearBottom, bottom, resizeInput,
    setFocusEntry, hasFocusEntry, tryFocusSent, revealProgress, revealComplete, onNarrowChange, initialize, dispose,
  };
}
