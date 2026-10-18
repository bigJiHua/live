import axios from "axios";
import { showToast, showSuccessToast, showFailToast } from '@/utils/toast';
import { UAParser } from "ua-parser-js";
import { generateBrowserFingerprint } from "./device-hash";
import { getFullNetworkInfo } from "./network";
import { isPinResponse, requestPinVerify, PIN_CODE } from "./request/pin";
import { normalizeApiError } from "./request/helpers";
import { createClientNonce } from "./securityHeaders";
import {
  isNonceReplay,
  buildNonceRetryConfig,
  NONCE_REPLAY_FALLBACK_MESSAGE,
} from "./request/nonceRetry";
import { getLegacyToken } from "./authState";
import { clearSession } from "./session";
import router from "@/router";

const baseUrl = "/api/v1";
const TIMEOUT = 60000; // 文件上传超时时间设置为 60s

const clientContext = {
  data: null,
  initPromise: null,
};

async function getClientFullData() {
  if (clientContext.data) return clientContext.data;
  if (clientContext.initPromise) return clientContext.initPromise;

  clientContext.initPromise = (async () => {
    try {
      const ua = new UAParser().getResult();
      const net = await getFullNetworkInfo();
      const fp = generateBrowserFingerprint();

      clientContext.data = {
        login_ip: net.external?.ip || "unknown",
        login_location: `${net.external?.country || ""}-${net.external?.region || ""}-${net.external?.city || ""}`,
        login_isp: net.external?.isp || "",
        login_lat: net.external?.latitude ?? null,
        login_lng: net.external?.longitude ?? null,
        user_agent: ua.ua,
        os_info: `${ua.os.name} ${ua.os.version}`,
        browser_info: `${ua.browser.name} ${ua.browser.version}`,
        device_model: ua.device.model || "Unknown",
        device_type: ua.device.type || "desktop",
        fingerprint: fp.hash,
        viewport: `${window.innerWidth}x${window.innerHeight}`,
        pixel_ratio: window.devicePixelRatio,
        login_lang: navigator.language,
        connection_type: net.local?.connectionType,
      };
      return clientContext.data;
    } catch (e) {
      console.error("Context Init Failed", e);
      return {};
    }
  })();
  return clientContext.initPromise;
}

const requestfile = axios.create({
  baseURL: baseUrl,
  withCredentials: true,
  timeout: TIMEOUT,
});

requestfile.interceptors.request.use(
  async (config) => {
    const deviceData = await getClientFullData();

    // 1. 注入安检 Headers
    config.headers["X-Requested-With"] = "XMLHttpRequest";
    config.headers["x-client-timestamp"] = Date.now();
    config.headers["x-client-nonce"] = createClientNonce();
    config.headers["x-fingerprint-hash"] = deviceData.fingerprint;
    config.headers["x-user-agent-custom"] = deviceData.user_agent;
    config.headers["x-device-model"] = deviceData.device_model;
    config.headers["x-os-name"] = deviceData.os_info;
    config.headers["x-device-type"] = deviceData.device_type;

    // 2. 注入 Token
    // 🔒 安全整改 P0-5：不再从 localStorage 读 JWT（凭证在 httpOnly Cookie 里，浏览器自动带）。
    //    仅当存在遗留 token 时才注入（平滑升级 / 降级兜底）。
    const legacyToken = getLegacyToken();
    if (legacyToken) config.headers["Authorization"] = `Bearer ${legacyToken}`;

    return config;
  },
  (error) => Promise.reject(error)
);

