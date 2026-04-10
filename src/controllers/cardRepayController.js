const CardRepay = require('../models/CardRepay');
const CardLog = require('../models/CardLog');

/**
 * 卡片还款控制器
 */
class CardRepayController {

  /**
   * 获取还款记录列表
   */
  async getList(req, res) {
    try {
      const { cardId, billId, billMonth } = req.query;
      const filters = {};

      if (cardId) filters.cardId = cardId;
      if (billId) filters.billId = billId;
      if (billMonth) filters.billMonth = billMonth;

      const records = await CardRepay.findAll(req.userId, filters);

      if (!records || records.length === 0) return res.say("暂无还款记录", 200);
      return res.json({ status: 200, message: "获取成功", data: records });
    } catch (error) {
      console.error('获取还款记录错误:', error);
      return res.say("获取失败", 500);
    }
  }

  /**
   * 获取单个还款记录详情
   */
  async getById(req, res) {
    if (!req.params.id) return res.say("还款记录id不能为空", 400);
    try {
      const record = await CardRepay.findById(req.params.id, req.userId);

      if (!record) return res.say("还款记录不存在", 404);
      return res.json({ status: 200, message: "获取成功", data: record });
    } catch (error) {
      console.error('获取还款记录详情错误:', error);
      return res.say("获取失败", 500);
    }
  }

  /**
   * 创建还款记录（支持多种还款方式）
   * repayMethod: card(本卡)/balance(余额)/bank_card(银行卡)/cash(现金)
   * bank_card 时需要传入 repayMethodCardId
   */
  async create(req, res) {
    try {
      const { 
        cardId, 
        billId, 
        repayAmount, 
        repayMethod,
        repayMethodCardId,
        repayTime,
        remark 
      } = req.body.data;

      // 使用 executeRepay 方法（失败会抛出错误）
      const result = await CardRepay.executeRepay({
        userId: req.userId,
        cardId,
        billId,
        repayAmount,
        repayMethod,
        repayMethodCardId,
        repayTime,
        remark
      });

      // 检查返回值确保还款记录确实创建成功
      if (!result || !result.repayId) {
        throw new Error('还款记录创建失败');
      }

      // 记录操作日志
      await CardLog.log(cardId, req.userId, `还款 ${repayAmount}元`, req.ip);

      return res.status(200).json({ 
        status: 200, 
        message: "还款成功",
        data: result
      });
    } catch (error) {
      console.error('创建还款记录错误:', error);
      return res.say(error.message || "创建失败", 500);
    }
  }

  /**
   * 更新还款记录
   */
  async update(req, res) {
    try {
      const record = await CardRepay.update(req.params.id, req.userId, req.body.data);

      if (!record) return res.say("还款记录不存在", 404);

      // 记录操作日志
      await CardLog.log(record.card_id, req.userId, '更新还款记录', req.ip);

      return res.json({ status: 200, message: "更新成功", data: record });
    } catch (error) {
      console.error('更新还款记录错误:', error);
      return res.say(error.message || "更新失败", 500);
    }
  }

  /**
   * 删除还款记录
   */
  async delete(req, res) {
    try {
      const record = await CardRepay.findById(req.params.id, req.userId);
      if (!record) return res.say("还款记录不存在", 404);

      const result = await CardRepay.delete(req.params.id, req.userId);

      if (!result) return res.say("还款记录不存在", 404);

      // 记录操作日志
      await CardLog.log(record.card_id, req.userId, '删除还款记录', req.ip);

      return res.json({ status: 200, message: "删除成功" });
    } catch (error) {
      console.error('删除还款记录错误:', error);
      return res.say(error.message || "删除失败", 500);
    }
  }
}

module.exports = new CardRepayController();
