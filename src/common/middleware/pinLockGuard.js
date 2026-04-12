const User = require('../../modules/auth/model/user');

/**
 * PIN 锁定中间件 - 检查会话是否需要 PIN 验证
 * 返回 423 状态码表示需要重新验证 PIN 码
 */
const pinLockGuard = async (req, res, next) => {
  try {
    // 检查会话是否已通过 PIN 验证
    if (req.session && req.session.pinVerified) {
      return next();
    }

    // 获取用户信息
    const user = await User.findById(req.userId);

    if (!user) {
      return res.status(404).json({ message: '用户不存在' });
    }

    // 如果用户没有设置 PIN 码，则跳过验证
    if (!user.pinHash) {
      return next();
    }

    // 返回 423 状态码，表示需要 PIN 验证
    return res.status(423).json({
      message: '需要验证 PIN 码才能访问此功能',
      code: 'PIN_REQUIRED',
      requiresPin: true,
    });
  } catch (error) {
    console.error('PIN 锁定中间件错误:', error);
    return res.status(500).json({
      message: 'PIN 验证检查失败',
      error: error.message,
    });
  }
};

module.exports = pinLockGuard;
