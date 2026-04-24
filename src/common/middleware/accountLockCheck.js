const User = require("../../modules/auth/model/user");

const requestTracker = new Map();

const RATE_LIMIT = {
  maxRequests: 10,
  windowMs: 60 * 1000,
  lockoutMs: 60 * 1000,
};

async function accountLockCheck(req, res, next) {
  try {
    const { nameOrEmail } = req.body.data || {};
    if (!nameOrEmail) {
      return res.say("非法未知参数请求！", 401);
    }

    const clientIp = req.ip || req.headers["x-forwarded-for"] || "unknown";
    const trackerKey = `${clientIp}:${nameOrEmail}`;

    const now = Date.now();
    let tracker = requestTracker.get(trackerKey);

    if (!tracker || now > tracker.resetTime) {
      tracker = { count: 0, resetTime: now + RATE_LIMIT.windowMs };
    }

    if (tracker.lockedUntil && now < tracker.lockedUntil) {
      const remainingSeconds = Math.ceil((tracker.lockedUntil - now) / 1000);
      return res.status(429).json({
        status: 429,
        message: `请求过于频繁，请稍后重试`,
        retryAfter: remainingSeconds,
      });
    }

    tracker.count++;

    if (tracker.count > RATE_LIMIT.maxRequests) {
      tracker.lockedUntil = now + RATE_LIMIT.lockoutMs;
      requestTracker.set(trackerKey, tracker);
      return res.status(429).json({
        status: 429,
        message: `请求过于频繁，请在稍后重试`,
        retryAfter: RATE_LIMIT.lockoutMs / 1000,
      });
    }

    requestTracker.set(trackerKey, tracker);

    if (requestTracker.size > 1000) {
      cleanupTracker();
    }

    const user =
      (await User.findByEmail(nameOrEmail)) ||
      (await User.findByUsername(nameOrEmail));

    if (!user) {
      return res.say("非法未知参数请求！", 401);
    }

    if (Number(user.status) === 0) {
      const lockTime = new Date(user.update_time).getTime();
      const unlockTime = lockTime + 12 * 60 * 60 * 1000;
      const currentTime = Date.now();

      if (currentTime < unlockTime) {
        const remainingHours = Math.ceil((unlockTime - currentTime) / (60 * 60 * 1000));
        return res.status(423).json({
          status: 423,
          message: `账户已被锁定，请在 ${remainingHours} 小时后重试`,
          locked: true,
        });
      } else {
        await User.setLockStatus(user.id, false);
        console.log(`[账户解锁] 用户 ${user.id} 锁定超过12小时，已自动解锁`);
      }
    }

    req.checkedUser = user;
    next();
  } catch (error) {
    console.error("账户锁定检查异常:", error);
    next();
  }
}

function cleanupTracker() {
  const now = Date.now();
  for (const [key, value] of requestTracker.entries()) {
    if (now > value.resetTime && (!value.lockedUntil || now > value.lockedUntil)) {
      requestTracker.delete(key);
    }
  }
}

module.exports = accountLockCheck;
