const db = require('../../../common/config/db');
const idUtils = require('../../../common/utils/idUtils');

/**
 * 银行分类模型 - 对应数据库 bus_category 表 (type='bank')
 * 用于管理用户的银行卡片分类（储蓄卡/信用卡的归属银行）
 */
class BusBank {
  static tableName = 'bus_category';

  // 虚拟银行分类（系统内置，不可删除）
  static VIRTUAL_BANKS = {
    CASH: 'xxxx',     // 现金
    BALANCE: 'yyyy',  // 余额
  };

  // 虚拟银行显示名称
  static VIRTUAL_NAMES = {
    'xxxx': { name: '现金', icon_url: '', remark: '系统虚拟账户' },
    'yyyy': { name: '余额', icon_url: '', remark: '系统虚拟账户（微信+支付宝）' },
  };

  /**
   * 获取银行分类列表（type='bank'）
   * 自动初始化虚拟银行分类（如果不存在）
   */
  static async findAll(userId) {
    // 确保虚拟银行分类已初始化
    await this.initVirtualBanks(userId);

    const query = `
      SELECT * FROM ${this.tableName}
      WHERE user_id = ? AND type = 'bank' AND is_deleted = 0
      ORDER BY sort ASC, name ASC
    `;
    const [rows] = await db.execute(query, [userId]);
    return rows;
  }

  /**
   * 根据ID查找银行分类
   */
  static async findById(id, userId) {
    const query = `SELECT * FROM ${this.tableName} WHERE id = ? AND user_id = ? AND type = 'bank' AND is_deleted = 0`;
    const [rows] = await db.execute(query, [id, userId]);
    return rows[0] || null;
  }

  /**
   * 创建银行分类
   */
  static async create({ userId, name, iconUrl, remark, sort }) {
    const id = idUtils.billId();

    // 获取最大排序值
    const [sortRows] = await db.execute(
      `SELECT COALESCE(MAX(sort), 0) as maxSort FROM ${this.tableName} WHERE user_id = ? AND type = 'bank' AND is_deleted = 0`,
      [userId]
    );
    const maxSort = parseInt(sortRows[0]?.maxSort || 0);
    const finalSort = sort !== undefined ? sort : maxSort + 1;

    const query = `
      INSERT INTO ${this.tableName} (id, user_id, name, type, icon_url, remark, sort, is_deleted)
      VALUES (?, ?, ?, 'bank', ?, ?, ?, 0)
    `;
    await db.execute(query, [id, userId, name, iconUrl || '', remark || '', finalSort]);

    return this.findById(id, userId);
  }

  /**
   * 更新银行分类
   */
  static async update(id, userId, { name, iconUrl, remark, sort }) {
    const fields = [];
    const params = [];

    if (name !== undefined) {
      fields.push('name = ?');
      params.push(name);
    }
    if (iconUrl !== undefined) {
      fields.push('icon_url = ?');
      params.push(iconUrl);
    }
    if (remark !== undefined) {
      fields.push('remark = ?');
      params.push(remark);
    }
    if (sort !== undefined) {
      fields.push('sort = ?');
      params.push(sort);
    }

    if (fields.length === 0) {
      return this.findById(id, userId);
    }

    params.push(id, userId);

    const query = `
      UPDATE ${this.tableName}
      SET ${fields.join(', ')}
      WHERE id = ? AND user_id = ? AND type = 'bank' AND is_deleted = 0
    `;
    await db.execute(query, params);
    return this.findById(id, userId);
  }

  /**
   * 删除银行分类（软删除）
   */
  static async delete(id, userId) {
    // 禁止删除虚拟银行分类
    if (this.VIRTUAL_BANKS[id]) {
      throw new Error('系统虚拟银行分类无法删除');
    }

    const query = `
      UPDATE ${this.tableName}
      SET is_deleted = 1
      WHERE id = ? AND user_id = ? AND type = 'bank'
    `;
    const [result] = await db.execute(query, [id, userId]);
    return result.affectedRows > 0;
  }

  /**
   * 初始化虚拟银行分类（系统内置）
   * xxxx = 现金，yyyy = 余额
   * 插入到 bus_category 表，type='bank'
   */
  static async initVirtualBanks(userId) {
    const results = [];

    for (const [type, bankId] of Object.entries(this.VIRTUAL_BANKS)) {
      // 检查是否已存在
      const [existing] = await db.execute(
        'SELECT id FROM bus_category WHERE id = ? AND user_id = ? AND type = ?',
        [bankId, userId, 'bank']
      );

      if (existing.length === 0) {
        // 创建虚拟银行分类
        const virtualInfo = this.VIRTUAL_NAMES[bankId];
        const query = `
          INSERT INTO bus_category (id, user_id, name, type, icon_url, remark, sort, is_deleted)
          VALUES (?, ?, ?, 'bank', ?, ?, ?, 0)
        `;
        await db.execute(query, [
          bankId,
          userId,
          virtualInfo.name,
          virtualInfo.icon_url,
          virtualInfo.remark,
          0 // sort=0 表示系统内置
        ]);
        console.log(`[虚拟银行] 创建 ${virtualInfo.name} (${bankId})`);
      }

      // 获取并返回虚拟银行信息
      const [rows] = await db.execute(
        'SELECT * FROM bus_category WHERE id = ? AND user_id = ? AND type = ?',
        [bankId, userId, 'bank']
      );
      if (rows[0]) {
        results.push(rows[0]);
      }
    }

    return results;
  }
}

module.exports = BusBank;
