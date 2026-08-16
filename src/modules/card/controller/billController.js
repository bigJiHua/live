const CardBill = require("../model/bill");
const CreditCore = require("../core/CreditCore");
const CardLog = require("../model/log");
const Card = require("../model");
const ForeignRegister = require("../model/foreignRegister");

/**
 * 卡片账单控制器
 */
class CardBillController {
  /**
   * 获取账单列表
   */
  async getList(req, res) {
    try {
      const { cardId, billMonth } = req.query;
      const filters = {};

      if (cardId) filters.cardId = cardId;
      if (billMonth) filters.billMonth = billMonth;

      let bills = await CardBill.findAll(req.userId, filters);

      // 如果没有账单且指定了卡片，尝试自动重建
      if ((!bills || bills.length === 0) && cardId) {
        // [CreditCore] 统一重算（替代旧 rebuildBillFromAccount）
        await CreditCore.syncCardBills(cardId, req.userId);
        bills = await CardBill.findAll(req.userId, filters);
      }

      // 仍然没有，且没有指定卡片，尝试为所有信用卡重建
      if ((!bills || bills.length === 0) && !cardId) {
        const creditCards = await Card.findAll(req.userId, { cardType: 'credit' });
        for (const card of creditCards) {
          // [CreditCore] 统一重算
          await CreditCore.syncCardBills(card.id, req.userId);
        }
        bills = await CardBill.findAll(req.userId, filters);
      }

      if (!bills || bills.length === 0) {
        return res.status(200).send({
          status: 200,
          message: "暂无账单",
          data: [],
        });
      }
      return res.json({ status: 200, message: "获取成功", data: bills });
    } catch (error) {
      console.error("获取账单列表错误:", error);
      return res.say("获取失败", 500);
    }
  }

  /**
   * 获取单个账单详情
   */
  async getById(req, res) {
    if (!req.params.id) return res.say("账单id不能为空", 400);
    try {
      const bill = await CardBill.findById(req.params.id, req.userId);

      if (!bill) return res.say("账单不存在", 200);

      // 实时计算逾期状态（已还清/待还为0时不应显示逾期）
      if (bill.is_overdue_calc !== undefined) {
        bill.is_overdue = bill.is_overdue_calc;
        bill.overdue_days = bill.overdue_days_calc;
      }
      return res.json({ status: 200, message: "获取成功", data: bill });
    } catch (error) {
      console.error("获取账单详情错误:", error);
      return res.say("获取失败", 500);
    }
  }

  /**
   * 获取卡片最新账单
   */
  async getLatestByCardId(req, res) {
    if (!req.params.cardId) return res.say("卡片id不能为空", 400);
    try {
      const bill = await CardBill.findLatestByCardId(req.params.cardId, req.userId);

      if (!bill) {
        return res.status(200).send({
          status: 200,
          message: "暂无账单",
          data: null,
        });
      }
      return res.json({ status: 200, message: "获取成功", data: bill });
    } catch (error) {
      console.error("获取最新账单错误:", error);
      return res.say("获取失败", 500);
    }
  }

  /**
   * 创建账单
   */
  async create(req, res) {
    try {
      const bill = await CardBill.create({
        userId: req.userId,
        ...req.body.data,
      });

      if (!bill || !bill.id) {
        throw new Error('账单创建失败');
      }

      // 记录操作日志
      await CardLog.log(req.body.data.cardId, req.userId, "创建账单", req.ip);

      return res
        .status(200)
        .json({ status: 200, message: "创建成功", data: bill });
    } catch (error) {
      console.error("创建账单错误:", error);
      return res.say(error.message || "创建失败", 500);
    }
  }

  /**
   * 更新账单
   */
  async update(req, res) {
    try {
      const bill = await CardBill.update(
        req.params.id,
        req.userId,
        req.body.data
      );

      if (!bill) return res.say("账单不存在", 404);

      // 记录操作日志
      await CardLog.log(bill.card_id, req.userId, "更新账单", req.ip);

      return res.json({ status: 200, message: "更新成功", data: bill });
    } catch (error) {
      console.error("更新账单错误:", error);
      return res.say(error.message || "更新失败", 500);
    }
  }

