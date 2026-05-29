/**
 * PIN 安全验证中间件
 *
 * 功能：
 * 1. 全局拦截所有带 PIN 码的用户请求
 * 2. 支持 30 秒放行窗口（验证一次后短期内免验证）
 * 3. 滑动窗口频率限制（60 秒内请求超限强制 PIN 验证）
 * 4. 敏感操作（POST/PUT/PATCH/DELETE）额外风控
 * 5. 连续 3 次 PIN 错误触发临时锁定（30 分钟后自动解锁）
 *
 * 响应码（code 字段）：
 * - 8301：验证成功
 * - 8302：PIN 错误
 * - 8303：需要验证（弹出 PIN 输入框）
 * - 8304：已锁定
 *
 * 验证状态（pin_status 字段）：
 * - 0：待验证（发起验证请求，等待用户输入）
 * - 1：验证通过
 * - 2：验证失败（PIN 错误）
 * - 3：已锁定（连续错误达到阈值）
 */

const jwt = require("jsonwebtoken");
const db = require("../../common/config/db");
const bcrypt = require("bcryptjs");
const pinLockGuard = require("./pinLockGuard");

const ROUTE_VERIFY_ACTION = "route_verify";
const ROUTE_VERIFY_API = "/security/pin/route-verify";

/**
 * 配置项
 * @property MAX_ERROR_COUNT    连续错误达到此值则触发锁定（默认 3 次）
 * @property LOCK_MULTIPLIER     锁定时长乘数（错误次数 × 此值 = 锁定分钟数，默认 3×30=90 分钟）
 * @property FREQ_WINDOW        频率检查时间窗口（秒，默认 60 秒）
 * @property FREQ_LIMIT         时间窗口内最大请求数（默认 60 次），超限触发 PIN 验证
 * @property SENSITIVE_WINDOW   敏感操作检查时间窗口（秒，默认 60 秒）
 * @property SENSITIVE_LIMIT    敏感操作次数上限（默认 10 次），超限触发 PIN 验证
 */
const CONFIG = {
  MAX_ERROR_COUNT: 3,
  LOCK_MULTIPLIER: 30,
  FREQ_WINDOW: 60,
  FREQ_LIMIT: 60,
  SENSITIVE_WINDOW: 60,
  SENSITIVE_LIMIT: 10,
};

/**
 * PIN 响应码
 * @property NEED_VERIFY 8303 需要验证（前端弹出 PIN 输入框）
 * @property SUCCESS     8301 验证成功
 * @property ERROR       8302 PIN 错误
 * @property LOCKED      8304 已锁定
 */
const CODE = {
  NEED_VERIFY: 8303,
  SUCCESS: 8301,
  ERROR: 8302,
  LOCKED: 8304,
};

/** 需要额外风控的 HTTP 方法（增删改操作） */
const SENSITIVE_METHODS = ["POST", "PUT", "PATCH", "DELETE"];

/**
 * 统一响应格式
 * @param {Object} res Express 响应对象
 * @param {number} code 响应码（8301-8304）
 * @param {string} message 提示信息
 * @param {Object} data 额外数据
 */
function respond(res, code, message, data = {}) {
  return res.status(200).json({ code, status: 401, message, data });
}

// ================= 内存滑动窗口（防暴力请求） =================

/** 频率限制滑动窗口：userId -> [时间戳数组] */
const freqMap = new Map();

/** 敏感操作滑动窗口：userId -> [时间戳数组] */
const sensitiveMap = new Map();

/**
 * 验证放行窗口
 * 用户验证 PIN 成功后，在此期间内的请求不再重复验证
 * userId -> { verifiedAt: 时间戳, count: 放行期内累计请求数 }
 */
const verifiedMap = new Map();

/**
 * 待验证记录插入锁
 * 防止同一用户并发请求时重复插入多条待验证记录
 * userId -> Promise（该用户的插入操作）
 */
const insertLocks = new Map();

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * 通用滑动窗口检查
 * @param {Map} map 存储窗口数据的 Map
 * @param {string} key 标识键（如 userId）
 * @param {number} windowSec 窗口时长（秒）
 * @param {number} limit 窗口内允许的最大事件数
 * @returns {boolean} 是否超限
 */
function slidingWindowCheck(map, key, windowSec, limit) {
  const now = Date.now();
  const windowMs = windowSec * 1000;

  let arr = map.get(key) || [];

  // 清除超出窗口的时间戳
  arr = arr.filter((t) => now - t < windowMs);
  arr.push(now);

  map.set(key, arr);

  return arr.length >= limit;
}

