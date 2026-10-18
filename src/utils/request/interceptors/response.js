import axios from 'axios';
import { showToast, showSuccessToast, showFailToast } from '@/utils/toast';
import config from "../config";
import { normalizeApiError } from "../helpers";
import { PIN_CODE, isPinResponse, requestPinVerify, registerForceLogin } from "../pin";
import { clearSession } from "@/utils/session";
import {
  isNonceReplay,
  buildNonceRetryConfig,
  NONCE_REPLAY_FALLBACK_MESSAGE,
} from "../nonceRetry";

let isRedirecting = false;

/** 防循环护栏：上次 forceLogin 时间（存 localStorage —— clearSession 的 sessionStorage.clear() 会把 sessionStorage 版清掉，导致跨页冷却失效） */
const KICK_KEY = "finance_kick_at";
const KICK_COOLDOWN_MS = 10 * 1000;

/**
 * 强制跳登录（移动端专用）
 *
 * 🚧 防循环护栏（2026-10-12 修复③）：
 * 此前「401 → 硬跳 /login」与路由守卫的软导航 replace("/login?redirect=x") 竞争，
 * URL 在两者之间变化 ⇒ 整页刷新反复发生（401 风暴）。护栏两条：
 * ① 已在登录页 ⇒ 会话无需再清，静默返回（配合修复②，登录页不应再有 401 源）
 * ② 冷却期内（10s 内已踢过一次）⇒ 只清会话、不再整页跳转，防止反复刷新
 */
function forceLogin(message) {
  if (isRedirecting) return;

  let onLoginPage = false;
  let inCooldown = false;
  try {
    onLoginPage = window.location.pathname === "/login";
    if (!onLoginPage) {
      const last = Number(localStorage.getItem(KICK_KEY) || 0);
      if (Date.now() - last < KICK_COOLDOWN_MS) {
        inCooldown = true;
      } else {
        localStorage.setItem(KICK_KEY, String(Date.now()));
      }
    }
  } catch {
    /* 存储不可用（隐私模式等）⇒ 按旧行为处理 */
  }

  if (onLoginPage) return;

  // 🔒 安全整改 P0-8 / P1-17：统一清会话痕迹。
  //    包括登录标记、遗留 token、AES 会话密钥、**API 响应缓存**（P0-8 的保护不能丢）。
  //    callServer:false —— 此刻令牌已失效，调后端 logout 没有意义；
  //    且 Cookie 本来就是由服务端在登出时清的，401 说明它已无用。
  clearSession({ callServer: false });

  if (inCooldown) return;

  isRedirecting = true;

  showToast({
    message: message || "登录已失效",
    position: "top",
    duration: 800,
  });

  setTimeout(() => {
    window.location.href = "/login";
  }, 300);
}

// 注册 forceLogin 到 pin.js，避免循环依赖
registerForceLogin(forceLogin);

export function createResponseInterceptor() {
  // ⚠ 两个处理器提成具名函数：nonce 复用自愈重发后需要把响应**重新送回同一套判定逻辑**
  //   （见 replayAfterNonceConflict），保证 PIN / 401 / 提示等业务处理完全一致。
  return [onFulfilled, onRejected];
}

/**
 * ✅ 成功响应
 *
 * 注意：实例设置了 `validateStatus: () => true`（见 core.js），
 * 因此 **HTTP 4xx/5xx 也会走到这里**，业务错误必须在本函数内判定。
 */
