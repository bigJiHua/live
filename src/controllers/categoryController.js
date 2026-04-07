const Category = require('../models/Category');

/**
 * 分类控制器
 */
class CategoryController {

  /**
   * 获取分类列表
   */
  async getList(req, res) {
    try {
      const { type } = req.query;
      const filters = {};
      
      if (type) filters.type = type;

      let categories = await Category.findAll(req.userId, filters);

      // 如果该类型没有分类，自动初始化默认分类
      if (categories.length === 0) {
        const allCategories = await Category.findAll(req.userId, {});
        
        if (allCategories.length === 0) {
          console.log(`[分类初始化] 用户 ${req.userId} 首次初始化默认分类`);
          await Category.createDefaults(req.userId);
          categories = await Category.findAll(req.userId, filters);
        }
      }

      res.json({
        status: 200,
        message: '获取成功',
        data: categories
      });
    } catch (error) {
      console.error('获取分类列表错误:', error);
      res.status(500).json({ status: 500, message: '获取失败', error: error.message });
    }
  }

  /**
   * 获取单个分类详情
   */
  async getById(req, res) {
    try {
      const category = await Category.findById(req.params.id, req.userId);

      if (!category) {
        return res.status(404).json({ status: 404, message: '分类不存在' });
      }

      res.json({ status: 200, message: '获取成功', data: category });
    } catch (error) {
      console.error('获取分类详情错误:', error);
      res.status(500).json({ status: 500, message: '获取失败', error: error.message });
    }
  }

  /**
   * 创建分类
   */
  async create(req, res) {
    try {
      const { name, type, iconUrl, remark } = req.body.data;

      const category = await Category.create({
        userId: req.userId,
        name,
        type,
        iconUrl,
        remark
      });

      res.status(200).json({
        status: 200,
        message: '创建成功',
        data: category
      });
    } catch (error) {
      console.error('创建分类错误:', error);
      res.status(500).json({ status: 500, message: '创建失败', error: error.message });
    }
  }

  /**
   * 更新分类
   */
  async update(req, res) {
    try {
      const { name, iconUrl, remark } = req.body.data;

      const category = await Category.update(req.params.id, req.userId, {
        name,
        iconUrl,
        remark
      });

      if (!category) {
        return res.status(404).json({ status: 404, message: '分类不存在' });
      }

      res.json({ status: 200, message: '更新成功', data: category });
    } catch (error) {
      console.error('更新分类错误:', error);
      res.status(500).json({ status: 500, message: '更新失败', error: error.message });
    }
  }

  /**
   * 删除分类
   */
  async delete(req, res) {
    try {
      const result = await Category.delete(req.params.id, req.userId);

      if (!result) {
        return res.status(404).json({ status: 404, message: '分类不存在' });
      }

      res.json({ status: 200, message: '删除成功' });
    } catch (error) {
      console.error('删除分类错误:', error);
      res.status(500).json({ status: 500, message: '删除失败', error: error.message });
    }
  }
}

module.exports = new CategoryController();
