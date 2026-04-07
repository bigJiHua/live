const CardBill = require('../models/CardBill');
const CardLog = require('../models/CardLog');

/**
 * 卡片账单控制器
 */
class CardBillController {

  /**
   * 获取账单列表
   */
  async getList(req, res) {
    try {
      const { cardId } = req.query;
      const filters = {};

      if (cardId) filters.cardId = cardId;

      const bills = await CardBill.findAll(req.userId, filters);

      res.json({
        status: 200,
        message: '获取成功',
        data: bills
      });
    } catch (error) {
      console.error('获取账单列表错误:', error);
      res.status(500).json({ status: 500, message: '获取失败', error: error.message });
    }
  }

  /**
   * 获取单个账单详情
   */
  async getById(req, res) {
    try {
      const bill = await CardBill.findById(req.params.id, req.userId);

      if (!bill) {
        return res.status(404).json({ status: 404, message: '账单不存在' });
      }

      res.json({ status: 200, message: '获取成功', data: bill });
    } catch (error) {
      console.error('获取账单详情错误:', error);
      res.status(500).json({ status: 500, message: '获取失败', error: error.message });
    }
  }

  /**
   * 获取卡片最新账单
   */
  async getLatestByCardId(req, res) {
    try {
      const { cardId } = req.params;
      const bill = await CardBill.findLatestByCardId(cardId);

      if (!bill) {
        return res.status(404).json({ status: 404, message: '暂无账单' });
      }

      res.json({ status: 200, message: '获取成功', data: bill });
    } catch (error) {
      console.error('获取最新账单错误:', error);
      res.status(500).json({ status: 500, message: '获取失败', error: error.message });
    }
  }

  /**
   * 创建账单
   */
  async create(req, res) {
    try {
      const bill = await CardBill.create({
        userId: req.userId,
        ...req.body.data
      });

      // 记录操作日志
      await CardLog.log(req.body.data.cardId, req.userId, '创建账单', req.ip);

      res.status(201).json({
        status: 201,
        message: '创建成功',
        data: bill
      });
    } catch (error) {
      console.error('创建账单错误:', error);
      res.status(500).json({ status: 500, message: '创建失败', error: error.message });
    }
  }

  /**
   * 更新账单
   */
  async update(req, res) {
    try {
      const bill = await CardBill.update(req.params.id, req.userId, req.body.data);

      if (!bill) {
        return res.status(404).json({ status: 404, message: '账单不存在' });
      }

      // 记录操作日志
      await CardLog.log(bill.card_id, req.userId, '更新账单', req.ip);

      res.json({ status: 200, message: '更新成功', data: bill });
    } catch (error) {
      console.error('更新账单错误:', error);
      res.status(500).json({ status: 500, message: '更新失败', error: error.message });
    }
  }

  /**
   * 删除账单
   */
  async delete(req, res) {
    try {
      const bill = await CardBill.findById(req.params.id, req.userId);
      if (!bill) {
        return res.status(404).json({ status: 404, message: '账单不存在' });
      }

      const result = await CardBill.delete(req.params.id, req.userId);

      if (!result) {
        return res.status(404).json({ status: 404, message: '账单不存在' });
      }

      // 记录操作日志
      await CardLog.log(bill.card_id, req.userId, '删除账单', req.ip);

      res.json({ status: 200, message: '删除成功' });
    } catch (error) {
      console.error('删除账单错误:', error);
      res.status(500).json({ status: 500, message: '删除失败', error: error.message });
    }
  }
}

module.exports = new CardBillController();