requestfile.interceptors.response.use(
  (response) => {
    const res = response.data || {};

    // PIN 码拦截（8303 需要验证 / 8304 锁定）
    if (isPinResponse(res)) {
      return handleFilePinResponse(res, response);
    }

    // 如果有 ismessage 字段且为 true，不显示任何 toast
    if (res.ismessage) {
      return res;
    }
    if (res.status === 200 || res.success) {
      return res;
    }
    showToast({
      message: res.message || "业务异常",
      position: "top",
      type: "fail",
    });
    return Promise.reject(normalizeApiError(res, "业务异常"));
  },
  (error) => {
    const resData = error.response?.data;

    // 🔁 nonce 复用（8401）：换一个随机数后用本实例重发一次，用户无感。
    //    本实例**没有** validateStatus:()=>true（与 core.js 不同），HTTP 400 会走到这里。
    if (isNonceReplay(resData)) {
      return replayFileAfterNonceConflict(error);
    }

    // PIN 码拦截（HTTP 错误中的 8303/8304）
    if (isPinResponse(resData)) {
      return handleFilePinResponse(resData, error.response);
    }

    // 如果有 ismessage 字段且为 true，不显示任何 toast
    if (resData?.ismessage) {
      return Promise.reject(normalizeApiError(error, "网络请求失败"));
    }
    const status = error.response?.status;
    if (status === 401) {
      showToast({
        message: "非法闯入-后端报错401",
        position: "top",
      });
      // 🔒 安全整改 P0-8/P1-17：统一清会话痕迹（含遗留 token、登录标记、API 缓存）
  clearSession({ callServer: false });
  router.push("/login");
    } else if (!error.response || status >= 500) {
      // ⚪ 灰 = 拿不到响应（本地连不上）或远端 5xx（API 故障）—— 五色语义
      showToast({
        type: "info",
        message: !error.response
          ? "网络异常"
          : resData?.message || "服务器开小差了，请稍后重试",
      });
    } else {
      // 🔴 红 = HTTP 4xx，后端明确告诉我们原因
      showFailToast(resData?.message || "网络请求失败");
    }
    // ⚠ 本实例**没有** `validateStatus:()=>true`（与 core.js 不同），HTTP 400 会走这里。
    //   直接 reject 原始 AxiosError 的话，`.message` 是英文
    //   "Request failed with status code 400"，会把后端给的「非法请求！」盖掉。
    return Promise.reject(normalizeApiError(error, "网络请求失败"));
  }
);

/**
 * nonce 复用（8401）后的自愈重发。
 *
 * 与 `interceptors/response.js` 的做法不同，这里**可以直接复用本实例**：
 * `requestfile` 的请求拦截器只注入安全头（不做 body 加密），
 * 重新走一遍即可自动获得全新的 `x-client-nonce`；
 * 且实例自带的响应拦截器会让业务判定保持一致。
 *
 * `__nonceRetried` 标记保证最多只重发一次；重发仍失败则按普通错误提示（绝不静默）。
 */
function replayFileAfterNonceConflict(error) {
  const cfg = buildNonceRetryConfig(error.config);

  if (!cfg) {
    const msg = error.response?.data?.message || NONCE_REPLAY_FALLBACK_MESSAGE;
    showFailToast(msg);
    return Promise.reject(normalizeApiError(error, NONCE_REPLAY_FALLBACK_MESSAGE));
  }

  console.warn("[nonce] 上传请求被判重放，已刷新随机数与时间戳并自动重发一次");
  return requestfile(cfg);
}

function handleFilePinResponse(resData, response) {
  if (resData.code === PIN_CODE.LOCKED) {
    showToast({ message: resData.message || "PIN 已锁定", position: "top" });
    // 🔒 安全整改 P0-8/P1-17：统一清会话痕迹
    clearSession({ callServer: false });
    setTimeout(() => { window.location.href = "/login"; }, 800);
    return Promise.reject(resData);
  }

  if (resData.code === PIN_CODE.NEED_VERIFY) {
    const originalRequest = {
      method: response.config.method,
      url: response.config.url,
      baseURL: response.config.baseURL,
      headers: { ...response.config.headers },
      data: response.config.data,
      params: response.config.params,
    };
    return requestPinVerify(originalRequest, resData.data || {});
  }

  return Promise.reject(resData);
}

export default requestfile;
