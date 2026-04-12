const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const dayjs = require("dayjs");
const utc = require("dayjs/plugin/utc"); // 必须引入 UTC 插件
const timezone = require("dayjs/plugin/timezone"); // 必须引入时区插件
const User = require("../model/user");
const UserLog = require("../model/log");
const CryptoJS = require("crypto-js");
const { sendVerificationEmail } = require("../../../common/utils/mailer");
const db = require("../../../common/config/db");
// 核心步骤：注册插件
dayjs.extend(utc);
dayjs.extend(timezone);

class AuthController {
  // 用户注册
  async register(req, res) {
    try {
      const { username, email, password } = req.body;

      // 检查用户是否已存在 (通过用户名和邮箱)
      const existingUser = await User.findByUsername(username);
      if (existingUser) {
        return res.status(400).json({ message: "该用户名已被注册" });
      }

      const existingEmail = await User.findByEmail(email);
      if (existingEmail) {
        return res.status(400).json({ message: "该邮箱已被注册" });
      }

      // 加密密码 (使用环境变量配置的 salt rounds)
      const saltRounds = parseInt(process.env.BCRYPT_SALT_ROUNDS) || 10;
      const password_hash = await bcrypt.hash(password, saltRounds);

      // 创建用户
      const user = await User.create({
        username,
        email,
        password_hash,
        is_locked: 0, // 新注册用户默认解锁
      });

      res.status(200).json({
        message: "注册成功",
        userId: user.id,
        user: {
          id: user.id,
          username: user.username,
          email: user.email,
        },
      });
    } catch (error) {
      console.error("注册错误:", error);
      res.status(500).json({ message: "注册失败", error: error.message });
    }
  }

  // 【已启用】
  // 用户登录：增加了对解密后数据的兼容处理
  async login(req, res) {
    // 这里的 req.body 已经是经过 decryptWithSecurity 中间件解密后的完整对象了
    const clientData = req.body;
    const { nameOrEmail, password } = req.body.data;
    try {
      // 1. 基本校验
      if (!nameOrEmail || !password) {
        return res.status(400).json({ message: "请输入账号和密码" });
      }

      // 2. 查找用户
      const user =
        (await User.findByEmail(nameOrEmail)) ||
        (await User.findByUsername(nameOrEmail));

      if (!user) {
        return res.status(404).json({ message: "用户不存在" });
      }

      // 3. 检查设备锁定状态 (利用中间件挂载的 req.device)
      if (
        req.device &&
        req.device.locked_until &&
        Date.now() < req.device.locked_until
      ) {
        return res
          .status(423)
          .json({ message: "由于尝试次数过多，设备已锁定，请稍后再试" });
      }

      // 4. 验证密码
      const isValid = await User.verifyPassword(password, user.login_pwd);

      if (!isValid) {
        // 🔴 登录失败统计
        await UserLog.append({
          user_id: user.id,
          type: "failed",
          status: 0,
          error_message: "密码错误",
          ...clientData, // 自动解构 request 传来的 login_ip, login_location 等所有字段
        });

        // 更新该指纹的失败计数 (db逻辑省略)
        return res.say("用户名或密码错误", 404);
      }

      // 5. 生成 Token
      const token = jwt.sign(
        {
          userId: user.id,
          username: user.username,
          role: user.identity,
          avatar: user.avatar,
          email: user.email,
        },
        process.env.JWT_SECRET,
        { expiresIn: "12h" }
      );

      // 6. 🟢 登录成功统计
      // 这里的 append 会把 clientData 里的 ip, location, isp, viewport 等全部存入 login_log
      await UserLog.append({
        user_id: user.id,
        type: "login",
        token: token.substring(0, 50), // 只存存根
        status: 1,
        ...clientData,
      });

      return res.status(200).json({
        message: "登录成功!",
        token,
        status: 200,
      });
    } catch (error) {
      console.error("登录异常:", error);
      return res.status(500).json({ message: "服务器内部错误" });
    }
  }

  // 【已启用】
  // 获取当前用户信息
  async getCurrentUser(req, res) {
    try {
      const user = await User.findById(req.userId);
      if (!user) return res.say("用户不存在", 404);
      res.status(200).send({
        status: 200,
        message: "获取用户信息成功",
        data: {
          username: user.username,
          email: user.email,
          avatar: user.avatar,
          identity: user.identity,
        },
      });
    } catch (error) {
      console.error("获取用户信息错误:", error);
      res
        .status(500)
        .json({ message: "获取用户信息失败", error: error.message });
    }
  }

