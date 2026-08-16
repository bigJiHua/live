/**
 * 同银行共享额度池模型（痛点2）
 * - 同一银行下 N 张卡共享一个额度池（如农业银行 A/B 两卡共享 5000）
 * - 池持有 total_credit_limit / total_temp_limit
 * - 卡片挂 share_pool_id；CreditCore.#resolveLimit 读池而非卡片自身额度
 */
const db = require("../../../common/config/db");
const idUtils = require("../../../common/utils/idUtils");

const TABLE = "card_credit_pool";

// 表列名缓存（进程内）：用于检测新迁移列是否存在，避免迁移未执行时读写报 ER_BAD_FIELD_ERROR。
// 列结构在进程生命周期内基本不变；执行迁移后重启进程即重新检测。
const COLUMN_CACHE = new Map();

/** 查询表当前列名集合（information_schema，结果缓存） */
async function tableColumns(table) {
  if (COLUMN_CACHE.has(table)) return COLUMN_CACHE.get(table);
  const [rows] = await db.execute(
    `SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?`,
    [table]
  );
  const cols = new Set(rows.map((r) => r.COLUMN_NAME));
  COLUMN_CACHE.set(table, cols);
  return cols;
}

class CreditPool {
  static nowStr() {
    const d = new Date();
    const p = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
  }

  /** 创建共享池 */
  static async create({
    userId,
    bankId = null,
    bankName = null,
    totalCreditLimit = 0,
    totalTempLimit = 0,
    creditReportMerged = 0,
    currency = "CNY",
    remark = null,
  }) {
    const id = idUtils.shortId();
    const now = this.nowStr();
    // 迁移 016 未执行时 credit_report_merged 列不存在：动态剔除，保证 CREATE 不崩
    const cols = await tableColumns(TABLE);
    const hasMerged = cols.has("credit_report_merged");
    const insertCols = [
      "id", "user_id", "bank_id", "bank_name",
      "total_credit_limit", "total_temp_limit",
      ...(hasMerged ? ["credit_report_merged"] : []),
      "currency", "remark", "create_time", "update_time", "is_deleted",
    ];
    const values = [
      id, userId, bankId, bankName,
      Number(totalCreditLimit || 0), Number(totalTempLimit || 0),
      ...(hasMerged ? [creditReportMerged ? 1 : 0] : []),
      currency, remark, now, now, 0,
    ];
    await db.execute(
      `INSERT INTO ${TABLE} (${insertCols.join(", ")})
       VALUES (${insertCols.map(() => "?").join(", ")})`,
      values
    );
    return this.findById(id, userId);
  }

  /** 更新池额度/信息 */
  static async update(id, userId, fields = {}) {
    // 迁移 016 未执行时 credit_report_merged 列不存在：动态剔除，保证 UPDATE 不崩
    const cols = await tableColumns(TABLE);
    const allowed = {
      bankId: "bank_id",
      bankName: "bank_name",
      totalCreditLimit: "total_credit_limit",
      totalTempLimit: "total_temp_limit",
      creditReportMerged: "credit_report_merged",
      currency: "currency",
      remark: "remark",
    };
    const sets = [];
    const params = [];
    for (const key of Object.keys(allowed)) {
      if (fields[key] !== undefined) {
        // 列不存在时跳过该字段（如 credit_report_merged 尚未迁移）
        if (!cols.has(allowed[key])) continue;
        sets.push(`${allowed[key]} = ?`);
        params.push(
          key === "totalCreditLimit" || key === "totalTempLimit"
            ? Number(fields[key] || 0)
            : key === "creditReportMerged"
            ? (fields[key] ? 1 : 0)
            : fields[key]
        );
      }
    }
    if (sets.length === 0) return this.findById(id, userId);
    sets.push("update_time = ?");
    params.push(this.nowStr());
    params.push(id, userId);
    await db.execute(
      `UPDATE ${TABLE} SET ${sets.join(", ")} WHERE id = ? AND user_id = ? AND is_deleted = 0`,
      params
    );
    const updated = await this.findById(id, userId);

    // 改池额度后重算池内卡片（H1：syncCardBills 已自动全池扩散，只需触发任意一张即可）
    try {
      const CreditCore = require('../core/CreditCore');
      const [cardRows] = await db.execute(
        `SELECT id FROM card_base WHERE share_pool_id = ? AND user_id = ? AND is_deleted = 0`,
        [id, userId]
      );
      if (cardRows.length > 0) {
        await CreditCore.syncCardBills(cardRows[0].id, userId);
      }
    } catch (e) {
      console.error('[共享池] 改额度后重算卡片账单失败:', e.message);
    }

    return updated;
  }

  static async findById(id, userId) {
    const [rows] = await db.execute(
      `SELECT * FROM ${TABLE} WHERE id = ? AND user_id = ? AND is_deleted = 0`,
      [id, userId]
    );
    return rows[0] || null;
  }

