export function getAiDraftDisplay(draft, now = Date.now()) {
  if (draft.status === 'committed') {
    return { expired: false, statusLabel: '已入账', validityLabel: '回执已保存' };
  }
  if (draft.status === 'discarded') {
    return { expired: false, statusLabel: '已取消', validityLabel: '已取消' };
  }
  const expired = now >= draft.expiresAt;
  return { expired, statusLabel: expired ? '已过期' : draft.status === 'reviewed' ? '已核对' : '待核对',
    validityLabel: expired ? '已过期' : '15分钟内有效' };
}
