const db = require("../config/db");

/**
 * 邮箱验证码发送频率限制中间件
 */
const emailCodeRateLimit = (req, res, next) => {
  (async () => {
    try {
      const { email, type } = req.body.data || req.body;
      const userId = req.body.userId || req.userId;

      if (!email || !userId) {
        return next();
      }

      const fingerprint = `email_verify_${type}`;
      const scene = email;
      const aes_key = userId;
      const now = Date.now();

      const todayStart = new Date();
      todayStart.setHours(0, 0, 0, 0);
      const todayStartTimestamp = todayStart.getTime();

      const sql = `
        SELECT * FROM device_crypto
        WHERE fingerprint = ? AND scene = ? AND aes_key = ?
      `;
      const [rows] = await db.execute(sql, [fingerprint, scene, aes_key]);

      if (rows && rows.length > 0) {
        const record = rows[0];
        const recordDate = new Date(record.created_at);
        const todayDate = new Date(todayStartTimestamp);

        const isToday =
          recordDate.getFullYear() === todayDate.getFullYear() &&
          recordDate.getMonth() === todayDate.getMonth() &&
          recordDate.getDate() === todayDate.getDate();

        if (isToday) {
          const todayCount = record.captcha_attempts || 0;
          if (todayCount >= 5) {
            return res.say("今日发送次数已达上限，请明天再试", 429);
          }
          if (
            now < record.captcha_expires_at &&
            Number(record.captcha_verified) === 0
          ) {
            const remainingSeconds = Math.floor(
              (record.captcha_expires_at - now) / 1000
            );
            return res.say(`【验证码】已发送，请检查你的邮箱（${remainingSeconds}秒后可重发）`, 429);
          }
        }
      }
      next();
    } catch (error) {
      console.error("验证码频率限制中间件错误:", error);
      next();
    }
  })();
};

module.exports = emailCodeRateLimit;
