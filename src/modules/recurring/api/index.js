const express = require("express");
const router = express.Router();
const recurringController = require("../controller");
const authGuard = require("../../../common/middleware/authGuard");
const pinLockGuard = require("../../../common/middleware/pinLockGuard");

router.use(authGuard);

router.get("/list", recurringController.list);
router.get("/summary", recurringController.summary);
router.get("/:id", recurringController.detail);
router.post("/", recurringController.create);
router.put("/:id", recurringController.update);
router.put("/:id/month-status", recurringController.updateMonthStatus);
router.delete("/:id", pinLockGuard, recurringController.delete);

module.exports = router;
