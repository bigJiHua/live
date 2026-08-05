const CreditAccount = require('../model/credit');

/**
 * 信用卡账务控制器
 * 处理信用卡消费的创建、更新、冲正
 */
class CreditController {
  /**
   * 创建信用卡消费记录
   */
  async create(req, res) {
    try {
      const transaction = await CreditAccount.create({
        userId: req.userId,
        ...req.body.data,
      });

      return res.status(200).json({
        status: 200,
        message: "创建成功",
        data: transaction,
      });
    } catch (error) {
      console.error("创建信用卡消费记录错误:", error);
      return res.status(400).json({
        status: 400,
        message: error.message || "创建失败"
      });
    }
  }

  /**
   * 更新信用卡消费记录
   */
  async update(req, res) {
    try {
      const data = req.body.data || req.body;
      const transaction = await CreditAccount.update(
        req.params.id,
        req.userId,
        data
      );

      if (!transaction) return res.status(404).json({ status: 404, message: "记录不存在" });

      return res.json({ status: 200, message: "更新成功", data: transaction });
    } catch (error) {
      console.error("更新信用卡消费记录错误:", error);
      return res.status(400).json({
        status: 400,
        message: error.message || "更新失败"
      });
    }
  }

  /**
   * 信用卡消费冲正
   */
  async reverseCreditExpense(req, res) {
    try {
      const { remark } = req.body || {};

      const result = await CreditAccount.reverseCreditExpenseById(req.params.id, req.userId, remark);

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
      // 简化前端提示，详细信息仅在控制台输出
      let clientMsg = error.message || "冲正失败";
      if (clientMsg.includes("已还清") || clientMsg.includes("已有还款记录")) {
        clientMsg = "该笔消费所在的账单周期已还清，冲正将产生溢缴，请先撤销还款再冲正！";
      }
      return res.status(400).json({
        status: 400,
        message: clientMsg
      });
    }
  }

  /**
   * 信用卡还款撤销
   */
  async reverseCreditRepay(req, res) {
    try {
      const { remark } = req.body || {};

      const result = await CreditAccount.reverseCreditRepayById(req.params.id, req.userId, remark);

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
        message: error.message || "撤销失败"
      });
    }
  }
}

module.exports = new CreditController();
