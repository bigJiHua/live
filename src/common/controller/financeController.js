const Account = require('../models/Account');
const Asset = require('../models/Asset');

/**
 * 账务控制器
 * 包含：收支记录、本月统计、总资产统计
 */
class FinanceController {

  // ========== 收支记录 ==========

  /**
   * 获取收支列表
   */
  async getTransactions(req, res) {
    try {
      const { page = 1, limit = 20, direction, categoryId, payMethod, startDate, endDate } = req.query;

      const filters = {};
      if (direction !== undefined) filters.direction = parseInt(direction);
      if (categoryId) filters.categoryId = categoryId;
      if (payMethod) filters.payMethod = payMethod;
      if (startDate) filters.startDate = startDate;
      if (endDate) filters.endDate = endDate;

      const result = await Account.findAll(req.userId, filters, parseInt(page), parseInt(limit));

      res.json({
        status: 200,
        message: '获取成功',
        data: {
          list: result.rows,
          pagination: {
            page: parseInt(page),
            limit: parseInt(limit),
            total: result.total,
            totalPages: Math.ceil(result.total / limit)
          }
        }
      });
    } catch (error) {
      console.error('获取收支列表错误:', error);
      res.status(500).json({ status: 500, message: '获取失败', error: error.message });
    }
  }

  /**
   * 获取单条收支详情
   */
  async getTransactionById(req, res) {
    try {
      const transaction = await Account.findById(req.params.id, req.userId);

      if (!transaction) {
        return res.status(404).json({ status: 404, message: '记录不存在' });
      }

      res.json({ status: 200, message: '获取成功', data: transaction });
    } catch (error) {
      console.error('获取收支详情错误:', error);
      res.status(500).json({ status: 500, message: '获取失败', error: error.message });
    }
  }

  /**
   * 创建收支记录
   */
  async createTransaction(req, res) {
    try {
      const { direction, categoryId, payType, payMethod, amount, currency, exchangeRate, transDate, remark, cardId } = req.body;

      // 基本验证
      if (amount === undefined || amount === null) {
        return res.status(400).json({ status: 400, message: '金额不能为空' });
      }
      if (direction === undefined) {
        return res.status(400).json({ status: 400, message: '请选择收入或支出' });
      }

      const transaction = await Account.create({
        userId: req.userId,
        direction: parseInt(direction),
        categoryId,
        payType,
        payMethod,
        amount: parseFloat(amount),
        currency,
        exchangeRate,
        transDate,
        remark,
        cardId
      });

      res.status(200).json({
        status: 200,
        message: '创建成功',
        data: transaction
      });
    } catch (error) {
      console.error('创建收支记录错误:', error);
      res.status(500).json({ status: 500, message: error, error: error.message });
    }
  }

  /**
   * 更新收支记录
   */
  async updateTransaction(req, res) {
    try {
      const { direction, categoryId, payType, payMethod, amount, currency, exchangeRate, transDate, remark, cardId } = req.body;

      const transaction = await Account.update(req.params.id, req.userId, {
        direction,
        categoryId,
        payType,
        payMethod,
        amount,
        currency,
        exchangeRate,
        transDate,
        remark,
        cardId
      });

      if (!transaction) {
        return res.status(404).json({ status: 404, message: '记录不存在' });
      }

      res.json({ status: 200, message: '更新成功', data: transaction });
    } catch (error) {
      console.error('更新收支记录错误:', error);
      res.status(500).json({ status: 500, message: '更新失败', error: error.message });
    }
  }

  /**
   * 删除收支记录
   */
  async deleteTransaction(req, res) {
    try {
      const result = await Account.delete(req.params.id, req.userId);

      if (!result) {
        return res.status(404).json({ status: 404, message: '记录不存在' });
      }

      res.json({ status: 200, message: '删除成功' });
    } catch (error) {
      console.error('删除收支记录错误:', error);
      res.status(500).json({ status: 500, message: '删除失败', error: error.message });
    }
  }

  // ========== 本月收支统计 ==========

  /**
   * 获取本月收支统计
   */
  async getMonthStats(req, res) {
    try {
      const now = new Date();
      const year = parseInt(req.query.year) || now.getFullYear();
      const month = parseInt(req.query.month) || (now.getMonth() + 1);

      const [stats, categoryStats] = await Promise.all([
        Account.getMonthStats(req.userId, year, month),
        Account.getStatsByCategory(req.userId, year, month)
      ]);

      res.json({
        status: 200,
        message: '获取成功',
        data: {
          ...stats,
          categoryBreakdown: categoryStats
        }
      });
    } catch (error) {
      console.error('获取本月统计错误:', error);
      res.status(500).json({ status: 500, message: '获取失败', error: error.message });
    }
  }

  // ========== 总资产统计 ==========

  /**
   * 获取预估总资产
   */
  async getTotalAssets(req, res) {
    try {
      const assets = await Asset.getTotalAssets(req.userId);

      res.json({
        status: 200,
        message: '获取成功',
        data: assets
      });
    } catch (error) {
      console.error('获取总资产错误:', error);
      res.status(500).json({ status: 500, message: '获取失败', error: error.message });
    }
  }

  /**
   * 获取资产明细
   */
  async getAssetDetail(req, res) {
    try {
      const { type } = req.query; // liquid | fixed | fund | card
      const userId = req.userId;

      let data;
      switch (type) {
        case 'liquid':
          data = await Asset.getLiquidAssets(userId);
          break;
        case 'fixed':
          data = await Asset.getFixedAssets(userId);
          break;
        case 'fund':
          data = await Asset.getFunds(userId);
          break;
        case 'card':
          data = await Asset.getCardBills(userId);
          break;
        default:
          data = await Asset.getTotalAssets(userId);
      }

      res.json({ status: 200, message: '获取成功', data });
    } catch (error) {
      console.error('获取资产明细错误:', error);
      res.status(500).json({ status: 500, message: '获取失败', error: error.message });
    }
  }

  // ========== 保留旧接口兼容性 ==========

  async getCategories(req, res) {
    // 分类功能暂时返回空，后续可扩展
    res.json({ status: 200, message: '获取成功', data: [] });
  }

  async createCategory(req, res) {
    res.status(501).json({ status: 501, message: '暂未实现' });
  }

  async updateCategory(req, res) {
    res.status(501).json({ status: 501, message: '暂未实现' });
  }

  async deleteCategory(req, res) {
    res.status(501).json({ status: 501, message: '暂未实现' });
  }

  async getFinanceReport(req, res) {
    // 财务报表重定向到本月统计
    const now = new Date();
    const year = parseInt(req.query.year) || now.getFullYear();
    const month = parseInt(req.query.month) || (now.getMonth() + 1);

    const stats = await Account.getMonthStats(req.userId, year, month);
    res.json({ status: 200, message: '获取成功', data: stats });
  }

  async calculateIRR(req, res) {
    res.status(501).json({ status: 501, message: '暂未实现' });
  }
}

module.exports = new FinanceController();
