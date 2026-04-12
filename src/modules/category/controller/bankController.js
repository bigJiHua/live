const BusBank = require('../model/bank');

/**
 * 银行分类控制器
 */
class BusBankController {

  /**
   * 获取银行分类列表（自动初始化虚拟银行分类）
   */
  async getList(req, res) {
    try {
      const banks = await BusBank.findAll(req.userId);

      if (!banks || banks.length === 0) {
        return res.status(200).send({
          status: 200,
          message: "暂无银行分类",
          data: [],
        });
      }
      return res.json({ status: 200, message: "获取成功", data: banks });
    } catch (error) {
      console.error('获取银行分类列表错误:', error);
      return res.say("获取失败", 500);
    }
  }

  /**
   * 获取单个银行分类详情
   */
  async getById(req, res) {
    if (!req.params.id) return res.say("银行ID不能为空", 400);
    try {
      const bank = await BusBank.findById(req.params.id, req.userId);

      if (!bank) return res.say("银行不存在", 404);
      return res.json({ status: 200, message: "获取成功", data: bank });
    } catch (error) {
      console.error('获取银行详情错误:', error);
      return res.say("获取失败", 500);
    }
  }

  /**
   * 创建银行分类
   */
  async create(req, res) {
    try {
      const { name, logoUrl, remark, sort } = req.body.data;

      const bank = await BusBank.create({
        userId: req.userId,
        name,
        logoUrl,
        remark,
        sort,
      });

      return res.status(200).json({
        status: 200,
        message: '创建成功',
        data: bank
      });
    } catch (error) {
      console.error('创建银行分类错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '创建失败' });
    }
  }

  /**
   * 更新银行分类
   */
  async update(req, res) {
    try {
      const { name, logoUrl, remark, sort } = req.body.data;

      const bank = await BusBank.update(req.params.id, req.userId, {
        name,
        logoUrl,
        remark,
        sort,
      });

      if (!bank) return res.say("银行不存在", 404);
      return res.json({ status: 200, message: '更新成功', data: bank });
    } catch (error) {
      console.error('更新银行分类错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '更新失败' });
    }
  }

  /**
   * 删除银行分类
   */
  async delete(req, res) {
    try {
      const result = await BusBank.delete(req.params.id, req.userId);

      if (!result) return res.say("银行不存在", 404);
      return res.json({ status: 200, message: '删除成功' });
    } catch (error) {
      console.error('删除银行分类错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '删除失败' });
    }
  }

  /**
   * 初始化虚拟银行分类
   */
  async initVirtual(req, res) {
    try {
      const results = await BusBank.initVirtualBanks(req.userId);
      const banks = await BusBank.findAll(req.userId);
      return res.json({ status: 200, message: '初始化成功', data: banks });
    } catch (error) {
      console.error('初始化虚拟银行错误:', error);
      return res.say("初始化失败", 500);
    }
  }
}

module.exports = new BusBankController();
