const db = require("../config/db");
const { nanoid } = require("nanoid");

/**
 * 文件附件模型 - sys_attachment 表
 * 表结构: id(varchar/nanoid), user_id(varchar), bus_id(int), bus_type, file_name, file_path, file_size(int), file_ext, create_time(varchar), is_deleted
 */
class SysAttachment {
  static tableName = "sys_attachment";

  /**
   * 【已启用】
   * 创建附件记录
   * @param {Object} data - 附件数据
   * @returns {Promise<Object>} 创建结果
   */
  static async create({ userId, busId, busType, fileName, filePath, fileSize, fileExt }) {
    try {
      const id = nanoid(); // 字符串主键
      const createTime = Date.now().toString(); // 存时间戳字符串

      const query = `
        INSERT INTO ${this.tableName}
        (id, user_id, bus_id, bus_type, file_name, file_path, file_size, file_ext, create_time, is_deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
      `;
      const [result] = await db.execute(query, [
        id,
        userId, // VARCHAR，直接存字符串
        busId || null,
        busType,
        fileName,
        filePath,
        fileSize || 0,
        fileExt,
        createTime
      ]);

      return { status: 200, id, message: "上传成功" };
    } catch (error) {
      console.error("创建附件记录失败:", error);
      return { status: 500, message: "上传失败" };
    }
  }

  /**
   * 【已启用】
   * 根据业务类型和业务ID查询附件列表
   * @param {string} busType - 业务类型
   * @param {number} busId - 业务ID
   * @param {string} userId - 用户ID
   * @returns {Promise<Array>} 附件列表
   */
  static async findByBus(busType, busId, userId) {
    try {
      const query = `
        SELECT * FROM ${this.tableName}
        WHERE bus_type = ? AND bus_id = ? AND user_id = ? AND is_deleted = 0
        ORDER BY create_time DESC
      `;
      const [rows] = await db.execute(query, [busType, busId, userId]);
      return rows;
    } catch (error) {
      console.error("查询附件失败:", error);
      return [];
    }
  }

  /**
   * 【已启用】
   * 查询用户的所有附件
   * @param {string} userId - 用户ID
   * @param {Object} options - 查询选项 { busType, limit, offset }
   * @returns {Promise<Array>} 附件列表
   */
  static async findByUser(userId, options = {}) {
    try {
      const { busType, limit = 50, offset = 0 } = options;
      let query = `
        SELECT * FROM ${this.tableName}
        WHERE user_id = ? AND is_deleted = 0
      `;
      const params = [userId];

      if (busType) {
        query += ` AND bus_type = ?`;
        params.push(busType);
      }

      query += ` ORDER BY create_time DESC LIMIT ? OFFSET ?`;
      params.push(limit, offset);

      const [rows] = await db.execute(query, params);
      return rows;
    } catch (error) {
      console.error("查询用户附件失败:", error);
      return [];
    }
  }

  /**
   * 【已启用】
   * 删除附件（硬删除，同时删除数据库记录和缩略图）
   * @param {string} id - 附件ID
   * @param {string} userId - 用户ID（安全校验）
   * @returns {Promise<Object>} 删除结果
   */
  static async delete(id, userId) {
    try {
      const query = `DELETE FROM ${this.tableName} WHERE id = ? AND user_id = ?`;
      const [result] = await db.execute(query, [id, userId]);

      if (result.affectedRows === 0) {
        return { status: 404, message: "附件不存在或无权删除" };
      }

      return { status: 200, message: "删除成功", deletedId: id };
    } catch (error) {
      console.error("删除附件失败:", error);
      return { status: 500, message: "删除失败" };
    }
  }

  /**
   * 【已启用】
   * 批量删除附件（硬删除）
   * @param {Array<string>} ids - 附件ID数组
   * @param {string} userId - 用户ID
   * @returns {Promise<Object>} 删除结果
   */
  static async batchDelete(ids, userId) {
    try {
      if (!ids || ids.length === 0) {
        return { status: 400, message: "请选择要删除的附件" };
      }

      const placeholders = ids.map(() => "?").join(",");
      const query = `DELETE FROM ${this.tableName} WHERE id IN (${placeholders}) AND user_id = ?`;
      const [result] = await db.execute(query, [...ids, userId]);

      return {
        status: 200,
        message: `已删除 ${result.affectedRows} 个附件`,
        count: result.affectedRows,
        deletedIds: ids
      };
    } catch (error) {
      console.error("批量删除附件失败:", error);
      return { status: 500, message: "批量删除失败" };
    }
  }

  /**
   * 【已启用】
   * 根据ID查询附件
   * @param {string} id - 附件ID
   * @returns {Promise<Object|null>} 附件对象
   */
  static async findById(id) {
    try {
      const query = `SELECT * FROM ${this.tableName} WHERE id = ? AND is_deleted = 0 LIMIT 1`;
      const [rows] = await db.execute(query, [id]);
      return rows[0] || null;
    } catch (error) {
      console.error("查询附件失败:", error);
      return null;
    }
  }
}

module.exports = SysAttachment;
