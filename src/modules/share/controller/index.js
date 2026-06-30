const Moment = require("../../moment/model");
const User = require("../../auth/model/user");
const { verifyShareToken } = require("../../../common/utils/shareToken");

// ── 密码模式防暴力破解（内存滑动窗口） ──
const pwTracker = new Map(); // key: "ip:diaryId" → { count, resetAt, lockedUntil }

const PW_LIMIT = {
  maxAttempts: 5,          // 窗口内最大尝试次数
  windowMs: 60 * 1000,     // 滑动窗口 60 秒
  lockoutMs: 120 * 1000,   // 超限后锁定 120 秒
};

/** 清理过期追踪条目 */
const cleanupTracker = () => {
  const now = Date.now();
  for (const [key, entry] of pwTracker) {
    if (entry.lockedUntil && now >= entry.lockedUntil) {
      pwTracker.delete(key);
    } else if (!entry.lockedUntil && now >= entry.resetAt + PW_LIMIT.windowMs) {
      pwTracker.delete(key);
    }
  }
};

const safeJsonParse = (val, fallback = null) => {
  if (!val) return fallback;
  try { return JSON.parse(val); } catch { return fallback; }
};

/** 解析 moment 的 visible_type 为对象 */
const parseVis = (moment) => {
  if (!moment) return null;
  try {
    return typeof moment.visible_type === "string"
      ? JSON.parse(moment.visible_type)
      : moment.visible_type;
  } catch {
    return null;
  }
};

/** 拼装返回数据 */
const buildResponse = async (moment) => {
  let authorName = "ta";
  try {
    const user = await User.findById(moment.user_id);
    if (user) authorName = user.username || user.name || "ta";
  } catch { /* fallback */ }

  return {
    status: 200,
    data: {
      id: moment.id,
      content: moment.content,
      img_url: safeJsonParse(moment.img_url),
      mood: safeJsonParse(moment.mood),
      location: safeJsonParse(moment.location),
      create_time: moment.create_time,
      author: { name: authorName },
    },
  };
};

/**
 * GET /share/:token — Token 模式公开查看
 */
const viewShare = async (req, res) => {
  try {
    const { token } = req.params;

    let payload;
    try {
      payload = verifyShareToken(token);
    } catch (err) {
      console.error("[Share] Token verify error:", err.message);
      return res.json({ status: 400, message: "链接无效或已损坏" });
    }

    if (payload.exp < Date.now()) {
      return res.json({ status: 410, message: "分享链接已过期，请联系分享者重新获取" });
    }

    const moment = await Moment.findById(payload.did);
    if (!moment || moment.is_deleted) {
      return res.json({ status: 404, message: "内容不存在或已删除" });
    }

    const vis = parseVis(moment);
    if (!vis || vis.vt !== 1) {
      return res.json({ status: 403, message: "分享已关闭" });
    }
    if (vis.vs !== payload.vs) {
      return res.json({ status: 403, message: "链接已失效，请获取最新链接" });
    }
    if (String(vis.pw) !== String(payload.pw)) {
      return res.json({ status: 403, message: "链接已失效，请获取最新链接" });
    }

    const result = await buildResponse(moment);
    result.data.expire_at = new Date(payload.exp).toISOString();
    res.json(result);
  } catch (error) {
    console.error("[Share] View error:", error);
    res.json({ status: 500, message: "查看分享失败" });
  }
};

/**
 * POST /share/password — 密码模式公开查看
 * body: { id, pw }
 *
 * 防爆规则：IP+日记ID 每 60 秒最多 5 次尝试，超限锁定 120 秒
 */
const verifyPassword = async (req, res) => {
  try {
    const { id, pw } = req.body || {};

    // 参数校验
    if (!id || pw === undefined || pw === null || pw === "") {
      return res.json({ status: 400, message: "参数不完整" });
    }

    // pw 必须是 6 位数字
    if (!/^\d{6}$/.test(String(pw))) {
      return res.json({ status: 400, message: "请输入 6 位数字密码" });
    }

    // ── 防爆：速率限制 ──
    const clientIp = req.ip || req.connection?.remoteAddress || "unknown";
    const trackerKey = `${clientIp}:${id}`;
    const now = Date.now();

    let entry = pwTracker.get(trackerKey);

    if (!entry || now >= entry.resetAt + PW_LIMIT.windowMs) {
      // 新窗口：重置计数
      entry = { count: 0, resetAt: now, lockedUntil: null };
      pwTracker.set(trackerKey, entry);
    }

    // 是否在锁定中
    if (entry.lockedUntil && now < entry.lockedUntil) {
      const remainSec = Math.ceil((entry.lockedUntil - now) / 1000);
      return res.json({
        status: 429,
        message: `尝试次数过多，请 ${remainSec} 秒后再试`,
        retryAfter: remainSec,
      });
    }

    // ── 查询日记 ──
    const moment = await Moment.findById(id);
    if (!moment || moment.is_deleted) {
      entry.count++;
      if (entry.count >= PW_LIMIT.maxAttempts) {
        entry.lockedUntil = now + PW_LIMIT.lockoutMs;
      }
      return res.json({ status: 404, message: "内容不存在或已删除" });
    }

    const vis = parseVis(moment);
    if (!vis || vis.vt !== 1) {
      entry.count++;
      if (entry.count >= PW_LIMIT.maxAttempts) {
        entry.lockedUntil = now + PW_LIMIT.lockoutMs;
      }
      return res.json({ status: 403, message: "分享已关闭或链接无效" });
    }

    // 比对密码
    if (String(vis.pw) !== String(pw)) {
      entry.count++;
      if (entry.count >= PW_LIMIT.maxAttempts) {
        entry.lockedUntil = now + PW_LIMIT.lockoutMs;
        console.warn(`[Share] 密码爆破锁定: ${trackerKey}`);
        return res.json({
          status: 429,
          message: `密码错误次数过多，请 ${PW_LIMIT.lockoutMs / 1000} 秒后再试`,
          retryAfter: PW_LIMIT.lockoutMs / 1000,
        });
      }
      return res.json({
        status: 403,
        message: `密码错误，还剩 ${PW_LIMIT.maxAttempts - entry.count} 次机会`,
      });
    }

    // ✅ 验证通过 → 清除追踪记录
    pwTracker.delete(trackerKey);

    // 清理过期条目（Map 超过 500 时触发）
    if (pwTracker.size > 500) cleanupTracker();

    res.json(await buildResponse(moment));
  } catch (error) {
    console.error("[Share] Password verify error:", error);
    res.json({ status: 500, message: "验证失败" });
  }
};

module.exports = { viewShare, verifyPassword };
