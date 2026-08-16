const UserPin = require("../../auth/model/pin");
const UserLog = require("../../auth/model/log");
const User = require("../../auth/model/user");
const db = require("../../../common/config/db");
const pinLockGuard = require("../../../common/middleware/pinLockGuard");
const { resolvePassword } = require("../../../common/utils/rsaKeys");

/** 解密 PIN（支持 RSA 密文数组，安全键盘 secureOnly 输入） */
const resolvePin = (raw) => resolvePassword(raw);

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
      const { pin: rawPin } = req.body.data;
      const pin = resolvePin(rawPin); // 支持 RSA 密文数组解密

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

      // ── 锁定态前置检查：pin_status=3（含系统硬锁）→ 直接拒绝，不再走验证 ──
      const [lockRows] = await db.execute(
        `SELECT id, error_count, create_time
         FROM security_verify_log
         WHERE user_id = ? AND pin_status = 3 AND action_type <> 'route_verify'
         ORDER BY id DESC LIMIT 1`,
        [req.userId]
      );
      const lockRecord = lockRows[0];
      if (lockRecord) {
        const lockMinutes =
          (Number(lockRecord.error_count) || 3) * pinLockGuard.CONFIG.LOCK_MULTIPLIER;
        const expire =
          new Date(lockRecord.create_time).getTime() + lockMinutes * 60000;
        if (Date.now() < expire) {
          const remainMinutes = Math.ceil((expire - Date.now()) / 60000);
          return res.status(200).json({
            code: 8304,
            status: 200,
            message: `PIN 已锁定，剩余 ${remainMinutes} 分钟`,
            data: { locked: true, remainMinutes },
          });
        }
        // 锁定期已过 → 清除锁定状态与错误计数，允许重新验证
        await db.execute(
          `UPDATE security_verify_log
           SET pin_status = 0, error_count = 0, remark = '锁定到期，重新验证'
           WHERE id = ?`,
          [lockRecord.id]
        );
      }

      // 验证 PIN
      const isValid = await UserPin.verify(pin, user.pin_code);
      if (!isValid) {
        // 错误计数：无记录则插入新失败记录，有则对最新一条记录累加（无论 pin_status 是 0/1/2），
        // 否则"验证成功过"或"无历史"时 error_count 永远为 0，剩余次数恒为 3。
        const [latestRecord] = await db.execute(
          `SELECT id, error_count FROM security_verify_log
           WHERE user_id = ? AND action_type <> 'route_verify'
           ORDER BY id DESC LIMIT 1`,
          [req.userId]
        );
        if (latestRecord[0]) {
          await db.execute(
            `UPDATE security_verify_log
             SET pin_status = 2, error_count = error_count + 1, remark = '验证失败'
             WHERE id = ?`,
            [latestRecord[0].id]
          );
        } else {
          await db.execute(
            `INSERT INTO security_verify_log
             (user_id, request_url, action_type, pin_status, error_count, remark, create_time)
             VALUES (?, ?, 'verify', 2, 1, '验证失败', NOW())`,
            [req.userId, req.originalUrl || '/security/pin/verify']
          );
        }

        // 检查 security_verify_log 的错误次数是否达阈值
        const secErrorCount = latestRecord[0]
          ? latestRecord[0].error_count + 1
          : 1;

        // 检查 user_log 的错误次数
        const logErrorCount = await UserLog.getPinErrorCount(req.userId);
        if (secErrorCount >= 3) {
          // security_verify_log 达到3次 → 硬锁定
          await db.execute(
            `UPDATE security_verify_log
             SET pin_status = 3, remark = '锁定90分钟'
             WHERE user_id = ? AND pin_status = 2 AND action_type <> 'route_verify'
             ORDER BY id DESC LIMIT 1`,
            [req.userId]
          );
          return res.status(200).json({
            code: 8304, status: 200, message: "PIN 错误次数过多，已锁定90分钟"
          });
        }
        if (logErrorCount >= 5) {
          await User.lockUser(req.userId);
          return res.status(401).json({
            status: 401, message: "连续输入错误超过5次，账户已被锁定", forceLogout: true, locked: true
          });
        }
        return res.status(200).json({
          code: 8302, status: 200, message: `PIN 错误（剩余 ${3 - secErrorCount} 次）`
        });
      }

      // ✅ PIN 正确 → 同步更新 security_verify_log，解除所有待验证/锁定状态
      // 1. 标记最近一条待验证/失败记录为成功，并重置错误计数（防止历史错误累积误锁）
      await db.execute(
        `UPDATE security_verify_log
         SET pin_status = 1, error_count = 0, remark = '验证成功'
         WHERE user_id = ? AND pin_status IN (0,2) AND action_type <> 'route_verify'
         ORDER BY id DESC LIMIT 1`,
        [req.userId]
      );
      // 2. 清除系统软锁定记录
      await db.execute(
        `UPDATE security_verify_log
         SET pin_status = 1, remark = '系统已解锁'
         WHERE user_id = ? AND action_type = 'lock' AND pin_status = 0`,
        [req.userId]
      );

      return res.json({ code: 8301, status: 200, message: "验证成功", verified: true });
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
      const { pin: rawPin, challengeId, requestUrl, method } = req.body.data || {};
      const pin = resolvePin(rawPin); // 支持 RSA 密文数组解密
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

      // 风险路由验证成功时，同步解除系统软锁定（避免后续普通请求被分支1.5 死循环拦截）
      await db.execute(
        `UPDATE security_verify_log
         SET pin_status = 1, remark = '系统已解锁'
         WHERE user_id = ? AND action_type = 'lock' AND pin_status = 0`,
        [req.userId]
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
      const { pin: rawPin } = req.body.data;
      const pin = resolvePin(rawPin); // 支持 RSA 密文数组解密

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
      const { oldPin: rawOldPin, newPin: rawNewPin } = req.body.data;
      const oldPin = resolvePin(rawOldPin); // 支持 RSA 密文数组解密
      const newPin = resolvePin(rawNewPin);

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
        // 错误计数：有记录则对最新一条累加，无记录则插入（与 verifyPin 的 3 次锁定语义一致）
        const [pinLogRows] = await db.execute(
          `SELECT id, error_count FROM security_verify_log
           WHERE user_id = ? AND action_type <> 'route_verify'
           ORDER BY id DESC LIMIT 1`,
          [req.userId]
        );
        if (pinLogRows[0]) {
          await db.execute(
            `UPDATE security_verify_log
             SET pin_status = 2, error_count = error_count + 1, remark = '验证失败'
             WHERE id = ?`,
            [pinLogRows[0].id]
          );
        } else {
          await db.execute(
            `INSERT INTO security_verify_log
             (user_id, request_url, action_type, pin_status, error_count, remark, create_time)
             VALUES (?, ?, 'verify', 2, 1, '验证失败', NOW())`,
            [req.userId, req.originalUrl || '/security/pin/change']
          );
        }
        const pinErrCount = pinLogRows[0]
          ? pinLogRows[0].error_count + 1
          : 1;

        if (pinErrCount >= 3) {
          // 连续 3 次 PIN 错误 → 锁定 PIN 验证（90 分钟，与 verifyPin 一致）
          await db.execute(
            `UPDATE security_verify_log
             SET pin_status = 3, remark = '锁定90分钟'
             WHERE user_id = ? AND pin_status = 2 AND action_type <> 'route_verify'
             ORDER BY id DESC LIMIT 1`,
            [req.userId]
          );
          return res.status(200).json({
            code: 8304,
            status: 200,
            message: "PIN 错误次数过多，已锁定90分钟",
            data: { locked: true },
          });
        }

        // 错误次数未达阈值：同步 user_log 做账户级统计（保持原 5 次锁账户兜底）
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
          errorCount: pinErrCount,
          remainingAttempts: Math.max(3 - pinErrCount, 0),
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
      const { verificationCode, newPin: rawNewPin } = req.body.data;
      const newPin = resolvePin(rawNewPin); // 支持 RSA 密文数组解密

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
