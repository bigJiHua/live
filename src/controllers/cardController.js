const Card = require("../models/Card");
const CardLog = require("../models/CardLog");
const AccountSettlement = require("../services/AccountSettlement");

/**
 * 卡片控制器
 */
class CardController {
  /**
   * 获取卡片列表
   */
  async getList(req, res) {
    try {
      const { cardType, isHide } = req.query;
      const filters = {};

      if (cardType) filters.cardType = cardType;
      if (isHide !== undefined) filters.isHide = parseInt(isHide);

      const cards = await Card.findAll(req.userId, filters);

      if (!cards || cards.length === 0) return res.say("暂无卡片", 204);
      return res.json({ status: 200, message: "获取成功", data: cards });
    } catch (error) {
      console.error("获取卡片列表错误:", error);
      return res.say("获取失败", 500);
    }
  }

  /**
   * 获取单个卡片详情
   */
  async getById(req, res) {
    if (!req.params.id) return res.say("卡片id不能为空", 400);
    try {
      const card = await Card.findById(req.params.id, req.userId);

      if (!card) return res.say("卡片不存在", 404);
      if (card.length === 0) return res.say("当前卡片无账单", 204);
      return res.json({ status: 200, message: "获取成功", data: card });
    } catch (error) {
      console.error("获取卡片详情错误:", error);
      return res.say("获取失败", 500);
    }
  }

  /**
   * 创建卡片
   */
  async create(req, res) {
    try {
      const card = await Card.create({
        userId: req.userId,
        ...req.body.data,
      });

      // ===== 创建卡片时初始化余额快照 =====
      await AccountSettlement.initCardBalance(card.id, req.userId);

      // 记录操作日志
      await CardLog.log(card.id, req.userId, "创建卡片", req.ip);

      return res.status(200).json({
        status: 200,
        message: "创建成功",
        data: card,
      });
    } catch (error) {
      console.error("创建卡片错误:", error);
      return res.say("创建失败", 500);
    }
  }

  /**
   * 更新卡片
   */
  async update(req, res) {
    try {
      const card = await Card.update(req.params.id, req.userId, req.body.data);

      if (!card) return res.say("卡片不存在", 404);

      // 记录操作日志
      await CardLog.log(req.params.id, req.userId, "更新卡片", req.ip);

      return res.json({ status: 200, message: "更新成功", data: card });
    } catch (error) {
      console.error("更新卡片错误:", error);
      return res.say("更新失败", 500);
    }
  }

  /**
   * 删除卡片
   */
  async delete(req, res) {
    try {
      const result = await Card.delete(req.params.id, req.userId);

      if (!result) return res.say("卡片不存在", 404);

      // 记录操作日志
      await CardLog.log(req.params.id, req.userId, "删除卡片", req.ip);

      return res.json({ status: 200, message: "删除成功" });
    } catch (error) {
      console.error("删除卡片错误:", error);
      return res.say("删除失败", 500);
    }
  }
}

module.exports = new CardController();