  // 刷新 Token
  async refreshToken(req, res) {
    try {
      const { token } = req.body;

      if (!token) {
        return res.status(400).json({ message: "Token 不能为空" });
      }

      // 验证旧 Token
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      const user = await User.findById(decoded.userId);

      if (!user) {
        return res.status(404).json({ message: "用户不存在" });
      }

      // 生成新 Token
      const newToken = jwt.sign(
        { userId: user.id, email: user.email },
        process.env.JWT_SECRET,
        { expiresIn: "7d" }
      );

      res.json({
        message: "Token 刷新成功",
        token: newToken,
      });
    } catch (error) {
      console.error("刷新 Token 错误:", error);
      res
        .status(401)
        .json({ message: "Token 无效或已过期", error: error.message });
    }
  }

  // 【已启用】
  // 更新用户资料
  async updateProfile(req, res) {
    // 1. 安全地获取业务数据
    // 使用可选链 ?. 或解构并给默认值，防止 undefined 报错
    const { username, avatar } = req.body.data || {};
    // 构造一个只包含“用户传了的内容”的对象
    const updateData = {};
    if (username !== undefined) updateData.username = username;
    if (avatar !== undefined) updateData.avatar = avatar;
    if (Object.keys(updateData).length === 0) {
      return res.say("没有提供要更新的内容", 400); // 🟢 记得 return
    }
    // 2. 调用 Model，传入 req 用于记录日志
    const updatedUser = await User.update(req.userId, updateData, req);
    if (updatedUser.status === 200) {
      // 3. 成功返回
      return res.status(200).json({
        status: 200,
        message: "资料更新成功",
        data: {
          user: {
            username: updatedUser.user.username,
            email: updatedUser.user.email,
            avatar: updatedUser.user.avatar,
          },
        },
      });
    } else return res.say(updatedUser.message, 400);
  }

  // 获取用户设置
  async getSettings(req, res) {
    try {
      const user = await User.findById(req.userId);
      res.json({ settings: user.settings || {} });
    } catch (error) {
      console.error("获取设置错误:", error);
      res.status(500).json({ message: "获取设置失败", error: error.message });
    }
  }

  // 更新用户设置
  async updateSettings(req, res) {
    try {
      const { settings } = req.body;
      await User.update(req.userId, { settings });

      res.json({ message: "设置更新成功", settings });
    } catch (error) {
      console.error("更新设置错误:", error);
      res.status(500).json({ message: "更新设置失败", error: error.message });
    }
  }

  // 【已启用】
  // 检查是否已有管理员
  async checkAdmin(req, res) {
    try {
      const count = await User.count();
      const hasAdmin = count > 0;
      res.status(200).send({
        status: 200,
        message: hasAdmin ? "系统已初始化" : "系统未初始化",
        data: {
          hasAdmin,
          count,
        },
      });
    } catch (error) {
      console.error("检查管理员状态错误:", error);
      res.status(500).json({
        message: "检查管理员状态失败",
        error: error.message,
      });
    }
  }

  // 【已启用】
  // 管理员首次注册
  async adminRegister(req, res) {
    try {
      const { username, email, password } = req.body.data;
      // 检查是否已有用户
      const count = await User.count();
      if (count > 0) {
        return res.status(403).json({
          message: "系统已存在用户,无法注册管理员",
        });
      }
      // 检查用户名是否已存在
      const existingUser = await User.findByUsername(username);
      if (existingUser) {
        return res.status(400).json({ message: "该用户名已被注册" });
      }
      // 检查邮箱是否已存在
      const existingEmail = await User.findByEmail(email);
      if (existingEmail) {
        return res.status(400).json({ message: "该邮箱已被注册" });
      }
      // 加密密码 (使用环境变量配置的 salt rounds)
      const saltRounds = parseInt(process.env.BCRYPT_SALT_ROUNDS) || 10;
      const password_hash = await bcrypt.hash(password, saltRounds);

      // 创建管理员用户
      const user = await User.create({
        username,
        email,
        login_pwd: password_hash,
        identity: count <= 1 ? "admin" : "user",
      });

      res.status(200).send({
        message: "注册成功！",
        user: {
          id: user.id,
          username: user.username,
          email: user.email,
        },
      });
    } catch (error) {
      console.log(error);
      res.status(500).send({
        message: "管理员注册失败",
        error: error.message,
        status: 500,
      });
    }
  }

