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
   * 修改收支备注（仅修改备注，不涉及其他字段）
   */
  async updateRemark(req, res) {
    try {
      const { remark } = req.body;

      if (remark === undefined) {
        return res.status(400).json({
          status: 400,
          message: "备注不能为空"
        });
      }

      const result = await Account.updateRemark(req.params.id, req.userId, remark);

      if (!result) {
        return res.status(404).json({
          status: 404,
          message: "记录不存在"
        });
      }

      return res.json({
        status: 200,
        message: "备注修改成功",
        data: result
      });
    } catch (error) {
      console.error("修改备注错误:", error);
      return res.status(500).json({
        status: 500,
        message: error.message || "修改失败"
      });
    }
  }

  /**
   * 冲正流水 - 借记卡
   * 原支出(direction=0) -> 冲正收入(direction=1)
   * 原收入(direction=1) -> 冲正支出(direction=0)
   */
  async reverseDebit(req, res) {
    try {
      const { remark } = req.body || {};

      const result = await Account.reverseDebitById(req.params.id, req.userId, remark);

      return res.json({
        status: 200,
        message: "借记卡冲正成功",
        data: {
          reversed: result,
          originalId: req.params.id
        }
      });
    } catch (error) {
      console.error("借记卡冲正流水错误:", error);
      return res.status(400).json({
        status: 400,
        message: error.message || "冲正失败"
      });
    }
  }

  /**
   * 冲正流水 - 信用卡消费
   * 原支出(direction=0) -> 冲正收入(direction=1) + 恢复账单额度
   */
  async reverseCreditExpense(req, res) {
    try {
      const { remark } = req.body || {};

      const result = await Account.reverseCreditExpenseById(req.params.id, req.userId, remark);

      return res.json({
        status: 200,
        message: "信用卡消费冲正成功",
        data: {
          reversed: result,
          originalId: req.params.id
        }
      });
    } catch (error) {
      console.error("信用卡消费冲正错误:", error);
      return res.status(400).json({
        status: 400,
        message: error.message || "冲正失败"
      });
    }
  }

  /**
   * 冲正流水 - 信用卡还款撤销
   * 原还款流水 -> 冲正支出 + 软删除card_repay + 恢复账单额度
   */
  async reverseCreditRepay(req, res) {
    try {
      const { remark } = req.body || {};

      const result = await Account.reverseCreditRepayById(req.params.id, req.userId, remark);

      return res.json({
        status: 200,
        message: "信用卡还款撤销成功",
        data: {
          reversed: result,
          originalId: req.params.id
        }
      });
    } catch (error) {
      console.error("信用卡还款撤销错误:", error);
      return res.status(400).json({
        status: 400,
        message: error.message || "冲正失败"
      });
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
