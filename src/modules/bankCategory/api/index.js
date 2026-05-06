const express = require("express");
const router = express.Router();
const { execute } = require("../../../common/config/db");
const authGuard = require("../../../common/middleware/authGuard");

router.use(authGuard);

router.get("/", async (req, res) => {
  try {
    const [banks] = await execute(
      "SELECT * FROM bus_category WHERE user_id = ? AND type = 'bank' AND is_deleted = 0 ORDER BY sort ASC, name ASC",
      [req.userId]
    );
    res.json({ status: 200, data: banks });
  } catch (error) {
    console.error("[BankCategory] list error:", error);
    res.json({ status: 200, data: [] });
  }
});

router.post("/", async (req, res) => {
  try {
    const { name, iconUrl, remark, sort = 99 } = req.body;
    const { nanoid } = require("nanoid");
    const id = "BC_" + nanoid(10);

    await execute(
      "INSERT INTO bus_category (id, user_id, name, icon_url, remark, type, sort, is_deleted) VALUES (?, ?, ?, ?, ?, 'bank', ?, 0)",
      [id, req.userId, name, iconUrl || '', remark || '', sort]
    );

    const [rows] = await execute("SELECT * FROM bus_category WHERE id = ?", [id]);
    res.json({ status: 200, data: rows[0] });
  } catch (error) {
    console.error("[BankCategory] create error:", error);
    res.say("创建失败", 500);
  }
});

router.put("/:id", async (req, res) => {
  try {
    const { id } = req.params;
    const { name, iconUrl, remark, sort } = req.body;

    await execute(
      "UPDATE bus_category SET name = ?, icon_url = ?, remark = ?, sort = ? WHERE id = ? AND user_id = ? AND type = 'bank'",
      [name, iconUrl || '', remark || '', sort || 99, id, req.userId]
    );

    const [rows] = await execute("SELECT * FROM bus_category WHERE id = ?", [id]);
    res.json({ status: 200, data: rows[0] });
  } catch (error) {
    console.error("[BankCategory] update error:", error);
    res.say("更新失败", 500);
  }
});

router.delete("/:id", async (req, res) => {
  try {
    const { id } = req.params;
    await execute("UPDATE bus_category SET is_deleted = 1 WHERE id = ? AND user_id = ? AND type = 'bank'", [id, req.userId]);
    res.json({ status: 200, message: "删除成功" });
  } catch (error) {
    console.error("[BankCategory] delete error:", error);
    res.say("删除失败", 500);
  }
});

module.exports = router;
