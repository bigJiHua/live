const UserPin = require("../../auth/model/pin");
const UserLog = require("../../auth/model/log");
const User = require("../../auth/model/user");
const db = require("../../../common/config/db");
const pinLockGuard = require("../../../common/middleware/pinLockGuard");

/**
 * 安全控制器 - PIN 码管理
 * 使用 UserPin 模型进行数据操作
 */
class SecurityController {
  /**
   * 0. 查看 PIN 状态
   * 检查用户是否已设置 PIN 码
   */
  async PinStatus(req, res) {
    try {
      const hasPin = await UserPin.hasPin(req.userId);
      if (!hasPin) {
        return res
          .status(400)
          .json({ status: 400, message: "请先设置 PIN 码", ismessage: true });
      }
      return res.say("状态正常！", 200);
    } catch (error) {
      console.error("检查 PIN 状态失败:", error);
      return res.say("检查失败", 500);
    }
  }

  /**
   * 1. 验证 PIN
   * @param {string} pin - 6位数字 PIN 码
   */
  async verifyPin(req, res) {
    try {
      const { pin } = req.body.data;

      // 校验 PIN 格式
      if (!pin || pin.length !== 6 || !/^\d+$/.test(pin)) {
        return res
          .status(400)
          .json({ status: 400, message: "PIN 码必须是6位纯数字" });
      }

      // 获取用户 PIN 信息
      const user = await UserPin.findById(req.userId);
      if (!user?.pin_code) {
        return res
          .status(400)
          .json({ status: 400, message: "请先设置 PIN 码" });
      }

      // 验证 PIN
      const isValid = await UserPin.verify(pin, user.pin_code);
      if (!isValid) {
        // 检查错误次数是否超过5次
        const errorCount = await UserLog.getPinErrorCount(req.userId);
        if (errorCount >= 5) {
          // 超过5次，锁定账户并强制退出登录
          await User.lockUser(req.userId);
          return res.status(401).json({
            status: 401,
            message: "连续输入错误超过5次，账户已被锁定",
            forceLogout: true,
            locked: true
          });
        }
        return res.status(400).json({
          status: 400,
          message: "PIN 码错误",
          errorCount: errorCount + 1,
          remainingAttempts: 5 - errorCount - 1
        });
      }

      return res.json({ status: 200, message: "验证成功", verified: true });
    } catch (error) {
      console.error("验证 PIN 失败:", error);
      return res.status(500).json({ status: 500, message: "验证失败" });
    }
  }

