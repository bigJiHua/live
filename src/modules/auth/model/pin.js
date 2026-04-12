const db = require("../../../common/config/db");
const bcrypt = require("bcryptjs");

/**
 * 用户 PIN 模型 - 匹配数据库表结构
 * 表字段: id, pin_code, create_time, update_time
 */
class UserPin {
  static tableName = "user_info";

  /**
   * 【已启用】
   * 通过用户ID查找 PIN 码
   * @param {number} userId - 用户 ID
   * @returns {Promise<Object|null>} 包含 pin_code 的用户对象或null
   */
  static async findById(userId) {
    const query = `SELECT id, pin_code FROM ${this.tableName} WHERE id = ? LIMIT 1`;
    const [rows] = await db.execute(query, [userId]);
    return rows[0] || null;
  }

  /**
   * 【已启用】
   * 检查用户是否已设置 PIN
   * @param {number} userId - 用户 ID
   * @returns {Promise<boolean>} 是否已设置 PIN
   */
  static async hasPin(userId) {
    const user = await this.findById(userId);
    return user && !!user.pin_code;
  }

  /**
   * 【已启用】
   * 验证 PIN 码
   * @param {string} plainPin - 用户输入的明文 PIN
   * @param {string} hashedPin - 数据库中存储的哈希值
   * @returns {Promise<boolean>} 是否匹配
   */
  static async verify(plainPin, hashedPin) {
    if (!plainPin || !hashedPin) return false;
    return await bcrypt.compare(plainPin, hashedPin);
  }

  /**
   * 【已启用】
   * 创建/设置 PIN 码
   * @param {number} userId - 用户 ID
   * @param {string} plainPin - 明文 PIN（将自动加密）
   * @returns {Promise<Object>} 结果对象
   */
  static async create(userId, plainPin) {
    try {
      // 检查是否已存在
      const existing = await this.findById(userId);
      if (existing?.pin_code) {
        return { status: 400, message: "PIN码已存在，请使用修改接口" };
      }

      // 加密 PIN
      const saltRounds = parseInt(process.env.BCRYPT_SALT_ROUNDS) || 10;
      const hashedPin = await bcrypt.hash(plainPin, saltRounds);

      const query = `UPDATE ${this.tableName} SET pin_code = ?, update_time = NOW() WHERE id = ?`;
      await db.execute(query, [hashedPin, userId]);

      return { status: 200, message: "设置成功" };
    } catch (error) {
      console.error("设置 PIN 失败:", error);
      return { status: 500, message: "设置失败" };
    }
  }

  /**
   * 【已启用】
   * 修改 PIN 码
   * @param {number} userId - 用户 ID
   * @param {string} oldPin - 原 PIN 码
   * @param {string} newPin - 新 PIN 码
   * @returns {Promise<Object>} 结果对象
   */
  static async update(userId, oldPin, newPin) {
    try {
      // 获取当前 PIN
      const user = await this.findById(userId);
      if (!user?.pin_code) {
        return { status: 400, message: "请先设置 PIN 码" };
      }

      // 验证原 PIN
      const isValid = await this.verify(oldPin, user.pin_code);
      if (!isValid) {
        return { status: 401, message: "原 PIN 码错误" };
      }

      // 加密新 PIN
      const saltRounds = parseInt(process.env.BCRYPT_SALT_ROUNDS) || 10;
      const hashedPin = await bcrypt.hash(newPin, saltRounds);

      const query = `UPDATE ${this.tableName} SET pin_code = ?, update_time = NOW() WHERE id = ?`;
      await db.execute(query, [hashedPin, userId]);

      return { status: 200, message: "修改成功" };
    } catch (error) {
      console.error("修改 PIN 失败:", error);
      return { status: 500, message: "修改失败" };
    }
  }

  /**
   * 【已启用】
   * 重置 PIN 码（通过验证码）
   * @param {number} userId - 用户 ID
   * @param {string} newPin - 新 PIN 码
   * @returns {Promise<Object>} 结果对象
   */
  static async reset(userId, newPin) {
    try {
      // 加密新 PIN
      const saltRounds = parseInt(process.env.BCRYPT_SALT_ROUNDS) || 10;
      const hashedPin = await bcrypt.hash(newPin, saltRounds);

      const query = `UPDATE ${this.tableName} SET pin_code = ?, update_time = NOW() WHERE id = ?`;
      await db.execute(query, [hashedPin, userId]);

      return { status: 200, message: "重置成功" };
    } catch (error) {
      console.error("重置 PIN 失败:", error);
      return { status: 500, message: "重置失败" };
    }
  }

  /**
   * 【已启用】
   * 关闭/删除 PIN 码
   * @param {number} userId - 用户 ID
   * @returns {Promise<Object>} 结果对象
   */
  static async close(userId) {
    try {
      const query = `UPDATE ${this.tableName} SET pin_code = NULL, update_time = NOW() WHERE id = ?`;
      await db.execute(query, [userId]);

      return { status: 200, message: "PIN 码已关闭" };
    } catch (error) {
      console.error("关闭 PIN 失败:", error);
      return { status: 500, message: "关闭失败" };
    }
  }
}

module.exports = UserPin;
