const express = require("express");
const router = express.Router();
const authGuard = require("../../../common/middleware/authGuard");
const pinLockGuard = require("../../../common/middleware/pinLockGuard");
const bankCategoryController = require("../controller");

router.use(authGuard);

router.get("/", bankCategoryController.getList);

router.post("/", bankCategoryController.create);

router.put("/:id", bankCategoryController.update);

router.delete("/:id", pinLockGuard, bankCategoryController.delete);

module.exports = router;