  /**
   * 风险路由 PIN 验证
   * 验证通过后生成短时一次性 token，供原始风险请求重发时携带。
   */
  async verifyRoutePin(req, res) {
    try {
      const { pin, challengeId, requestUrl, method } = req.body.data || {};
      const normalizedMethod = String(method || "").toUpperCase();

      if (!pin || pin.length !== 6 || !/^\d+$/.test(pin)) {
        return res.status(400).json({
          code: pinLockGuard.CODE.ERROR,
          status: 400,
          message: "PIN 码格式错误",
        });
      }

      if (!challengeId || !requestUrl || !normalizedMethod) {
        return res.status(400).json({
          status: 400,
          message: "风险验证参数不完整",
        });
      }

      const [rows] = await db.execute(
        `SELECT *
         FROM security_verify_log
         WHERE id = ?
           AND user_id = ?
           AND request_url = ?
           AND action_type = ?
           AND pin_status IN (0, 2)
           AND verify_expire_time > NOW()
         LIMIT 1`,
        [challengeId, req.userId, requestUrl, pinLockGuard.ACTION_TYPE]
      );

      const challenge = rows[0];
      if (!challenge || !String(challenge.remark || "").includes(`method:${normalizedMethod}`)) {
        return res.status(200).json({
          code: pinLockGuard.CODE.NEED_VERIFY,
          status: 200,
          message: "风险验证已过期，请重新操作",
          data: {
            action_type: pinLockGuard.ACTION_TYPE,
            expired: true,
          },
        });
      }

      const bodyHash = String(challenge.remark || "").match(/body:([a-f0-9]{64})/)?.[1];
      if (!bodyHash) {
        return res.status(200).json({
          code: pinLockGuard.CODE.NEED_VERIFY,
          status: 200,
          message: "风险验证已更新，请重新操作",
          data: {
            action_type: pinLockGuard.ACTION_TYPE,
            expired: true,
          },
        });
      }

      const user = await UserPin.findById(req.userId);
      if (!user?.pin_code) {
        return res.status(400).json({ status: 400, message: "请先设置 PIN 码" });
      }

      const isValid = await UserPin.verify(pin, user.pin_code);
      if (!isValid) {
        const errorCount = Number(challenge.error_count || 0) + 1;

        if (errorCount >= pinLockGuard.CONFIG.MAX_ERROR_COUNT) {
          await db.execute(
            `UPDATE security_verify_log
             SET pin_status = 2, error_count = ?, remark = ?
             WHERE id = ? AND user_id = ?`,
            [
              errorCount,
              `${challenge.remark.split(";验证失败第")[0]};触发系统锁定`,
              challenge.id,
              req.userId,
            ]
          );

          // 防止短时间内重复插入软锁记录
          const [existingLock] = await db.execute(
            `SELECT id FROM security_verify_log
             WHERE user_id = ? AND action_type = 'lock' AND pin_status = 0
             LIMIT 1`,
            [req.userId]
          );

          if (existingLock.length === 0) {
            await db.execute(
              `INSERT INTO security_verify_log
               (user_id, request_url, action_type, pin_status, error_count, remark, create_time)
               VALUES (?, ?, ?, 0, 0, ?, NOW())`,
              [
                req.userId,
                "/lock-system",
                "lock",
                "风险操作 PIN 错误次数过多，临时锁定",
              ]
            );
          }

          return res.status(200).json({
            code: pinLockGuard.CODE.ERROR,
            status: 200,
            message: "PIN 错误次数过多，操作受限，请验证系统 PIN",
            data: {
              action_type: pinLockGuard.ACTION_TYPE,
            },
          });
        }

        await db.execute(
          `UPDATE security_verify_log
           SET pin_status = 2, error_count = ?, remark = ?
           WHERE id = ? AND user_id = ?`,
          [
            errorCount,
            `${challenge.remark.split(";验证失败第")[0]};验证失败第${errorCount}次`,
            challenge.id,
            req.userId,
          ]
        );

        return res.status(200).json({
          code: pinLockGuard.CODE.ERROR,
          status: 200,
          message: "PIN 错误",
          data: {
            action_type: pinLockGuard.ACTION_TYPE,
            challengeId: challenge.id,
            requestUrl,
            method: normalizedMethod,
            remainingAttempts: pinLockGuard.CONFIG.MAX_ERROR_COUNT - errorCount,
          },
        });
      }

      const token = pinLockGuard.createToken();
      const tokenHash = pinLockGuard.hashToken(token);

      await db.execute(
        `UPDATE security_verify_log
         SET pin_status = 1,
             error_count = 0,
             verify_expire_time = DATE_ADD(NOW(), INTERVAL ? MINUTE),
             remark = ?
         WHERE id = ? AND user_id = ?`,
        [
          pinLockGuard.CONFIG.EXPIRE_MINUTES,
          pinLockGuard.tokenRemark(tokenHash, normalizedMethod, bodyHash),
          challenge.id,
          req.userId,
        ]
      );

      return res.status(200).json({
        code: 8301,
        status: 200,
        message: "验证成功",
        data: {
          action_type: pinLockGuard.ACTION_TYPE,
          token,
          headerName: pinLockGuard.HEADER_TOKEN,
          expiresIn: pinLockGuard.CONFIG.EXPIRE_MINUTES * 60,
        },
      });
    } catch (error) {
      console.error("风险路由 PIN 验证失败:", error);
      return res.status(500).json({ status: 500, message: "验证失败" });
    }
  }

