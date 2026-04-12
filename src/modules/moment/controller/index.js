const Moment = require("../model");

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
        visibleType: visibleType || 0,
      });
      res.status(200).json({ status: 200, message: "发布成功", id: result.id });
    } catch (error) {
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
        // content 只返回最多20个字符作为标题
        parsed.content = parsed.content ? parsed.content.substring(0, 20) : "";
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

  // --- 找回你原本的更新方法 ---
  update = async (req, res) => {
    try {
      const { id } = req.params;
      const { content, images, mood, location, visibleType } =
        req.body.data || req.body;

      const updates = {};
      if (content !== undefined) updates.content = content;
      if (images !== undefined)
        updates.imgUrl = images ? JSON.stringify(images) : null;
      if (mood !== undefined) updates.mood = mood ? JSON.stringify(mood) : null;
      if (location !== undefined)
        updates.location = location ? JSON.stringify(location) : null;
      if (visibleType !== undefined) updates.visibleType = visibleType;

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

module.exports = new MomentController();
