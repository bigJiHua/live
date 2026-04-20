import { showToast, showSuccessToast, showFailToast } from "vant";
import config from "../config";
import { PIN_CODE, isPinResponse, requestPinVerify, registerForceLogin } from "../pin";

let isRedirecting = false;

/**
 * 强制跳登录（移动端专用）
 */
function forceLogin(message) {
  if (isRedirecting) return;
  isRedirecting = true;

  showToast({
    message: message || "登录已失效",
    position: "top",
    duration: 800,
  });

  // 清缓存
  sessionStorage.clear();
  localStorage.removeItem(config.tokenKey);

  setTimeout(() => {
    window.location.href = "/login";
  }, 300);
}

// 注册 forceLogin 到 pin.js，避免循环依赖
registerForceLogin(forceLogin);

export function createResponseInterceptor() {
  return [
    // ✅ 成功响应
    (response) => {
      const res = response.data || {};

      // 🔒 PIN 码拦截（8303 需要验证 / 8302 错误 / 8304 锁定）
      if (isPinResponse(res)) {
        return handlePinResponse(res, response);
      }

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

      // 🔒 PIN 码拦截（HTTP 401 + PIN code）
      if (isPinResponse(resData)) {
        return handlePinResponse(resData, response);
      }

      if (resData?.ismessage) {
        return Promise.reject(resData);
      }

      // 🚨 真401（非 PIN 相关）
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

/**
 * 处理 PIN 码相关响应
 * - 8303 需要验证 → 弹出 PIN 输入框，拦截当前请求
 * - 8302 PIN 错误 → 由 pin.js 内部处理
 * - 8304 锁定 → 退出登录
 * - 8301 验证成功 → 正常通过（一般不会出现在业务响应中）
 */
function handlePinResponse(resData, response) {
  const code = resData.code;

  // 8304 锁定 → 退出登录
  if (code === PIN_CODE.LOCKED) {
    forceLogin(resData.message || "PIN 已锁定，请重新登录");
    return Promise.reject(resData);
  }

  // 8303 需要验证 → 拦截请求，弹出 PIN 输入框
  if (code === PIN_CODE.NEED_VERIFY) {
    // 保存原始请求配置用于重发
    const originalRequest = {
      method: response.config.method,
      url: response.config.url,
      baseURL: response.config.baseURL,
      headers: { ...response.config.headers },
      data: response.config.data,
      params: response.config.params,
    };

    return requestPinVerify(originalRequest);
  }

  // 8302 PIN 错误（一般不会直接到达这里，由 submitPin 处理）
  if (code === PIN_CODE.ERROR) {
    return Promise.reject(resData);
  }

  // 8301 验证成功（一般不会直接到达这里）
  if (code === PIN_CODE.SUCCESS) {
    return resData;
  }

  return Promise.reject(resData);
}

export default { createResponseInterceptor };