async function onFulfilled(response) {
  if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
    // 🔄 P2-18（2026-10-08）：二进制通道也做 8401 自愈。
    //   后端业务错误（含 8401）的 body 是 JSON，但 responseType:'blob' 时 axios
    //   会把 JSON 包成 Blob ⇒ 旧代码直接 return，下载通道被判重放时
    //   调用方拿到一个"内容是 JSON 错误"的 Blob 存成坏文件（静默损坏）。
    return handleBinaryResponse(response);
  }

  const res = response.data || {};

  // 🔁 nonce 复用（8401）：随机数被别人用过 ⇒ 换一个随机数自动重发一次，用户无感。
  //    放在最前面：它不属于任何业务语义，先自愈再谈业务。
  if (isNonceReplay(res)) {
    return replayAfterNonceConflict(res, response);
  }

  // 🔒 PIN 码拦截（8303 需要验证 / 8302 错误 / 8304 锁定）
  if (isPinResponse(res)) {
    return handlePinResponse(res, response);
  }

  // 🚨 手机端关键：业务401也拦
  if (res.status === 401) {
    forceLogin(res.message);
    return Promise.reject(normalizeApiError(res, "登录已失效"));
  }

  if (res.ismessage === true) return res;

  if (res.status === 200 || res.status === 202 || res.success) {
    const isWrite = config.encryptedMethods.includes(
      response.config.method?.toLowerCase()
    );

    if (isWrite && res.status !== 202) {
      showSuccessToast(res.message || "成功");
    }

    return res;
  }

  showFailToast(res.message || "业务异常");
  // ⚠ reject 的必须是「归一化后的对象」：页面层普遍写 `catch (e) => showToast(e.message)`，
  //   直接抛后端体虽然也有 message，但走 AxiosError 的路径就会把英文文案透给用户。
  return Promise.reject(normalizeApiError(res, "业务异常"));
}

/**
 * ❌ 错误响应
 */
async function onRejected(error) {
  const response = error.response;
  const status = response?.status;
  const resData = response?.data;

  // 🔁 nonce 复用（8401）：兜底一层。本实例因 `validateStatus: () => true` 通常不会走到这里，
  //    但若将来有人改掉该配置，这条能保证自愈逻辑依然生效。
  if (isNonceReplay(resData)) {
    return replayAfterNonceConflict(resData, response);
  }

  // 🔒 PIN 码拦截（HTTP 401 + PIN code）
  if (isPinResponse(resData)) {
    return handlePinResponse(resData, response);
  }

  if (resData?.ismessage) {
    return Promise.reject(normalizeApiError(resData, "请求失败"));
  }

  // 🚨 真401（非 PIN 相关）
  if (status === 401) {
    forceLogin(resData?.message);
    return;
  }

  if (status === 429) {
    window.location.href = "/429";
    return;
  }

  if (!response) {
    // ⚪ 灰 = 拿不到响应（本地连不上 / 超时 / 断网）—— 五色语义
    showToast({ type: "info", message: "网络异常" });
    return Promise.reject(normalizeApiError(error, "网络异常"));
  }

  if (status >= 500) {
    // ⚪ 灰 = 远端 5xx（API 故障），不是业务失败
    showToast({ type: "info", message: resData?.message || "服务器开小差了，请稍后重试" });
  } else {
    // 🔴 红 = HTTP 4xx，后端明确告诉我们原因
    showFailToast(resData?.message || "请求失败");
  }
  // ⚠ 归一化后再 reject：`error.message` 原本是 axios 的英文
  //   "Request failed with status code 400"，会盖掉后端给的 message。
  return Promise.reject(normalizeApiError(error, "请求失败"));
}

/**
 * nonce 复用后的「自愈重发」。
 *
 * ⚠ 必须用**全局 axios**（不带本实例的拦截器）重发：
 * 本实例的请求拦截器会对写操作**再次加密** body（`{ _p: encrypt(...) }`），
 * 而 `response.config.data` 里已经是密文，再加密一次后端就解不开了。
 * 这与 `pin.js` 中 PIN 重发使用裸 axios 的原因一致。
 *
 * 重发后把响应交回 onFulfilled / onRejected，保证业务判定完全一致；
 * 且 `__nonceRetried` 标记保证最多只重发一次。
 */
/**
 * 🔄 P2-18：二进制（blob/arraybuffer）通道的 8401 识别与自愈
 *
 * 后端业务错误的 body 是 JSON，但 responseType:'blob' 时 axios 会把
 * JSON 错误包成 Blob。这里检测「JSON 类型的 Blob」并解析判定 8401。
 *
 * ⚠ 注意：真实的 .zip/.xlsx 下载响应 Content-Type 不是 json ⇒ 不受影响，
 *   不会被误解析（parse 失败也按原样返回）。
 */
