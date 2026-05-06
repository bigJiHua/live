const express = require("express");
const router = express.Router();
const { execute } = require("../../../common/config/db");
const authGuard = require("../../../common/middleware/authGuard");
const dayjs = require("dayjs");

router.use(authGuard);

router.get("/", async (req, res) => {
  try {
    const userId = req.userId;
    const today = dayjs().format("YYYY-MM-DD");
    const currentMonth = dayjs().format("YYYY-MM");

    const [rows] = await execute(
      `SELECT COALESCE(SUM(balance), 0) as total FROM account_balance WHERE user_id = ? AND is_deleted = 0`, [userId]
    );
    const totalBalance = rows[0]?.total || 0;

    const [todayRows] = await execute(
      `SELECT COALESCE(SUM(CASE WHEN direction = 1 THEN amount ELSE 0 END), 0) as todayIncome,
              COALESCE(SUM(CASE WHEN direction = 0 THEN amount ELSE 0 END), 0) as todayExpense
       FROM account WHERE user_id = ? AND trans_date = ? AND is_deleted = 0`, [userId, today]
    );

    const [monthRows] = await execute(
      `SELECT COALESCE(SUM(CASE WHEN direction = 1 THEN amount ELSE 0 END), 0) as monthIncome,
              COALESCE(SUM(CASE WHEN direction = 0 THEN amount ELSE 0 END), 0) as monthExpense
       FROM account WHERE user_id = ? AND trans_date LIKE ? AND is_deleted = 0`, [userId, `${currentMonth}%`]
    );

    const [cardRows] = await execute(
      `SELECT COALESCE(SUM(CASE WHEN card_type = 'debit' THEN 1 ELSE 0 END), 0) as debitCardCount,
              COALESCE(SUM(CASE WHEN card_type = 'credit' THEN 1 ELSE 0 END), 0) as creditCardCount
       FROM card_base WHERE user_id = ? AND is_deleted = 0`, [userId]
    );

    const [debtRows] = await execute(
      `SELECT COALESCE(SUM(need_repay), 0) as total FROM card_bill WHERE user_id = ? AND repay_status != '已还' AND is_deleted = 0`, [userId]
    );

    const [recentRows] = await execute(
      `SELECT a.*, c.name as category_name FROM account a
       LEFT JOIN bus_category c ON a.category_id = c.id
       WHERE a.user_id = ? AND a.is_deleted = 0
       ORDER BY a.trans_date DESC, a.create_time DESC LIMIT 10`, [userId]
    );

    res.json({ status: 200, data: {
      totalBalance: totalBalance || 0,
      todayIncome: todayRows[0]?.todayIncome || 0,
      todayExpense: todayRows[0]?.todayExpense || 0,
      monthIncome: monthRows[0]?.monthIncome || 0,
      monthExpense: monthRows[0]?.monthExpense || 0,
      monthlySurplus: (monthRows[0]?.monthIncome || 0) - (monthRows[0]?.monthExpense || 0),
      debitCardCount: cardRows[0]?.debitCardCount || 0,
      creditCardCount: cardRows[0]?.creditCardCount || 0,
      creditToPay: debtRows[0]?.total || 0,
      recentRecords: recentRows || []
    }});
  } catch (error) {
    console.error("[Dashboard] Error:", error);
    res.status(200).json({ status: 200, data: {
      totalBalance: 0, todayIncome: 0, todayExpense: 0,
      monthIncome: 0, monthExpense: 0, monthlySurplus: 0,
      debitCardCount: 0, creditCardCount: 0, creditToPay: 0, recentRecords: []
    }});
  }
});

router.get("/v2", async (req, res) => {
  try {
    const userId = req.userId;
    const today = dayjs().format("YYYY-MM-DD");
    const currentMonth = dayjs().format("YYYY-MM");

    const [rows] = await execute(
      `SELECT COALESCE(SUM(balance), 0) as total FROM account_balance WHERE user_id = ? AND is_deleted = 0`, [userId]
    );

    const [todayRows] = await execute(
      `SELECT COALESCE(SUM(CASE WHEN direction = 1 THEN amount ELSE 0 END), 0) as todayIncome,
              COALESCE(SUM(CASE WHEN direction = 0 THEN amount ELSE 0 END), 0) as todayExpense
       FROM account WHERE user_id = ? AND trans_date = ? AND is_deleted = 0`, [userId, today]
    );

    const [monthRows] = await execute(
      `SELECT COALESCE(SUM(CASE WHEN direction = 1 THEN amount ELSE 0 END), 0) as monthIncome,
              COALESCE(SUM(CASE WHEN direction = 0 THEN amount ELSE 0 END), 0) as monthExpense
       FROM account WHERE user_id = ? AND trans_date LIKE ? AND is_deleted = 0`, [userId, `${currentMonth}%`]
    );

    const [cardRows] = await execute(
      `SELECT COALESCE(SUM(CASE WHEN card_type = 'debit' THEN 1 ELSE 0 END), 0) as debitCardCount,
              COALESCE(SUM(CASE WHEN card_type = 'credit' THEN 1 ELSE 0 END), 0) as creditCardCount
       FROM card_base WHERE user_id = ? AND is_deleted = 0`, [userId]
    );

    const [debtRows] = await execute(
      `SELECT COALESCE(SUM(need_repay), 0) as total FROM card_bill WHERE user_id = ? AND repay_status != '已还' AND is_deleted = 0`, [userId]
    );

    const [budgetRows] = await execute(
      "SELECT * FROM budget WHERE user_id = ? AND is_deleted = 0 ORDER BY create_time DESC", [userId]
    );

    const [todoRows] = await execute(
      "SELECT * FROM todo WHERE user_id = ? AND status = '待完成' AND is_deleted = 0 ORDER BY happen_date ASC LIMIT 10", [userId]
    );

    const [recentRows] = await execute(
      `SELECT a.*, c.name as category_name FROM account a
       LEFT JOIN bus_category c ON a.category_id = c.id
       WHERE a.user_id = ? AND a.is_deleted = 0
       ORDER BY a.trans_date DESC, a.create_time DESC LIMIT 10`, [userId]
    );

    res.json({ status: 200, data: {
      totalBalance: rows[0]?.total || 0,
      todayIncome: todayRows[0]?.todayIncome || 0,
      todayExpense: todayRows[0]?.todayExpense || 0,
      monthIncome: monthRows[0]?.monthIncome || 0,
      monthExpense: monthRows[0]?.monthExpense || 0,
      monthlySurplus: (monthRows[0]?.monthIncome || 0) - (monthRows[0]?.monthExpense || 0),
      debitCardCount: cardRows[0]?.debitCardCount || 0,
      creditCardCount: cardRows[0]?.creditCardCount || 0,
      creditToPay: debtRows[0]?.total || 0,
      budgets: budgetRows || [],
      todos: todoRows || [],
      recentRecords: recentRows || []
    }});
  } catch (error) {
    console.error("[Dashboard V2] Error:", error);
    res.status(200).json({ status: 200, data: {
      totalBalance: 0, todayIncome: 0, todayExpense: 0,
      monthIncome: 0, monthExpense: 0, monthlySurplus: 0,
      debitCardCount: 0, creditCardCount: 0, creditToPay: 0,
      budgets: [], todos: [], recentRecords: []
    }});
  }
});

module.exports = router;
