const Card = require("../model");
const CardLog = require("../model/log");
const AccountSettlement = require("../../account/service/settlement");
const CreditPool = require("../model/pool");
const CreditCore = require("../core/CreditCore");

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

      // R4：findById 返回单卡对象（非数组），无 length 属性；原 `card.length === 0` 判断永不生效
      if (!card) return res.say("卡片不存在", 404);
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

      if (!card || !card.id) {
        throw new Error('卡片创建失败');
      }

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
      return res.say(error.message || "创建失败", 500);
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
      return res.say(error, 500);
    }
  }

  /**
   * 批量更新排序：前端整列 1-N 重排后一次提交
   * body: { items: [{ id, sort }] }
   */
  async updateSortBatch(req, res) {
    try {
      const items = req.body.items || req.body.data?.items || req.body;
      if (!Array.isArray(items) || items.length === 0) {
        return res.say("排序数据为空", 400);
      }
      await Card.updateSortBatch(req.userId, items);
      return res.json({ status: 200, message: "排序已保存" });
    } catch (error) {
      console.error("批量更新排序错误:", error);
      return res.say("排序保存失败", 500);
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

  // ===================== 共享额度池（痛点2） =====================

  /** 创建共享池 */
  async createPool(req, res) {
    try {
      const { bankId, bankName, totalCreditLimit, totalTempLimit, creditReportMerged, currency, remark } = req.body.data || req.body;
      const pool = await CreditPool.create({
        userId: req.userId,
        bankId, bankName,
        totalCreditLimit, totalTempLimit, creditReportMerged, currency, remark
      });
      return res.json({ status: 200, message: "创建成功", data: pool });
    } catch (error) {
      console.error("创建共享池错误:", error);
      return res.say("创建失败", 500);
    }
  }

  /** 更新共享池 */
  async updatePool(req, res) {
    try {
      const { totalCreditLimit, totalTempLimit, bankId, bankName, creditReportMerged, currency, remark } = req.body.data || req.body;
      const pool = await CreditPool.update(req.params.id, req.userId, {
        totalCreditLimit, totalTempLimit, bankId, bankName, creditReportMerged, currency, remark
      });
      if (!pool) return res.say("共享池不存在", 404);
      return res.json({ status: 200, message: "更新成功", data: pool });
    } catch (error) {
      console.error("更新共享池错误:", error);
      return res.say("更新失败", 500);
    }
  }

  /** 列出我的共享池 */
  async listPools(req, res) {
    try {
      const pools = await CreditPool.findByUser(req.userId);
      return res.json({ status: 200, message: "获取成功", data: pools });
    } catch (error) {
      console.error("获取共享池错误:", error);
      return res.say("获取失败", 500);
    }
  }

  /** 删除共享池 */
  async deletePool(req, res) {
    try {
      await CreditPool.delete(req.params.id, req.userId);
      return res.json({ status: 200, message: "删除成功" });
    } catch (error) {
      console.error("删除共享池错误:", error);
      return res.say("删除失败", 500);
    }
  }

  /** 把卡片归入/移出共享池 */
  async assignCardPool(req, res) {
    try {
      const { cardId, poolId } = req.body.data || req.body;
      // assignCard 内部已事务化更新 share_pool_id 并全池重算（旧池/新池/本卡，H1 审计）
      await CreditPool.assignCard(cardId, req.userId, poolId || null);
      return res.json({ status: 200, message: "操作成功" });
    } catch (error) {
      console.error("归入共享池错误:", error);
      return res.say("操作失败", 500);
    }
  }
}

module.exports = new CardController();

