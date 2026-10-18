import { defineStore } from "pinia";
import { hasAuthMark, getLegacyToken } from "@/utils/authState";

/**
 * 用户信息管理 Store
 * 用于管理用户登录状态、个人信息等
 *
 * 🔒 安全整改 P0-5（2026-10-08）：`token` **不再**从 localStorage 读取。
 *    真实凭证已改为服务端下发的 httpOnly Cookie（JS 读不到）。
 *    这里保留 `token` 字段仅为**兼容旧版本遗留**（见 getLegacyToken 注释），
 *    登录/登出时会被 `clearAuth()` 清掉。
 */
export const useUserStore = defineStore("user", {
  state: () => ({
    username: "",
    email: "",
    avatar: "",
    // 认证信息：仅作遗留兼容，新代码不应依赖它做鉴权判断
    token: getLegacyToken(),
    // 加载状态
    loading: false,
    // PIN 码状态（静默保存，避免切换界面闪烁）
    pinEnabled: localStorage.getItem("pin_enabled") === "true",
    hasPinSet: localStorage.getItem("pin_has_set") === "true",
  }),

  getters: {
    // 是否已登录：优先看登录标记（httpOnly Cookie 时代 JS 读不到 token）
    isLoggedIn: (state) => !!state.token || hasAuthMark(),
    // 是否有用户信息
    hasUserInfo: (state) => !!state.email && !!state.username,
    // 显示名称（优先显示用户名，其次邮箱）
    displayName: (state) => state.username || state.email || "用户",
    // 默认头像
    defaultAvatar: () => "https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg",
    // 实际头像地址
    actualAvatar: (state) => state.avatar || "https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg",
  },

  actions: {
    /**
     * 设置用户信息
     */
    setUserInfo(info) {
      this.username = info.username || "";
      this.email = info.email || "";
      this.avatar = info.avatar || "";
    },
    /**
     * 更新用户名
     */
    updateUsername(username) {
      this.username = username;
    },
    /**
     * 更新邮箱
     */
    updateEmail(email) {
      this.email = email;
    },

    /**
     * 更新头像
     */
    updateAvatar(avatar) {
      this.avatar = avatar;
    },

    /**
     * 设置 token（安全整改 P0-5：已废弃写盘）
     *
     * @deprecated 真实凭证已改为服务端下发的 httpOnly Cookie，**不应再存localStorage**。
     *   保留此方法仅为兼容既有调用方；它只更新内存值并确保磁盘上没有 token。
     *   新的登录流程请用 `markAuthed()`（见 utils/authState.js）。
     */
    setToken(token) {
      this.token = token || null;
      // 主动清理可能存在的旧 token，避免「登录态」与「磁盘残留」不一致
      try {
        localStorage.removeItem("finance_token");
      } catch {
        /* ignore */
      }
    },

    /**
     * 清空用户信息（登出）
     */
    clearUserInfo() {
      this.id = null;
      this.username = "";
      this.email = "";
      this.avatar = "";
      this.token = null;
    },

    /**
     * 设置 PIN 状态（静默保存）
     */
    setPinState(enabled, hasSet = null) {
      this.pinEnabled = enabled;
      localStorage.setItem("pin_enabled", enabled ? "true" : "false");
      if (hasSet !== null) {
        this.hasPinSet = hasSet;
        localStorage.setItem("pin_has_set", hasSet ? "true" : "false");
      }
    },
  },
});
