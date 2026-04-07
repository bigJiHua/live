const db = require('../config/db');

/**
 * 资产模型 - 用于计算预估总资产
 * 包含: asset(流动资产), fixed_asset(固定资产), fund(理财), card_bill(信用卡负债)
 */
class Asset {
  static tableAsset = 'asset';
  static tableFixed = 'fixed_asset';
  static tableFund = 'fund';
  static tableCardBill = 'card_bill';
  static tableCard = 'card_base';

  /**
   * 获取用户所有资产汇总
   */
  static async getTotalAssets(userId) {
    // 并行获取所有资产数据
    const [liquidAssets, fixedAssets, funds, cardBills, cards] = await Promise.all([
      this.getLiquidAssets(userId),      // 流动资产/负债
      this.getFixedAssets(userId),       // 固定资产
      this.getFunds(userId),             // 理财基金
      this.getCardBills(userId),         // 信用卡账单
      this.getCards(userId)              // 银行卡列表
    ]);

    // 计算总资产 = 流动资产 + 固定资产 + 理财市值
    const totalAssets = liquidAssets.total + fixedAssets.total + funds.total;
    
    // 计算总负债 = 信用卡已用额度
    const totalLiabilities = cardBills.totalUsed;

    // 净资产 = 总资产 - 总负债
    const netAssets = totalAssets - totalLiabilities;

    return {
      totalAssets: Math.round(totalAssets * 100) / 100,
      totalLiabilities: Math.round(totalLiabilities * 100) / 100,
      netAssets: Math.round(netAssets * 100) / 100,
      breakdown: {
        liquid: liquidAssets,
        fixed: fixedAssets,
        fund: funds,
        cardBill: cardBills,
        cards
      }
    };
  }

  /**
   * 获取流动资产/负债 (asset表)
   * type: 资产(增加净资产) / 负债(减少净资产)
   */
  static async getLiquidAssets(userId) {
    const query = `
      SELECT 
        id, name, amount, type,
        create_time, update_time
      FROM ${this.tableAsset}
      WHERE user_id = ? AND is_deleted = 0
    `;
    const [rows] = await db.execute(query, [userId]);

    let total = 0;
    const items = [];

    rows.forEach(row => {
      const amount = parseFloat(row.amount) || 0;
      if (row.type === '资产') {
        total += amount;
      } else if (row.type === '负债') {
        total -= amount;
      }
      items.push({
        id: row.id,
        name: row.name,
        amount,
        type: row.type
      });
    });

    return { total, items, count: items.length };
  }

  /**
   * 获取固定资产 (fixed_asset表)
   */
  static async getFixedAssets(userId) {
    const query = `
      SELECT 
        id, info as name, tag, img_url,
        buy_price, now_val as current_value,
        use_year, month_deprec, total_deprec,
        status, buy_date, residual_val
      FROM ${this.tableFixed}
      WHERE user_id = ? AND is_deleted = 0
    `;
    const [rows] = await db.execute(query, [userId]);

    let total = 0;
    const items = [];

    rows.forEach(row => {
      const value = parseFloat(row.current_value) || 0;
      total += value;
      items.push({
        id: row.id,
        name: row.name,
        tag: row.tag,
        imgUrl: row.img_url,
        buyPrice: parseFloat(row.buy_price) || 0,
        currentValue: value,
        useYear: row.use_year,
        monthDeprec: parseFloat(row.month_deprec) || 0,
        totalDeprec: parseFloat(row.total_deprec) || 0,
        status: row.status,
        buyDate: row.buy_date,
        residualVal: parseFloat(row.residual_val) || 0
      });
    });

    return { total, items, count: items.length };
  }

