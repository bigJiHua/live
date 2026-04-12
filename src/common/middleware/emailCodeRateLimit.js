const db = require("../config/db");

/**
 * 邮箱验证码发送频率限制中间件
 * 作用：
 * 1. 验证码有效期内重复请求 → 拦截
 * 2. 今日发送次数超过限制 → 拦截
 */
const emailCodeRateLimit = (req, res, next) => {
  (async () => {
    try {
      const { email, type } = req.body.data; // type: "email" 或 "pwd"
      const userId = req.body.userId || req.userId; // 从请求中获取用户ID

      // 没有邮箱或用户ID参数，放行让后续校验处理
      if (!email || !userId) {
        return next();
      }

      // type: "email" → fingerprint = "email_verify_email"
      // type: "pwd" → fingerprint = "email_verify_pwd"
      const fingerprint = `email_verify_${type}`;
      const scene = email; // scene 存储用户邮箱
      const aes_key = userId; // aes_key 存储用户ID，用于身份识别
      const now = Date.now();
      // 计算今天0点的时间戳
      const todayStart = new Date();
      todayStart.setHours(0, 0, 0, 0);
      const todayStartTimestamp = todayStart.getTime();
      // 查询该用途该邮箱该用户的验证码记录（通过三个字段索引）
      const sql = `
        SELECT * FROM device_crypto
        WHERE fingerprint = ? AND scene = ? AND aes_key = ?
      `;
      const [rows] = await db.execute(sql, [fingerprint, scene, aes_key]);
      if (rows && rows.length > 0) {
        const record = rows[0];
        const recordDate = new Date(record.created_at);
        const todayDate = new Date(todayStartTimestamp);

        // 检查记录的创建日期是否是今天
        const isToday =
          recordDate.getFullYear() === todayDate.getFullYear() &&
          recordDate.getMonth() === todayDate.getMonth() &&
          recordDate.getDate() === todayDate.getDate();
        if (isToday) {
          // 今天创建的记录，检查发送次数
          const todayCount = record.captcha_attempts || 0;
          console.log(
            `[发送验证码统计] 用户ID: ${userId}, 邮箱: ${email}, 类型: ${type}, 今天已发送: ${todayCount} 次`
          );
          // 检查是否超过5次
          if (todayCount >= 5) {
            console.log(
              `[发送验证码拒绝] 用户ID: ${userId}, 邮箱: ${email}, 类型: ${type}, 今天已发送 ${todayCount} 次，超过限制`
            );
            return res.say("今日发送次数已达上限，请明天再试", 429);
          }
          // 检查验证码是否还在有效期内
          if (
            now < record.captcha_expires_at &&
            Number(record.captcha_verified) === 0
          ) {
            const remainingSeconds = Math.floor(
              (record.captcha_expires_at - now) / 1000
            );
            console.log(
              `[验证码频率拦截] 用户ID: ${userId}, 邮箱: ${email}, 类型: ${type}, 剩余时间: ${remainingSeconds}秒`
            );
            return res.say(`【验证码】已发送，请检查你的邮箱`, 429);
          }
        }
      }
      // 验证码已过期或已校验或不存在，放行到控制器进行写入
      next();
    } catch (error) {
      console.error("验证码频率限制中间件错误:", error);
      next(); // 出错也放行，避免阻塞正常请求
    }
  })();
};

module.exports = emailCodeRateLimit;
