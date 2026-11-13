import { computed, getCurrentScope, onScopeDispose, ref, watch } from 'vue';

// This is a presentation buffer. The authoritative answer remains on item.text
// for storage, copying, retries and business-card reconciliation.
export function replyGraphemes(text) {
  if (typeof Intl.Segmenter === 'function') {
    const segmenter = new Intl.Segmenter(undefined, { granularity: 'grapheme' });
    return Array.from(segmenter.segment(text), entry => entry.segment);
  }
  // Older clients retain surrogate pairs and combining marks together.
  const parts = [];
  for (const point of Array.from(text)) {
    const previous = parts.at(-1), regional = value => value && Array.from(value).length === 1 && /^[\u{1f1e6}-\u{1f1ff}]$/u.test(value);
    if (parts.length && (/\p{Mark}|\p{Emoji_Modifier}/u.test(point) || point === '\u200d' || previous.endsWith('\u200d')
      || /[\ufe0e\ufe0f\u{e0020}-\u{e007f}]/u.test(point) || regional(point) && regional(previous) || point === '\n' && previous === '\r'))
      parts[parts.length - 1] += point;
    else parts.push(point);
  }
  return parts;
}

export function useReplyReveal(getItem, options = {}) {
  const displayedText = ref(''), isRevealing = ref(false), hasRevealed = ref(false);
  const progress = ref(1);
  const now = options.now || (() => Date.now());
  const schedule = options.schedule || ((callback, delay) => setTimeout(callback, delay));
  const cancelSchedule = options.cancelSchedule || (timer => clearTimeout(timer));
  const surface = options.surface === undefined ? globalThis.window : options.surface;
  const documentSurface = options.document === undefined ? globalThis.document : options.document;
  const media = options.media || surface?.matchMedia?.('(prefers-reduced-motion: reduce)');
  const reducedMotion = ref(Boolean(media?.matches));
  const onProgress = options.onProgress || (() => {});
  const onComplete = options.onComplete || (() => {});
  let timer = null, generation = 0, disposed = false;
  let initialized = false, identity = null, armed = false, target = '', points = [], startedAt = 0, duration = 0, shownCount = 0, revealItem = null, revealOwner = null;

  const snapshot = () => {
    const item = getItem() || {};
    const key = item.requestId ? `${item.role || ''}:${item.requestId}:${item.continuation || 'main'}` : `${item.role || ''}:${item.id ?? ''}`;
    return { key, role: item.role, pending: item.pending === true, error: item.error === true,
      state: item.turn?.state || '', text: String(item.text || '') };
  };
  function clearTimer() {
    generation++;
    if (timer != null) cancelSchedule(timer);
    timer = null;
  }
  function complete(reason = 'complete', notify = true) {
    const wasRevealing = isRevealing.value;
    const owner = revealItem;
    const token = revealOwner;
    clearTimer();
    displayedText.value = target;
    isRevealing.value = false; progress.value = 1;
    revealItem = null; revealOwner = null;
    if (notify && wasRevealing) onComplete(owner, reason, token);
  }
  function showAll() { complete('skip'); }
  function tick(epoch) {
    if (disposed || epoch !== generation || !isRevealing.value) return;
    timer = null;
    const ratio = Math.min(1, Math.max(0, (now() - startedAt) / duration));
    const count = Math.max(shownCount, 1, Math.ceil(points.length * ratio));
    const next = points.slice(0, count).join('');
    if (next !== displayedText.value) {
      shownCount = count; displayedText.value = next; progress.value = count / points.length;
      onProgress(revealItem, progress.value, revealOwner);
    }
    if (ratio >= 1 || count >= points.length) { complete(); return; }
    timer = schedule(() => tick(epoch), 40); timer?.unref?.();
  }
  function start(text) {
    clearTimer(); target = text; points = replyGraphemes(text);
    if (!points.length || reducedMotion.value || documentSurface?.visibilityState === 'hidden') {
      displayedText.value = target; isRevealing.value = false; progress.value = 1;
      return;
    }
    // Short messages keep a readable cadence; a long article never blocks the
    // view for more than six seconds. The user can finish it immediately.
    duration = Math.min(6000, Math.max(320, points.length * 14));
    startedAt = now(); shownCount = Math.min(3, points.length); displayedText.value = points.slice(0, shownCount).join('');
    revealItem = getItem(); revealOwner = Object.freeze({ id: revealItem?.id, requestId: revealItem?.requestId });
    hasRevealed.value = true; isRevealing.value = true; progress.value = shownCount / points.length;
    onProgress(revealItem, progress.value, revealOwner);
    const epoch = generation;
    timer = schedule(() => tick(epoch), 40); timer?.unref?.();
  }
  const stopWatch = watch(snapshot, current => {
    if (disposed) return;
    if (!initialized || current.key !== identity) {
      complete('identity', initialized); initialized = true; identity = current.key;
      armed = current.pending; hasRevealed.value = false; target = current.text; displayedText.value = current.text;
      return;
    }
    if (current.error || ['failed', 'uncertain', 'cancelled', 'canceled', 'interrupted'].includes(current.state) || current.role !== 'assistant') {
      target = current.text; armed = false; complete('interrupted'); return;
    }
    if (current.pending) {
      armed = true; target = current.text; complete('pending'); hasRevealed.value = false; return;
    }
    if (armed && current.text) { armed = false; start(current.text); return; }
    if (isRevealing.value) {
      // A newer authoritative answer is a correction, not a text delta. Avoid
      // splicing two versions or replaying an already completed response.
      if (current.text !== target) { target = current.text; complete('correction'); }
      return;
    }
    target = current.text; displayedText.value = current.text;
  }, { immediate: true, flush: 'pre' });
  function mediaChanged() {
    reducedMotion.value = Boolean(media?.matches);
    if (reducedMotion.value) complete('reduced-motion');
  }
  function visibilityChanged() {
    if (documentSurface?.visibilityState === 'hidden') complete('hidden');
  }
  media?.addEventListener?.('change', mediaChanged);
  if (!media?.addEventListener) media?.addListener?.(mediaChanged);
  documentSurface?.addEventListener?.('visibilitychange', visibilityChanged);
  function dispose() {
    if (disposed) return;
    disposed = true; complete('dispose'); stopWatch();
    media?.removeEventListener?.('change', mediaChanged);
    if (!media?.removeEventListener) media?.removeListener?.(mediaChanged);
    documentSurface?.removeEventListener?.('visibilitychange', visibilityChanged);
    isRevealing.value = false;
  }
  if (getCurrentScope()) onScopeDispose(dispose);
  return { displayedText, isRevealing, hasRevealed, progress: computed(() => progress.value), reducedMotion, showAll, dispose };
}