/**
 * 检查用户请求频率是否超限
 * 60 秒内超过 60 次请求则触发 PIN 验证
 */
function checkFrequency(userId) {
  return slidingWindowCheck(freqMap, userId, CONFIG.FREQ_WINDOW, CONFIG.FREQ_LIMIT);
}

/**
 * 检查用户敏感操作频率是否超限
 * 60 秒内超过 10 次敏感操作（POST/PUT/PATCH/DELETE）则触发 PIN 验证
 */
function checkSensitive(userId) {
  return slidingWindowCheck(
    sensitiveMap,
    userId,
    CONFIG.SENSITIVE_WINDOW,
    CONFIG.SENSITIVE_LIMIT
  );
}

// ================= 数据库操作 =================

/**
 * 获取用户 PIN 码哈希
 * @param {string} userId 用户 ID
 * @returns {Promise<string|null>} bcrypt 哈希或 null（未设置 PIN）
 */
async function getUserPin(userId) {
  const [rows] = await db.execute(
    `SELECT pin_code FROM user_info WHERE id = ? LIMIT 1`,
    [userId]
  );
  return rows[0]?.pin_code || null;
}

/**
 * 获取用户最新一条 PIN 验证记录
 * @param {string} userId 用户 ID
 * @returns {Promise<Object|null>}
 */
async function getLatestRecord(userId) {
  const [rows] = await db.execute(
    `SELECT *
     FROM security_verify_log
     WHERE user_id = ? AND action_type <> ?
     ORDER BY id DESC LIMIT 1`,
    [userId, ROUTE_VERIFY_ACTION]
  );
  return rows[0] || null;
}

/**


 * 插入一条待验证记录（pin_status = 0）
 * 同一用户的并发请求会等待前一次插入完成后再插入，避免重复
 *
 * @param {string} userId  用户 ID
 * @param {string} url     触发验证的请求路径
 * @param {string} action  操作类型（通常为 HTTP 方法）
 * @param {string} remark  备注信息
 */
async function insertRecord(userId, url, action, remark = "待验证") {
  // 如果已有同用户的插入在执行中，等待完成后直接返回（避免重复插入）
  if (insertLocks.has(userId)) {
    await insertLocks.get(userId);
    return;
  }

  const lockPromise = (async () => {
    try {
      await db.execute(
        `INSERT INTO security_verify_log
         (user_id, request_url, action_type, pin_status, error_count, remark, create_time)
         VALUES (?, ?, ?, 0, 0, ?, NOW())`,
        [userId, url, action || "verify", remark]
      );
    } finally {
      insertLocks.delete(userId);
    }
  })();

  insertLocks.set(userId, lockPromise);
  await lockPromise;
}

/**
 * 将最新的待验证/失败记录标记为验证成功
 * 只更新状态为 0（待验证）或 2（失败）的记录
 */
async function markSuccess(userId) {
  await db.execute(
    `UPDATE security_verify_log
     SET pin_status = 1, remark = '验证成功'
     WHERE user_id = ? AND pin_status IN (0,2)
     ORDER BY id DESC LIMIT 1`,
    [userId]
  );
}

/**
 * 将最新的待验证记录标记为验证失败
 * @param {number} count 当前累计错误次数
 */
async function markFailed(userId, count) {
  await db.execute(
    `UPDATE security_verify_log
     SET pin_status = 2, error_count = ?, remark = ?
     WHERE user_id = ? AND pin_status IN (0,2)
     ORDER BY id DESC LIMIT 1`,
    [count, `验证失败第${count}次`, userId]
  );
}

/**
 * 将最新的待验证记录标记为已锁定
 * @param {number} count 当前累计错误次数（决定锁定时长：count × LOCK_MULTIPLIER 分钟）
 */
async function markLocked(userId, count) {
  await db.execute(
    `UPDATE security_verify_log
     SET pin_status = 3, error_count = ?, remark = ?
     WHERE user_id = ? AND pin_status IN (0,2)
     ORDER BY id DESC LIMIT 1`,
    [count, `锁定${count * CONFIG.LOCK_MULTIPLIER}分钟`, userId]
  );
}

/**
 * 检查锁定记录是否仍然有效（未到期）
 * @param {Object} record security_verify_log 表的最新记录
 * @returns {{ locked: boolean, remainMinutes?: number }}
 *          locked=true 表示仍在锁定中，remainMinutes 为剩余分钟数
 */
