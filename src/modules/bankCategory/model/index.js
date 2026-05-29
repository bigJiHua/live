const { execute } = require("../../../common/config/db");
const idUtils = require("../../../common/utils/idUtils");

class BankCategory {
  static tableName = "bus_category";

  static async findAll(userId) {
    const [banks] = await execute(
      `SELECT * FROM ${this.tableName}
       WHERE user_id = ? AND type = 'bank' AND is_deleted = 0
       ORDER BY sort ASC, name ASC`,
      [userId]
    );
    return banks;
  }

  static async findById(id, userId) {
    const [rows] = await execute(
      `SELECT * FROM ${this.tableName}
       WHERE id = ? AND user_id = ? AND type = 'bank' AND is_deleted = 0`,
      [id, userId]
    );
    return rows[0] || null;
  }

  static async create({ userId, name, iconUrl, remark, sort = 99 }) {
    const id = `BC_${idUtils.shortId()}`;

    await execute(
      `INSERT INTO ${this.tableName}
       (id, user_id, name, icon_url, remark, type, sort, is_deleted)
       VALUES (?, ?, ?, ?, ?, 'bank', ?, 0)`,
      [id, userId, name, iconUrl || "", remark || "", sort]
    );

    return this.findById(id, userId);
  }

  static async update(id, userId, { name, iconUrl, remark, sort }) {
    await execute(
      `UPDATE ${this.tableName}
       SET name = ?, icon_url = ?, remark = ?, sort = ?
       WHERE id = ? AND user_id = ? AND type = 'bank'`,
      [name, iconUrl || "", remark || "", sort || 99, id, userId]
    );

    return this.findById(id, userId);
  }

  static async delete(id, userId) {
    await execute(
      `UPDATE ${this.tableName}
       SET is_deleted = 1
       WHERE id = ? AND user_id = ? AND type = 'bank'`,
      [id, userId]
    );
  }
}

module.exports = BankCategory;
