/**
 * SSE 流式请求（追加164）
 *
 * 为什么绕过 axios：浏览器 axios 会把响应整体缓冲，无法逐字渲染 AI 输出。
 * 服务端流式端点在业务开始前可能以普通 JSON 返回错误（限流/守卫 409/业务 401），
 * 开始后才是 text/event-stream——本封装对两种形态都处理。
 *
 * 安全机制与 axios 请求拦截器（interceptors/request.js）逐项对齐：
 * - 写方法 AES 加密封包 {_p: encrypt(rawData, aesKey)}（crypto/handshake 同源）
 * - 同一批安全头：一次性 nonce/时间戳/指纹/设备上下文（securityHeaders/client 同源）
 * - Cookie 会话（credentials: 'include'）
 * - 业务 401 → 清会话跳登录；其余错误 → normalizeApiError 同款错误形状
 * - 🔁 解密隧道失效自愈（2026-11-07）：400「请求数据非法」= 服务端解密失败
 *   （前端握手 key 与当前服务端不一致，多端/多服务器漂移典型）⇒ 清 key 重新
 *   握手后用新 key 重新加密重发一次。请求未进入业务层（安全中间件已拒），
 *   重发不构成业务重复调用。
 */

import config from './config';
import { getClientContext } from './client';
import { getAesKey, clearAesKey } from './handshake';
import { encrypt } from './crypto';
import { createClientNonce } from '../securityHeaders';
import { getLegacyToken } from '../authState';
import { clearSession } from '../session';
import { normalizeApiError } from './helpers';
import { createSseFrameParser } from '../aiSse';

const DECRYPT_FAIL_MESSAGES = ['请求数据非法', 'KEY_ERR'];
const STREAM_DEADLINE_MS = 120000;
const isDecryptFailureBody = payload =>
  Boolean(payload && DECRYPT_FAIL_MESSAGES.some(m => typeof payload.message === 'string' && payload.message.includes(m)));

// Context and shared handshake promises do not accept a signal. Stop waiting at
// the same deadline anyway, and never let their late result start another stage.
function abortable(action, signal) {
  return new Promise((resolve, reject) => {
    const abort = () => { signal.removeEventListener('abort', abort); reject(signal.reason); };
    if (signal.aborted) { abort(); return; }
    signal.addEventListener('abort', abort, { once: true });
    Promise.resolve().then(() => {
      if (signal.aborted) throw signal.reason;
      return action();
    }).then(value => {
      signal.removeEventListener('abort', abort);
      if (signal.aborted) reject(signal.reason);
      else resolve(value);
    }, error => { signal.removeEventListener('abort', abort); reject(error); });
  });
}

async function attemptStream(url, data, deviceData, signal) {
  const fp = deviceData.fingerprint;
  const aesKey = await abortable(() => getAesKey(fp, deviceData), signal);
  const rawData = { ...deviceData, data, path: window.location.pathname };
  const body = aesKey ? { _p: encrypt(rawData, aesKey) } : rawData;
  const headers = {
    'Content-Type': 'application/json',
    'X-Requested-With': 'XMLHttpRequest',
    'x-client-timestamp': Date.now(),
    'x-client-nonce': createClientNonce(),
    'x-fingerprint-hash': fp,
    'x-user-agent-custom': deviceData.userAgent,
    'x-device-model': deviceData.deviceModel,
    'x-os-name': deviceData.osInfo,
    'x-device-type': deviceData.deviceType,
    'X-FP-ID': fp,
  };
  const legacyToken = getLegacyToken();
  if (legacyToken) headers['Authorization'] = `Bearer ${legacyToken}`;

  return abortable(() => fetch(config.baseURL + url, {
    method: 'POST', headers, credentials: 'include', body: JSON.stringify(body), signal,
  }), signal);
}

