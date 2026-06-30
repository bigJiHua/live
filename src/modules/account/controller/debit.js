const DebitAccount = require('../model/debit');

/**
 * 借记卡账务控制器
 * 处理借记卡/虚拟账户的收支创建、更新、冲正
 */
class DebitController {
  /**
   * 创建借记卡收支记录
   */
  async create(req, res) {
    try {
      const transaction = await DebitAccount.create({
        userId: req.userId,
        ...req.body.data,
      });

      return res.status(200).json({
        status: 200,
        message: "创建成功",
        data: transaction,
      });
    } catch (error) {
      console.error("创建借记卡收支记录错误:", error);
      return res.status(400).json({
        status: 400,
        message: error.message || "创建失败"
      });
    }
  }

  /**
   * 更新借记卡收支记录
   */
  async update(req, res) {
    try {
      const data = req.body.data || req.body;
      const transaction = await DebitAccount.update(
        req.params.id,
        req.userId,
        data
      );

      if (!transaction) return res.status(404).json({ status: 404, message: "记录不存在" });

      return res.json({ status: 200, message: "更新成功", data: transaction });
    } catch (error) {
      console.error("更新借记卡收支记录错误:", error);
      return res.status(400).json({
        status: 400,
        message: error.message || "更新失败"
      });
    }
  }

  /**
   * 借记卡冲正
   */
  async reverseDebit(req, res) {
    try {
      const { remark } = req.body || {};

      const result = await DebitAccount.reverseDebitById(req.params.id, req.userId, remark);

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
      // 简化前端提示，详细信息仅在控制台输出
      let clientMsg = error.message || "冲正失败";
      if (clientMsg.includes("余额不足")) {
        clientMsg = "交易余额不足，无法冲正";
      }
      return res.status(400).json({
        status: 400,
        message: clientMsg
      });
    }
  }

  /**
   * 转账冲正
   */
  async reverseTransfer(req, res) {
    try {
      const { remark } = req.body || {};

      const result = await DebitAccount.reverseTransferById(req.params.id, req.userId, remark);

      return res.json({
        status: 200,
        message: "转账冲正成功",
        data: {
          reversed: result,
          originalId: req.params.id
        }
      });
    } catch (error) {
      console.error("转账冲正错误:", error);
      return res.status(400).json({
        status: 400,
        message: error.message || "冲正失败"
      });
    }
  }
}

module.exports = new DebitController();
