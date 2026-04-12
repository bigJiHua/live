const User = require("../../modules/auth/model/user");

// 临时变量：存储请求频率控制 (生产环境建议使用 Redis)
// 格式: { "ip:nameOrEmail": { count: 次数, resetTime: 重置时间 } }
const requestTracker = new Map();

// 频率限制配置
const RATE_LIMIT = {
  maxRequests: 10,      // 时间窗口内最大请求次数
  windowMs: 60 * 1000,  // 时间窗口：60秒
  lockoutMs: 60 * 1000, // 触发限制后锁定时间：60秒
};

/**
 * 账户锁定检查中间件
 * 在登录验证之前检查账户是否被锁定
 * 如果锁定超过12小时则自动解锁，否则拒绝登录
 * 内置暴力请求防护
 */
async function accountLockCheck(req, res, next) {
  try {
    // 从解密后的请求体中获取用户名或邮箱
    const { nameOrEmail } = req.body.data || {};
    if (!nameOrEmail) {
      // 没有用户名/邮箱，跳过检查，让后续验证处理
      return res.say("非法未知参数请求！", 401);
    }
    // 获取客户端 IP
    const clientIp = req.ip || req.headers["x-forwarded-for"] || "unknown";
    const trackerKey = `${clientIp}:${nameOrEmail}`;

    // === 暴力请求防护 ===
    const now = Date.now();
    let tracker = requestTracker.get(trackerKey);
    console.log(tracker);
    

    // 初始化或重置追踪器
    if (!tracker || now > tracker.resetTime) {
      tracker = { count: 0, resetTime: now + RATE_LIMIT.windowMs };
    }

    // 检查是否在锁定状态
    if (tracker.lockedUntil && now < tracker.lockedUntil) {
      const remainingSeconds = Math.ceil((tracker.lockedUntil - now) / 1000);
      console.log(`请求过于频繁，请在 ${remainingSeconds} 秒后重试`);
      return res.status(429).json({
        status: 429,
        message: `请求过于频繁，请稍后重试`,
        retryAfter: remainingSeconds,
      });
    }

    // 增加请求计数
    tracker.count++;

    // 超过限制，锁定
    if (tracker.count > RATE_LIMIT.maxRequests) {
      tracker.lockedUntil = now + RATE_LIMIT.lockoutMs;
      requestTracker.set(trackerKey, tracker);
      console.log(`[频率限制] IP: ${clientIp}, 用户: ${nameOrEmail}, 触发限制`);
      console.log(`请求过于频繁，请在 ${RATE_LIMIT.lockoutMs / 1000} 秒后重试`);
      return res.status(429).json({
        status: 429,
        message: `请求过于频繁，请在稍后重试`,
        retryAfter: RATE_LIMIT.lockoutMs / 1000,
      });
    }

    // 更新追踪器
    requestTracker.set(trackerKey, tracker);

    // 每分钟清理一次过期数据（避免内存泄漏）
    if (requestTracker.size > 1000) {
      cleanupTracker();
    }

    // === 原有账户锁定检查逻辑 ===
    // 查找用户
    const user =
      (await User.findByEmail(nameOrEmail)) ||
      (await User.findByUsername(nameOrEmail));

    if (!user) {
      // 用户不存在，跳过检查，让后续验证处理
      return res.say("非法未知参数请求！", 401);
    }

    // 检查账户是否被锁定 (status = 0 表示锁定)
    if (Number(user.status) === 0) {
      const lockTime = new Date(user.update_time).getTime();
      const unlockTime = lockTime + 12 * 60 * 60 * 1000; // 12小时后解锁
      const currentTime = Date.now();

      if (currentTime < unlockTime) {
        // 未超过12小时，拒绝登录
        const remainingHours = Math.ceil((unlockTime - currentTime) / (60 * 60 * 1000));
        return res.status(423).json({
          status: 423,
          message: `账户已被锁定，请在 ${remainingHours} 小时后重试`,
          locked: true,
        });
      } else {
        // 超过12小时，自动解锁账户
        await User.setLockStatus(user.id, false);
        console.log(`[账户解锁] 用户 ${user.id} 锁定超过12小时，已自动解锁`);
      }
    }

    // 将用户信息挂载到请求对象，供后续控制器使用
    req.checkedUser = user;
    next();
  } catch (error) {
    console.error("账户锁定检查异常:", error);
    // 检查失败时跳过，继续后续流程
    next();
  }
}

/**
 * 清理过期的追踪数据
 */
function cleanupTracker() {
  const now = Date.now();
  for (const [key, value] of requestTracker.entries()) {
    if (now > value.resetTime && (!value.lockedUntil || now > value.lockedUntil)) {
      requestTracker.delete(key);
    }
  }
}

module.exports = accountLockCheck;
