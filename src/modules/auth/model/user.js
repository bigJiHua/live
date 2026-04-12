const db = require("../../../common/config/db");
const bcrypt = require("bcryptjs");
const dayjs = require("dayjs");
const idUtils = require("../../../common/utils/idUtils");
const utc = require("dayjs/plugin/utc"); // 必须引入 UTC 插件
const timezone = require("dayjs/plugin/timezone"); // 必须引入时区插件
// 核心步骤：注册插件
dayjs.extend(utc);
dayjs.extend(timezone);

/**
 * 用户模型 - 匹配数据库表结构
 * 表字段: id, username, password_hash, email, pin_code, is_locked, created_at, updated_at
 */
class User {
  static tableName = "user_info";
  /**
   * 【已启用】
   * 通过用户名查找用户
   * @param {string} username - 用户名
   * @returns {Promise<Object|null>} 用户对象或null
   */
  static async findByUsername(username) {
    const query = `SELECT * FROM ${this.tableName} WHERE username = ?`;
    const [rows] = await db.execute(query, [username]);
    return rows[0] || null;
  }

  /**
   * 【已启用】
   * 通过邮箱查找用户
   * @param {string} email - 邮箱地址
   * @returns {Promise<Object|null>} 用户对象或null
   */
  static async findByEmail(email) {
    const query = `SELECT * FROM ${this.tableName} WHERE email = ?`;
    const [rows] = await db.execute(query, [email]);
    return rows[0] || null;
  }

  /**
   * 【已启用】
   * 通过 ID 查找用户
   * @param {number} id - 用户 ID
   * @returns {Promise<Object|null>} 用户对象或null
   */
  static async findById(id) {
    const query = `SELECT * FROM ${this.tableName} WHERE id = ?`;
    const [rows] = await db.execute(query, [id]);
    return { ...rows[0], login_pwd: "", pin_code: "" } || null;
  }

  /**
   * 【已启用】
   * 创建用户
   * @param {Object} userData - 用户数据
   * @param {string} userData.username - 用户名
   * @param {string} userData.login_pwd - 加密后的密码
   * @param {string} userData.email - 邮箱
   * @param {string} userData.identity - 身份标识
   * @returns {Promise<Object>} 创建的用户对象
   */
  static async create({ username, email, login_pwd, identity }) {
    // 1. 显式列出所有字段，手动对应问号
    const query = `
    INSERT INTO ${this.tableName} 
    (id, username, email, login_pwd, identity, create_time, update_time) 
    VALUES (?, ?, ?, ?, ?, ?, ?)
  `;
    const newUserId = idUtils.userId();
    // 2. 构造纯数组，顺序必须与上面括号内的字段一一对应
    const userData = [
      newUserId,
      username,
      email || null, // 防 undefined 报错
      login_pwd,
      identity || "user",
      Date.now(), // 统一存时间戳
      dayjs().tz("Asia/Shanghai").format("YYYY-MM-DD HH:mm:ss"), // 统一存明文
    ];

    // 3. 执行
    const [result] = await db.execute(query, userData);
    return result;
  }

