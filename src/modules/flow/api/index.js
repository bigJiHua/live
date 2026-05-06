const express = require("express");
const router = express.Router();
const { execute } = require("../../../common/config/db");
const authGuard = require("../../../common/middleware/authGuard");

router.use(authGuard);

router.get("/calendar", async (req, res) => {
  try {
    const { year, month } = req.query;
    const m = String(month).padStart(2, '0');
    const startDate = `${year}-${m}-01`;
    const nextMonth = parseInt(month) + 1;
    const y2 = nextMonth > 12 ? parseInt(year) + 1 : parseInt(year);
    const m2 = nextMonth > 12 ? 1 : nextMonth;
    const endDate = `${y2}-${String(m2).padStart(2, '0')}-01`;

    const [flows] = await execute(
      `SELECT DATE(trans_date) as date,
              COALESCE(SUM(CASE WHEN direction = 1 THEN amount ELSE 0 END), 0) as income,
              COALESCE(SUM(CASE WHEN direction = 0 THEN amount ELSE 0 END), 0) as expense
       FROM account
       WHERE user_id = ? AND is_deleted = 0 AND trans_date >= ? AND trans_date < ?
       GROUP BY DATE(trans_date) ORDER BY date`,
      [req.userId, startDate, endDate]
    );

    const result = {};
    flows.forEach(f => {
      result[f.date] = { income: f.income || 0, expense: f.expense || 0 };
    });
    res.json({ status: 200, data: result });
  } catch (error) {
    console.error("[Flow] calendar error:", error);
    res.json({ status: 200, data: {} });
  }
});

router.get("/:id", async (req, res) => {
  try {
    const { id } = req.params;
    const [rows] = await execute(
      "SELECT * FROM account WHERE id = ? AND user_id = ? AND is_deleted = 0", [id, req.userId]
    );
    if (!rows.length) return res.say("记录不存在", 404);
    res.json({ status: 200, data: rows[0] });
  } catch (error) {
    console.error("[Flow] detail error:", error);
    res.say("查询失败", 500);
  }
});

module.exports = router;
