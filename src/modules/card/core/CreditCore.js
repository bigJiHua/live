/**
 * CreditCore —— 信用卡核心计算层（唯一真相源）
 * =========================================================================
 * 职责：
 *   独占信用卡「额度 / 已用 / 可用 / 已还 / 待还 / 溢缴款 / 积分 / 逾期」的全部派生计算。
 *   所有 controller / model（credit.js、repay.js、billController、AccountSettlement）
 *   只能【调用】本类方法，严禁在外部自行拼接额度公式。
 *
 * 设计红线（用户明确，禁止改动）：
 *   1. 账单日(bill_day) / 还款日(repay_day) 的【推导逻辑】必须复用 CardBill 的周期函数，
 *      不得重写（getBillMonthByDate / calculateBillPeriod / calculateRepayDate）。
 *   2. 汇率语义不得改动：外币→人民币一律走 toCNY(amount, currency, exchangeRate)，
 *      即「每100外币等值人民币」口径，公式固定 amount*exchange_rate/100。
 *
 * 相对旧逻辑的修正（见 信用卡系统机制分析.md §5）：
 *   A. 旧 repay.js 末尾 syncFromRepay 调用参数整体错位（no-op）→ 这里彻底废除增量同步，
 *      改为每次从账本(account + card_repay)全量重算，不存在错位/漂移。
 *   B. 旧 repay.js 计算 avail 不含临时额度 → 这里 avail = credit + temp - (spent - repaid)。
 *   C. 旧 used_limit 三套口径互斥（syncFromExpense 加消费 / executeRepay 减还款 /
 *      rebuild 仅消费汇总）→ 统一为：spent=账本消费汇总，avail=credit+temp-(spent-repaid)，
 *      还款【恢复】可用额度（标准信用卡语义）。
 *   D. 旧 rebuild 的 avail 不反映还款 → 同上修正；溢缴款(overpayment)作为一级概念建模：
 *      netOwed = spent - repaid；needRepay = max(0, netOwed)；
 *      overflow(溢缴款) = max(0, -netOwed)，自然体现于 avail(>credit+temp)。
 *
 * 扩展点（Phase 2，本次未实现数据表，仅留接口）：
 *   - #resolveLimit：当前直接读 card_base.credit_limit/temp_limit。
 *     共享额度池落地后，此处改为读取「同银行共享池」的额度即可，调用方无需改动。
 *   - 外币消费登记入账：当前仍按 toCNY 即时入账（与旧行为一致，不回归）；
 *     专用登记页 + 对账表落地后，spent 计算可排除「未对账外币流水」、改用「已对账人民币」。
 * =========================================================================
 */

const db = require('../../../common/config/db');
const idUtils = require('../../../common/utils/idUtils');
const { toCNY } = require('../../../common/utils/currency');
// 延迟加载，避免与 ../model 形成循环依赖（model/index.js 也 require 本类）
const CardBill = require('../model/bill');
const CreditPool = require('../model/pool');
const ForeignRegister = require('../model/foreignRegister');

let _Card = null;
const getCard = () => {
  if (!_Card) _Card = require('../model');
  return _Card;
};

const DAY_MS = 1000 * 60 * 60 * 24;

class CreditCore {
  /**
   * 同步一张卡的全部账单（从账本全量重算后 upsert）。
   * 替代旧：CardBill.syncFromExpense / rollbackExpense / rebuildBillFromAccount /
   *        CardRepay.executeRepay 内的手写 UPDATE + 错位 syncFromRepay。
   * @param {string} cardId
   * @param {string} userId
   * @param {object} [conn] 事务连接（可选）；不传则用默认连接
   * @returns {Promise<Array>} 各账单快照
   */
  static async syncCardBills(cardId, userId, conn) {
    const executor = conn || db;
    const card = await getCard().findById(cardId, userId);
    if (!card || card.card_type !== 'credit') return null;

    // 共享池卡：整池原子重算（H1 审计）。池内任何一张卡发生交易/还款/改额，
    // 池内所有卡快照必须同步刷新，否则其他卡 avail_limit 会落后一笔。
    // #syncOne 本身不扩散，避免递归。
    if (card.share_pool_id) {
      const [poolCards] = await executor.execute(
        `SELECT id FROM card_base WHERE share_pool_id = ? AND user_id = ? AND is_deleted = 0`,
        [card.share_pool_id, userId]
      );
      const all = [];
      for (const c of poolCards) {
        const s = await this.#syncOne(c.id, userId, executor);
        if (s) all.push(...s);
      }
      return all;
    }
    return this.#syncOne(cardId, userId, executor);
  }

