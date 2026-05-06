const express = require("express");
const router = express.Router();
const { execute } = require("../../../common/config/db");
const authGuard = require("../../../common/middleware/authGuard");

router.use(authGuard);

router.get("/", async (req, res) => {
  try {
    const { page = 1, pageSize = 20 } = req.query;
    const offset = (page - 1) * pageSize;

    const [files] = await execute(
      "SELECT * FROM attachment WHERE user_id = ? ORDER BY create_time DESC LIMIT ? OFFSET ?",
      [req.userId, parseInt(pageSize), parseInt(offset)]
    );
    const [countRows] = await execute(
      "SELECT COUNT(*) as total FROM attachment WHERE user_id = ?", [req.userId]
    );

    const list = files.map(f => ({
      id: f.id,
      fileName: f.file_name,
      filePath: f.file_path,
      fileSize: f.file_size,
      fileExt: (f.file_type || '').split('/').pop(),
      createTime: f.create_time
    }));

    res.json({ status: 200, data: { list, total: countRows[0]?.total || 0, page: parseInt(page), pageSize: parseInt(pageSize) } });
  } catch (error) {
    console.error("[Resource] list error:", error);
    res.json({ status: 200, data: { list: [], total: 0, page: 1, pageSize: 20 } });
  }
});

router.delete("/:id", async (req, res) => {
  try {
    const { id } = req.params;
    await execute("DELETE FROM attachment WHERE id = ? AND user_id = ?", [id, req.userId]);
    res.json({ status: 200, message: "删除成功" });
  } catch (error) {
    console.error("[Resource] delete error:", error);
    res.say("删除失败", 500);
  }
});

module.exports = router;
