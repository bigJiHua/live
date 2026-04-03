import axios from "axios";
import { showToast, showSuccessToast, showFailToast } from "vant";
import { UAParser } from "ua-parser-js";
import { generateBrowserFingerprint } from "./device-hash";
import { getFullNetworkInfo } from "./network";
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
    config.headers["x-fingerprint-hash"] = deviceData.fingerprint;
    config.headers["x-user-agent-custom"] = deviceData.user_agent;
    config.headers["x-device-model"] = deviceData.device_model;
    config.headers["x-os-name"] = deviceData.os_info;
    config.headers["x-device-type"] = deviceData.device_type;

    // 2. 注入 Token
    const token = localStorage.getItem("finance_token");
    if (token) config.headers["Authorization"] = `Bearer ${token}`;

    return config;
  },
  (error) => Promise.reject(error)
);

requestfile.interceptors.response.use(
  (response) => {
    const res = response.data || {};
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
        message: "非法闯入-后端报错401",
        position: "top",
      });
      sessionStorage.clear();
      localStorage.removeItem("finance_token");
      router.push("/login");
    } else {
      showFailToast(error.response?.data?.message || "网络请求失败");
    }
    return Promise.reject(error);
  }
);

export default requestfile;