  /** 单卡重算（不扩散到池），供 syncCardBills 池内循环复用 */
  static async #syncOne(cardId, userId, executor) {
    const card = await getCard().findById(cardId, userId);
    if (!card || card.card_type !== 'credit') return null;

    const months = await this.#computeMonths(card, userId, executor);
    const now = String(Date.now());
    const snapshots = [];
    // R2：快照冗余列 bill_day/repay_day/points_rate 需与 card_base 保持一致（改卡后刷新），
    //     points_rate 列是 decimal(10,0) 整数，写入前取整避免严格模式截断报错
    const pointsRateWrite = Math.round(parseFloat(card.points_rate) || 1);

    for (const m of months) {
      const existing = await CardBill.findByCardAndMonth(cardId, m.billMonth, executor);
      if (existing) {
        await executor.execute(
          `UPDATE card_bill SET
             bill_day = ?, repay_day = ?, points_rate = ?,
             credit_limit = ?, temp_limit = ?, used_limit = ?, avail_limit = ?, bill_amount = ?,
             repaid = ?, need_repay = ?, repay_status = ?, is_overdue = ?, overdue_days = ?,
             points = ?, min_repay = ?, bill_start_date = ?, bill_end_date = ?, update_time = ?
           WHERE id = ? AND is_deleted = 0`,
          [
            card.bill_day, card.repay_day, pointsRateWrite,
            m.creditLimit, m.tempLimit, m.usedLimit, m.availLimit, m.billAmount,
            m.repaid, m.needRepay, m.repayStatus, m.isOverdue ? 1 : 0, m.overdueDays,
            m.points, m.minRepay, m.billStartDate, m.billEndDate, now, existing.id
          ]
        );
        snapshots.push({ billMonth: m.billMonth, billId: existing.id, ...m.core });
      } else {
        const id = idUtils.billId();
        await executor.execute(
          `INSERT INTO card_bill
             (id, user_id, card_id, bill_month, bill_day, repay_day, credit_limit, temp_limit,
              used_limit, avail_limit, bill_amount, repaid, need_repay, repay_status,
              is_overdue, overdue_days, points, points_rate, min_repay, bill_start_date, bill_end_date,
              remind_switch, remind_days, create_time, update_time, is_deleted)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 3, ?, ?, 0)`,
          [
            id, userId, cardId, m.billMonth, card.bill_day, card.repay_day,
            m.creditLimit, m.tempLimit, m.usedLimit, m.availLimit, m.billAmount,
            m.repaid, m.needRepay, m.repayStatus, m.isOverdue ? 1 : 0, m.overdueDays,
            m.points, pointsRateWrite, m.minRepay, m.billStartDate, m.billEndDate, now, now
          ]
        );
        snapshots.push({ billMonth: m.billMonth, billId: id, ...m.core });
      }
    }

