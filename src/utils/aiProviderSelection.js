// 设置保存后定位刚保存的接入商。后端在地址/模型/Key 变化时会轮换
// connectionId，因此不能继续使用提交前的旧编号。
export function resolveSavedProvider(settings, { name, previousConnectionId = null, wasActive = false } = {}) {
  const profiles = Array.isArray(settings?.profiles) ? settings.profiles : [];
  const saved = profiles.find(item => item?.name === name)
    || (previousConnectionId ? profiles.find(item => item?.connectionId === previousConnectionId) : null)
    || (settings?.activeConnectionId ? profiles.find(item => item?.connectionId === settings.activeConnectionId) : null)
    || null;
  return { saved, activateId: !wasActive && saved && saved.active !== true ? saved.connectionId : null };
}