  /**
   * 重建账单（从流水重新计算）
   */
  async rebuild(req, res) {
    if (!req.params.cardId) return res.say("卡片id不能为空", 400);
    try {
      // [CreditCore] 从账本统一重算所有账单
      const results = await CreditCore.syncCardBills(
        req.params.cardId,
        req.userId
      );
      // 获取重建后的最新账单列表
      const bills = await CardBill.findAll(req.userId, { cardId: req.params.cardId });

      // 记录操作日志
      await CardLog.log(req.params.cardId, req.userId, "重建账单", req.ip);

      return res.json({
        status: 200,
        message: `已重建 ${results ? results.length : 0} 个月账单`,
        data: bills,
      });
    } catch (error) {
      console.error("重建账单错误:", error);
      return res.say("重建失败", 500);
    }
  }

  /**
   * 批量重建所有信用卡账单（修复历史数据）
   */
  async rebuildAll(req, res) {
    try {
      const creditCards = await Card.findAll(req.userId, { cardType: 'credit' });
      const results = { total: 0, success: 0, failed: 0, cards: [] };

      for (const card of creditCards) {
        try {
          // [CreditCore] 统一重算
          const billData = await CreditCore.syncCardBills(card.id, req.userId);
          results.total++;
          if (billData) {
            results.success++;
            results.cards.push({ cardId: card.id, cardName: card.alias || card.last4_no, months: billData.length });
          }
        } catch (cardError) {
          results.failed++;
          console.error(`重建卡片 ${card.id} 失败:`, cardError.message);
        }
      }

      return res.json({
        status: 200,
        message: `批量重建完成：成功 ${results.success} 张, 失败 ${results.failed} 张`,
        data: results,
      });
    } catch (error) {
      console.error("批量重建错误:", error);
      return res.say("批量重建失败", 500);
    }
  }

  /**
   * 删除账单
   */
  async delete(req, res) {
    try {
      const bill = await CardBill.findById(req.params.id, req.userId);
      if (!bill) return res.say("账单不存在", 404);

      const result = await CardBill.delete(req.params.id, req.userId);

      if (!result) return res.say("账单不存在", 404);

      // 记录操作日志
      await CardLog.log(bill.card_id, req.userId, "删除账单", req.ip);

      return res.json({ status: 200, message: "删除成功" });
    } catch (error) {
      console.error("删除账单错误:", error);
      return res.say("删除失败", 500);
    }
  }

  // ===================== 外币消费登记/对账（痛点4） =====================

  /** 待对账外币列表（专用登记页） */
  async foreignPending(req, res) {
    try {
      const list = await CreditCore.getPendingRegisters(req.userId);
      return res.json({ status: 200, message: "获取成功", data: list });
    } catch (error) {
      console.error("获取待对账外币错误:", error);
      return res.say("获取失败", 500);
    }
  }

  /** 全部外币登记列表 */
  async foreignList(req, res) {
    try {
      const list = await CreditCore.getForeignRegisters(req.userId);
      return res.json({ status: 200, message: "获取成功", data: list });
    } catch (error) {
      console.error("获取外币登记错误:", error);
      return res.say("获取失败", 500);
    }
  }

  /** 历史外币消费流水（扫描 account 账本，含未登记，支持按卡/时间范围筛选） */
  async foreignHistory(req, res) {
    try {
      const { cardId, startDate, endDate } = req.query;
      const list = await ForeignRegister.findForeignExpenseHistory(req.userId, { cardId, startDate, endDate });
      return res.json({ status: 200, message: "获取成功", data: list });
    } catch (error) {
      console.error("获取历史外币流水错误:", error);
      return res.say("获取失败", 500);
    }
  }

