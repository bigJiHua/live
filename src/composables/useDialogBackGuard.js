import { watch, onBeforeUnmount } from 'vue';

// History 哨兵标记：写入 pushState 条目的 state，用于区分「哨兵条目」与「真实路由条目」
const GUARD_KEY = 'aiDialogBackGuard';

const hasGuard = () => Boolean(history.state?.[GUARD_KEY]);

/**
 * 弹窗返回拦截（History 哨兵，等价 Android 原生 Dialog 拦截 Back 键）
 *
 * 背景：AI 设置域弹窗（会话设置/模型设置/日志抽屉）均为 van-popup 组件状态、
 * 不占路由历史，用户按返回（手机手势/浏览器返回/导航栏返回）会直接后退 /ai 路由，
 * 把整个聊天页退掉回主页，临时调整被打断。
 *
 * 机制：
 * - 弹窗打开：pushState 压入一条同 URL 哨兵条目（保留 vue-router 自身 state，URL 不变）
 * - 用户按返回：先 pop 的是哨兵 → popstate 里只关弹窗，vue-router 因 URL 未变不导航，
 *   页面停留在聊天界面；多层弹窗叠加时逐层返回（每次返回关一层）
 * - 点 X 手动关闭：主动 back() 消费栈顶哨兵，历史栈不留冗余
 * - 「关 A 同时开 B」（会话设置→模型设置）：B 在 A 的哨兵条目上叠加哨兵，
 *   A 的 back 消费栈顶（B 的哨兵），B 靠 popstate 兜底收敛，最终状态正确
 */
export function useDialogBackGuard(show, close) {
  const isOpen = () => (typeof show === 'function' ? show() : show.value);

  const onPop = () => {
    if (!hasGuard() && isOpen()) close();
  };

  watch(isOpen, (open) => {
    if (open) {
      history.pushState({ ...history.state, [GUARD_KEY]: true }, '');
      window.addEventListener('popstate', onPop);
    } else {
      window.removeEventListener('popstate', onPop);
      // 延迟消费哨兵并执行前重校验：若同一 tick 内发生了路由 push（如会话设置→模型设置页），
      // 当前条目已是新路由（无哨兵标记）→ 跳过 back，避免与导航产生 back/pushState 竞态
      setTimeout(() => { if (hasGuard()) history.back(); }, 0);
    }
  });

  onBeforeUnmount(() => {
    window.removeEventListener('popstate', onPop);
    // 组件随路由卸载（如跳转模型设置页）时把哨兵条目洗成普通条目：
    // replaceState 同步无导航，不打断进行中的导航；避免返回链上残留冗余条目（多按一次）
    if (hasGuard()) history.replaceState({ ...history.state, [GUARD_KEY]: undefined }, '');
  });
}
/**
 * 同步洗白当前条目上的哨兵标记（replaceState 无导航、无竞态）。
 * 供「关闭弹窗的同时跳转路由」的入口在 push 前调用：哨兵被洗白后，
 * 弹窗关闭的延迟消费（setTimeout back）因 hasGuard()=false 自然跳过，
 * 不会打断进行中的 router.push（动态 import 属宏任务，比 setTimeout(0) 更晚完成，
 * 延迟消费在此窗口内会误判哨兵仍在而 back 打断导航）。
 */
export function clearDialogGuard() {
  if (hasGuard()) history.replaceState({ ...history.state, [GUARD_KEY]: undefined }, '');
}