    return snapshots;
  }

  /**
   * 按交易日期触发同步（语义上对应旧 syncFromExpense）。内部即全量重算，幂等。
   */
  static async syncByDate(cardId, userId, transDate, conn) {
    return this.syncCardBills(cardId, userId, conn);
  }

  /**
   * 按账单触发同步（语义上对应旧 syncFromRepay / rebuild）。
   */
  static async syncByBill(cardId, userId, billId, conn) {
    return this.syncCardBills(cardId, userId, conn);
  }

  /**
   * 汇总用户全部信用卡的额度/负债（供信用卡中心、总负债统计、getAllStats 使用）。
   * 只读计算，不写库。
   * @returns {Promise<{totalCreditLimit,totalTempLimit,totalDebt,totalAvail,totalOverflow,cards}>}
   */
  static async aggregate(userId, conn) {
    const executor = conn || db;
    const cards = await getCard().findAll(userId, { cardType: 'credit' });

    // 分组：归入同一共享池的卡合并为一组；无池的卡各自一组（避免共享额度重复累加）
    const groups = new Map();
    for (const card of cards) {
      const key = card.share_pool_id ? `p_${card.share_pool_id}` : `c_${card.id}`;
      if (!groups.has(key)) groups.set(key, { poolId: card.share_pool_id || null, cards: [] });
      groups.get(key).cards.push(card);
    }

    let totalCreditLimit = 0;
    let totalTempLimit = 0;
    let totalDebt = 0;
    let totalAvail = 0;
    let totalOverflow = 0;
    const out = [];

    for (const [key, g] of groups) {
      if (g.poolId) {
        // —— 共享池：以池额度为准，负债 = 池内全部卡 netOwed 之和 ——
        const pool = await CreditPool.findById(g.poolId, userId);
        // 池被删除/找不到时，回退到组内卡片自身额度之和（P6 审计：不可按0导致负可用额度）
        const fallbackCredit = g.cards.reduce((s, c) => s + (parseFloat(c.credit_limit) || 0), 0);
        const fallbackTemp = g.cards.reduce((s, c) => s + (parseFloat(c.temp_limit) || 0), 0);
        const poolCredit = pool ? parseFloat(pool.total_credit_limit) || 0 : fallbackCredit;
        const poolTemp = pool ? parseFloat(pool.total_temp_limit) || 0 : fallbackTemp;
        const poolNet = await this.#poolNetOwed(g.poolId, userId, executor);
        const poolPending = await this.#poolPendingReserved(g.poolId, userId, executor);
        const debt = Math.max(0, poolNet);
        const avail = poolCredit + poolTemp - poolNet - poolPending;
        const overflow = await this.#poolOverflow(g.poolId, userId, executor);
        totalCreditLimit += poolCredit;
        totalTempLimit += poolTemp;
        totalDebt += debt;
        totalAvail += avail;
        totalOverflow += overflow;
        out.push({
          poolId: g.poolId,
          bankName: pool ? pool.bank_name : null,
          shared: true,
          creditLimit: poolCredit,
          tempLimit: poolTemp,
          debt,
          avail,
          overflow,
          cards: g.cards.map((c) => ({ cardId: c.id, alias: c.alias }))
        });
      } else {
        // —— 独立卡 ——
        const card = g.cards[0];
        const months = await this.#computeMonths(card, userId, executor);
        const cardCredit = parseFloat(card.credit_limit) || 0;
        const cardTemp = parseFloat(card.temp_limit) || 0;
        let totalNet = 0;
        let overflow = 0;
        for (const m of months) {
          totalNet += m.usedLimitOfNet; // 各账期增量净负债之和 = 累计消费 − 累计还款（可为负）
          overflow = m.overflow; // H7：累计溢缴余额取最后一月，跨月不重复累计
        }
        // R1 审计：真实待还 = max(0, 累计净负债)，不能用 Σ needRepay。
        // 跨期溢缴场景（1月消费1000还300、2月消费500还900）Σ needRepay=700，但真实负债=300
        // （2月的400溢缴应优先抵1月欠款），Σ 会虚高且与 avail 口径不一致。
        const debt = Math.max(0, totalNet);
        // 可用额度 = 总额度 − 全部账期净负债之和 − pending 外币占用（与快照 availLimit 口径一致）
        const pendingReserved = await this.#pendingReserved(card.id, userId, executor);
        const avail = cardCredit + cardTemp - totalNet - pendingReserved;
        totalCreditLimit += cardCredit;
        totalTempLimit += cardTemp;
        totalDebt += debt;
        totalAvail += avail;
        totalOverflow += overflow;
        out.push({
          cardId: card.id,
          alias: card.alias,
          shared: false,
          creditLimit: cardCredit,
          tempLimit: cardTemp,
          debt,
          avail,
          overflow
        });
      }
    }

    return {
      totalCreditLimit,
      totalTempLimit,
      totalDebt,
      totalAvail,
      totalOverflow,
      cards: out
    };
  }

  /**
   * 外币消费登记/对账（痛点4）——独立修改实际汇率/人民币的登记方法。
   * 录入银行 App 实际结算汇率/人民币后，重算该卡账单（已对账外币才计入）。
   * @param {string} registerId
   * @param {string} userId
   * @param {{actualRate:number,actualRmb?:number,settleDate?:string,remark?:string}} data
   */
  static async reconcileForeign(registerId, userId, { actualRate, actualRmb, settleDate, remark }) {
    // H4 审计：登记对账与账单重算必须同一事务，避免「对账成功但重算失败/反之」的半完成态
    const conn = await db.getConnection();
    let reg;
    try {
      await conn.beginTransaction();
      reg = await ForeignRegister.reconcile(registerId, userId, {
        actualRate,
        actualRmb,
        settleDate,
        remark
      }, conn);
      await this.syncCardBills(reg.card_id, userId, conn);
      await conn.commit();
    } catch (e) {
      await conn.rollback();
      throw e;
    } finally {
      conn.release();
    }
    return reg;
  }

  /** 用户维度全部待对账外币（供专用登记页） */
  static async getPendingRegisters(userId) {
    return ForeignRegister.findPendingByUser(userId);
  }

  /** 用户维度全部外币登记（含已对账） */
  static async getForeignRegisters(userId) {
    return ForeignRegister.findAllByUser(userId);
  }

  // =========================================================================
  // 私有：额度真相源解析（共享池扩展点）
  // =========================================================================
  static async #resolveLimit(card, userId, executor) {
    // 痛点2：若卡片归属「同银行共享额度池」，额度真相源改为读取池
    if (card.share_pool_id) {
      const [rows] = await executor.execute(
        `SELECT total_credit_limit, total_temp_limit
         FROM card_credit_pool
         WHERE id = ? AND user_id = ? AND is_deleted = 0`,
        [card.share_pool_id, userId]
      );
      if (rows[0]) {
        return {
          creditLimit: parseFloat(rows[0].total_credit_limit) || 0,
          tempLimit: parseFloat(rows[0].total_temp_limit) || 0,
          pooled: true
        };
      }
    }
    return {
      creditLimit: parseFloat(card.credit_limit) || 0,
      tempLimit: parseFloat(card.temp_limit) || 0,
      pooled: false
    };
  }

  /**
   * 共享池全部卡的净负债之和（账本实时计算，H1 审计）。
   * ⚠️ 不能读 card_bill 快照：某卡重算时其他池内卡快照尚未刷新，会算出旧负债。
   * 口径与 #computeMonths 一致：
   *   - 消费：direction=0 且非 CATEGORY_REPAY，按 toCNY 折算（每100外币=人民币）
   *   - 还款：card_repay 全量（软删来源流水对应记录的还款不计，因会被冲正）
   *   - pending 外币消费不计入（与单卡口径一致，单独由 #poolPendingReserved 占用额度）
   */
  static async #poolNetOwed(poolId, userId, executor) {
    const [rows] = await executor.execute(
      `SELECT COALESCE(SUM(t.net), 0) AS net FROM (
         SELECT COALESCE(fr.actual_rmb,
           a.amount * CASE WHEN a.currency = 'CNY' OR a.currency IS NULL THEN 1 ELSE a.exchange_rate / 100 END) AS net
         FROM account a
         JOIN card_base c ON a.card_id = c.id
         LEFT JOIN card_foreign_register fr ON fr.account_id = a.id AND fr.is_deleted = 0
         WHERE c.share_pool_id = ? AND c.user_id = ? AND a.is_deleted = 0 AND c.is_deleted = 0
           AND a.direction = 0 AND a.reversed_id IS NULL
           AND (a.category_id IS NULL OR a.category_id != 'CATEGORY_REPAY')
           AND (fr.status IS NULL OR fr.status != 'pending')
         UNION ALL
         SELECT -r.repay_amount AS net
         FROM card_repay r
         JOIN card_base c ON r.card_id = c.id
         WHERE c.share_pool_id = ? AND c.user_id = ? AND r.is_deleted = 0
           AND (r.account_id IS NULL OR NOT EXISTS (
             SELECT 1 FROM account a2 WHERE a2.id = r.account_id AND a2.is_deleted = 1
           ))
       ) t`,
      [poolId, userId, poolId, userId]
    );
    return parseFloat(rows[0].net) || 0;
  }

  /** 共享池溢缴款（= max(0, 池内还款 − 池内消费)，账本实时口径，与单卡一致） */
  static async #poolOverflow(poolId, userId, executor) {
    const net = await this.#poolNetOwed(poolId, userId, executor);
    return Math.max(0, -net);
  }

  /** 单卡 pending 外币登记占用额度之和（按登记汇率折算人民币） */
  static async #pendingReserved(cardId, userId, executor) {
    const [rows] = await executor.execute(
      `SELECT COALESCE(SUM(registered_rmb), 0) AS total
       FROM card_foreign_register
       WHERE card_id = ? AND user_id = ? AND status = 'pending' AND is_deleted = 0`,
      [cardId, userId]
    );
    return parseFloat(rows[0].total) || 0;
  }

  /** 共享池内全部卡 pending 外币登记占用额度之和 */
  static async #poolPendingReserved(poolId, userId, executor) {
    const [rows] = await executor.execute(
      `SELECT COALESCE(SUM(fr.registered_rmb), 0) AS total
       FROM card_foreign_register fr
       JOIN card_base c ON fr.card_id = c.id
       WHERE c.share_pool_id = ? AND c.user_id = ? AND fr.status = 'pending' AND fr.is_deleted = 0`,
      [poolId, userId]
    );
    return parseFloat(rows[0].total) || 0;
  }

  /** 载入某卡全部外币登记（按 account_id 建索引） */
  static async #loadForeignRegisters(cardId, userId, executor) {
    const [rows] = await executor.execute(
      `SELECT * FROM card_foreign_register
       WHERE card_id = ? AND user_id = ? AND is_deleted = 0`,
      [cardId, userId]
    );
    return rows;
  }

  // =========================================================================
  // 私有：纯计算，从账本推导每张账单的快照（不写库）
  // =========================================================================
  static async #computeMonths(card, userId, executor) {
    const billDay = Number(card.bill_day) || 15;
    const repayDay = Number(card.repay_day) || 25;
    const pointsRate = parseFloat(card.points_rate) || 1;
    const { creditLimit, tempLimit, pooled } = await this.#resolveLimit(card, userId, executor);

    // 1. 消费流水（account 表，direction=0，非还款分类）
    const [expenseRows] = await executor.execute(
      `SELECT id, amount, currency, exchange_rate, trans_date, reversed_id
       FROM account
       WHERE card_id = ? AND user_id = ? AND is_deleted = 0
         AND direction = 0 AND (category_id IS NULL OR category_id != 'CATEGORY_REPAY')`,
      [card.id, userId]
    );

    // 2. 已冲正的消费 ID（避免重复计入）
    const [reversalFlows] = await executor.execute(
      `SELECT reversed_id FROM account
       WHERE card_id = ? AND user_id = ? AND is_deleted = 0
         AND reversed_id IS NOT NULL AND direction = 1`,
      [card.id, userId]
    );
    const reversedExpenseIds = new Set(reversalFlows.map(r => r.reversed_id));

    // 载入该卡外币登记（痛点4）：已对账用 actual_rmb；pending 不计入账单
    const regRows = await this.#loadForeignRegisters(card.id, userId, executor);
    const regByAccount = new Map();
    for (const r of regRows) regByAccount.set(r.account_id, r);

    const spentByMonth = {};
    for (const row of expenseRows) {
      if (row.reversed_id || reversedExpenseIds.has(row.id)) continue;
      let amt;
      if (row.currency === 'CNY' || !row.currency) {
        amt = toCNY(row.amount, row.currency, row.exchange_rate);
      } else {
        const reg = regByAccount.get(row.id);
        if (reg && reg.status === 'reconciled') {
          amt = parseFloat(reg.actual_rmb) || 0; // 用银行实际结算人民币入账
        } else if (reg && reg.status === 'pending') {
          continue; // 未对账：不计入账单，避免用登记汇率粗暴入账
        } else {
          amt = toCNY(row.amount, row.currency, row.exchange_rate); // 历史无登记，回退
        }
      }
      if (amt <= 0) continue;
      const bm = CardBill.getBillMonthByDate(row.trans_date, billDay);
      spentByMonth[bm] = (spentByMonth[bm] || 0) + amt;
    }

    // 3. 还款流水（card_repay 表，关联到账单月）
    const [repayRows] = await executor.execute(
      `SELECT r.repay_amount, COALESCE(r.bill_month, b.bill_month) AS target_bill_month
       FROM card_repay r
       LEFT JOIN card_bill b ON r.bill_id = b.id AND b.is_deleted = 0
       LEFT JOIN account a ON r.account_id = a.id
       WHERE r.card_id = ? AND r.user_id = ? AND r.is_deleted = 0
         AND (r.account_id IS NULL OR a.is_deleted = 0)`,
      [card.id, userId]
    );
    const repaidRawByMonth = {};
    const currentBillMonth = CardBill.getCurrentBillMonth();
    for (const r of repayRows) {
      const bm = r.target_bill_month || r.bill_month || currentBillMonth;
      const amt = parseFloat(r.repay_amount) || 0;
      if (amt <= 0) continue;
      repaidRawByMonth[bm] = (repaidRawByMonth[bm] || 0) + amt;
    }

    // 4. 按月聚合（H7 溢缴款模型：全局累计净负债，netOwed 可为负，avail 反映溢缴）
    // 纳入已有账单的月份：即使本期已无流水，也要保留并归零，避免脏账单残留
    const [existingBills] = await executor.execute(
      `SELECT bill_month FROM card_bill WHERE card_id = ? AND user_id = ? AND is_deleted = 0`,
      [card.id, userId]
    );
    const monthSet = new Set([
      ...Object.keys(spentByMonth),
      ...Object.keys(repaidRawByMonth),
      ...existingBills.map(b => b.bill_month)
    ]);
    const allMonths = Array.from(monthSet).sort();

    // H3 审计：pending 外币消费按登记汇率折算人民币，占用可用额度（不释放），
    // 但 bill_amount 仍不含（待对账后按实际结算入账）。
    const pendingReserved = await this.#pendingReserved(card.id, userId, executor);

    const result = [];
    let carryOver = 0; // 溢缴款顺延
    let cumulativeExpense = 0; // 跨月累计消费
    let cumulativeRepaid = 0;  // 跨月累计还款
    const now = new Date();
    // M1 审计：日期级比较（还款日当天不算逾期，避免晚于零点即标逾期）
    const todayMidnight = new Date(now.getFullYear(), now.getMonth(), now.getDate());

    for (const billMonth of allMonths) {
      const totalExpense = spentByMonth[billMonth] || 0;
      const totalRepaidThisMonth = repaidRawByMonth[billMonth] || 0;

      cumulativeExpense += totalExpense;
      cumulativeRepaid += totalRepaidThisMonth;

      const availableRepaid = totalRepaidThisMonth + carryOver;
      const appliedToThisBill = Math.min(availableRepaid, totalExpense);
      // 本月溢缴（还款超过消费部分），顺延到后续月份
      const excessRepaid = Math.max(0, availableRepaid - totalExpense);
      carryOver = excessRepaid;

      const repaid = appliedToThisBill; // 快照：该账单实际吸收的还款（截断）
      const needRepay = Math.max(0, totalExpense - appliedToThisBill);
      // 该月增量净负债（不含顺延，供 aggregate 累加 Σ = 总消费 − 总还款）
      const netOwedIncrement = totalExpense - totalRepaidThisMonth;
      // 跨月累计净负债（含溢缴顺延；可为负 → 溢缴款提升可用额度）
      const cumulativeNet = cumulativeExpense - cumulativeRepaid;
      // 累计溢缴余额 = max(0, -累计净负债)，跨月不重复累计
      const overflow = Math.max(0, -cumulativeNet);

      const usedLimit = totalExpense;                 // 累计消费（不被还款减少）
      // 可用额度 = 总额度 − 累计净负债 − pending 外币占用；溢缴时 > credit+temp
      const availLimit = creditLimit + tempLimit - cumulativeNet - pendingReserved;
      const points = Math.round(totalExpense * pointsRate);
      const minRepay = Math.max(0, totalExpense * 0.1);

      const { billStartDate, billEndDate } = CardBill.calculateBillPeriod(billMonth, billDay);
      const repayDate = CardBill.calculateRepayDate(billMonth, repayDay);

      let repayStatus = '未还款';
      if (needRepay <= 0) repayStatus = '已还清';
      else if (repaid > 0) repayStatus = '部分还款';

      let isOverdue = false;
      let overdueDays = 0;
      if (needRepay > 0 && repayDate) {
        const due = new Date(`${repayDate}T00:00:00`);
        if (todayMidnight > due) {
          isOverdue = true;
          overdueDays = Math.ceil((todayMidnight - due) / DAY_MS);
        }
      }

      result.push({
        billMonth,
        creditLimit: Number(creditLimit.toFixed(4)),
        tempLimit: Number(tempLimit.toFixed(4)),
        usedLimit: Number(usedLimit.toFixed(4)),
        availLimit: Number(availLimit.toFixed(4)),
        billAmount: Number(totalExpense.toFixed(4)),
        repaid: Number(repaid.toFixed(4)),
        needRepay: Number(needRepay.toFixed(4)),
        overflow: Number(overflow.toFixed(4)),
        points,
        minRepay: Number(minRepay.toFixed(4)),
        repayStatus,
        isOverdue,
        overdueDays,
        billStartDate,
        billEndDate,
        // 供 aggregate 使用：该月增量净负债（Σ 后 = 总消费 − 总还款）
        usedLimitOfNet: Number(netOwedIncrement.toFixed(4)),
        core: {
          creditLimit: Number(creditLimit.toFixed(4)),
          tempLimit: Number(tempLimit.toFixed(4)),
          usedLimit: Number(usedLimit.toFixed(4)),
          availLimit: Number(availLimit.toFixed(4)),
          billAmount: Number(totalExpense.toFixed(4)),
          repaid: Number(repaid.toFixed(4)),
          needRepay: Number(needRepay.toFixed(4)),
          overflow: Number(overflow.toFixed(4)),
          points,
          repayStatus,
          isOverdue,
          overdueDays
        }
      });
    }

    // 共享池：可用额度 = 池总额度 − 池内全部卡净负债（账本实时，含 pending 占用），
    // 反映「两卡共享5000，A花4500剩500」。H1 审计：poolNet 从账本算，不再依赖快照先后。
    if (pooled) {
      const poolNet = await this.#poolNetOwed(card.share_pool_id, userId, executor);
      const poolPending = await this.#poolPendingReserved(card.share_pool_id, userId, executor);
      const sharedAvail = creditLimit + tempLimit - poolNet - poolPending;
      for (const m of result) {
        m.availLimit = Number(sharedAvail.toFixed(4));
        m.core.availLimit = Number(sharedAvail.toFixed(4));
      }
    }

    return result;
  }
}

module.exports = CreditCore;