  // 【已启用】
  // 获取加密密钥 (已修正 ERR_HTTP_HEADERS_SENT 问题)
  async getHandshake(req, res) {
    try {
      const { fp, scene = "login" } = req.query;
      if (!fp) return res.say("非法设备标识", 400);

      const tempKey = CryptoJS.lib.WordArray.random(32).toString();
      // 有效期2小时
      const aesExpires = Date.now() + 2 * 60 * 60 * 1000;
      const now = Date.now();

      const sql = `
        INSERT INTO device_crypto (
          fingerprint, scene, aes_key, aes_expires_at,
          client_ip, user_agent, created_at, updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          aes_key = VALUES(aes_key),
          aes_expires_at = VALUES(aes_expires_at),
          client_ip = VALUES(client_ip),
          updated_at = VALUES(updated_at),
          captcha_verified = 0,
          fail_count = 0
      `;

      await db.execute(sql, [
        fp,
        scene,
        tempKey,
        aesExpires,
        req.ip,
        req.headers["user-agent"],
        now,
        now,
      ]);

      // 这里必须 return，确保响应只发送一次
      return res.json({ status: 200, key: tempKey, scene });
    } catch (error) {
      console.error("安全握手异常:", error);
      // 增加防御性判断
      if (!res.headersSent) {
        return res.say("初始化失败", 500);
      }
    }
  }

  // 【已启用】
  // 发送邮箱验证码
  async sendEmailCode(req, res) {
    try {
      const { email, type = "email" } = req.body.data;
      const userId = req.body.userId || req.userId; // 从请求中获取用户ID

      if (!email) return res.say("邮箱不能为空", 400);
      if (!userId) return res.say("用户ID不能为空", 400);

      const now = Date.now();
      // type: "email" → fingerprint = "email_verify_email"
      // type: "pwd" → fingerprint = "email_verify_pwd"
      const fingerprint = `email_verify_${type}`;
      const scene = email; // scene 存储用户邮箱
      const aes_key = userId; // aes_key 存储用户ID，用于身份识别

      // 生成6位验证码
      const code = Math.floor(100000 + Math.random() * 900000).toString();
      const expiresAt = now + 10 * 60 * 1000; // 10分钟有效期

      // 计算今天0点的时间戳
      const todayStart = new Date();
      todayStart.setHours(0, 0, 0, 0);
      const todayStartTimestamp = todayStart.getTime();

      // 查询现有记录（通过 fingerprint + scene + aes_key 三个条件查询）
      const selectSql = `
        SELECT * FROM device_crypto
        WHERE fingerprint = ? AND aes_key = ?
      `;
      const [rows] = await db.execute(selectSql, [fingerprint, aes_key]);

      let insertOrUpdateSql;
      let params;
      // 如果有就校验有效时间
      if (rows && rows.length > 0) {
        const record = rows[0];
        const recordDate = new Date(record.created_at);
        const todayDate = new Date(todayStartTimestamp);

        // 检查记录的创建日期是否是今天
        const isToday =
          recordDate.getFullYear() === todayDate.getFullYear() &&
          recordDate.getMonth() === todayDate.getMonth() &&
          recordDate.getDate() === todayDate.getDate();
        // 超过 N 次不让发了
        if (isToday && Number(record.captcha_attempts) > 5) {
          console.log("发送次数上限！");
          return res.say("今日获取验证码次数上限，次日再试！", 400);
        }
        // 增加发送次数
        const newAttempts = isToday ? (record.captcha_attempts || 0) + 1 : 1;
        // 如果不是今天，同步创建时间为今日；如果是今天，保持原创建时间
        const created_at = isToday ? record.created_at : now;
        // 更新现有记录
        insertOrUpdateSql = `
          UPDATE device_crypto
          SET captcha_code = ?, captcha_expires_at = ?, captcha_verified = 0,
              captcha_attempts = ?, updated_at = ?, created_at = ?
          WHERE fingerprint = ? AND aes_key = ?
        `;
        params = [
          code,
          expiresAt,
          newAttempts,
          now,
          created_at,
          fingerprint,
          aes_key,
        ];
        console.log(
          `存在字段 -- 发送次数: ${newAttempts}, 是否今日: ${isToday}`
        );
      } else {
        // 第一次发送验证码
        // 插入新记录
        insertOrUpdateSql = `
          INSERT INTO device_crypto (
            fingerprint, scene, aes_key, captcha_code, captcha_expires_at,
            captcha_attempts, captcha_verified, client_ip, user_agent, created_at, updated_at
          )
          VALUES (?, ?, ?, ?, ?, 1, 0, ?, ?, ?, ?)
        `;
        params = [
          fingerprint,
          scene,
          aes_key,
          code,
          expiresAt,
          req.ip,
          req.headers["user-agent"],
          now,
          now,
        ];
      }
      const dat = await db.execute(insertOrUpdateSql, params);
      if (dat[0].affectedRows === 0) return res.say("验证码发送错误", 500);

      // TODO: 开发阶段留2个要填的值
      // 1. EMAIL_HOST 和 EMAIL_PORT 在 .env 中配置
      // 2. EMAIL_USER 和 EMAIL_PASS 在 .env 中配置
      // 当前使用模拟发送
      console.log(
        `请配置环境变量 EMAIL_HOST、EMAIL_PORT、EMAIL_USER、EMAIL_PASS 以启用真实发送`
      );
      console.log(
        `[发送验证码] 用户ID: ${userId}, 邮箱: ${email}, 类型: ${type}, 验证码: ${code}, 过期时间: ${new Date(
          expiresAt
        )}`
      );

      return res.status(200).send({
        status: 200,
        message: "验证码已发送",
        data: {
          code: process.env.NODE_ENV === "development" ? code : undefined,
        },
      });
    } catch (error) {
      console.error("发送验证码错误:", error);
      return res.say("发送验证码失败，请稍后再试", 400);
    }
  }

