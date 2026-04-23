const db = require("../../../common/config/db");
const { nanoid } = require("nanoid");

class Moment {
  /**
   * 【已启用】
   * 创建动态
   * parentId = null 表示今日第一条（父亲），parent_id = id
   * parentId = 有值 表示追加，parent_id = parentId
   */
  static async create({
    userId,
    parentId,
    content,
    imgUrl,
    mood,
    location,
    visibleType = 0,
  }) {
    const id = nanoid();
    const now = Date.now().toString();

    // 如果是今日第一条（parentId = null），parent_id = id（指向自己）
    // 否则 parent_id = parentId（指向今日第一条）
    const finalParentId = parentId || id;

    const sql = `
      INSERT INTO moment 
      (id, user_id, parent_id, content, img_url, mood, location, visible_type, create_time, update_time, is_deleted) 
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)`;

    await db.execute(sql, [
      id,
      userId,
      finalParentId,
      content || "",
      imgUrl,
      mood,
      location,
      visibleType,
      now,
      now,
    ]);
    return { id };
  }

  /**
   * 【已启用】
   * 查找用户今天的第一条主动态（用于聚合）
   * 主动态：parent_id = id
   */
  static async findTodayMain(userId) {
    const start = new Date();
    start.setHours(0, 0, 0, 0);
    const end = new Date();
    end.setHours(23, 59, 59, 999);
    const sql = `
      SELECT id FROM moment 
      WHERE user_id = ? 
        AND parent_id = id 
        AND create_time BETWEEN ? AND ? 
        AND is_deleted = 0 
      LIMIT 1`;
    const [rows] = await db.execute(sql, [
      userId,
      start.getTime().toString(),
      end.getTime().toString(),
    ]);
    return rows[0] || null;
  }

  /**
   * 【已启用】
   * 分页列表：主动态列表（parent_id = id）+ 子动态 id 数组（GROUP_CONCAT）
   */
  static async findByUser(userId, { page, pageSize }) {
    const offset = (page - 1) * pageSize;

    // 查询主动态：parent_id = id（自己的 parent_id 指向自己）
    const sql = `
      SELECT m.*, 
      (SELECT GROUP_CONCAT(id ORDER BY create_time ASC) 
       FROM moment 
       WHERE parent_id = m.id AND id != m.id AND is_deleted = 0) as children
      FROM moment m
      WHERE m.user_id = ? 
        AND m.parent_id = m.id 
        AND m.is_deleted = 0
      ORDER BY m.create_time DESC 
      LIMIT ? OFFSET ?`;

    // 查询总数：统计主动态（parent_id = id）
    const countSql = `
      SELECT COUNT(*) as total FROM moment 
      WHERE user_id = ? 
        AND parent_id = id 
        AND is_deleted = 0`;

    const [list] = await db.execute(sql, [userId, pageSize, offset]);
    const [counts] = await db.execute(countSql, [userId]);

    return {
      list,
      total: counts[0]?.total || 0,
      page,
      pageSize,
      totalPages: Math.ceil((counts[0]?.total || 0) / pageSize),
    };
  }

  /**
   * 【已启用】
   * 今日动态（含所有子动态）
   * 主动态：parent_id = id
   */
  static async findTodayWithChildren(userId) {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayStart = today.getTime().toString();

    const query = `
      SELECT m.*, 
      (SELECT GROUP_CONCAT(id ORDER BY create_time ASC) 
       FROM moment 
       WHERE parent_id = m.id AND id != m.id AND is_deleted = 0) as children
      FROM moment m
      WHERE m.user_id = ? AND m.is_deleted = 0 AND m.create_time >= ?
      ORDER BY m.create_time ASC`;

    const [rows] = await db.execute(query, [userId, todayStart]);

    // 分离父级和子级：parent_id = id 的是主动态
    const result = [];
    const childrenMap = {};

    rows.forEach((item) => {
      if (item.parent_id === item.id) {
        result.push({ ...item, children_data: [] });
      } else {
        if (!childrenMap[item.parent_id]) {
          childrenMap[item.parent_id] = [];
        }
        childrenMap[item.parent_id].push(item);
      }
    });

    // 挂载子动态
    result.forEach((item) => {
      if (childrenMap[item.id]) {
        item.children_data = childrenMap[item.id];
      }
    });

    return result;
  }

