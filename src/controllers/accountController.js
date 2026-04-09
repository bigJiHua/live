const Account = require("../models/Account");

/**
 * 账务记录控制器
 */
class AccountController {
  /**
   * 获取收支列表
   */
  async getList(req, res) {
    try {
      const {
        page = 1,
        limit = 20,
        direction,
        categoryId,
        payMethod,
        startDate,
        endDate,
      } = req.query;
      const filters = {};

      if (direction !== undefined) filters.direction = parseInt(direction);
      if (categoryId) filters.categoryId = categoryId;
      if (payMethod) filters.payMethod = payMethod;
      if (startDate) filters.startDate = startDate;
      if (endDate) filters.endDate = endDate;

      const result = await Account.findAll(
        req.userId,
        filters,
        parseInt(page),
        parseInt(limit)
      );

      if (!result.rows || result.rows.length === 0) {
        return res.status(200).send({
          status: 200,
          message: "暂无记录",
          data: [],
        });
      }

      return res.json({
        status: 200,
        message: "获取成功",
        data: {
          list: result.rows,
          pagination: {
            page: parseInt(page),
            limit: parseInt(limit),
            total: result.total,
            totalPages: Math.ceil(result.total / limit),
          },
        },
      });
    } catch (error) {
      console.error("获取收支列表错误:", error);
      return res.say("获取失败", 500);
    }
  }

  /**
   * 获取单条收支详情
   */
  async getById(req, res) {
    if (!req.params.id) return res.say("记录id不能为空", 400);
    try {
      const transaction = await Account.findById(req.params.id, req.userId);

      if (!transaction) return res.say("记录不存在", 404);

      return res.json({ status: 200, message: "获取成功", data: transaction });
    } catch (error) {
      console.error("获取收支详情错误:", error);
      return res.say("获取失败", 500);
    }
  }

  /**
   * 创建收支记录
   */
  async create(req, res) {
    try {
      const transaction = await Account.create({
        userId: req.userId,
        ...req.body.data,
      });

      return res.status(200).json({
        status: 200,
        message: "创建成功",
        data: transaction,
      });
    } catch (error) {
      console.error("创建收支记录错误:", error);
      return res.say(error, 400);
    }
  }

  /**
   * 更新收支记录
   */
  async update(req, res) {
    try {
      const transaction = await Account.update(
        req.params.id,
        req.userId,
        req.body.data
      );

      if (!transaction) return res.say("记录不存在", 404);

      return res.json({ status: 200, message: "更新成功", data: transaction });
    } catch (error) {
      console.error("更新收支记录错误:", error);
      return res.say("更新失败", 500);
    }
  }

  /**
   * 删除收支记录
   */
  async delete(req, res) {
    try {
      const result = await Account.delete(req.params.id, req.userId);

      if (!result) return res.say("记录不存在", 404);

      return res.json({ status: 200, message: "删除成功" });
    } catch (error) {
      console.error("删除收支记录错误:", error);
      return res.say("删除失败", 500);
    }
  }

  /**
   * 获取本月收支统计
   * @param type=summary 返回总计 | type=detail 返回明细（按分类）
   */
  async getMonthStats(req, res) {
    try {
      const now = new Date();
      const year = parseInt(req.query.year) || now.getFullYear();
      const month = parseInt(req.query.month) || now.getMonth() + 1;
      const type = req.query.type; // summary=总计, detail=明细
      const stats = await Account.getMonthStats(req.userId, year, month);

      // 如果传了 type=summary，只返回总计
      if (type === "summary") {
        return res.json({
          status: 200,
          message: "获取成功",
          data: stats,
        });
      }

      // 默认返回明细（按分类）
      const categoryStats = await Account.getStatsByCategory(
        req.userId,
        year,
        month
      );

      return res.json({
        status: 200,
        message: "获取成功",
        data: {
          ...stats,
          categoryBreakdown: categoryStats,
        },
      });
    } catch (error) {
      console.error("获取本月统计错误:", error);
      return res.say("获取失败", 500);
    }
  }
}

module.exports = new AccountController();
