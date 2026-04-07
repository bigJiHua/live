import request from "@/utils/request";

/**
 * 分类管理 API
 * 支持类型：income(收入) / expense(支出) / asset(资产) / fixed(固定资产) / bank(银行)
 */
export const categoryApi = {
  /**
   * 获取分类列表
   * @param {string} type - 分类类型：income/expense/asset/fixed/bank
   */
  list(type) {
    return request.get("/category", { params: { type } });
  },

  /**
   * 获取单个分类详情
   * @param {number|string} id - 分类ID
   */
  getById(id) {
    return request.get(`/category/${id}`);
  },

  /**
   * 创建分类
   * @param {object} data - { name, type, iconUrl?, remark? }
   * @param {string} data.name - 分类名称
   * @param {string} data.type - 类型：income/expense/asset/fixed/bank
   * @param {string} [data.iconUrl] - 图标URL
   * @param {string} [data.remark] - 备注
   */
  create(data) {
    return request.post("/category", data);
  },

  /**
   * 更新分类
   * @param {number|string} id - 分类ID
   * @param {object} data - { name?, iconUrl?, remark? }
   */
  update(id, data) {
    return request.put(`/category/${id}`, data);
  },

  /**
   * 删除分类
   * @param {number|string} id - 分类ID
   */
  delete(id) {
    return request.delete(`/category/${id}`);
  },
};