  // 修改邮箱（需要验证码）
  async changeEmail(req, res) {
    try {
      const { email, code } = req.body.data;
      const userId = req.userId; // UVu_3pIgiaqTkHAzX4bPU

      // 1. 严格纠正拼写和对应关系
      const fingerprint = `email_verify_email`;
      const scene = email;
      const id = userId; // 对应你数据库里的 aes_key 字段

      console.log(
        `[准备查询] fingerprint: ${fingerprint}, scene: ${scene}, aes_key: ${id}`
      );

      const sql = `
        SELECT * FROM device_crypto 
        WHERE fingerprint = ? AND aes_key = ?`;

      // 这里的参数顺序必须和 SQL 里的 ? 一一对应
      const [rows] = await db.execute(sql, [fingerprint, id]);
      console.log(`[数据库查询结果] 找到 ${rows ? rows.length : 0} 条记录`);
      if (!rows || rows.length === 0) {
        console.log(`[验证码校验失败] 验证码不存在`);
        return res.say("验证码不存在或已过期", 400);
      }
      const record = rows[0];
      const now = Date.now();
      const expiresAt = record.captcha_expires_at;
      console.log(
        `[验证码信息] 存储的验证码: ${
          record.captcha_code
        }, 过期时间: ${new Date(expiresAt)}, 当前时间: ${new Date(
          now
        )}, 剩余时间: ${Math.floor((expiresAt - now) / 1000)}秒`
      );

      // 验证验证码
      if (record.captcha_code !== code) {
        console.log(
          `[验证码校验失败] 验证码错误: 输入=${code}, 存储=${record.captcha_code}`
        );
        // 增加失败次数（不增加 captcha_attempts，因为那是发送次数）
        await db.execute(
          `UPDATE device_crypto
           SET fail_count = fail_count + 1, updated_at = ?
           WHERE fingerprint = ? AND scene = ? AND aes_key = ?`,
          [now, fingerprint, scene, id]
        );
        return res.say("验证码错误", 400);
      }

      // 检查验证码是否过期
      if (now > expiresAt) {
        console.log(`[验证码校验失败] 验证码已过期`);
        return res.say("验证码已过期", 400);
      }

      if (record.captcha_verified && Number(record.captcha_verified) === 1) {
        console.log("已验证过");
        return res.say("验证码已验证过，请重试！", 400);
      }

      // 验证成功，增加使用次数（captcha_attempts 既用于统计发送次数，也用于统计使用次数）
      const newAttempts = (record.captcha_attempts || 0) + 1;

      // 检查邮箱是否已被使用
      const existingEmail = await User.findByEmail(email);
      if (existingEmail && existingEmail.id !== userId) {
        console.log(`[邮箱检查失败] 邮箱已被使用`);
        return res.say("该邮箱已被使用", 400);
      }

      // 更新邮箱
      const user = await User.update(userId, { email });

      // 更新使用次数并标记已验证
      await db.execute(
        `UPDATE device_crypto
         SET captcha_attempts = ?, captcha_verified = 1, updated_at = ?
         WHERE fingerprint = ? AND scene = ? AND aes_key = ?`,
        [newAttempts, now, fingerprint, scene, id]
      );

      console.log(
        `[修改邮箱成功] 用户 ${userId} 邮箱已修改为 ${email}, 验证码使用次数: ${newAttempts}`
      );

      res.status(200).send({
        message: "邮箱修改成功",
        status: 200,
        data: {
          user: {
            username: user.user.username,
            email: user.user.email,
            avatar: user.user.avatar,
          },
        },
      });
    } catch (error) {
      console.error("修改邮箱错误:", error);
      return res.say("修改邮箱失败", 500);
    }
  }

