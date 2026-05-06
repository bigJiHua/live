const CardBill = require("../model/bill");
const CardLog = require("../model/log");
const Card = require("../model");

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
        await CardBill.rebuildBillFromAccount(cardId, req.userId);
        bills = await CardBill.findAll(req.userId, filters);
      }

      // 仍然没有，且没有指定卡片，尝试为所有信用卡重建
      if ((!bills || bills.length === 0) && !cardId) {
        const creditCards = await Card.findAll(req.userId, { cardType: 'credit' });
        for (const card of creditCards) {
          await CardBill.rebuildBillFromAccount(card.id, req.userId);
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
      const bill = await CardBill.findLatestByCardId(req.params.cardId);

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
      // 从流水重新计算所有账单
      const results = await CardBill.rebuildBillFromAccount(
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
          const billData = await CardBill.rebuildBillFromAccount(card.id, req.userId);
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
}

module.exports = new CardBillController();
