const jwt = require("jsonwebtoken");
const db = require("../../common/config/db");
const bcrypt = require("bcryptjs");

const CONFIG = {
  MAX_ERROR_COUNT: 3,
  LOCK_MULTIPLIER: 30,
  FREQ_WINDOW: 60,
  FREQ_LIMIT: 60,
  SENSITIVE_WINDOW: 60,
  SENSITIVE_LIMIT: 10,
};

const CODE = {
  NEED_VERIFY: 8303,
  SUCCESS: 8301,
  ERROR: 8302,
  LOCKED: 8304,
};

const SENSITIVE_METHODS = ["POST", "PUT", "PATCH", "DELETE"];

function respond(res, code, message, data = {}) {
  return res.status(200).json({ code, status: 401, message, data });
}

// ================= 内存滑动窗口 =================

const freqMap = new Map();
const sensitiveMap = new Map();

// 🔥 验证放任窗口
const verifiedMap = new Map();
/**
 * userId -> {
 *   verifiedAt,
 *   count
 * }
 */

function slidingWindowCheck(map, key, windowSec, limit) {
  const now = Date.now();
  const windowMs = windowSec * 1000;

  let arr = map.get(key) || [];

  arr = arr.filter((t) => now - t < windowMs);
  arr.push(now);

  map.set(key, arr);

  return arr.length >= limit;
}

function checkFrequency(userId) {
  return slidingWindowCheck(freqMap, userId, CONFIG.FREQ_WINDOW, CONFIG.FREQ_LIMIT);
}

function checkSensitive(userId) {
  return slidingWindowCheck(
    sensitiveMap,
    userId,
    CONFIG.SENSITIVE_WINDOW,
    CONFIG.SENSITIVE_LIMIT
  );
}

// ================= DB =================

async function getUserPin(userId) {
  const [rows] = await db.execute(
    `SELECT pin_code FROM user_info WHERE id = ? LIMIT 1`,
    [userId]
  );
  return rows[0]?.pin_code || null;
}

async function getLatestRecord(userId) {
  const [rows] = await db.execute(
    `SELECT * FROM security_verify_log WHERE user_id = ? ORDER BY id DESC LIMIT 1`,
    [userId]
  );
  return rows[0] || null;
}

async function insertRecord(userId, url, action) {
  await db.execute(
    `INSERT INTO security_verify_log 
     (user_id, request_url, action_type, pin_status, error_count, remark, create_time)
     VALUES (?, ?, ?, 0, 0, ?, NOW())`,
    [userId, url, action || "verify", "待验证"]
  );
}

async function markSuccess(userId) {
  await db.execute(
    `UPDATE security_verify_log 
     SET pin_status = 1, remark = '验证成功'
     WHERE user_id = ? AND pin_status IN (0,2)
     ORDER BY id DESC LIMIT 1`,
    [userId]
  );
}

async function markFailed(userId, count) {
  await db.execute(
    `UPDATE security_verify_log 
     SET pin_status = 2, error_count = ?, remark = ?
     WHERE user_id = ? AND pin_status IN (0,2)
     ORDER BY id DESC LIMIT 1`,
    [count, `验证失败第${count}次`, userId]
  );
}

async function markLocked(userId, count) {
  await db.execute(
    `UPDATE security_verify_log 
     SET pin_status = 3, error_count = ?, remark = ?
     WHERE user_id = ? AND pin_status IN (0,2)
     ORDER BY id DESC LIMIT 1`,
    [count, `锁定${count * CONFIG.LOCK_MULTIPLIER}分钟`, userId]
  );
}

async function checkLock(record) {
  if (!record || record.pin_status !== 3) return { locked: false };

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

async function verifyPin(userId, pin) {
  const hash = await getUserPin(userId);
  if (!hash) return false;
  return bcrypt.compare(pin, hash);
}

// ================= 中间件 =================

const pinSecurityGuard = async (req, res, next) => {
  try {
    const url = req.originalUrl || req.path;
    const method = req.method;

    if (url.includes("/handshake")) return next();

    let userId = null;
    const authHeader = req.headers.authorization;

    if (authHeader?.startsWith("Bearer ")) {
      try {
        const token = authHeader.substring(7);
        const decoded = jwt.verify(token, process.env.JWT_SECRET);
        userId = decoded.userId;
      } catch {}
    }

    if (!userId) return next();

    let latest = await getLatestRecord(userId);

    // 🔒 锁定
    if (latest?.pin_status === 3) {
      const lock = await checkLock(latest);
      if (lock.locked) {
        return respond(res, CODE.LOCKED, `锁定中 ${lock.remainMinutes} 分钟`);
      }
    }

    const pin = req.body?.data?.pin;
    const userPin = await getUserPin(userId);
    if (!userPin) return next();

    // ================= 验证流程 =================
    if (!latest || latest.pin_status === 0 || latest.pin_status === 2) {
      if (!latest) {
        await insertRecord(userId, url, method);
      }

      if (pin) {
        const valid = await verifyPin(userId, pin);

        if (valid) {
          await markSuccess(userId);

          // 🔥 开启30秒放任期
          verifiedMap.set(userId, {
            verifiedAt: Date.now(),
            count: 0,
          });

          return next();
        }

        const count = (latest?.error_count || 0) + 1;

        if (count >= CONFIG.MAX_ERROR_COUNT) {
          await markLocked(userId, count);
          return respond(res, CODE.LOCKED, "已锁定");
        }

        await markFailed(userId, count);
        return respond(res, CODE.ERROR, "PIN 错误");
      }

      return respond(res, CODE.NEED_VERIFY, "请输入 PIN");
    }

    // ================= 已验证 =================
    if (latest.pin_status === 1) {

      if (url.includes("/security/pin/check")) return next();

      const now = Date.now();
      let session = verifiedMap.get(userId);

      if (!session) {
        verifiedMap.set(userId, { verifiedAt: now, count: 1 });
        return next();
      }

      const diff = now - session.verifiedAt;

      // 🟢 30秒放任期
      if (diff < 30 * 1000) {

        session.count++;

        if (session.count >= CONFIG.FREQ_LIMIT) {

          const [pending] = await db.execute(
            `SELECT id FROM security_verify_log 
             WHERE user_id = ? AND pin_status = 0 LIMIT 1`,
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

      // 🔴 超过30秒 → 恢复风控
      const freq = checkFrequency(userId);
      const sensitive =
        SENSITIVE_METHODS.includes(method) && checkSensitive(userId);

      if (freq || sensitive) {

        const [pending] = await db.execute(
          `SELECT id FROM security_verify_log 
           WHERE user_id = ? AND pin_status = 0 LIMIT 1`,
          [userId]
        );

        if (pending.length === 0) {
          await insertRecord(userId, url, method);
        }

        return respond(res, CODE.NEED_VERIFY, "触发安全验证");
      }

      return next();
    }

    next();
  } catch (err) {
    console.error(err);
    next();
  }
};

module.exports = { pinSecurityGuard, CODE, CONFIG };