  /**
   * 2. 设置 PIN
   * @param {string} pin - 6位数字 PIN 码
   */
  async setPin(req, res) {
    try {
      const { pin } = req.body.data;

      // 校验 PIN 格式
      if (!pin || pin.length !== 6 || !/^\d+$/.test(pin)) {
        return res.say("PIN码必须是6位纯数字", 400);
      }

      // 使用 UserPin.create 设置 PIN
      const result = await UserPin.create(req.userId, pin);

      if (result.status !== 200) {
        return res
          .status(result.status)
          .json({ status: result.status, message: result.message });
      }

      return res.say("设置成功", 200);
    } catch (error) {
      console.error("设置 PIN 失败:", error);
      return res.say("设置失败", 500);
    }
  }

  /**
   * 3. 修改 / 关闭 PIN 码
   * @param {string} oldPin - 原 PIN 码
   * @param {string} newPin - 新 PIN 码 / 000000 = 关闭PIN
   */
  async changePin(req, res) {
    const clientData = req.body;
    try {
      const { oldPin, newPin } = req.body.data;

      // 格式校验（6 位纯数字强制校验）
      if (!oldPin || oldPin.length !== 6 || !/^\d+$/.test(oldPin)) {
        return res
          .status(400)
          .json({ status: 400, message: "原 PIN 码必须是6位纯数字" });
      }
      if (!newPin || newPin.length !== 6 || !/^\d+$/.test(newPin)) {
        return res
          .status(400)
          .json({ status: 400, message: "新 PIN 码必须是6位纯数字" });
      }

      // 关闭 PIN 的固定标记（6位纯数字，符合校验）
      const CLOSE_PIN_FLAG = "000000";

      // 1. 获取用户 PIN 信息
      const user = await UserPin.findById(req.userId);
      if (!user?.pin_code) {
        return res
          .status(400)
          .json({ status: 400, message: "请先设置 PIN 码" });
      }

      // 2. 校验旧 PIN
      const isValid = await UserPin.verify(oldPin, user.pin_code);
      if (!isValid) {
        // 检查错误次数是否超过5次
        const errorCount = await UserLog.getPinErrorCount(user.id);
        if (errorCount >= 5) {
          // 超过5次，锁定账户并强制退出登录
          await User.lockUser(user.id);
          return res.status(401).json({
            status: 401,
            message: "连续输入错误超过5次，账户已被锁定",
            forceLogout: true,
            locked: true
          });
        }

        // 🔴 登录失败统计
        await UserLog.append({
          user_id: user.id,
          type: "pin",
          status: 0,
          error_message: "尝试修改PIN码PIN码输入错误",
          ...clientData, // 自动解构 request 传来的 login_ip, login_location 等所有字段
        });
        return res.status(400).json({
          status: 400,
          message: "原 PIN 码错误",
          errorCount: errorCount + 1,
          remainingAttempts: 5 - errorCount - 1
        });
      }

      // 3. 逻辑分支：关闭 PIN 或修改 PIN
      let result;
      if (newPin === CLOSE_PIN_FLAG) {
        // 关闭 PIN
        result = await UserPin.close(req.userId);
      } else {
        // 修改 PIN
        result = await UserPin.update(req.userId, oldPin, newPin);
      }

      return res.status(result.status).json({
        status: result.status,
        message: result.message,
      });
    } catch (error) {
      console.error("修改 PIN 失败:", error);
      return res.status(500).json({ status: 500, message: "操作失败" });
    }
  }

  /**
   * 4. 重置 PIN
   * @param {string} verificationCode - 邮箱验证码
   * @param {string} newPin - 新 PIN 码
   */
  async resetPin(req, res) {
    try {
      const { verificationCode, newPin } = req.body.data;

      // 新 PIN 格式校验
      if (!newPin || newPin.length !== 6 || !/^\d+$/.test(newPin)) {
        return res
          .status(400)
          .json({ status: 400, message: "PIN 码必须是6位纯数字" });
      }

      // 验证码校验（后续启用）
      // const isV = await Captcha.verify(email, verificationCode, 'pin_reset');
      // if (!isV) return res.status(400).json({ status: 400, message: "验证码错误" });

      // 使用 UserPin.reset 重置 PIN
      const result = await UserPin.reset(req.userId, newPin);

      return res.status(result.status).json({
        status: result.status,
        message: result.message,
      });
    } catch (error) {
      console.error("重置 PIN 失败:", error);
      return res.status(500).json({ status: 500, message: "重置失败" });
    }
  }
}

module.exports = new SecurityController();