async function checkLock(record) {
  if (!record || record.pin_status !== 3) return { locked: false };

  // 锁定时长 = error_count × LOCK_MULTIPLIER 分钟
  const lockMinutes = (record.error_count || 1) * CONFIG.LOCK_MULTIPLIER;
  const expire = new Date(record.create_time).getTime() + lockMinutes * 60000;

  if (Date.now() < expire) {
    return {
      locked: true,
      remainMinutes: Math.ceil((expire - Date.now()) / 60000),
    };
  }

  return { locked: false };
}

/**
 * 验证用户输入的 PIN 码
 * @param {string} userId 用户 ID
 * @param {string} pin     用户输入的 6 位 PIN
 * @returns {Promise<boolean>} PIN 是否正确
 */
async function verifyPin(userId, pin) {
  const hash = await getUserPin(userId);
  if (!hash) return false;
  return bcrypt.compare(pin, hash);
}

// ================= 中间件 =================

/**
 * PIN 安全验证中间件
 *
 * 流程概览：
 * 1. 从 Authorization Header 解析 userId，未登录则跳过
 * 2. 检查用户是否设置了 PIN，未设置则跳过
 * 3. 检查是否处于锁定状态，锁定中则返回 8304
 * 4. 根据 security_verify_log 最新记录的状态分支处理：
 *    - pin_status = 0/2（待验证/失败）：请求带 PIN 参数？验证 or 弹窗
 *    - pin_status = 1（已验证）：检查 30 秒放行窗口 and 请求频率
 *    - 无记录：插入待验证记录，返回 8303
 */
