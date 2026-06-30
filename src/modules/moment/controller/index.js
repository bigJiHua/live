const Moment = require("../model");
const { generateShareToken } = require("../../../common/utils/shareToken");

const genPw = () => String(Math.floor(100000 + Math.random() * 900000)); // 6位数字

class MomentController {
  /**
   * 统一解析函数
   */
  _parseFields = (item) => {
    if (!item) return null;
    let childIds = [];
    if (item.children) {
      childIds =
        typeof item.children === "string"
          ? item.children.split(",").filter(Boolean)
          : Array.isArray(item.children)
          ? item.children
          : [];
    }
    return {
      ...item,
      img_url: item.img_url ? JSON.parse(item.img_url) : [],
      mood: item.mood ? JSON.parse(item.mood) : null,
      location: item.location ? JSON.parse(item.location) : null,
      visible_type: (() => {
        if (!item.visible_type) return { vt: 0, vs: 0, pw: 0 };
        try {
          const parsed = JSON.parse(item.visible_type);
          // 兼容旧 tinyint 值：解析出数字 0 → 转为对象
          if (typeof parsed === 'number') return { vt: parsed, vs: 0, pw: 0 };
          return parsed;
        } catch {
          return { vt: 0, vs: 0, pw: 0 };
        }
      })(),
      children: childIds,
    };
  };

  // 【创建】
  create = async (req, res) => {
    try {
      const { content, images, mood, location, visibleType } =
        req.body.data || req.body;
      const todayMain = await Moment.findTodayMain(req.userId);
      const result = await Moment.create({
        userId: req.userId,
        parentId: todayMain ? todayMain.id : null, // null means first of day
        content: content || "",
        imgUrl: images ? JSON.stringify(images) : null,
        mood: mood ? JSON.stringify(mood) : null,
        location: location ? JSON.stringify(location) : null,
        visibleType: visibleType
          ? JSON.stringify(visibleType)
          : '{"vt":0,"vs":0,"pw":0}',
      });
      res.status(200).json({ status: 200, message: "发布成功", id: result.id });
    } catch (error) {
      console.log(error);
      
      res.status(500).json({ status: 500, message: "发布失败" });
    }
  };

  // 【列表】
  list = async (req, res) => {
    try {
      const { page = 1, pageSize = 10 } = req.query;
      const result = await Moment.findByUser(req.userId, {
        page: parseInt(page),
        pageSize: parseInt(pageSize),
      });
      result.list = result.list.map((item) => {
        const parsed = this._parseFields(item);
        const stripped = parsed.content ? parsed.content.replace(/<[^>]*>/g, '') : '';
        parsed.content = stripped.substring(0, 20);
        return parsed;
      });
      res.status(200).json({ status: 200, data: result });
    } catch (error) {
      res.status(500).json({ status: 500, message: "查询失败" });
    }
  };

  // 【今日】
  today = async (req, res) => {
    try {
      const moments = await Moment.findTodayWithChildren(req.userId);
      const data = moments.map((parent) => ({
        ...this._parseFields(parent),
        children_data: (parent.children_data || []).map((child) =>
          this._parseFields(child)
        ),
      }));
      res.status(200).json({ status: 200, data });
    } catch (error) {
      res.status(500).json({ status: 500, message: "查询今日动态失败" });
    }
  };

  // 【详情】
  detail = async (req, res) => {
    try {
      const moment = await Moment.findById(req.params.id);
      if (!moment || moment.user_id !== req.userId) {
        return res.status(404).json({ message: "未找到" });
      }
      res.status(200).json({ status: 200, data: this._parseFields(moment) });
    } catch (error) {
      res.status(500).json({ status: 500, message: "详情查询失败" });
    }
  };

  // 【批量详情】
  batchDetail = async (req, res) => {
    try {
      const { ids } = req.body.data || req.body;
      if (!ids || !ids.length)
        return res.status(200).json({ status: 200, data: [] });
      const moments = await Moment.findByIds(ids, req.userId);
      const data = moments.map((item) => this._parseFields(item));
      res.status(200).json({ status: 200, data });
    } catch (error) {
      res.status(500).json({ status: 500, message: "批量查询失败" });
    }
  };

