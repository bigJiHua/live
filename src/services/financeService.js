const Transaction = require('../models/Transaction');
const Category = require('../models/Category');

/**
 * 财务服务 - 复杂业务逻辑
 */
class FinanceService {
  // 生成财务报表
  async generateReport(userId, { startDate, endDate } = {}) {
    // 获取统计数据
    const stats = await Transaction.getStats(userId, { startDate, endDate });

    // 获取按分类汇总的数据
    const categorySummary = await this.getCategorySummary(userId, {
      startDate,
      endDate,
    });

    // 获取按日期汇总的数据
    const dailySummary = await this.getDailySummary(userId, {
      startDate,
      endDate,
    });

    return {
      summary: stats,
      categorySummary,
      dailySummary,
      reportDate: new Date().toISOString(),
    };
  }

  // 获取按分类汇总的数据
  async getCategorySummary(userId, { startDate, endDate } = {}) {
    let whereClause = 'WHERE t.user_id = ?';
    const params = [userId];

    if (startDate) {
      whereClause += ' AND t.date >= ?';
      params.push(startDate);
    }

    if (endDate) {
      whereClause += ' AND t.date <= ?';
      params.push(endDate);
    }

    const query = `
      SELECT
        c.name as category_name,
        c.color as category_color,
        c.icon as category_icon,
        t.type,
        SUM(t.amount) as total,
        COUNT(t.id) as count
      FROM transactions t
      LEFT JOIN categories c ON t.category_id = c.id
      ${whereClause}
      GROUP BY c.id, t.type
      ORDER BY t.type, total DESC
    `;

    const [rows] = await require('../config/db').execute(query, params);

    // 按收入和支出分类
    const summary = {
      income: [],
      expense: [],
    };

    rows.forEach((row) => {
      const item = {
        name: row.category_name,
        color: row.category_color,
        icon: row.category_icon,
        total: parseFloat(row.total) || 0,
        count: row.count,
      };

      if (row.type === 'income') {
        summary.income.push(item);
      } else if (row.type === 'expense') {
        summary.expense.push(item);
      }
    });

    return summary;
  }

  // 获取按日期汇总的数据
  async getDailySummary(userId, { startDate, endDate } = {}) {
    let whereClause = 'WHERE user_id = ?';
    const params = [userId];

    if (startDate) {
      whereClause += ' AND date >= ?';
      params.push(startDate);
    }

    if (endDate) {
      whereClause += ' AND date <= ?';
      params.push(endDate);
    }

    const query = `
      SELECT
        DATE(date) as date,
        type,
        SUM(amount) as total,
        COUNT(*) as count
      FROM transactions
      ${whereClause}
      GROUP BY DATE(date), type
      ORDER BY date DESC
    `;

    const [rows] = await require('../config/db').execute(query, params);

    // 按日期分组
    const summary = {};

    rows.forEach((row) => {
      const date = row.date;
      if (!summary[date]) {
        summary[date] = {
          income: 0,
          expense: 0,
          balance: 0,
          count: 0,
        };
      }

      if (row.type === 'income') {
        summary[date].income += parseFloat(row.total) || 0;
      } else if (row.type === 'expense') {
        summary[date].expense += parseFloat(row.total) || 0;
      }

      summary[date].count += row.count;
      summary[date].balance = summary[date].income - summary[date].expense;
    });

    return summary;
  }

  // 计算内部收益率 (IRR) - 使用牛顿迭代法
  calculateIRR(cashFlows, tolerance = 0.0001, maxIterations = 100) {
    if (!Array.isArray(cashFlows) || cashFlows.length === 0) {
      throw new Error('现金流数据无效');
    }

    // 初始猜测值为 0.1 (10%)
    let rate = 0.1;

    for (let i = 0; i < maxIterations; i++) {
      let npv = 0;
      let dNpv = 0;

      for (let j = 0; j < cashFlows.length; j++) {
        npv += cashFlows[j] / Math.pow(1 + rate, j);
        dNpv -= j * cashFlows[j] / Math.pow(1 + rate, j + 1);
      }

      const newRate = rate - npv / dNpv;

      // 检查是否收敛
      if (Math.abs(newRate - rate) < tolerance) {
        return newRate;
      }

      rate = newRate;
    }

    // 如果在最大迭代次数内未收敛，返回当前值
    return rate;
  }

  // 计算净现值 (NPV)
  calculateNPV(cashFlows, rate) {
    let npv = 0;

    for (let i = 0; i < cashFlows.length; i++) {
      npv += cashFlows[i] / Math.pow(1 + rate, i);
    }

    return npv;
  }

  // 计算投资回报期
  calculatePaybackPeriod(cashFlows) {
    if (cashFlows.length === 0) {
      throw new Error('现金流数据无效');
    }

    let cumulative = 0;

    for (let i = 0; i < cashFlows.length; i++) {
      cumulative += cashFlows[i];

      if (cumulative >= 0) {
        // 计算精确的回收期
        const previousCumulative = cumulative - cashFlows[i];
        const fraction = -previousCumulative / cashFlows[i];
        return i + fraction;
      }
    }

    // 如果投资从未回收，返回 null
    return null;
  }
}

module.exports = new FinanceService();
