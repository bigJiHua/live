import { watch, onBeforeUnmount } from 'vue';

const GUARD = 'aiPersonalizationBackGuard';
// One history entry covers the mutually exclusive editor/history overlays.
// A running save keeps its input visible when Back consumes the entry.
export function usePersonalizationBackGuard(show, busy, close) {
  const open = () => typeof show === 'function' ? show() : show.value;
  const working = () => typeof busy === 'function' ? busy() : busy.value;
  const hasGuard = () => window.history.state?.[GUARD] === true;
  const pushGuard = () => window.history.pushState({ ...window.history.state, [GUARD]: true }, '');
  let disposed = false;
  function onPop() {
    if (!open() || hasGuard()) return;
    if (working()) pushGuard();
    else close();
  }
  const stop = watch(open, value => {
    if (value) {
      if (!hasGuard()) pushGuard();
      window.addEventListener('popstate', onPop);
    } else {
      window.removeEventListener('popstate', onPop);
      setTimeout(() => { if (!disposed && hasGuard()) window.history.back(); }, 0);
    }
  });
  onBeforeUnmount(() => {
    disposed = true; stop(); window.removeEventListener('popstate', onPop);
    if (hasGuard()) window.history.replaceState({ ...window.history.state, [GUARD]: undefined }, '');
  });
}