async function handleBinaryResponse(response) {
  const data = response.data;
  // 🔄 复审修复（2026-10-08）：补 arraybuffer 通道 —— 分支声明覆盖了
  //   'arraybuffer'，但这里此前只认 Blob，arraybuffer 里的 JSON 8401 不被自愈。
  let text = null;
  if (typeof Blob !== 'undefined' && data instanceof Blob) {
    if (data.type && data.type.includes('json')) {
      try { text = await data.text(); } catch { text = null; }
    }
  } else if (data instanceof ArrayBuffer) {
    // ArrayBuffer 无 content-type 可看：只有小体积响应才尝试按 JSON 解析
    //（正常下载是压缩包/表格，动辄 MB 级；8401 错误体 < 1KB）
    if (data.byteLength > 0 && data.byteLength <= 1024) {
      try { text = new TextDecoder().decode(data); } catch { text = null; }
    }
  }

  if (text !== null) {
    try {
      const parsed = JSON.parse(text);
      if (isNonceReplay(parsed)) {
        return replayBinaryAfterNonceConflict(parsed, response);
      }
    } catch {
      /* 不是合法 JSON（正常下载内容）⇒ 按原响应返回 */
    }
  }
  return response;
}

/**
 * 🔄 P2-18：二进制通道的 8401 重发（一次性，与 replayAfterNonceConflict 同语义）
 */
async function replayBinaryAfterNonceConflict(res, response) {
  const cfg = buildNonceRetryConfig(response?.config);

  if (!cfg) {
    // 已重发过仍被判重放 ⇒ 按普通业务失败处理，绝不静默吞掉
    showFailToast(res.message || NONCE_REPLAY_FALLBACK_MESSAGE);
    return Promise.reject(normalizeApiError(res, NONCE_REPLAY_FALLBACK_MESSAGE));
  }

  console.warn("[nonce] 下载通道被判重放，已刷新随机数与时间戳并自动重发一次");
  let retried;
  try {
    retried = await axios({ ...cfg, validateStatus: () => true });
  } catch (e) {
    return onRejected(e);
  }
  // 重发结果再走一遍 onFulfilled：仍是 json blob 错误也能被正确判定
  return onFulfilled(retried);
}

async function replayAfterNonceConflict(res, response) {
  const cfg = buildNonceRetryConfig(response?.config);

  if (!cfg) {
    // 已经重发过一次仍被判重放：不再重试，按普通业务失败提示（绝不静默吞掉）
    showFailToast(res.message || NONCE_REPLAY_FALLBACK_MESSAGE);
    return Promise.reject(normalizeApiError(res, NONCE_REPLAY_FALLBACK_MESSAGE));
  }

  console.warn("[nonce] 请求被判重放，已刷新随机数与时间戳并自动重发一次");

  let retried;
  try {
    // validateStatus 恒真：与实例配置保持一致，让 HTTP 错误继续由 onFulfilled 判定
    retried = await axios({ ...cfg, validateStatus: () => true });
  } catch (e) {
    // 网络层失败（拿不到响应）→ 交给 onRejected 统一处理
    return onRejected(e);
  }

  return onFulfilled(retried);
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
    if (response.config.url?.includes("/security/pin/route-verify")) {
      return Promise.reject(resData);
    }

    // 锁定接口自身返回 8303（语义是"已锁定"，不是"需要验证"）：
    // 不能进 requestPinVerify 队列，否则解锁成功后 onPinSuccess 会重发一次 /auth/lock-system，
    // 后端此时查不到 pin_status=0 的 lock 记录 → 又插一条 → 刚解锁又被锁上。
    if (response.config.url?.includes("/lock-system")) {
      return resData;
    }

    // 保存原始请求配置用于重发
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
