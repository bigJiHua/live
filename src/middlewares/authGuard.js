const jwt = require('jsonwebtoken');

/**
 * 认证中间件 - 验证 JWT Token
 */
const authGuard = (req, res, next) => {
  try {
    // 从请求头获取 Token
    const authHeader = req.headers.authorization;

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ message: '未提供认证 Token' });
    }

    const token = authHeader.substring(7); // 移除 'Bearer ' 前缀

    // 验证 Token
    const decoded = jwt.verify(token, process.env.JWT_SECRET);

    // 将用户信息添加到请求对象
    req.userId = decoded.userId;
    req.userEmail = decoded.email;

    next();
  } catch (error) {
    if (error.name === 'TokenExpiredError') {
      return res.status(401).json({ message: 'Token 已过期' });
    }
    if (error.name === 'JsonWebTokenError') {
      return res.status(401).json({ message: 'Token 无效' });
    }

    console.error('认证中间件错误:', error);
    return res.status(500).json({ message: '认证失败', error: error.message });
  }
};

module.exports = authGuard;