const pinSecurityGuard = async (req, res, next) => {
  // 小程序端暂时跳过设备验证
  // if (req.xcxBypass) return next();

  try {
    const url = req.originalUrl || req.path;
    const method = req.method;
    const routeVerifyTarget = pinLockGuard.matchProtected(method, url);

    // 握手接口跳过验证（用于前端检测服务器状态）
    if (url.includes("/handshake")) return next();

    // 从 JWT Token 中提取 userId
    let userId = null;
    const authHeader = req.headers.authorization;

    if (authHeader?.startsWith("Bearer ")) {
      try {
        const token = authHeader.substring(7);
        const decoded = jwt.verify(token, process.env.JWT_SECRET);
        userId = decoded.userId;
      } catch {}
    }

    // 未登录用户直接放行
    if (!userId) return next();

    // 查询用户最新一条 PIN 验证记录
    let latest = await getLatestRecord(userId);

    // ── 分支 1：系统 PIN 硬锁 ──
    if (latest?.pin_status === 3) {
      const lock = await checkLock(latest);
      if (lock.locked) {
        return respond(res, CODE.LOCKED, `锁定中 ${lock.remainMinutes} 分钟`);
      }
      // 锁已过期 → 必须验证 PIN，错误则重新锁定 90 分钟
      const pin = req.body?.data?.pin;
      if (!pin) {
        return respond(res, CODE.NEED_VERIFY, "系统已锁定，请验证 PIN");
      }
      const valid = await verifyPin(userId, pin);
      if (!valid) {
        await db.execute(`UPDATE security_verify_log SET remark = CONCAT(remark, ';已重新锁定') WHERE id = ?`, [latest.id]);
        await db.execute(
          `INSERT INTO security_verify_log
           (user_id, request_url, action_type, pin_status, error_count, remark, create_time)
           VALUES (?, '/lock-system', 'verify', 3, 3, ?, NOW())`,
          [userId, `锁定${CONFIG.LOCK_MULTIPLIER * 3}分钟`]
        );
        return respond(res, CODE.LOCKED, "PIN 错误，已重新锁定");
      }
      // 正确 → 解锁
      await db.execute(`UPDATE security_verify_log SET pin_status = 1, remark = '系统已解锁' WHERE id = ?`, [latest.id]);
      verifiedMap.set(userId, { verifiedAt: Date.now(), count: 0 });
      return next();
    }

    // ── 分支 1.5：检查路由验证触发的软锁定（无时间限制，验证 PIN 后自动解除）──
    if (latest?.action_type === 'lock' && latest?.pin_status === 0) {
      const pin = req.body?.data?.pin;
      if (!pin) {
        return respond(res, CODE.NEED_VERIFY, "操作受限，请验证 PIN");
      }
      // 携带了 PIN → 继续到下面的 PIN 验证流程，成功后 markSuccess 会自动清除软锁
    }

    // 携带路由验证 token → 系统锁已过 → 放行给 pinLockGuard 校验 token
    const routeToken = req.headers['x-route-verify-token'];
    if (routeToken) return next();

    // 风险路由 PIN 校验入口交给 pinLockGuard 自己处理，避免全局 PIN 抢先消费。
    if (url.includes(ROUTE_VERIFY_API)) return next();

    // 已挂 pinLockGuard 的风险路由（首次请求无 token）由路由级 challenge 流程负责
    if (routeVerifyTarget && (!latest || latest.pin_status === 1 || latest.pin_status === 3)) {
      return next();
    }

    // 获取用户 PIN 码（用于判断是否启用了 PIN）
    const pin = req.body?.data?.pin;
    const userPin = await getUserPin(userId);

    // 未设置 PIN 的用户直接放行
    if (!userPin) return next();

    // ── 分支 2：待验证 / 验证失败 / 无记录 ──
    if (!latest || latest.pin_status === 0 || latest.pin_status === 2) {

      // 无记录时，先插入一条待验证记录
      if (!latest) {
        await insertRecord(userId, url, method);
      }

      // 请求中带了 PIN 参数 → 尝试验证
      if (pin) {
        const valid = await verifyPin(userId, pin);

        if (valid) {
          // PIN 正确 → 标记验证成功，设置 30 秒放行窗口
          await markSuccess(userId);
          verifiedMap.set(userId, {
            verifiedAt: Date.now(),
            count: 0,
          });
          return next();
        }

        // PIN 错误 → 累计错误次数，达到 3 次则锁定
        const count = (latest?.error_count || 0) + 1;

        if (count >= CONFIG.MAX_ERROR_COUNT) {
          await markLocked(userId, count);
          return respond(res, CODE.LOCKED, "已锁定");
        }

        await markFailed(userId, count);
        return respond(res, CODE.ERROR, "PIN 错误");
      }

      // 请求中无 PIN 参数 → 弹出 PIN 输入框
      return respond(res, CODE.NEED_VERIFY, "请输入 PIN");
    }

    // ── 分支 3：最近一次验证已通过（pin_status = 1）──
    if (latest.pin_status === 1) {

      // PIN 状态检查接口本身跳过验证，避免死循环
      if (url.includes("/security/pin/check")) return next();

      const now = Date.now();
      let session = verifiedMap.get(userId);

      // 内存中没有放行记录 → 设置记录并放行
      if (!session) {
        verifiedMap.set(userId, { verifiedAt: now, count: 1 });
        return next();
      }

      const diff = now - session.verifiedAt;

      // 🟢 30 秒放行窗口内：累计请求次数，超限则重新验证
      if (diff < 30 * 1000) {
        session.count++;

        // 30 秒内请求超过 60 次，强制重新验证
        if (session.count >= CONFIG.FREQ_LIMIT) {
          const [pending] = await db.execute(
            `SELECT id FROM security_verify_log
             WHERE user_id = ? AND pin_status = 0 AND action_type <> 'route_verify' LIMIT 1`,
            [userId]
          );
          if (pending.length === 0) {
            await insertRecord(userId, url, method);
          }
          verifiedMap.delete(userId);
          return respond(res, CODE.NEED_VERIFY, "请求过多，请重新验证");
        }

        return next();
      }

      // 🔴 超过 30 秒放行窗口：恢复风控检查
      const freq = checkFrequency(userId);                          // 全局频率检查
      const sensitive = SENSITIVE_METHODS.includes(method) && checkSensitive(userId);  // 敏感操作频率检查

      if (freq || sensitive) {
        // 频率超限，插入待验证记录，弹出 PIN 验证
        const [pending] = await db.execute(
          `SELECT id FROM security_verify_log
           WHERE user_id = ? AND pin_status = 0 AND action_type <> 'route_verify' LIMIT 1`,
          [userId]
        );
        if (pending.length === 0) {
          await insertRecord(userId, url, method);
        }
        return respond(res, CODE.NEED_VERIFY, "触发安全验证");
      }

      return next();
    }

    // 兜底放行（理论上不会走到这里）
    next();
  } catch (err) {
    console.error(err);
    // 出错时放行，避免阻塞正常业务
    next();
  }
};

module.exports = { pinSecurityGuard, CODE, CONFIG };
