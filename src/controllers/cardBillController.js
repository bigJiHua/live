const CardBill = require("../models/CardBill");
const CardLog = require("../models/CardLog");

/**
 * 卡片账单控制器
 */
class CardBillController {
  /**
   * 获取账单列表（自动重建遗留账单）
   */
  async getList(req, res) {
    try {
      const { cardId, billMonth } = req.query;
      const filters = {};

      if (cardId) filters.cardId = cardId;
      if (billMonth) filters.billMonth = billMonth;

      // 1. 先获取账单
      let bills = await CardBill.findAll(req.userId, filters);

      // 2. 如果指定了卡片，自动重建遗留账单
      if (cardId) {
        await CardBill.rebuildBillFromAccount(cardId, req.userId);
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

      // 记录操作日志
      await CardLog.log(req.body.data.cardId, req.userId, "创建账单", req.ip);

      return res
        .status(200)
        .json({ status: 200, message: "创建成功", data: bill });
    } catch (error) {
      console.error("创建账单错误:", error);
      return res.say("创建失败", 500);
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
      return res.say("更新失败", 500);
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