  static async findByUser(userId) {
    const [rows] = await db.execute(
      `SELECT * FROM ${TABLE} WHERE user_id = ? AND is_deleted = 0 ORDER BY create_time DESC`,
      [userId]
    );
    if (rows.length === 0) return rows;
    // 补充池内卡片信息：card_count（张数）与 cards（卡 id 列表），供前端展示 "N 张卡共享"
    const placeholders = rows.map(() => '?').join(',');
    const [cardRows] = await db.execute(
      `SELECT id, share_pool_id FROM card_base
       WHERE user_id = ? AND share_pool_id IN (${placeholders}) AND is_deleted = 0`,
      [userId, ...rows.map((r) => r.id)]
    );
    const byPool = {};
    cardRows.forEach((c) => {
      (byPool[c.share_pool_id] = byPool[c.share_pool_id] || []).push(c.id);
    });
    return rows.map((r) => ({
      ...r,
      card_count: (byPool[r.id] || []).length,
      cards: byPool[r.id] || [],
    }));
  }

  static async delete(id, userId) {
    const conn = await db.getConnection();
    let unboundCards = [];
    try {
      await conn.beginTransaction();
      const [cardRows] = await conn.execute(
        'SELECT id FROM card_base WHERE share_pool_id = ? AND user_id = ? AND is_deleted = 0',
        [id, userId]
      );
      unboundCards = cardRows.map(r => r.id);
      await conn.execute(
        `UPDATE ${TABLE} SET is_deleted = 1, update_time = ? WHERE id = ? AND user_id = ?`,
        [this.nowStr(), id, userId]
      );
      // 解除池内所有卡的 share_pool_id，避免聚合时找不到池却仍按0处理（P6 审计）
      await conn.execute(
        `UPDATE card_base SET share_pool_id = NULL, update_time = ? WHERE share_pool_id = ? AND user_id = ? AND is_deleted = 0`,
        [this.nowStr(), id, userId]
      );
      await conn.commit();
    } catch (e) {
      await conn.rollback();
      throw e;
    } finally {
      conn.release();
    }
    // 事务外：重算被解绑卡（额度真相源切换回自身额度，H1 审计）
    try {
      const CreditCore = require('../core/CreditCore');
      for (const cid of unboundCards) {
        await CreditCore.syncCardBills(cid, userId);
      }
    } catch (e) {
      console.error('[共享池] 删除后重算解绑卡失败:', e.message);
    }
    return true;
  }

  /** 把某卡归入/移出池（card_base.share_pool_id） */
  static async assignCard(cardId, userId, poolId) {
    const conn = await db.getConnection();
    let oldPoolId = null;
    try {
      await conn.beginTransaction();
      const [oldRows] = await conn.execute(
        'SELECT share_pool_id, bank_id FROM card_base WHERE id = ? AND user_id = ? AND is_deleted = 0 FOR UPDATE',
        [cardId, userId]
      );
      if (!oldRows[0]) throw new Error('卡片不存在');
      oldPoolId = oldRows[0].share_pool_id || null;
      const newPoolId = poolId || null;
      if (newPoolId) {
        const [poolRows] = await conn.execute(
          'SELECT id, bank_id FROM card_credit_pool WHERE id = ? AND user_id = ? AND is_deleted = 0',
          [newPoolId, userId]
        );
        if (!poolRows[0]) throw new Error('共享池不存在');
        // 不同银行不能交叉归池：卡与池的 bank_id 必须一致（双方都有值时校验）
        const cardBankId = oldRows[0].bank_id || null;
        const poolBankId = poolRows[0].bank_id || null;
        if (cardBankId && poolBankId && cardBankId !== poolBankId) {
          throw new Error('不同银行的卡不能归入该共享池');
        }
      }
      await conn.execute(
        `UPDATE card_base SET share_pool_id = ?, update_time = ? WHERE id = ? AND user_id = ? AND is_deleted = 0`,
        [newPoolId, this.nowStr(), cardId, userId]
      );
      await conn.commit();
    } catch (e) {
      await conn.rollback();
      throw e;
    } finally {
      conn.release();
    }
    // 事务外：重算该卡 + 旧池 + 新池相关所有卡（H1：移池必须刷新两侧，避免旧池/新池余额滞后）
    try {
      const CreditCore = require('../core/CreditCore');
      const newPoolId = poolId || null;
      const poolSet = new Set([oldPoolId, newPoolId].filter(Boolean));
      for (const pid of poolSet) {
        const [poolCards] = await db.execute(
          'SELECT id FROM card_base WHERE share_pool_id = ? AND user_id = ? AND is_deleted = 0',
          [pid, userId]
        );
        if (poolCards.length > 0) {
          await CreditCore.syncCardBills(poolCards[0].id, userId); // 自动全池扩散
        }
      }
      await CreditCore.syncCardBills(cardId, userId); // 当前卡自身（移出池后独立）
    } catch (e) {
      console.error('[共享池] 归池/移池后重算失败:', e.message);
    }
    return true;
  }
}

module.exports = CreditPool;
