const RecurringExpense = require("../model");

class RecurringExpenseController {
  async list(req, res) {
    try {
      const userId = req.userId;
      const { month, includeInactive } = req.query;
      const rows = await RecurringExpense.findAll(userId, {
        month,
        includeInactive: includeInactive === "1" || includeInactive === "true",
      });
      return res.json({ status: 200, message: "查询成功", data: rows });
    } catch (error) {
      console.error("获取固定支出错误:", error);
      return res.status(500).json({ status: 500, message: error.message || "查询失败" });
    }
  }

  async summary(req, res) {
    try {
      const userId = req.userId;
      const { month } = req.query;
      if (!month) return res.status(400).json({ status: 400, message: "month 参数必填" });
      const data = await RecurringExpense.getMonthSummary(userId, month);
      return res.json({ status: 200, message: "查询成功", data });
    } catch (error) {
      console.error("获取固定支出统计错误:", error);
      return res.status(500).json({ status: 500, message: error.message || "查询失败" });
    }
  }

  async create(req, res) {
    try {
      const userId = req.userId;
      const data = req.body.data || req.body || {};
      const row = await RecurringExpense.create({
        userId,
        name: data.name,
        amount: data.amount,
        categoryId: data.category_id,
        accountId: data.account_id,
        cycle: data.cycle,
        dayOfCycle: data.day_of_cycle,
        remindDays: data.remind_days,
        remark: data.remark,
        isActive: data.is_active,
      });
      return res.json({ status: 200, message: "创建成功", data: row });
    } catch (error) {
      console.error("创建固定支出错误:", error);
      return res.status(500).json({ status: 500, message: error.message || "创建失败" });
    }
  }

  async detail(req, res) {
    try {
      const row = await RecurringExpense.findById(req.params.id, req.userId);
      if (!row) return res.status(404).json({ status: 404, message: "固定支出不存在" });
      return res.json({ status: 200, message: "查询成功", data: row });
    } catch (error) {
      console.error("获取固定支出详情错误:", error);
      return res.status(500).json({ status: 500, message: error.message || "查询失败" });
    }
  }

  async update(req, res) {
    try {
      const data = req.body.data || req.body || {};
      const row = await RecurringExpense.update(req.params.id, req.userId, {
        name: data.name,
        amount: data.amount,
        categoryId: data.category_id,
        accountId: data.account_id,
        cycle: data.cycle,
        dayOfCycle: data.day_of_cycle,
        remindDays: data.remind_days,
        remark: data.remark,
        isActive: data.is_active,
      });
      return res.json({ status: 200, message: "更新成功", data: row });
    } catch (error) {
      console.error("更新固定支出错误:", error);
      return res.status(500).json({ status: 500, message: error.message || "更新失败" });
    }
  }

  async delete(req, res) {
    try {
      const ok = await RecurringExpense.delete(req.params.id, req.userId);
      if (!ok) return res.status(404).json({ status: 404, message: "固定支出不存在" });
      return res.json({ status: 200, message: "删除成功" });
    } catch (error) {
      console.error("删除固定支出错误:", error);
      return res.status(500).json({ status: 500, message: error.message || "删除失败" });
    }
  }

  async updateMonthStatus(req, res) {
    try {
      const data = req.body.data || req.body || {};
      const row = await RecurringExpense.updateMonthStatus(req.params.id, req.userId, {
        month: data.month,
        status: data.status,
        amount: data.amount,
        remark: data.remark,
      });
      return res.json({ status: 200, message: "更新成功", data: row });
    } catch (error) {
      console.error("更新固定支出月度状态错误:", error);
      return res.status(500).json({ status: 500, message: error.message || "更新失败" });
    }
  }
}

module.exports = new RecurringExpenseController();
