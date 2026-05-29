const BankCategory = require("../model");

const bankCategoryController = {
  async getList(req, res) {
    try {
      const banks = await BankCategory.findAll(req.userId);
      res.json({ status: 200, data: banks });
    } catch (error) {
      console.error("[BankCategory] list error:", error);
      res.json({ status: 200, data: [] });
    }
  },

  async create(req, res) {
    try {
      const bank = await BankCategory.create({
        userId: req.userId,
        ...req.body,
      });
      res.json({ status: 200, data: bank });
    } catch (error) {
      console.error("[BankCategory] create error:", error);
      res.say("创建失败", 500);
    }
  },

  async update(req, res) {
    try {
      const bank = await BankCategory.update(req.params.id, req.userId, req.body);
      res.json({ status: 200, data: bank });
    } catch (error) {
      console.error("[BankCategory] update error:", error);
      res.say("更新失败", 500);
    }
  },

  async delete(req, res) {
    try {
      await BankCategory.delete(req.params.id, req.userId);
      res.json({ status: 200, message: "删除成功" });
    } catch (error) {
      console.error("[BankCategory] delete error:", error);
      res.say("删除失败", 500);
    }
  },
};

module.exports = bankCategoryController;
