const express = require("express");
const router = express.Router();
const shareController = require("../controller");

// 公开接口，无需 authGuard
router.get("/:token", shareController.viewShare);
router.post("/password", shareController.verifyPassword);

module.exports = router;
