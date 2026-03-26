const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const User = require('../models/User');
const { sendVerificationEmail } = require('../utils/mailer');

class AuthController {
  // 用户注册
  async register(req, res) {
    try {
      const { username, email, password } = req.body;

      // 检查用户是否已存在 (通过用户名和邮箱)
      const existingUser = await User.findByUsername(username);
      if (existingUser) {
        return res.status(400).json({ message: '该用户名已被注册' });
      }

      const existingEmail = await User.findByEmail(email);
      if (existingEmail) {
        return res.status(400).json({ message: '该邮箱已被注册' });
      }

      // 加密密码 (使用环境变量配置的 salt rounds)
      const saltRounds = parseInt(process.env.BCRYPT_SALT_ROUNDS) || 10;
      const password_hash = await bcrypt.hash(password, saltRounds);

      // 创建用户
      const user = await User.create({
        username,
        email,
        password_hash,
        is_locked: 0 // 新注册用户默认解锁
      });

      res.status(201).json({
        message: '注册成功',
        userId: user.id,
        user: {
          id: user.id,
          username: user.username,
          email: user.email
        }
      });
    } catch (error) {
      console.error('注册错误:', error);
      res.status(500).json({ message: '注册失败', error: error.message });
    }
  }

  // 用户登录
  async login(req, res) {
    try {
      const { email, password } = req.body;

      // 查找用户 (通过邮箱或用户名)
      let user = await User.findByEmail(email);
      if (!user) {
        user = await User.findByUsername(email);
      }

      if (!user) {
        return res.status(401).json({ message: '邮箱或用户名不存在' });
      }

      // 检查用户是否被锁定
      if (user.is_locked === 1) {
        return res.status(403).json({ message: '账号已被锁定,请联系管理员' });
      }

      // 验证密码 (注意数据库字段是 password_hash)
      const isValidPassword = await bcrypt.compare(password, user.password_hash);
      if (!isValidPassword) {
        return res.status(401).json({ message: '密码错误' });
      }
      // 生成 JWT Token
      const token = jwt.sign(
        { userId: user.id, username: user.username, email: user.email },
        process.env.JWT_SECRET,
        { expiresIn: '7d' }
      );
      res.json({
        message: '登录成功',
        token,
        user: {
          id: user.id,
          username: user.username,
          email: user.email,
        },
      });
    } catch (error) {
      console.error('登录错误:', error);
      res.status(500).json({ message: '登录失败', error: error.message });
    }
  }

  // 获取当前用户信息
  async getCurrentUser(req, res) {
    try {
      const user = await User.findById(req.userId);
      if (!user) {
        return res.status(404).json({ message: '用户不存在' });
      }

      res.json({
        user: {
          id: user.id,
          username: user.username,
          email: user.email,
          createdAt: user.createdAt,
        },
      });
    } catch (error) {
      console.error('获取用户信息错误:', error);
      res.status(500).json({ message: '获取用户信息失败', error: error.message });
    }
  }

  // 刷新 Token
  async refreshToken(req, res) {
    try {
      const { token } = req.body;

      if (!token) {
        return res.status(400).json({ message: 'Token 不能为空' });
      }

      // 验证旧 Token
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      const user = await User.findById(decoded.userId);

      if (!user) {
        return res.status(404).json({ message: '用户不存在' });
      }

      // 生成新 Token
      const newToken = jwt.sign(
        { userId: user.id, email: user.email },
        process.env.JWT_SECRET,
        { expiresIn: '7d' }
      );

      res.json({
        message: 'Token 刷新成功',
        token: newToken,
      });
    } catch (error) {
      console.error('刷新 Token 错误:', error);
      res.status(401).json({ message: 'Token 无效或已过期', error: error.message });
    }
  }

  // 更新用户资料
  async updateProfile(req, res) {
    try {
      const { username } = req.body;
      const user = await User.update(req.userId, { username });

      res.json({
        message: '更新成功',
        user: {
          id: user.id,
          username: user.username,
          email: user.email,
        },
      });
    } catch (error) {
      console.error('更新资料错误:', error);
      res.status(500).json({ message: '更新失败', error: error.message });
    }
  }

  // 修改密码
  async changePassword(req, res) {
    try {
      const { oldPassword, newPassword } = req.body;

      const user = await User.findById(req.userId);
      if (!user) {
        return res.status(404).json({ message: '用户不存在' });
      }

      // 验证旧密码 (注意数据库字段是 password_hash)
      const isValidPassword = await bcrypt.compare(oldPassword, user.password_hash);
      if (!isValidPassword) {
        return res.status(400).json({ message: '旧密码错误' });
      }

      // 加密新密码 (使用环境变量配置的 salt rounds)
      const saltRounds = parseInt(process.env.BCRYPT_SALT_ROUNDS) || 10;
      const password_hash = await bcrypt.hash(newPassword, saltRounds);

      // 更新密码 (注意字段名是 password_hash)
      await User.update(req.userId, { password_hash });

      res.json({ message: '密码修改成功' });
    } catch (error) {
      console.error('修改密码错误:', error);
      res.status(500).json({ message: '修改密码失败', error: error.message });
    }
  }

  // 获取用户设置
  async getSettings(req, res) {
    try {
      const user = await User.findById(req.userId);
      res.json({ settings: user.settings || {} });
    } catch (error) {
      console.error('获取设置错误:', error);
      res.status(500).json({ message: '获取设置失败', error: error.message });
    }
  }

  // 更新用户设置
  async updateSettings(req, res) {
    try {
      const { settings } = req.body;
      await User.update(req.userId, { settings });

      res.json({ message: '设置更新成功', settings });
    } catch (error) {
      console.error('更新设置错误:', error);
      res.status(500).json({ message: '更新设置失败', error: error.message });
    }
  }

  // 检查是否已有管理员
  async checkAdmin(req, res) {
    try {
      const count = await User.count();
      const hasAdmin = count > 0;

      res.json({
        hasAdmin,
        count,
        message: hasAdmin ? '系统已初始化' : '系统未初始化'
      });
    } catch (error) {
      console.error('检查管理员状态错误:', error);
      res.status(500).json({
        message: '检查管理员状态失败',
        error: error.message
      });
    }
  }

  // 管理员首次注册
  async adminRegister(req, res) {
    try {
      const { username, email, password } = req.body;

      // 检查是否已有用户
      const count = await User.count();

      if (count > 0) {
        return res.status(403).json({
          message: '系统已存在用户,无法注册管理员'
        });
      }

      // 检查用户名是否已存在
      const existingUser = await User.findByUsername(username);
      if (existingUser) {
        return res.status(400).json({ message: '该用户名已被注册' });
      }

      // 检查邮箱是否已存在
      const existingEmail = await User.findByEmail(email);
      if (existingEmail) {
        return res.status(400).json({ message: '该邮箱已被注册' });
      }

      // 加密密码 (使用环境变量配置的 salt rounds)
      const saltRounds = parseInt(process.env.BCRYPT_SALT_ROUNDS) || 10;
      const password_hash = await bcrypt.hash(password, saltRounds);

      // 创建管理员用户
      const user = await User.create({
        username,
        email,
        password_hash,
        is_locked: 0 // 管理员默认解锁
      });

      res.json({
        message: '管理员注册成功',
        user: {
          id: user.id,
          username: user.username,
          email: user.email
        }
      });
    } catch (error) {
      console.error('管理员注册错误:', error);
      res.status(500).json({
        message: '管理员注册失败',
        error: error.message
      });
    }
  }
}

module.exports = new AuthController();
