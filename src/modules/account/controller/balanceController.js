const db = require('../../../common/config/db');
const AccountSettlement = require('../service/settlement');
const idUtils = require('../../../common/utils/idUtils');

// 虚拟账户类型（无卡号实体账户）
const VIRTUAL_TYPES = {
  CASH: 'xxxx',      // 现金
  WX: 'yyyy',        // 微信/支付宝（余额）
  ALIPAY: 'yyyy',    // 支付宝
};

// 虚拟账户显示名称
const VIRTUAL_NAMES = {
  'xxxx': { name: '现金', alias: '现金账户' },
  'yyyy': { name: '余额', alias: '余额账户（微信/支付宝）' },
};

/**
 * 账户余额控制器
 * - 虚拟账户：xxxx（现金）、yyyy（余额）
 * - 实体卡片：card_base 表中的储蓄卡（debit）
 * - 余额统一换算成人民币
 */
class AccountBalanceController {

  /**
   * 获取所有账户余额列表
   * 自动初始化未登记的卡片账户和虚拟账户
   * 自动校验卡片和流水是否正常
   */
  static async getList(req, res) {
    try {
      // 0. 动态校验：清理无效卡片，重新计算余额
      await AccountBalanceController.verifyAndSync(req.userId);

      // 1. 初始化虚拟账户（现金 + 余额）
      await AccountBalanceController.ensureVirtualAccounts(req.userId);

      // 2. 初始化储蓄卡
      await AccountBalanceController.initAllCardBalances(req.userId);

      // 3. 获取余额快照列表
      const balances = await AccountBalanceController.getSnapshotList(req.userId);

      if (!balances || balances.length === 0) {
        return res.status(200).send({
          status: 200,
          message: "暂无余额记录",
          data: [],
        });
      }
      return res.json({ status: 200, message: "获取成功", data: balances });
    } catch (error) {
      console.error('获取账户余额列表错误:', error);
      return res.say("获取失败", 500);
    }
  }

  /**
   * 获取余额快照列表
   */
  static async getSnapshotList(userId) {
    const query = `
      SELECT ab.*,
             cb.alias as card_alias,
             cb.last4_no as card_last4,
             cb.card_type
      FROM account_balance ab
      LEFT JOIN card_base cb ON ab.card_id = cb.id
      WHERE ab.user_id = ? AND ab.is_deleted = 0
      ORDER BY 
        CASE WHEN ab.card_id IN ('xxxx', 'yyyy') THEN 0 ELSE 1 END,
        ab.card_id
    `;
    const [rows] = await db.execute(query, [userId]);
    
    return rows.map(row => {
      // 虚拟账户（xxxx=现金，yyyy=余额）
      if (VIRTUAL_NAMES[row.card_id]) {
        return {
          ...row,
          alias: VIRTUAL_NAMES[row.card_id].alias || row.card_id,
          card_type: 'virtual',
        };
      }
      return {
        ...row,
        alias: row.card_alias || row.card_id,
      };
    });
  }

  /**
   * 确保虚拟账户已初始化
   * xxxx = 现金，yyyy = 余额（微信/支付宝）
   */
  static async ensureVirtualAccounts(userId) {
    for (const [type, cardId] of Object.entries(VIRTUAL_TYPES)) {
      const existing = await AccountSettlement.getBalanceSnapshot(cardId, userId);
      if (!existing) {
        const balanceInfo = await AccountSettlement.calculateBalance(cardId, userId);
        await AccountBalanceController.createSnapshot(cardId, userId, balanceInfo.balance);
      }
    }
  }

  /**
   * 动态校验并同步余额
   * 1. 清理已删除的卡片关联的余额记录
   * 2. 重新计算所有余额
   */
  static async verifyAndSync(userId) {
    const now = String(Date.now());
    
    // 1. 软删除 account_balance 中已失效的卡片记录（卡片已删除或不是储蓄卡）
    const balanceQuery = `
      SELECT DISTINCT ab.card_id, ab.id
      FROM account_balance ab
      WHERE ab.user_id = ? AND ab.is_deleted = 0
        AND ab.card_id NOT IN ('xxxx', 'yyyy')
    `;
    const [balances] = await db.execute(balanceQuery, [userId]);
    
    for (const item of balances) {
      // 检查卡片是否还存在且是储蓄卡
      const [cardRows] = await db.execute(
        'SELECT id FROM card_base WHERE id = ? AND user_id = ? AND card_type = ? AND is_deleted = 0',
        [item.card_id, userId, 'debit']
      );
      
      if (cardRows.length === 0) {
        await db.execute(
          'UPDATE account_balance SET is_deleted = 1, update_time = ? WHERE id = ?',
          [now, item.id]
        );
        console.log(`[校验] 删除无效余额记录: ${item.card_id}`);
      }
    }
    
    // 2. 重新计算并同步所有有效卡片的余额
    const validBalanceQuery = `
      SELECT DISTINCT card_id
      FROM account_balance
      WHERE user_id = ? AND is_deleted = 0 AND card_id NOT IN ('xxxx', 'yyyy')
    `;
    const [validBalances] = await db.execute(validBalanceQuery, [userId]);
    
    for (const item of validBalances) {
      const balanceInfo = await AccountSettlement.calculateBalance(item.card_id, userId);
      await db.execute(
        'UPDATE account_balance SET balance = ?, update_time = ? WHERE card_id = ? AND user_id = ?',
        [balanceInfo.balance, now, item.card_id, userId]
      );
    }
    
    // 3. 同步虚拟账户余额
    for (const [type, cardId] of Object.entries(VIRTUAL_TYPES)) {
      const balanceInfo = await AccountSettlement.calculateBalance(cardId, userId);
      await db.execute(
        'UPDATE account_balance SET balance = ?, update_time = ? WHERE card_id = ? AND user_id = ?',
        [balanceInfo.balance, now, cardId, userId]
      );
    }
    
    console.log(`[校验] 用户 ${userId} 余额同步完成`);
  }

