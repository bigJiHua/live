const Transaction = require('../models/Transaction');
const Category = require('../models/Category');
const financeService = require('../services/financeService');

class FinanceController {
  // 获取账务流水列表
  async getTransactions(req, res) {
    try {
      const { page = 1, limit = 20, type, categoryId, startDate, endDate } = req.query;

      const filters = { userId: req.userId };
      if (type) filters.type = type;
      if (categoryId) filters.categoryId = categoryId;
      if (startDate) filters.startDate = startDate;
      if (endDate) filters.endDate = endDate;

      const result = await Transaction.findAll(filters, page, limit);

      res.json({
        transactions: result.rows,
        pagination: {
          page: parseInt(page),
          limit: parseInt(limit),
          total: result.count,
          totalPages: Math.ceil(result.count / limit),
        },
      });
    } catch (error) {
      console.error('获取账务流水错误:', error);
      res.status(500).json({ message: '获取账务流水失败', error: error.message });
    }
  }

  // 获取单个账务流水详情
  async getTransactionById(req, res) {
    try {
      const transaction = await Transaction.findById(req.params.id, req.userId);

      if (!transaction) {
        return res.status(404).json({ message: '账务流水不存在' });
      }

      res.json({ transaction });
    } catch (error) {
      console.error('获取账务流水详情错误:', error);
      res.status(500).json({ message: '获取账务流水详情失败', error: error.message });
    }
  }

  // 创建账务流水
  async createTransaction(req, res) {
    try {
      const { amount, type, categoryId, description, date } = req.body;

      // 验证分类是否存在
      const category = await Category.findById(categoryId, req.userId);
      if (!category) {
        return res.status(400).json({ message: '分类不存在' });
      }

      const transaction = await Transaction.create({
        userId: req.userId,
        amount,
        type,
        categoryId,
        description,
        date: date || new Date(),
      });

      res.status(201).json({
        message: '创建成功',
        transaction,
      });
    } catch (error) {
      console.error('创建账务流水错误:', error);
      res.status(500).json({ message: '创建账务流水失败', error: error.message });
    }
  }

  // 更新账务流水
  async updateTransaction(req, res) {
    try {
      const { amount, type, categoryId, description, date } = req.body;

      // 验证分类是否存在
      const category = await Category.findById(categoryId, req.userId);
      if (!category) {
        return res.status(400).json({ message: '分类不存在' });
      }

      const transaction = await Transaction.update(req.params.id, req.userId, {
        amount,
        type,
        categoryId,
        description,
        date,
      });

      if (!transaction) {
        return res.status(404).json({ message: '账务流水不存在' });
      }

      res.json({
        message: '更新成功',
        transaction,
      });
    } catch (error) {
      console.error('更新账务流水错误:', error);
      res.status(500).json({ message: '更新账务流水失败', error: error.message });
    }
  }

  // 删除账务流水
  async deleteTransaction(req, res) {
    try {
      const result = await Transaction.delete(req.params.id, req.userId);

      if (!result) {
        return res.status(404).json({ message: '账务流水不存在' });
      }

      res.json({ message: '删除成功' });
    } catch (error) {
      console.error('删除账务流水错误:', error);
      res.status(500).json({ message: '删除账务流水失败', error: error.message });
    }
  }

  // 获取分类列表
  async getCategories(req, res) {
    try {
      const { type } = req.query;
      const filters = { userId: req.userId };
      if (type) filters.type = type;

      const categories = await Category.findAll(filters);

      res.json({ categories });
    } catch (error) {
      console.error('获取分类错误:', error);
      res.status(500).json({ message: '获取分类失败', error: error.message });
    }
  }

  // 创建分类
  async createCategory(req, res) {
    try {
      const { name, type, color, icon } = req.body;

      const category = await Category.create({
        userId: req.userId,
        name,
        type,
        color,
        icon,
      });

      res.status(201).json({
        message: '创建成功',
        category,
      });
    } catch (error) {
      console.error('创建分类错误:', error);
      res.status(500).json({ message: '创建分类失败', error: error.message });
    }
  }

  // 更新分类
  async updateCategory(req, res) {
    try {
      const { name, type, color, icon } = req.body;

      const category = await Category.update(req.params.id, req.userId, {
        name,
        type,
        color,
        icon,
      });

      if (!category) {
        return res.status(404).json({ message: '分类不存在' });
      }

      res.json({
        message: '更新成功',
        category,
      });
    } catch (error) {
      console.error('更新分类错误:', error);
      res.status(500).json({ message: '更新分类失败', error: error.message });
    }
  }

  // 删除分类
  async deleteCategory(req, res) {
    try {
      const result = await Category.delete(req.params.id, req.userId);

      if (!result) {
        return res.status(404).json({ message: '分类不存在' });
      }

      res.json({ message: '删除成功' });
    } catch (error) {
      console.error('删除分类错误:', error);
      res.status(500).json({ message: '删除分类失败', error: error.message });
    }
  }

  // 获取财务报表
  async getFinanceReport(req, res) {
    try {
      const { startDate, endDate } = req.query;

      const report = await financeService.generateReport(req.userId, {
        startDate,
        endDate,
      });

      res.json({ report });
    } catch (error) {
      console.error('获取财务报表错误:', error);
      res.status(500).json({ message: '获取财务报表失败', error: error.message });
    }
  }

  // 计算投资回报率 (IRR)
  async calculateIRR(req, res) {
    try {
      const { cashFlows } = req.body;

      if (!Array.isArray(cashFlows) || cashFlows.length === 0) {
        return res.status(400).json({ message: '现金流数据无效' });
      }

      const irr = financeService.calculateIRR(cashFlows);

      res.json({
        irr: irr * 100, // 转换为百分比
        message: `内部收益率为 ${(irr * 100).toFixed(2)}%`,
      });
    } catch (error) {
      console.error('计算 IRR 错误:', error);
      res.status(500).json({ message: '计算 IRR 失败', error: error.message });
    }
  }
}

module.exports = new FinanceController();
