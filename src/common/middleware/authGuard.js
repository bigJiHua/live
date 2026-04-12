const jwt = require("jsonwebtoken");

/**
 * 认证中间件 - 验证 JWT Token
 */
const authGuard = (req, res, next) => {
  try {
    // 从请求头获取 Token
    const authHeader = req.headers.authorization;

    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return res.say("请重新登录！", 401);
    }
    const token = authHeader.substring(7); // 移除 'Bearer ' 前缀
    // 验证 Token
    const decoded = jwt.verify(token, process.env.JWT_SECRET);

    // 将用户信息添加到请求对象
    req.userId = decoded.userId;
    req.userEmail = decoded.email;

    next();
  } catch (error) {
    console.log(error);
    
    console.error("认证中间件错误:", error);
    return res.say("非法闯入！ 401", 401);
  }
};

module.exports = authGuard;