  /**
   * 初始化所有储蓄卡账户的余额
   */
  static async initAllCardBalances(userId) {
    const query = `
      SELECT id FROM card_base 
      WHERE user_id = ? AND card_type = 'debit' AND is_deleted = 0
    `;
    const [cards] = await db.execute(query, [userId]);

    for (const card of cards) {
      const existing = await AccountSettlement.getBalanceSnapshot(card.id, userId);
      if (!existing) {
        const balanceInfo = await AccountSettlement.calculateBalance(card.id, userId);
        await AccountBalanceController.createSnapshot(card.id, userId, balanceInfo.balance);
      }
    }
  }

  /**
   * 创建余额快照（使用 ON DUPLICATE KEY UPDATE 防止重复）
   */
  static async createSnapshot(cardId, userId, balance) {
    const id = idUtils.billId();
    const now = String(Date.now());
    const query = `
      INSERT INTO account_balance (id, user_id, card_id, balance, update_time, is_deleted)
      VALUES (?, ?, ?, ?, ?, 0)
      ON DUPLICATE KEY UPDATE
        balance = VALUES(balance),
        update_time = VALUES(update_time)
    `;
    await db.execute(query, [id, userId, cardId, balance, now]);
  }

  /**
   * 获取单张卡/账户余额
   */
  static async getByCardId(req, res) {
    if (!req.params.cardId) return res.say("卡片ID不能为空", 400);
    try {
      const cardId = req.params.cardId;
      let balance = await AccountSettlement.getBalanceSnapshot(cardId, req.userId);

      if (!balance) {
        const balanceInfo = await AccountSettlement.calculateBalance(cardId, req.userId);
        await AccountBalanceController.createSnapshot(cardId, req.userId, balanceInfo.balance);
        balance = await AccountSettlement.getBalanceSnapshot(cardId, req.userId);
      }
      return res.json({ status: 200, message: "获取成功", data: balance });
    } catch (error) {
      console.error('获取账户余额错误:', error);
      return res.say("获取失败", 500);
    }
  }

  /**
   * 初始化所有账户
   */
  static async initVirtual(req, res) {
    try {
      await AccountBalanceController.ensureVirtualAccounts(req.userId);
      await AccountBalanceController.initAllCardBalances(req.userId);
      const balances = await AccountBalanceController.getSnapshotList(req.userId);
      return res.json({ status: 200, message: "初始化成功", data: balances });
    } catch (error) {
      console.error('初始化账户错误:', error);
      return res.say("初始化失败", 500);
    }
  }

  /**
   * 重建所有账户余额
   */
  static async rebuild(req, res) {
    try {
      // 重建虚拟账户
      await AccountBalanceController.ensureVirtualAccounts(req.userId);
      
      // 重建储蓄卡
      await AccountBalanceController.initAllCardBalances(req.userId);

      // 从收支表重建余额
      const allBalances = await AccountSettlement.getAllBalances(req.userId);
      for (const item of allBalances) {
        const existing = await AccountSettlement.getBalanceSnapshot(item.card_id, req.userId);
        if (existing) {
          await db.execute(
            `UPDATE account_balance SET balance = ?, update_time = ? WHERE card_id = ? AND user_id = ?`,
            [item.balance, String(Date.now()), item.card_id, req.userId]
          );
        } else {
          await AccountBalanceController.createSnapshot(item.card_id, req.userId, item.balance);
        }
      }

      const result = await AccountBalanceController.getSnapshotList(req.userId);
      return res.json({ status: 200, message: "重建成功", data: result });
    } catch (error) {
      console.error('重建余额错误:', error);
      return res.say("重建失败", 500);
    }
  }
}

module.exports = AccountBalanceController;