export async function streamRequest({ url, data, onEvent, signal, timeoutMs }) {
  const deadlineMs = Number.isInteger(timeoutMs) && timeoutMs >= 30000 && timeoutMs <= 370000
    ? timeoutMs : STREAM_DEADLINE_MS;
  const controller = new AbortController();
  const manualAbort = () => controller.abort(signal.reason?.name === 'AbortError' ? signal.reason :
    new DOMException('已停止读取 AI 响应', 'AbortError'));
  signal?.addEventListener('abort', manualAbort, { once: true });
  if (signal?.aborted) manualAbort();
  const timer = setTimeout(() => controller.abort(Object.assign(new Error('AI 响应等待超时，请读取最终回执'),
    { status: -1, code: 'AI_STREAM_TIMEOUT', kind: 'network' })), deadlineMs);
  let reader;
  const cancelReader = () => {
    // Cancellation can itself hang on a broken stream. It must not keep the
    // request or its timer alive after a terminal event, timeout, or manual stop.
    try { Promise.resolve(reader?.cancel()).catch(() => {}); } catch (_) { /* 已关闭 */ }
  };
  try {
    const deviceData = await abortable(() => getClientContext(), controller.signal);
    let response = await attemptStream(url, data, deviceData, controller.signal);

    // Only a confirmed pre-business decrypt rejection may use the existing one-time repair.
    if (response.status === 400) {
      let payload = null;
      try { payload = await abortable(() => response.clone().json(), controller.signal); }
      catch (error) { if (controller.signal.aborted) throw error; }
      if (isDecryptFailureBody(payload)) {
        clearAesKey();
        console.warn('[key] 流式请求被服务端解密拒绝，已重新握手并用新 key 重发一次');
        response = await attemptStream(url, data, deviceData, controller.signal);
      }
    }

    const contentType = response.headers.get('content-type') || '';
    if (!contentType.includes('text/event-stream')) {
      let payload = null;
      try { payload = await abortable(() => response.json(), controller.signal); }
      catch (error) { if (controller.signal.aborted) throw error; }
      if (payload && (payload.status === 401 || response.status === 401)) {
        clearSession();
        window.location.replace('/login');
        throw Object.assign(new Error(payload.message || '登录已失效'), { status: 401, code: 'AUTH_REQUIRED', kind: 'business' });
      }
      throw normalizeApiError({ response: { status: response.status, data: payload, headers: response.headers } }, 'AI 流式请求失败');
    }

    reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let sawDone = false;
    const parser = createSseFrameParser(({ event, data }) => {
      if (controller.signal.aborted || sawDone) return;
      onEvent?.({ event, data });
      if (event === 'done') sawDone = true;
    });
    for (;;) {
      const { done, value } = await abortable(() => reader.read(), controller.signal);
      if (done) break;
      parser.push(decoder.decode(value, { stream: true }));
      if (controller.signal.aborted) throw controller.signal.reason;
      if (sawDone) { cancelReader(); break; }
    }
    parser.end();
    if (controller.signal.aborted) throw controller.signal.reason;
  } catch (error) {
    cancelReader();
    throw controller.signal.aborted ? controller.signal.reason : error;
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', manualAbort);
  }
}

export default { streamRequest };

// Authenticated, read-only GET SSE. It has no provider request or write retry.
export async function subscribeRequest({ url, onEvent, signal }) {
  const controller = new AbortController();
  const abort = () => controller.abort(signal?.reason || new DOMException('已停止同步', 'AbortError'));
  signal?.addEventListener('abort', abort, { once: true });
  if (signal?.aborted) abort();
  const timer = setTimeout(() => controller.abort(new Error('同步连接到期')), 65000);
  let reader;
  try {
    const device = await abortable(() => getClientContext(), controller.signal);
    const headers = { 'Accept': 'text/event-stream', 'X-Requested-With': 'XMLHttpRequest',
      'x-client-timestamp': Date.now(), 'x-client-nonce': createClientNonce(), 'x-fingerprint-hash': device.fingerprint,
      'x-user-agent-custom': device.userAgent, 'x-device-model': device.deviceModel, 'x-os-name': device.osInfo,
      'x-device-type': device.deviceType, 'X-FP-ID': device.fingerprint };
    const token = getLegacyToken(); if (token) headers.Authorization = `Bearer ${token}`;
    const response = await abortable(() => fetch(config.baseURL + url, { method: 'GET', credentials: 'include', headers, signal: controller.signal }), controller.signal);
    if (!response.headers.get('content-type')?.includes('text/event-stream')) {
      const data = await abortable(() => response.json(), controller.signal);
      throw normalizeApiError({ response: { status: response.status, data, headers: response.headers } }, '会话同步暂不可用');
    }
    reader = response.body.getReader();
    const decoder = new TextDecoder();
    let frames = Promise.resolve();
    const parser = createSseFrameParser(frame => {
      frames = frames.then(() => { if (!controller.signal.aborted) return onEvent?.(frame); });
      frames.catch(() => {}); // A late asynchronous callback must not become an orphan rejection after abort.
    });
    for (;;) {
      const part = await abortable(() => reader.read(), controller.signal);
      if (part.done) break;
      parser.push(decoder.decode(part.value, { stream: true }));
      await abortable(() => frames, controller.signal);
    }
    parser.end(); await abortable(() => frames, controller.signal);
  } finally {
    clearTimeout(timer); signal?.removeEventListener('abort', abort);
    try { Promise.resolve(reader?.cancel()).catch(() => {}); } catch (_) { /* Already closed. */ }
    controller.abort();
  }
}
