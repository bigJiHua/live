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
      const { cardId, billId } = req.query;
      const filters = {};

      if (cardId) filters.cardId = cardId;
      if (billId) filters.billId = billId;

      const records = await CardRepay.findAll(req.userId, filters);

      res.json({
        status: 200,
        message: '获取成功',
        data: records
      });
    } catch (error) {
      console.error('获取还款记录错误:', error);
      res.status(500).json({ status: 500, message: '获取失败', error: error.message });
    }
  }

  /**
   * 获取单个还款记录详情
   */
  async getById(req, res) {
    try {
      const record = await CardRepay.findById(req.params.id, req.userId);

      if (!record) {
        return res.status(404).json({ status: 404, message: '还款记录不存在' });
      }

      res.json({ status: 200, message: '获取成功', data: record });
    } catch (error) {
      console.error('获取还款记录详情错误:', error);
      res.status(500).json({ status: 500, message: '获取失败', error: error.message });
    }
  }

  /**
   * 创建还款记录
   */
  async create(req, res) {
    try {
      const record = await CardRepay.create({
        userId: req.userId,
        ...req.body.data
      });

      // 记录操作日志
      await CardLog.log(req.body.data.cardId, req.userId, '创建还款记录', req.ip);

      res.status(201).json({
        status: 201,
        message: '创建成功',
        data: record
      });
    } catch (error) {
      console.error('创建还款记录错误:', error);
      res.status(500).json({ status: 500, message: '创建失败', error: error.message });
    }
  }

  /**
   * 更新还款记录
   */
  async update(req, res) {
    try {
      const record = await CardRepay.update(req.params.id, req.userId, req.body.data);

      if (!record) {
        return res.status(404).json({ status: 404, message: '还款记录不存在' });
      }

      // 记录操作日志
      await CardLog.log(record.card_id, req.userId, '更新还款记录', req.ip);

      res.json({ status: 200, message: '更新成功', data: record });
    } catch (error) {
      console.error('更新还款记录错误:', error);
      res.status(500).json({ status: 500, message: '更新失败', error: error.message });
    }
  }

  /**
   * 删除还款记录
   */
  async delete(req, res) {
    try {
      const record = await CardRepay.findById(req.params.id, req.userId);
      if (!record) {
        return res.status(404).json({ status: 404, message: '还款记录不存在' });
      }

      const result = await CardRepay.delete(req.params.id, req.userId);

      if (!result) {
        return res.status(404).json({ status: 404, message: '还款记录不存在' });
      }

      // 记录操作日志
      await CardLog.log(record.card_id, req.userId, '删除还款记录', req.ip);

      res.json({ status: 200, message: '删除成功' });
    } catch (error) {
      console.error('删除还款记录错误:', error);
      res.status(500).json({ status: 500, message: '删除失败', error: error.message });
    }
  }
}

module.exports = new CardRepayController();
