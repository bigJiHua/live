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
    const res = response.data || {};
    // 如果有 ismessage 字段且为 true，不显示任何 toast
    if (res.ismessage) {
      return res;
    }
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
    showToast({
      message: res.message || "业务异常",
      position: "top",
      type: "fail",
    });
    return Promise.reject(res);
  },
  (error) => {
    // 如果有 ismessage 字段且为 true，不显示任何 toast
    if (error.response?.data?.ismessage) {
      return Promise.reject(error);
    }
    const status = error.response?.status;
    if (status === 401) {
      showToast({
        message: "非法闯入--后端报错401",
        position: "top",
      });
      sessionStorage.clear(); // 清理旧 Key
      localStorage.removeItem("finance_token");
      router.push("/login");
    }else if (status === 429) {
      sessionStorage.clear(); // 清理旧 Key
      localStorage.removeItem("finance_token");
      router.push("/429");
    } else {
      showFailToast(error.response?.data?.message || "网络请求失败");
    }
    return Promise.reject(error);
  }
);

export default request;
