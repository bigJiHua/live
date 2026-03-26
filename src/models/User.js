const db = require('../config/db');
const bcrypt = require('bcryptjs');

/**
 * 用户模型 - 匹配数据库表结构
 * 表字段: id, username, password_hash, email, pin_code, is_locked, created_at, updated_at
 */
class User {
  /**
   * 通过用户名查找用户
   * @param {string} username - 用户名
   * @returns {Promise<Object|null>} 用户对象或null
   */
  static async findByUsername(username) {
    const query = 'SELECT * FROM users WHERE username = ?';
    const [rows] = await db.execute(query, [username]);
    return rows[0] || null;
  }

  /**
   * 通过邮箱查找用户
   * @param {string} email - 邮箱地址
   * @returns {Promise<Object|null>} 用户对象或null
   */
  static async findByEmail(email) {
    const query = 'SELECT * FROM users WHERE email = ?';
    const [rows] = await db.execute(query, [email]);
    return rows[0] || null;
  }

  /**
   * 通过 ID 查找用户
   * @param {number} id - 用户 ID
   * @returns {Promise<Object|null>} 用户对象或null
   */
  static async findById(id) {
    const query = 'SELECT * FROM users WHERE id = ?';
    const [rows] = await db.execute(query, [id]);
    return rows[0] || null;
  }

  /**
   * 创建用户
   * @param {Object} userData - 用户数据
   * @param {string} userData.username - 用户名
   * @param {string} userData.password_hash - 加密后的密码
   * @param {string} userData.email - 邮箱
   * @param {string} userData.pin_code - PIN 码 (可选)
   * @param {number} userData.is_locked - 锁定状态 (默认1)
   * @returns {Promise<Object>} 创建的用户对象
   */
  static async create({
    username,
    password_hash,
    email = null,
    pin_code = null,
    is_locked = 1
  }) {
    const now = new Date().toISOString().slice(0, 19).replace('T', ' '); // YYYY-MM-DD HH:mm:ss

    const query = `
      INSERT INTO users (username, password_hash, email, pin_code, is_locked, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, NOW())
    `;
    const [result] = await db.execute(query, [
      username,
      password_hash,
      email,
      pin_code,
      is_locked,
      now
    ]);
    return this.findById(result.insertId);
  }

  /**
   * 更新用户
   * @param {number} id - 用户 ID
   * @param {Object} updates - 更新数据
   * @returns {Promise<Object>} 更新后的用户对象
   */
  static async update(id, updates) {
    const fields = [];
    const values = [];

    // 字段映射
    const fieldMap = {
      username: 'username',
      password_hash: 'password_hash',
      email: 'email',
      pin_code: 'pin_code',
      is_locked: 'is_locked',
      updated_at: 'updated_at'
    };

    Object.keys(updates).forEach((key) => {
      if (fieldMap[key]) {
        fields.push(`${fieldMap[key]} = ?`);
        values.push(updates[key]);
      }
    });

    if (fields.length === 0) {
      return this.findById(id);
    }

    // 自动更新 updated_at
    fields.push('updated_at = NOW()');

    values.push(id);
    const query = `UPDATE users SET ${fields.join(', ')} WHERE id = ?`;
    await db.execute(query, values);

    return this.findById(id);
  }

  /**
   * 删除用户
   * @param {number} id - 用户 ID
   * @returns {Promise<boolean>} 是否删除成功
   */
  static async delete(id) {
    const query = 'DELETE FROM users WHERE id = ?';
    const [result] = await db.execute(query, [id]);
    return result.affectedRows > 0;
  }

  /**
   * 检查用户是否被锁定
   * @param {number} id - 用户 ID
   * @returns {Promise<boolean>} 是否锁定
   */
  static async isLocked(id) {
    const user = await this.findById(id);
    return user ? user.is_locked === 1 : false;
  }

  /**
   * 锁定/解锁用户
   * @param {number} id - 用户 ID
   * @param {boolean} locked - 是否锁定
   * @returns {Promise<Object>} 更新后的用户对象
   */
  static async setLockStatus(id, locked) {
    return this.update(id, { is_locked: locked ? 1 : 0 });
  }

  /**
   * 获取用户数量
   * @returns {Promise<number>} 用户总数
   */
  static async count() {
    const query = 'SELECT COUNT(*) as count FROM users';
    const [rows] = await db.execute(query);
    return rows[0].count;
  }
}

module.exports = User;
