/**
 * 流水数据同步 Store
 * Detail 修改成功后存入变更，列表页 onActivated 时同步到本地列表
 * 避免返回时重新请求数据，实现无感更新
 */
import { defineStore } from 'pinia'

export const useFlowSyncStore = defineStore('flowSync', {
  state: () => ({
    /** @type {Record<string, object>} id -> 变更字段 */
    changes: {},
  }),

  actions: {
    /** Detail 保存成功后调用，记录变更 */
    recordChange(id, patch) {
      this.changes[id] = { ...this.changes[id], ...patch }
    },

    /** 列表页 onActivated 调用，取走变更并清除 */
    consumeChanges() {
      const result = { ...this.changes }
      this.changes = {}
      return result
    },

    /** 清除所有待同步数据（用于异常场景） */
    clear() {
      this.changes = {}
    },
  },
})
