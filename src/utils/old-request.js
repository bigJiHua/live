import axios from "axios";
import { showToast, showSuccessToast, showFailToast } from "vant";
import { UAParser } from "ua-parser-js";
import { generateBrowserFingerprint } from "./device-hash";
import { getFullNetworkInfo } from "./network";
import { aesUtil } from "./aes";
import router from "@/router";

const baseUrl = "/api/v1";
const TIMEOUT = 15000;

const clientContext = {
  data: null,
  initPromise: null,
};

// 握手状态控制
let sessionAesKey = null;
let handshakePromise = null;

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
        login_location: `${net.external?.country || ""}-${
          net.external?.region || ""
        }-${net.external?.city || ""}`,
        login_isp: net.external?.isp || "",
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

// 核心：单例握手逻辑，防止并发冲突
async function doHandshake(fp, deviceData) {
  if (handshakePromise) return handshakePromise;

  handshakePromise = (async () => {
    try {
      const now = Date.now();
      const hRes = await axios.get(`${baseUrl}/auth/handshake`, {
        params: { fp },
        headers: {
          "X-Requested-With": "XMLHttpRequest",
          "x-client-timestamp": now,
          "x-fingerprint-hash": fp,
          "x-user-agent-custom": deviceData.user_agent,
          "x-device-model": deviceData.device_model,
          "x-os-name": deviceData.os_info,
          "x-device-type": deviceData.device_type,
        },
      });

      if (hRes.data.status === 200) {
        const newKey = hRes.data.key;
        sessionStorage.setItem(`aes_${fp}`, newKey);
        sessionAesKey = newKey;
        return newKey;
      }
      throw new Error("Handshake Failed");
    } finally {
      handshakePromise = null; // 请求结束，清空锁
    }
  })();
  return handshakePromise;
}

const request = axios.create({
  baseURL: baseUrl,
  withCredentials: true,
  timeout: TIMEOUT,
  // validateStatus: (status) => true, // 👈 就是这一行！解决所有弹窗问题！
});

request.interceptors.request.use(
  async (config) => {
    const deviceData = await getClientFullData();
    const fp = deviceData.fingerprint;

    // 1. 注入安检 Headers
    config.headers["X-Requested-With"] = "XMLHttpRequest";
    config.headers["x-client-timestamp"] = Date.now();
    config.headers["x-fingerprint-hash"] = fp;
    config.headers["x-user-agent-custom"] = deviceData.user_agent;
    config.headers["x-device-model"] = deviceData.device_model;
    config.headers["x-os-name"] = deviceData.os_info;
    config.headers["x-device-type"] = deviceData.device_type;

    const token = localStorage.getItem("finance_token");
    if (token) config.headers["Authorization"] = `Bearer ${token}`;

    if (config.url.includes("/auth/handshake")) return config;

    // 2. 加密写操作
    const isWrite = ["post", "put", "delete"].includes(
      config.method?.toLowerCase()
    );
    if (isWrite) {
      const rawData = {
        ...deviceData,
        data: config.data,
        path: window.location.pathname,
      };

      // 自动维护 AES Key
      if (!sessionAesKey) sessionAesKey = sessionStorage.getItem(`aes_${fp}`);
      if (!sessionAesKey) {
        sessionAesKey = await doHandshake(fp, deviceData);
      }

      if (sessionAesKey) {
        config.data = {
          _p: aesUtil.encrypt(JSON.stringify(rawData), sessionAesKey),
        };
        config.headers["X-FP-ID"] = fp;
      }
    }
    return config;
  },
  (error) => Promise.reject(error)
);

request.interceptors.response.use(
  (response) => {
    // 【成功路径】状态码 2xx 进入这里
    const res = response.data || {};

    // 1. 业务静默模式：如果有 ismessage 字段且为 true，直接返回，不触发任何 UI 提示
    if (res.ismessage === true) {
      return res;
    }

    // 2. 正常业务成功处理
    if (res.status === 200 || res.success) {
      if (
        ["post", "put", "delete"].includes(
          response.config.method?.toLowerCase()
        )
      ) {
        showSuccessToast(res.message || "成功");
      }
      return res;
    }

    // 3. 业务逻辑错误（例如：余额不足、验证码错误）
    showToast({
      message: res.message || "业务异常",
      position: "top",
      type: "fail",
    });
    return Promise.reject(res);
  },
  (error) => {
    // 【异常路径】状态码非 2xx (如 404, 500, 401) 进入这里
    const response = error.response;
    const resData = response?.data; // 这里的 resData 就是你后端返回的真实 JSON

    // 1. 核心改进：即便 HTTP 报错，如果后端传了自定义业务信息，优先按业务逻辑处理
    if (resData && resData.ismessage === true) {
      // 如果后端要求静默，或者需要特殊处理，直接 reject 数据给业务层，不弹窗
      return Promise.reject(resData);
    }

    // 2. 处理特定的 HTTP 状态码
    const status = response?.status;

    // 如果后端在 404/400 时传了 message，优先显示后端的 message
    const errorMsg = resData?.message || "网络请求失败";

    if (status === 401) {
      showToast({ message: "登录失效，请重新登录", position: "top" });
      sessionStorage.clear();
      localStorage.removeItem("finance_token");
      router.push("/login");
    } else if (status === 429) {
      sessionStorage.clear();
      router.push("/429");
    } else {
      // 3. 其他系统级错误（如 500 崩溃、404 资源不存在）
      console.error("System Error:", error);
      showFailToast(errorMsg);
    }

    return Promise.reject(error);
  }
);

export default request;