  /**
   * 【已启用】
   * 更新用户
   * @param {number} id - 用户 ID
   * @param {Object} updates - 更新数据
   * @returns {Promise<Object>} 更新后的用户对象
   * 【注意】：此方法只能修改用户名和头像等非敏感数据
   */
  static async update(id, updates, req) {
    try {
      const fields = [];
      const values = [];

      // --- 1. 用户名修改逻辑增强 ---
      if (updates.username) {
        const currentUser = await this.findById(id);
        // 只有当新旧用户名不同时，才触发校验和日志
        if (updates.username !== currentUser.username) {
          // A. 校验唯一性
          const [existing] = await db.execute(
            `SELECT id FROM ${this.tableName} WHERE username = ? AND id != ? LIMIT 1`,
            [updates.username, id]
          );
          if (existing && existing.length > 0) {
            return {
              status: 206,
              message: "该用户名已被占用",
            };
          }

          // B. 校验修改周期：1年 (31536000000 毫秒)
          const oneYearMs = 365 * 24 * 60 * 60 * 1000;
          const now = Date.now();
          const lastUpdate = new Date(currentUser.update_time).getTime();

          if (now - lastUpdate < oneYearMs) {
            const diffDays = Math.ceil(
              (oneYearMs - (now - lastUpdate)) / (24 * 60 * 60 * 1000)
            );
            console.log(`用户${id}，还需等待 ${diffDays} 天`);
            return {
              status: 206,
              message: "用户名一年只能修改一次！",
            };
          }

          // 标记：更新成功后需要写日志
          var _logData = {
            oldName: currentUser.username,
            newName: updates.username,
          };
        } else {
          return {
            status: 206,
            message: "新旧名不能一致！",
          };
        }
      }

      // --- 2. 构建 SQL 字段 ---
      const fieldMap = {
        username: "username",
        avatar: "avatar",
        email: 'email'
      };

      Object.keys(updates).forEach((key) => {
        if (fieldMap[key]) {
          fields.push(`${fieldMap[key]} = ?`);
          values.push(updates[key]);
        }
      });

      if (fields.length === 0)
        return {
          status: 200,
          user: await this.findById(id),
        };

      // --- 3. 执行更新 ---
      fields.push("update_time = NOW()"); // 更新时间戳
      const query = `UPDATE ${this.tableName} SET ${fields.join(
        ", "
      )} WHERE id = ?`;
      values.push(id);
      await db.execute(query, values);

      // --- 4. 自动化记录 UserLog ---
      // 数据全部从 req.body 中获取（解密中间件已平铺）
      if (_logData && req) {
        const UserLog = require("./UserLog");
        await UserLog.append({
          user_id: id,
          type: "change_username",
          token: req.headers.authorization,
          // 直接引用 req.body 里的平铺数据
          login_ip:
            req.body.login_ip === "unknown" ? req.ip : req.body.login_ip,
          login_location: req.body.login_location,
          user_agent: req.body.user_agent,
          os_info: req.body.os_info,
          browser_info: req.body.browser_info,
          device_model: req.body.device_model,
          fingerprint: req.body.fingerprint,
          viewport: req.body.viewport,
          pixel_ratio: req.body.pixel_ratio,
          login_lang: req.body.login_lang,
          path: req.body.path || "/profile/edit",
          status: 1,
          error_message: `修改用户名: [${_logData.oldName}] -> [${_logData.newName}]`,
        });
      }

      return {
        status: 200,
        user: await this.findById(id),
      };
    } catch (error) {
      console.error("Database Error:", error);
      return {
        status: 500,
        message: "系统繁忙，请稍后再试",
      };
    }
  }

  /**
   * 删除用户
   * @param {number} id - 用户 ID
   * @returns {Promise<boolean>} 是否删除成功
   */
  static async delete(id) {
    const query = `DELETE FROM ${this.tableName} WHERE id = ?`;
    const [result] = await db.execute(query, [id]);
    return result.affectedRows > 0;
  }

  /**
   * 检查用户是否被锁定
   * @param {number} id - 用户 ID
   * @returns {Promise<boolean>} 是否锁定
   */
  static async isStatus(id) {
    const user = await this.findById(id);
    return user ? Number(user.status) === 1 : false;
  }

  /**
   * 锁定/解锁用户
   * @param {number} id - 用户 ID
   * @param {boolean} locked - 是否锁定
   * @returns {Promise<Object>} 更新后的用户对象
   */
  static async setLockStatus(id, locked) {
    try {
      const query = `
        UPDATE ${this.tableName}
        SET status = ?, update_time = ?
        WHERE id = ?
      `;
      await db.execute(query, [locked ? 0 : 1, Date.now(), id]);
      return { status: 200, message: locked ? "账户已锁定" : "账户已解锁" };
    } catch (error) {
      console.error("锁定用户失败:", error);
      return { status: 500, message: "操作失败" };
    }
  }

  /**
   * 锁定用户（PIN错误超过5次时调用）
   * @param {number} id - 用户 ID
   * @returns {Promise<Object>} 更新结果
   */
  static async lockUser(id) {
    try {
      const query = `
        UPDATE ${this.tableName}
        SET status = 0, update_time = ?
        WHERE id = ?
      `;
      await db.execute(query, [Date.now(), id]);
      return { status: 200, message: "账户已被锁定" };
    } catch (error) {
      console.error("锁定用户失败:", error);
      return { status: 500, message: "操作失败" };
    }
  }

  /**
   * 验证密码静态方法
   * @param {string} plainPassword - 用户输入的明文密码
   * @param {string} hashedPassword - 数据库中存储的 $2b$10$... 开头的哈希值
   * @returns {Promise<boolean>} - 返回布尔值：true 匹配，false 不匹配
   */
  static async verifyPassword(plainPassword, hashedPassword) {
    try {
      if (!plainPassword || !hashedPassword) {
        return false;
      }
      // bcrypt.compare 会自动从 hashedPassword 中提取 salt 并进行计算对比
      return await bcrypt.compare(plainPassword, hashedPassword);
    } catch (error) {
      console.error("密码验证过程出错:", error);
      return false; // 出错一律返回不匹配，保证安全
    }
  }

  /**
   * 获取用户数量
   * @returns {Promise<number>} 用户总数
   */
  static async count() {
    const query = `SELECT COUNT(*) as count FROM ${this.tableName}`;
    const [rows] = await db.execute(query);
    return rows[0].count;
  }
}

module.exports = User;
