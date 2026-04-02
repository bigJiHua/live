import { defineStore } from "pinia";

/**
 * 用户信息管理 Store
 * 用于管理用户登录状态、个人信息等
 */
export const useUserStore = defineStore("user", {
  state: () => ({
    username: "",
    email: "",
    avatar: "",
    // 认证信息
    token: localStorage.getItem("finance_token") || null,
    // 加载状态
    loading: false,
  }),

  getters: {
    // 是否已登录
    isLoggedIn: (state) => !!state.token,
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
     * 设置 token
     */
    setToken(token) {
      this.token = token;
      if (token) {
        localStorage.setItem("finance_token", token);
      } else {
        localStorage.removeItem("finance_token");
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
  },
});
