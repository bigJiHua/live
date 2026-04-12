const User = require("../common/models/User");

/**
 * PIN 锁定中间件 - 检查会话是否需要 PIN 验证
 */
const pinLockGuard = async (req, res, next) => {
  try {
    // 跳过登录相关接口
    if (req.path.includes("/auth/login") || req.path.includes("/auth/register")) {
      return next();
    }

    // 检查用户是否启用了 PIN 锁定
    const user = await User.findById(req.userId);
    if (!user) return next();

    // 如果没有设置 PIN，直接通过
    if (!user.pin_hash) {
      return next();
    }

    // 检查会话是否已验证 PIN
    if (req.session && req.session.pinVerified) {
      return next();
    }

    // 返回 423 表示需要重新验证 PIN 码
    return res.json({ status: 423, message: "请先验证 PIN 码" });
  } catch (error) {
    console.error("PIN Lock Guard Error:", error);
    // 出错时放行，避免阻塞正常业务
    next();
  }
};

module.exports = pinLockGuard;
