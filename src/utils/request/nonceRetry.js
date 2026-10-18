/**
 * nonce 复用（后端业务码 8401）的识别与「自愈重发」
 *
 * ## 背景
 * `securityCheck` 的 `x-client-nonce` 是一次性的，复用会被判定为重放并拒绝。
 * 客户端每次请求都应生成新随机数，但**"重发/重试"路径不可能穷举排查干净**：
 * 浏览器对幂等请求（GET）在连接被重置时的自动重试、反向代理重放、
 * 后人新增的任意重试代码 —— 都会复用同一份请求头。
 *
 * 所以这里做一层兜底：识别到 8401 就**自动重发一次**，用户无感；
 * 重发仍失败才按普通业务错误提示（绝不静默吞掉）。
 *
 * 后端对应实现：`api/src/common/middleware/securityCheck.js`（`CODE_NONCE_REPLAY`）
 */

import axios from "axios";
import { refreshVolatileSecurityHeaders } from "../securityHeaders";

/** 后端在「nonce 复用」时返回的独立业务码（必须与后端保持一致） */
export const CODE_NONCE_REPLAY = 8401;

/** 重发仍失败时的兜底提示文案 */
export const NONCE_REPLAY_FALLBACK_MESSAGE = "请求重复提交，请稍后重试";

/**
 * 该响应体是否表示「nonce 复用」
 * @param {object} resData 响应体
 * @returns {boolean}
 */
export function isNonceReplay(resData) {
  return !!resData && Number(resData.code) === CODE_NONCE_REPLAY;
}

/**
 * 生成可用于重发的请求配置。
 *
 * - 刷新 `x-client-timestamp`（可能已超出后端 ±2 分钟窗口）与 `x-client-nonce`（必须换新）
 * - 打上 `__nonceRetried` 标记，保证**最多只重发一次**（防死循环）
 * - 顺带清掉 PIN 队列用的内部控制字段
 *
 * @param {object} config 原始 axios 请求配置
 * @returns {object|null} 可重发的配置；已重发过则返回 null（调用方按普通错误处理）
 */
export function buildNonceRetryConfig(config) {
  if (!config || config.__nonceRetried) return null;

  const cfg = { ...config, __nonceRetried: true };
  refreshVolatileSecurityHeaders(cfg.headers);
  delete cfg.__pinVerify;
  return cfg;
}

/**
 * 发请求并自带 8401 一次性自愈（安全整改 P2-16，2026-10-08）
 *
 * ## 为什么需要它
 * 主实例（`request/core.js`）有响应拦截器处理 8401 重发，但 `pin.js` 的
 * 冷却静默重发与 PIN 成功后的批量重发走的是**裸全局 axios**（无拦截器）——
 * 重发若被判重放（8401），调用方会拿到 `{code:8401}` 当成普通响应，
 * 与阶段 3 之前 8303 被误判为成功是同一类问题。
 *
 * ## 行为
 * - `validateStatus: () => true`：与主实例口径一致，HTTP 4xx/5xx 不抛异常，
 *   由调用方按业务码判定（pin.js 两处调用点本来就是判定 `res.data` 的写法）。
 * - 收到 8401 ⇒ `buildNonceRetryConfig`（换 nonce/时间戳，带防循环标记）重发**一次**。
 * - 再失败 ⇒ 原样返回 8401 响应，**不静默吞掉**，调用方按普通业务错误处理。
 *
 * @param {object} config axios 请求配置
 * @returns {Promise<object>} axios response（含可能仍是 8401 的 data）
 */
export async function axiosWithNonceRetry(config) {
  let cfg = { ...config, validateStatus: () => true };
  let response = await axios(cfg);

  if (isNonceReplay(response.data)) {
    const retryCfg = buildNonceRetryConfig(config);
    if (!retryCfg) {
      // 已经重发过一次 ⇒ 原样返回，绝不无限重试
      return response;
    }
    console.warn("[nonce] 重发通道被判重放，已刷新随机数与时间戳并自动重发一次");
    cfg = { ...retryCfg, validateStatus: () => true };
    response = await axios(cfg);
  }

  return response;
}
