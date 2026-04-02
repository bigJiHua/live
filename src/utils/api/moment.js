import request from "@/utils/request";

/**
 * 动态/日记 API (moment.js)
 *
 * | 方法 | 路径 | 说明 |
 * |------|------|------|
 * | POST | `/api/moment` | 创建动态 |
 * | GET | `/api/moment/list` | 查询所有动态 |
 * | GET | `/api/moment/today` | 查询今日动态（含追加） |
 * | GET | `/api/moment/:id` | 查询单条动态 |
 * | PUT | `/api/moment/:id` | 更新动态 |
 * | DELETE | `/api/moment/:id` | 删除动态 |
 *
 * parent_id 逻辑：
 * - 今日第一条发布: parent_id = "0"
 * - 今日第二条及之后: parent_id = 今日第一条的 id
 */

export const momentApi = {
  /**
   * 创建动态
   * @param {object} data - 动态数据
   * @param {string} data.content - 文本内容
   * @param {Array<{url: string}>} data.images - 图片列表
   * @param {string} data.mood - 心情
   * @param {{name: string, lat: number, lng: number}} data.location - 位置信息
   * @param {number} data.visibleType - 可见类型 (0: 公开, 1: 私密等)
   */
  create(data) {
    return request.post("/moment", data);
  },

  /**
   * 查询所有动态
   * @param {object} params - 查询参数
   * @param {number} params.limit - 每页数量
   * @param {number} params.offset - 偏移量
   */
  list(params = {}) {
    return request.get("/moment/list", { params });
  },

  /**
   * 查询今日动态（含追加）
   */
  today() {
    return request.get("/moment/today");
  },

  /**
   * 查询单条动态
   * @param {string} id - 动态ID
   */
  getOne(id) {
    return request.get(`/moment/${id}`);
  },

  /**
   * 批量获取动态详情（用于获取追文）
   * @param {string[]} ids - 动态ID数组
   */
  batchDetail(ids) {
    return request.post("/moment/batch", { ids });
  },

  /**
   * 更新动态
   * @param {string} id - 动态ID
   * @param {object} data - 更新数据
   */
  update(id, data) {
    return request.put(`/moment/${id}`, data);
  },

  /**
   * 删除动态
   * @param {string} id - 动态ID
   */
  delete(id) {
    return request.delete(`/moment/${id}`);
  },
};

// 心情枚举
export const MoodType = {
  HAPPY: "开心",
  PEACEFUL: "平静",
  SAD: "难过",
  TIRED: "累",
  EXCITED: "兴奋",
  DEPRESSED: "郁闷",
};

// 可见类型枚举
export const VisibleType = {
  PUBLIC: 0,   // 公开
  FRIENDS: 1,  // 好友可见
  PRIVATE: 2, // 仅自己可见
};
