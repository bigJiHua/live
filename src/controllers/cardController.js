const Card = require('../models/Card');
const CardLog = require('../models/CardLog');

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

      res.json({
        status: 200,
        message: '获取成功',
        data: cards
      });
    } catch (error) {
      console.error('获取卡片列表错误:', error);
      res.status(500).json({ status: 500, message: '获取失败', error: error.message });
    }
  }

  /**
   * 获取单个卡片详情
   */
  async getById(req, res) {
    try {
      const card = await Card.findById(req.params.id, req.userId);

      if (!card) {
        return res.status(404).json({ status: 404, message: '卡片不存在' });
      }

      res.json({ status: 200, message: '获取成功', data: card });
    } catch (error) {
      console.error('获取卡片详情错误:', error);
      res.status(500).json({ status: 500, message: '获取失败', error: error.message });
    }
  }

  /**
   * 创建卡片
   */
  async create(req, res) {
    try {
      const card = await Card.create({
        userId: req.userId,
        ...req.body.data
      });

      // 记录操作日志
      await CardLog.log(card.id, req.userId, '创建卡片', req.ip);

      res.status(201).json({
        status: 201,
        message: '创建成功',
        data: card
      });
    } catch (error) {
      console.error('创建卡片错误:', error);
      res.status(500).json({ status: 500, message: '创建失败', error: error.message });
    }
  }

  /**
   * 更新卡片
   */
  async update(req, res) {
    try {
      const card = await Card.update(req.params.id, req.userId, req.body.data);

      if (!card) {
        return res.status(404).json({ status: 404, message: '卡片不存在' });
      }

      // 记录操作日志
      await CardLog.log(req.params.id, req.userId, '更新卡片', req.ip);

      res.json({ status: 200, message: '更新成功', data: card });
    } catch (error) {
      console.error('更新卡片错误:', error);
      res.status(500).json({ status: 500, message: '更新失败', error: error.message });
    }
  }

  /**
   * 删除卡片
   */
  async delete(req, res) {
    try {
      const result = await Card.delete(req.params.id, req.userId);

      if (!result) {
        return res.status(404).json({ status: 404, message: '卡片不存在' });
      }

      // 记录操作日志
      await CardLog.log(req.params.id, req.userId, '删除卡片', req.ip);

      res.json({ status: 200, message: '删除成功' });
    } catch (error) {
      console.error('删除卡片错误:', error);
      res.status(500).json({ status: 500, message: '删除失败', error: error.message });
    }
  }
}

module.exports = new CardController();
