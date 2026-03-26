const bcrypt = require('bcryptjs');
const { generatePin, verifyPinHash } = require('../utils/crypto');
const User = require('../models/User');
const { sendVerificationEmail } = require('../utils/mailer');

class SecurityController {
  // 验证 PIN 码
  async verifyPin(req, res) {
    try {
      const { pin } = req.body;

      if (!pin) {
        return res.status(400).json({ message: 'PIN 码不能为空' });
      }

      const user = await User.findById(req.userId);
      if (!user) {
        return res.status(404).json({ message: '用户不存在' });
      }

      if (!user.pinHash) {
        return res.status(400).json({ message: '请先设置 PIN 码' });
      }

      const isValid = await verifyPinHash(pin, user.pinHash);

      if (!isValid) {
        return res.status(401).json({ message: 'PIN 码错误' });
      }

      // 标记会话已通过 PIN 验证
      req.session.pinVerified = true;

      res.json({
        message: 'PIN 码验证成功',
        verified: true,
      });
    } catch (error) {
      console.error('验证 PIN 码错误:', error);
      res.status(500).json({ message: '验证 PIN 码失败', error: error.message });
    }
  }

  // 设置 PIN 码
  async setPin(req, res) {
    try {
      const { pin, confirmPassword } = req.body;

      if (!pin || pin.length !== 6 || !/^\d+$/.test(pin)) {
        return res.status(400).json({ message: 'PIN 码必须是6位数字' });
      }

      if (pin !== confirmPassword) {
        return res.status(400).json({ message: '两次输入的 PIN 码不一致' });
      }

      const user = await User.findById(req.userId);
      if (user.pinHash) {
        return res.status(400).json({ message: 'PIN 码已设置，请使用修改功能' });
      }

      // 加密 PIN 码
      const pinHash = await bcrypt.hash(pin, 10);

      await User.update(req.userId, { pinHash });

      res.json({
        message: 'PIN 码设置成功',
        hasPin: true,
      });
    } catch (error) {
      console.error('设置 PIN 码错误:', error);
      res.status(500).json({ message: '设置 PIN 码失败', error: error.message });
    }
  }

  // 修改 PIN 码
  async changePin(req, res) {
    try {
      const { oldPin, newPin, confirmPassword } = req.body;

      if (!oldPin || !newPin || !confirmPassword) {
        return res.status(400).json({ message: '请填写完整信息' });
      }

      if (newPin.length !== 6 || !/^\d+$/.test(newPin)) {
        return res.status(400).json({ message: '新 PIN 码必须是6位数字' });
      }

      if (newPin !== confirmPassword) {
        return res.status(400).json({ message: '两次输入的新 PIN 码不一致' });
      }

      const user = await User.findById(req.userId);
      if (!user.pinHash) {
        return res.status(400).json({ message: '请先设置 PIN 码' });
      }

      // 验证旧 PIN 码
      const isValid = await bcrypt.compare(oldPin, user.pinHash);
      if (!isValid) {
        return res.status(401).json({ message: '旧 PIN 码错误' });
      }

      // 加密新 PIN 码
      const pinHash = await bcrypt.hash(newPin, 10);

      await User.update(req.userId, { pinHash });

      res.json({
        message: 'PIN 码修改成功',
      });
    } catch (error) {
      console.error('修改 PIN 码错误:', error);
      res.status(500).json({ message: '修改 PIN 码失败', error: error.message });
    }
  }

  // 重置 PIN 码 (需要邮箱验证)
  async resetPin(req, res) {
    try {
      const { email, verificationCode, newPin, confirmPassword } = req.body;

      if (!email || !newPin || !confirmPassword) {
        return res.status(400).json({ message: '请填写完整信息' });
      }

      // 验证验证码
      // 这里需要实现验证码验证逻辑
      // const isValidCode = await verifyCode(email, verificationCode);
      // if (!isValidCode) {
      //   return res.status(400).json({ message: '验证码错误或已过期' });
      // }

      const user = await User.findByEmail(email);
      if (!user) {
        return res.status(404).json({ message: '用户不存在' });
      }

      if (newPin.length !== 6 || !/^\d+$/.test(newPin)) {
        return res.status(400).json({ message: '新 PIN 码必须是6位数字' });
      }

      if (newPin !== confirmPassword) {
        return res.status(400).json({ message: '两次输入的 PIN 码不一致' });
      }

      // 加密新 PIN 码
      const pinHash = await bcrypt.hash(newPin, 10);

      await User.update(user.id, { pinHash });

      res.json({
        message: 'PIN 码重置成功',
      });
    } catch (error) {
      console.error('重置 PIN 码错误:', error);
      res.status(500).json({ message: '重置 PIN 码失败', error: error.message });
    }
  }

  // 发送验证码 (邮箱验证)
  async sendVerificationCode(req, res) {
    try {
      const { email } = req.body;

      const user = await User.findByEmail(email);
      if (!user) {
        return res.status(404).json({ message: '用户不存在' });
      }

      // 生成验证码
      const code = generatePin(6);

      // 发送验证邮件
      await sendVerificationEmail(email, code);

      res.json({
        message: '验证码已发送到您的邮箱',
      });
    } catch (error) {
      console.error('发送验证码错误:', error);
      res.status(500).json({ message: '发送验证码失败', error: error.message });
    }
  }
}

module.exports = new SecurityController();
