const jwt = require("jsonwebtoken");
const db = require("../config/db");

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

      // 验证用户是否存在 + 密码变更吊销检查
      let user;
      try {
        const [users] = await db.execute(
          "SELECT id, password_changed_at FROM user_info WHERE id = ? AND is_deleted = 0",
          [req.userId]
        );
        user = users[0];
      } catch {
        // password_changed_at 字段可能尚未迁移，回退到基础查询
        const [users] = await db.execute(
          "SELECT id FROM user_info WHERE id = ? AND is_deleted = 0",
          [req.userId]
        );
        user = users[0];
      }

      if (!user) {
        return res.json({ status: 401, message: "用户不存在" });
      }

      // 密码变更吊销：token.iat 早于密码变更时间 → 令牌已失效
      // 字段不存在时 password_changed_at 为 undefined，安全跳过
      const pwdChangedAt = user?.password_changed_at;
      if (pwdChangedAt && decoded.iat) {
        const iatMs = decoded.iat * 1000; // iat 是秒，转为毫秒
        if (iatMs < pwdChangedAt) {
          return res.json({ status: 401, message: "密码已变更，请重新登录" });
        }
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