const UserPin = require("../models/UserPin");

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
        return res.status(400).json({ status: 400, message: "PIN 码错误" });
      }

      return res.json({ status: 200, message: "验证成功", verified: true });
    } catch (error) {
      console.error("验证 PIN 失败:", error);
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
        return res.status(400).json({ status: 400, message: "原 PIN 码错误" });
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
