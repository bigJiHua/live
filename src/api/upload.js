const express = require("express");
const router = express.Router();
const multer = require("multer");
const path = require("path");
const fs = require("fs");
const uploadController = require("../controllers/uploadController");
const authGuard = require("../middlewares/authGuard");

// 所有上传路由都需要认证
router.use(authGuard);

// 确保 temp 目录存在
const tempDir = path.join(process.cwd(), "public", "uploads", "temp");
if (!fs.existsSync(tempDir)) {
  fs.mkdirSync(tempDir, { recursive: true });
}

// 配置文件上传中间件
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    // 临时存储目录，上传后移动到目标位置
    cb(null, tempDir);
  },
  filename: (req, file, cb) => {
    // 临时文件名，加上随机字符串保证不重复
    const ext = path.extname(file.originalname);
    const random = Math.random().toString(36).substring(2, 8);
    cb(null, `temp_${Date.now()}_${random}${ext}`);
  },
});

const upload = multer({
  storage,
  limits: {
    fileSize: 10 * 1024 * 1024, // 10MB
  },
});

// 【已启用】
// 单文件上传
// 参数: type=image|file|video|audio, busType=avatar|post|product, busId=可选
router.post("/single", upload.single("file"), uploadController.upload);

// 【已启用】
// 多文件上传
router.post("/multiple", upload.array("files", 10), uploadController.uploadMultiple);

// 【已启用】
// 查询附件列表
router.get("/list", uploadController.list);

// 【已启用】
// 搜索附件（按类型和关键词）
router.get("/search", uploadController.search);

// 【已启用】
// 批量删除附件（必须在 /:id 前面，否则会被 :id 匹配）
router.post("/batch-delete", uploadController.batchDelete);

// 【已启用】
// 编辑附件（修改 remark 和 tags）
router.post("/:id", uploadController.edit);

module.exports = router;