  // 【已启用】
  // 修改密码（校验：旧密码 + 新密码 + 验证码）
  async changePasswordWithCode(req, res) {
    try {
      const { oldPassword, newPassword, code } = req.body.data;
      const userId = req.userId;
      const id = userId;

      // 内部专用：带密码查询
      const findForAuth = async (targetId) => {
        const [rows] = await db.execute(
          `SELECT id, username, email, avatar, login_pwd FROM user_info WHERE id = ? AND is_deleted = 0 LIMIT 1`,
          [targetId]
        );
        return rows[0];
      };

      // 1. 获取用户信息
      const userRecord = await findForAuth(userId);
      if (!userRecord) return res.say("用户不存在", 404);

      // 2. 校验：新旧密码不能一样
      if (oldPassword === newPassword) {
        return res.say("新密码不能与原密码相同", 400);
      }

      // 3. 校验：原密码是否正确
      const isOldPasswordCorrect = await bcrypt.compare(
        oldPassword,
        userRecord.login_pwd || "" // 防止数据库字段为 NULL 导致报错
      );

      if (!isOldPasswordCorrect) {
        return res.say("原密码输入错误", 400);
      }

      // 4. 校验：验证码
      const fingerprint = `email_verify_pwd`;
      const scene = userRecord.email;
      const [vRows] = await db.execute(
        `SELECT * FROM device_crypto WHERE fingerprint = ? AND scene = ? AND aes_key = ?`,
        [fingerprint, scene, id]
      );

      if (!vRows || vRows.length === 0)
        return res.say("验证码不存在或已过期", 400);

      const record = vRows[0];
      const now = Date.now();

      if (now > record.captcha_expires_at) return res.say("验证码已过期", 400);
      if (record.captcha_verified === 1) return res.say("验证码已被使用", 400);
      if (record.captcha_code !== code) {
        await db.execute(
          `UPDATE device_crypto SET fail_count = fail_count + 1, updated_at = ? 
           WHERE fingerprint = ? AND scene = ? AND aes_key = ?`,
          [now, fingerprint, scene, id]
        );
        return res.say("验证码错误", 400);
      }

      // 5. 【关键重写】执行更新：直接用原生 SQL 更新 login_pwd
      const saltRounds = parseInt(process.env.BCRYPT_SALT_ROUNDS) || 10;
      const hashedPwd = await bcrypt.hash(newPassword, saltRounds);

      const updateTime = new Date()
        .toISOString()
        .replace("T", " ")
        .substring(0, 19); // 格式化为 2026-04-01 21:12:41

      // 🔴 不再使用 User.update，直接操作数据库
      const [updateResult] = await db.execute(
        `UPDATE user_info SET login_pwd = ?, update_time = ? WHERE id = ? AND is_deleted = 0`,
        [hashedPwd, updateTime, userId]
      );

      if (updateResult.affectedRows === 0) {
        throw new Error("数据库更新失败，受影响行数为 0");
      }

      // 6. 核销验证码
      await db.execute(
        `UPDATE device_crypto SET captcha_verified = 1, updated_at = ?
         WHERE fingerprint = ? AND scene = ? AND aes_key = ?`,
        [now, fingerprint, scene, id]
      );

      console.log(`[成功] 用户 ${userId} 密码已硬写入数据库`);

      return res.status(200).send({
        status: 200,
        message: "密码修改成功，请重新登录",
      });
    } catch (error) {
      console.error("修改密码错误:", error);
      return res.say("服务器错误，修改失败", 500);
    }
  }
}

module.exports = new AuthController();