  /**
   * 【已启用】
   * 单条查询
   */
  static async findById(id) {
    const sql = `
      SELECT m.*, 
      (SELECT GROUP_CONCAT(id ORDER BY create_time ASC) 
       FROM moment 
       WHERE parent_id = m.id AND id != m.id AND is_deleted = 0) as children
      FROM moment m 
      WHERE m.id = ? AND m.is_deleted = 0`;
    const [rows] = await db.execute(sql, [id]);
    return rows[0] || null;
  }

  /**
   * 【已启用】
   * 批量查询
   */
  static async findByIds(ids, userId) {
    if (!ids || ids.length === 0) return [];
    const placeholders = ids.map(() => "?").join(",");
    const sql = `
      SELECT * FROM moment 
      WHERE id IN (${placeholders}) AND user_id = ? AND is_deleted = 0
      ORDER BY create_time ASC`;
    const [rows] = await db.execute(sql, [...ids, userId]);
    return rows;
  }

  /**
   * 【已启用】
   * 更新动态
   */
  static async update(id, userId, updates) {
    const fields = [];
    const values = [];

    if (updates.content !== undefined) {
      fields.push("content = ?");
      values.push(updates.content);
    }
    if (updates.imgUrl !== undefined) {
      fields.push("img_url = ?");
      values.push(updates.imgUrl);
    }
    if (updates.mood !== undefined) {
      fields.push("mood = ?");
      values.push(updates.mood);
    }
    if (updates.location !== undefined) {
      fields.push("location = ?");
      values.push(updates.location);
    }
    if (updates.visibleType !== undefined) {
      fields.push("visible_type = ?");
      values.push(updates.visibleType);
    }

    if (fields.length === 0) return { status: 200, message: "无更新内容" };

    fields.push("update_time = ?");
    values.push(Date.now().toString());
    values.push(id, userId);

    const sql = `UPDATE moment SET ${fields.join(
      ", "
    )} WHERE id = ? AND user_id = ? AND is_deleted = 0`;
    const [result] = await db.execute(sql, values);

    if (result.affectedRows === 0)
      return { status: 404, message: "动态不存在或无权修改" };
    return { status: 200, message: "更新成功" };
  }

  /**
   * 【已启用】
   * 删除动态（软删除 + 父动态重绑定）
   * 逻辑：
   * - 子动态(parent_id != null)：直接软删除
   * - 父动态(parent_id = null)：找第一个子动态升级为父，剩下的子动态绑定到新父
   */
  static async delete(id, userId) {
    // 1️⃣ 查当前动态
    const [rows] = await db.execute(
      `SELECT id, parent_id
     FROM moment
     WHERE id = ? AND user_id = ? AND is_deleted = 0
     LIMIT 1`,
      [id, userId]
    );

    if (!rows.length) {
      return { status: 404, message: "动态不存在或无权删除" };
    }

    const moment = rows[0];
    const now = Date.now().toString();

    // 2️⃣ 判断是否父（自引用）
    const isParent = moment.id === moment.parent_id;

    // =========================
    // ✅ 子动态：直接删
    // =========================
    if (!isParent) {
      await db.execute(
        `UPDATE moment SET is_deleted = 1, update_time = ? WHERE id = ?`,
        [now, id]
      );

      return { status: 200, message: "删除成功" };
    }

    // =========================
    // ✅ 父动态：重建结构
    // =========================

    // 3️⃣ 找所有子（只靠 parent_id）
    const [children] = await db.execute(
      `SELECT id
     FROM moment
     WHERE parent_id = ?
       AND id != parent_id
       AND user_id = ?
       AND is_deleted = 0
     ORDER BY create_time ASC`,
      [id, userId]
    );

    if (children.length > 0) {
      const newParentId = children[0].id;

      // 4️⃣ 子1 → 升级为父（自己指向自己）
      await db.execute(
        `UPDATE moment SET parent_id = id, update_time = ? WHERE id = ?`,
        [now, newParentId]
      );

      // 5️⃣ 其他子 → 全部挂到新父
      await db.execute(
        `UPDATE moment
       SET parent_id = ?, update_time = ?
       WHERE parent_id = ?
         AND id != ?
         AND id != parent_id`,
        [newParentId, now, id, newParentId]
      );
    }

    // 6️⃣ 删除旧父
    await db.execute(
      `UPDATE moment SET is_deleted = 1, update_time = ? WHERE id = ?`,
      [now, id]
    );

    return { status: 200, message: "删除成功" };
  }
}

module.exports = Moment;