  /**
   * 补登记未登记的历史外币消费流水（痛点4 补充）。
   * 流水原本没有登记（早期录入/遗漏）时在账本中按登记汇率回退入账，
   * 补登记后转为 pending（占额度不入账），可进入对账页按实际汇率对账。
   * 补登记与账单重算同一事务（H4：避免登记建成但账单未重算的半完成态）。
   */
  async foreignRegister(req, res) {
    const db = require("../../../common/config/db");
    const { accountId } = req.body.data || req.body;
    if (!accountId) return res.say("流水ID不能为空", 400);

    const conn = await db.getConnection();
    try {
      await conn.beginTransaction();
      const [rows] = await conn.execute(
        `SELECT a.id, a.card_id, a.amount, a.currency, a.exchange_rate, c.card_type
         FROM account a
         JOIN card_base c ON a.card_id = c.id AND c.user_id = a.user_id AND c.is_deleted = 0
         WHERE a.id = ? AND a.user_id = ? AND a.is_deleted = 0 AND a.direction = 0 AND a.reversed_id IS NULL`,
        [accountId, req.userId]
      );
      const row = rows[0];
      if (!row) throw new Error("外币消费流水不存在");
      if (row.card_type !== 'credit') throw new Error("该流水不是信用卡消费");
      if (!row.currency || row.currency === 'CNY') throw new Error("该流水不是外币消费，无需登记");
      if (!Number(row.exchange_rate) || Number(row.exchange_rate) <= 0) {
        throw new Error("该流水缺少登记汇率，无法自动登记，请先在消费录入页补全汇率");
      }
      const regId = await ForeignRegister.ensurePending({
        userId: req.userId,
        cardId: row.card_id,
        accountId: row.id,
        currency: row.currency,
        foreignAmount: row.amount,
        registeredRate: row.exchange_rate
      }, conn);
      // 补登记后该笔从「回退 toCNY 入账」变为「pending 占额不入账」，需重算该卡（含共享池扩散）
      await CreditCore.syncCardBills(row.card_id, req.userId, conn);
      await conn.commit();
      const reg = await ForeignRegister.findById(regId, req.userId);
      return res.json({ status: 200, message: "已登记为待对账", data: reg });
    } catch (error) {
      await conn.rollback();
      console.error("登记外币流水错误:", error);
      return res.say(error.message || "登记失败", 500);
    } finally {
      conn.release();
    }
  }

  /** 外币对账：录入实际汇率/人民币（或修改） */
  async foreignReconcile(req, res) {
    try {
      const { actualRate, actualRmb, settleDate, remark } = req.body.data || req.body;
      // P1-5 审计：对账金额/汇率必须为正，否则可把外币消费"归零"从账单消失
      const rate = Number(actualRate);
      if (!Number.isFinite(rate) || rate <= 0) {
        return res.say("实际汇率必须大于 0", 400);
      }
      if (actualRmb !== undefined && actualRmb !== null && actualRmb !== '') {
        const rmb = Number(actualRmb);
        if (!Number.isFinite(rmb) || rmb <= 0) {
          return res.say("实际人民币金额必须大于 0", 400);
        }
      }
      const reg = await CreditCore.reconcileForeign(
        req.params.id,
        req.userId,
        { actualRate, actualRmb, settleDate, remark }
      );
      return res.json({ status: 200, message: "对账成功", data: reg });
    } catch (error) {
      console.error("外币对账错误:", error);
      return res.say(error.message || "对账失败", 500);
    }
  }

  /** 删除外币登记（H4：删除与账单重算同一事务，避免 pending 释放后额度不同步） */
  async foreignDelete(req, res) {
    const db = require("../../../common/config/db");
    let cardId = null;
    const conn = await db.getConnection();
    try {
      await conn.beginTransaction();
      const reg = await ForeignRegister.findById(req.params.id, req.userId, conn);
      if (!reg) throw new Error("外币登记记录不存在");
      cardId = reg.card_id;
      await ForeignRegister.delete(req.params.id, req.userId, conn);
      // 删除登记后重算对应卡（含共享池扩散）
      await CreditCore.syncCardBills(cardId, req.userId, conn);
      await conn.commit();
      return res.json({ status: 200, message: "删除成功" });
    } catch (error) {
      await conn.rollback();
      console.error("删除外币登记错误:", error);
      return res.say(error.message || "删除失败", 500);
    } finally {
      conn.release();
    }
  }
}

module.exports = new CardBillController();
