const jwt = require("jsonwebtoken");
const db = require("../common/config/db");

/**
 * JWT 认证守卫
 * 验证请求头中的 Bearer Token
 */
const authGuard = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;

    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return res.json({ status: 401, message: "未提供认证令牌" });
    }

    const token = authHeader.split(" ")[1];

    try {
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      req.userId = decoded.userId;

      // 验证用户是否存在
      const [users] = await db.execute(
        "SELECT id FROM user_base WHERE id = ? AND is_deleted = 0",
        [req.userId]
      );

      if (users.length === 0) {
        return res.json({ status: 401, message: "用户不存在" });
      }

      next();
    } catch (err) {
      if (err.name === "TokenExpiredError") {
        return res.json({ status: 401, message: "令牌已过期" });
      }
      return res.json({ status: 401, message: "无效的认证令牌" });
    }
  } catch (error) {
    console.error("Auth Guard Error:", error);
    return res.json({ status: 500, message: "认证检查失败" });
  }
};

module.exports = authGuard;