  /**
   * 获取理财基金 (fund表)
   */
  static async getFunds(userId) {
    const query = `
      SELECT 
        id, fund_name, share, fund_account,
        trade_account, sell_org, fund_company,
        buy_date, last_report_date, net_value,
        market_val, rate
      FROM ${this.tableFund}
      WHERE user_id = ? AND is_deleted = 0
    `;
    const [rows] = await db.execute(query, [userId]);

    let total = 0;
    const items = [];

    rows.forEach(row => {
      const marketVal = parseFloat(row.market_val) || 0;
      total += marketVal;
      items.push({
        id: row.id,
        fundName: row.fund_name,
        share: parseFloat(row.share) || 0,
        fundAccount: row.fund_account,
        tradeAccount: row.trade_account,
        sellOrg: row.sell_org,
        fundCompany: row.fund_company,
        buyDate: row.buy_date,
        lastReportDate: row.last_report_date,
        netValue: parseFloat(row.net_value) || 0,
        marketValue: marketVal,
        rate: row.rate
      });
    });

    return { total, items, count: items.length };
  }

  /**
   * 获取信用卡账单汇总 (card_bill表)
   */
  static async getCardBills(userId) {
    // 先获取所有卡片
    const cardQuery = `
      SELECT id, alias, last4_no, card_type, bank_id
      FROM ${this.tableCard}
      WHERE user_id = ? AND is_deleted = 0 AND card_type = '信用卡'
    `;
    const [cards] = await db.execute(cardQuery, [userId]);

    let totalUsed = 0;
    let totalNeedRepay = 0;
    const items = [];

    for (const card of cards) {
      const billQuery = `
        SELECT 
          credit_limit, avail_limit, used_limit,
          bill_amount, min_repay, repaid, need_repay,
          repay_status, is_overdue, overdue_days
        FROM ${this.tableCardBill}
        WHERE card_id = ? AND is_deleted = 0
        ORDER BY update_time DESC
        LIMIT 1
      `;
      const [bills] = await db.execute(billQuery, [card.id]);
      
      if (bills.length > 0) {
        const bill = bills[0];
        const usedLimit = parseFloat(bill.used_limit) || 0;
        const needRepay = parseFloat(bill.need_repay) || 0;
        totalUsed += usedLimit;
        totalNeedRepay += needRepay;
        
        items.push({
          cardId: card.id,
          cardAlias: card.alias,
          cardLast4: card.last4_no,
          creditLimit: parseFloat(bill.credit_limit) || 0,
          usedLimit,
          availLimit: parseFloat(bill.avail_limit) || 0,
          billAmount: parseFloat(bill.bill_amount) || 0,
          minRepay: parseFloat(bill.min_repay) || 0,
          repaid: parseFloat(bill.repaid) || 0,
          needRepay,
          repayStatus: bill.repay_status,
          isOverdue: bill.is_overdue,
          overdueDays: bill.overdue_days
        });
      }
    }

    return { totalUsed, totalNeedRepay, items, count: items.length };
  }

  /**
   * 获取银行卡列表 (card_base表)
   */
  static async getCards(userId) {
    const query = `
      SELECT 
        id, bank_id, card_type, card_level,
        card_org, last4_no, mask_no, alias,
        card_img, open_date, expire_date,
        bill_day, repay_day, currency, status,
        is_default, is_hide, tag, remark
      FROM ${this.tableCard}
      WHERE user_id = ? AND is_deleted = 0
      ORDER BY is_default DESC, sort ASC
    `;
    const [rows] = await db.execute(query, [userId]);

    return rows.map(row => ({
      id: row.id,
      bankId: row.bank_id,
      cardType: row.card_type,
      cardLevel: row.card_level,
      cardOrg: row.card_org,
      last4No: row.last4_no,
      maskNo: row.mask_no,
      alias: row.alias,
      cardImg: row.card_img,
      openDate: row.open_date,
      expireDate: row.expire_date,
      billDay: row.bill_day,
      repayDay: row.repay_day,
      currency: row.currency,
      status: row.status,
      isDefault: row.is_default,
      isHide: row.is_hide,
      tag: row.tag,
      remark: row.remark
    }));
  }
}

module.exports = Asset;
