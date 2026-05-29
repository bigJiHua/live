const crypto = require("crypto");
const db = require("../../common/config/db");

const ACTION_TYPE = "route_verify";
const HEADER_TOKEN = "x-route-verify-token";
const TOKEN_PREFIX = "route-token:";

const CODE = {
  NEED_VERIFY: 8303,
  ERROR: 8302,
  LOCKED: 8304,
};

const CONFIG = {
  EXPIRE_MINUTES: 5,
  MAX_ERROR_COUNT: 3,
  LOCK_MULTIPLIER: 30,
};

function respond(res, code, message, data = {}) {
  return res.status(200).json({ code, status: 401, message, data });
}

function normalizeUrl(req) {
  return req.originalUrl || req.baseUrl + req.path || req.path;
}

function normalizeMethod(req) {
  return (req.method || "").toUpperCase();
}

function tokenRemark(hash, method, bodyHash) {
  return `${TOKEN_PREFIX}${method}:${bodyHash}:${hash}`;
}

function hashToken(token) {
  return crypto.createHash("sha256").update(String(token)).digest("hex");
}

function createToken() {
  return crypto
    .randomBytes(32)
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function stableStringify(value) {
  if (value === null || typeof value !== "object") {
    return JSON.stringify(value);
  }

  if (Array.isArray(value)) {
    return `[${value.map(stableStringify).join(",")}]`;
  }

  return `{${Object.keys(value)
    .sort()
    .map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`)
    .join(",")}}`;
}

function requestBodyHash(req) {
  return hashToken(stableStringify(req.body?.data ?? null));
}

function bodyRemark(hash) {
  return `body:${hash}`;
}

function challengeRemark(method, bodyHash, statusText) {
  return `method:${method};${bodyRemark(bodyHash)};${statusText}`;
}

async function getUserPin(userId) {
  const [rows] = await db.execute(
    `SELECT pin_code FROM user_info WHERE id = ? LIMIT 1`,
    [userId]
  );
  return rows[0]?.pin_code || null;
}

async function getActiveChallenge(userId, requestUrl, method, bodyHash) {
  const [rows] = await db.execute(
    `SELECT *
     FROM security_verify_log
     WHERE user_id = ?
       AND request_url = ?
       AND action_type = ?
       AND pin_status IN (0, 2)
       AND verify_expire_time > NOW()
       AND remark LIKE ?
       AND remark LIKE ?
     ORDER BY id DESC
     LIMIT 1`,
    [userId, requestUrl, ACTION_TYPE, `method:${method};%`, `%${bodyRemark(bodyHash)}%`]
  );

  return rows[0] || null;
}

async function createChallenge(userId, requestUrl, method, bodyHash) {
  const [result] = await db.execute(
    `INSERT INTO security_verify_log
     (user_id, request_url, action_type, pin_status, error_count, verify_expire_time, remark, create_time)
     VALUES (?, ?, ?, 0, 0, DATE_ADD(NOW(), INTERVAL ? MINUTE), ?, NOW())`,
    [
      userId,
      requestUrl,
      ACTION_TYPE,
      CONFIG.EXPIRE_MINUTES,
      challengeRemark(method, bodyHash, "待验证"),
    ]
  );

  return {
    id: result.insertId,
    user_id: userId,
    request_url: requestUrl,
    action_type: ACTION_TYPE,
    pin_status: 0,
    error_count: 0,
    remark: challengeRemark(method, bodyHash, "待验证"),
  };
}

async function getOrCreateChallenge(userId, requestUrl, method, bodyHash) {
  const existing = await getActiveChallenge(userId, requestUrl, method, bodyHash);
  if (existing) return existing;
  return createChallenge(userId, requestUrl, method, bodyHash);
}

async function consumeToken(userId, requestUrl, method, bodyHash, token) {
  if (!token) return false;

  const hash = hashToken(token);
  const [result] = await db.execute(
    `UPDATE security_verify_log
     SET pin_status = 1, remark = '已验证并已使用'
     WHERE user_id = ?
       AND request_url = ?
       AND action_type = ?
       AND pin_status = 1
       AND verify_expire_time > NOW()
       AND remark = ?
     ORDER BY id DESC
     LIMIT 1`,
    [userId, requestUrl, ACTION_TYPE, tokenRemark(hash, method, bodyHash)]
  );

  return result.affectedRows > 0;
}

function getChallengePayload(record, requestUrl, method) {
  return {
    action_type: ACTION_TYPE,
    challengeId: record.id,
    requestUrl,
    method,
  };
}

const pinLockGuard = async (req, res, next) => {
  try {
    // ── 自动注册：从 req 提取路由信息，首次调用时注册给 pinSecurityGuard ──
    const routePattern = req.baseUrl + req.route.path;
    if (!_registeredSet.has(`${req.method}:${routePattern}`)) {
      _registeredSet.add(`${req.method}:${routePattern}`);
      const escaped = routePattern.replace(/:[^/]+/g, "[^/]+");
      _protectedRoutes.push({ method: req.method, pattern: new RegExp(`^${escaped}$`) });
    }

    const userId = req.userId;
    if (!userId) return next();

    const userPin = await getUserPin(userId);
    if (!userPin) return next();

    const requestUrl = normalizeUrl(req);
    const method = normalizeMethod(req);
    const bodyHash = requestBodyHash(req);
    const routeToken = req.headers[HEADER_TOKEN];

    if (routeToken) {
      const ok = await consumeToken(userId, requestUrl, method, bodyHash, routeToken);
      if (ok) return next();

      const record = await getOrCreateChallenge(userId, requestUrl, method, bodyHash);
      return respond(res, CODE.NEED_VERIFY, "风险操作验证已失效，请重新输入 PIN", {
        ...getChallengePayload(record, requestUrl, method),
        reason: "invalid_token",
      });
    }

    const record = await getOrCreateChallenge(userId, requestUrl, method, bodyHash);
    return respond(res, CODE.NEED_VERIFY, "风险操作需要验证 PIN", getChallengePayload(record, requestUrl, method));
  } catch (error) {
    console.error("PIN Lock Guard Error:", error);
    return res.status(500).json({
      status: 500,
      message: "风险操作验证失败",
    });
  }
};

// ── 路由自动注册表（pinSecurityGuard 据此判断哪些路由归 pinLockGuard 管）──
const _protectedRoutes = [];
const _registeredSet = new Set();

/**
 * 检查当前请求是否匹配已注册的受保护路由（由 pinSecurityGuard 调用）
 */
pinLockGuard.matchProtected = function (method, url) {
  const pathname = (url || "").split("?")[0].replace(/\/+$/, "") || "/";
  return _protectedRoutes.some((r) => r.method === method && r.pattern.test(pathname));
};

// 保留静态属性
pinLockGuard.ACTION_TYPE = ACTION_TYPE;
pinLockGuard.HEADER_TOKEN = HEADER_TOKEN;
pinLockGuard.TOKEN_PREFIX = TOKEN_PREFIX;
pinLockGuard.CODE = CODE;
pinLockGuard.CONFIG = CONFIG;
pinLockGuard.hashToken = hashToken;
pinLockGuard.createToken = createToken;
pinLockGuard.tokenRemark = tokenRemark;
pinLockGuard.requestBodyHash = requestBodyHash;
pinLockGuard.bodyRemark = bodyRemark;
pinLockGuard.challengeRemark = challengeRemark;

module.exports = pinLockGuard;
