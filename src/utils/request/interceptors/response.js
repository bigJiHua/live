import { showToast, showSuccessToast, showFailToast } from "vant";
import config from "../config";

let isRedirecting = false;

/**
 * 强制跳登录（移动端专用）
 */
function forceLogin() {
  if (isRedirecting) return;
  isRedirecting = true;

  showToast({
    message: "登录已失效",
    position: "top",
    duration: 800,
  });

  // 清缓存
  sessionStorage.clear();
  localStorage.removeItem(config.tokenKey);

  // 🚀 移动端最稳写法（比 replace 更狠）
  setTimeout(() => {
    window.location.href = "/login"; // ✅ 直接整页跳
  }, 300);
}

export function createResponseInterceptor() {
  return [
    // ✅ 成功响应
    (response) => {
      const res = response.data || {};

      // 🚨 手机端关键：业务401也拦
      if (res.status === 401) {
        forceLogin();
        return Promise.reject(res);
      }

      if (res.ismessage === true) return res;

      if (res.status === 200 || res.success) {
        const isWrite = config.encryptedMethods.includes(
          response.config.method?.toLowerCase()
        );

        if (isWrite) {
          showSuccessToast(res.message || "成功");
        }

        return res;
      }

      showFailToast(res.message || "业务异常");
      return Promise.reject(res);
    },

    // ❌ 错误响应
    (error) => {
      const response = error.response;
      const status = response?.status;
      const resData = response?.data;

      if (resData?.ismessage) {
        return Promise.reject(resData);
      }

      // 🚨 真401
      if (status === 401) {
        forceLogin();
        return;
      }

      if (status === 429) {
        window.location.href = "/429";
        return;
      }

      if (!response) {
        showFailToast("网络异常");
        return Promise.reject(error);
      }

      showFailToast(resData?.message || "请求失败");
      return Promise.reject(error);
    },
  ];
}

export default { createResponseInterceptor };