  // --- 通用更新 ---
  update = async (req, res) => {
    try {
      const { id } = req.params;
      const {
        content, images, mood, location, visibleType,
        shareAction, shareDuration,
      } = req.body.data || req.body;

      // 分享相关 → 独立方法
      if (["open", "close", "token"].includes(shareAction)) {
        return handleShareAction(req, res, {
          id,
          userId: req.userId,
          action: shareAction,
          duration: shareDuration,
        });
      }

      const updates = {};
      if (content !== undefined) updates.content = content;
      if (images !== undefined)
        updates.imgUrl = images ? JSON.stringify(images) : null;
      if (mood !== undefined) updates.mood = mood ? JSON.stringify(mood) : null;
      if (location !== undefined)
        updates.location = location ? JSON.stringify(location) : null;
      if (visibleType !== undefined)
        updates.visibleType = JSON.stringify(visibleType);

      const result = await Moment.update(id, req.userId, updates);
      return res.status(result.status || 200).json(result);
    } catch (error) {
      console.error("更新失败:", error);
      res.status(500).json({ status: 500, message: "更新失败" });
    }
  };

  // --- 找回你原本的删除方法 ---
  delete = async (req, res) => {
    try {
      const { id } = req.params;
      const result = await Moment.delete(id, req.userId);
      return res.status(result.status || 200).json(result);
    } catch (error) {
      console.error("删除失败:", error);
      res.status(500).json({ status: 500, message: "删除失败" });
    }
  };
}

// ──────────────────────────────────────────
// 分享开关（独立函数，与更新/删除同级）
// ──────────────────────────────────────────
/**
 * @param {object} req   Express request
 * @param {object} res   Express response
 * @param {object} opts
 * @param {string} opts.id       日记 ID
 * @param {string} opts.userId   用户 ID
 * @param {"open"|"close"} opts.action  操作类型
 * @param {number} [opts.duration=1]    有效小时数（仅 open）
 */
async function handleShareAction(_req, res, { id, userId, action, duration }) {
  try {
    // 权限校验
    const current = await Moment.findById(id);
    if (!current || current.user_id !== userId) {
      return res.status(404).json({ status: 404, message: "未找到" });
    }

    // 解析当前 visible_type
    let curVis = { vt: 0, vs: 0, pw: 0 };
    try {
      curVis = typeof current.visible_type === "string"
        ? JSON.parse(current.visible_type)
        : current.visible_type || curVis;
    } catch { /* fallback */ }

    // ── 开启分享 ──
    if (action === "open") {
      const newVs = curVis.vs === 0 ? 1 : curVis.vs + 1;
      const newPw = genPw();
      const expireHours = Number(duration) || 1;

      const newVis = { vt: 1, vs: newVs, pw: newPw };
      const token = generateShareToken({
        diaryId: id,
        vs: newVs,
        pw: newPw,
        expireHours,
      });

      await Moment.update(id, userId, {
        visibleType: JSON.stringify(newVis),
      });

      const shareUrl = `${process.env.FRONTEND_URL || ""}/share/diary/detail`;
      return res.json({
        status: 200,
        message: "分享已开启",
        share: {
          token,
          password: newPw,
          tokenUrl: `${shareUrl}?token=${encodeURIComponent(token)}`,
          pwUrl: `${shareUrl}?id=${id}`,
          expireHours,
        },
      });
    }

    // ── 刷新 token（不改变密码/版本号） ──
    if (action === "token") {
      if (curVis.vt !== 1) {
        return res.json({ status: 403, message: "分享未开启" });
      }
      const expireHours = Number(duration) || 1;
      const token = generateShareToken({
        diaryId: id,
        vs: curVis.vs,
        pw: curVis.pw,
        expireHours,
      });

      const shareUrl = `${process.env.FRONTEND_URL || ""}/share/diary/detail`;
      return res.json({
        status: 200,
        message: "Token 已生成",
        share: {
          token,
          tokenUrl: `${shareUrl}?token=${encodeURIComponent(token)}`,
          expireHours,
        },
      });
    }

    // ── 关闭分享 ──
    if (action === "close") {
      const newVis = { vt: 0, vs: curVis.vs, pw: 0 };
      await Moment.update(id, userId, {
        visibleType: JSON.stringify(newVis),
      });
      return res.json({ status: 200, message: "分享已关闭" });
    }
  } catch (error) {
    console.error("[ShareAction] 失败:", error);
    res.status(500).json({ status: 500, message: "分享操作失败" });
  }
}

module.exports = new MomentController();
