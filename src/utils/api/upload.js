import fileRequest from "@/utils/fileRequest";

/**
 * 上传超时按文件体积动态计算（🔄 2026-10-12 优化，api.md §8.5）
 *
 * 原固定 60s：2Mbps（256KB/s）下 10MB 要 40s（临界）、多文件/大备份必超时。
 * 新公式：max(60s 下限, 体积/256KB/s + 15s 余量)。
 *   - 1MB  → 60s（下限兜底，弱网小文件也留足余量）
 *   - 10MB → 60s（39s 传输 + 15s 余量 ≈ 54s，仍在下限内）
 *   - 50MB → 210s（195s + 15s）
 * 15s 余量覆盖：服务端 sharp 压缩、磁盘落盘、TLS 重传。
 */
const UPLOAD_BPS = 256 * 1024; // 2Mbps = 256KB/s
const UPLOAD_TIMEOUT_FLOOR_MS = 60000;
const UPLOAD_TIMEOUT_MARGIN_MS = 15000;
function uploadTimeoutBytes(totalBytes) {
  return Math.max(
    UPLOAD_TIMEOUT_FLOOR_MS,
    Math.ceil((totalBytes / UPLOAD_BPS) * 1000) + UPLOAD_TIMEOUT_MARGIN_MS
  );
}

/**
 * 文件上传 API (upload.js)
 *
 * | 方法 | 路径 | 说明 | 参数 |
 * |------|------|------|------|
 * | POST | `/api/upload/single` | 单文件上传 | type, busType, busId |
 * | POST | `/api/upload/multiple` | 多文件上传 | type, busType, busId, files[] |
 * | GET | `/api/upload/list` | 查询附件 | busType, busId, limit, offset |
 * | DELETE | `/api/upload/:id` | 删除附件 | 附件ID |
 * | POST | `/api/upload/batch-delete` | 批量删除 | {ids: []} |
 */

// 业务类型
export const BusType = {
  POST: "post", // 动态
  PRODUCT: "product", // 资产
  BANK: "bank", // 银行 Icon
  OTHER: "other", // 其他
};

export const uploadApi = {
  /**
   * 单文件上传
   * @param {File} file - 文件对象
   * @param {string} busType - 业务类型
   * @param {string} busId - 业务ID（可选）
   * @param {string} remark - 图片说明（可选）
   * @param {string[]} tags - 标签数组（可选）
   */
  single(file, busType, busId = "", remark = "", tags = []) {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("busType", busType);
    if (busId) formData.append("busId", busId);
    if (remark) formData.append("remark", remark);
    // tags 单独 append 每个元素，确保后端拿到数组格式
    if (tags && tags.length > 0) {
      tags.forEach((tag) => formData.append("tags[]", tag));
    }
    return fileRequest.post("/upload/single", formData, {
      timeout: uploadTimeoutBytes(file.size || 0),
    });
  },

  /**
   * 多文件上传
   * @param {File[]} files - 文件数组
   * @param {string} busType - 业务类型
   * @param {string} busId - 业务ID（可选）
   * @param {string} remark - 图片说明（可选）
   * @param {string[]} tags - 标签数组（可选）
   */
  multiple(files, busType, busId = "", remark = "", tags = []) {
    const formData = new FormData();
    files.forEach((file) => formData.append("files", file));
    formData.append("busType", busType);
    if (busId) formData.append("busId", busId);
    if (remark) formData.append("remark", remark);
    // tags 单独 append 每个元素，确保后端拿到数组格式
    if (tags && tags.length > 0) {
      tags.forEach((tag) => formData.append("tags[]", tag));
    }
    // 多文件按总体积计算超时（串行上传由 fileRequest 实例承担，取总和）
    const totalBytes = files.reduce((sum, f) => sum + (f.size || 0), 0);
    return fileRequest.post("/upload/multiple", formData, {
      timeout: uploadTimeoutBytes(totalBytes),
    });
  },

  /**
   * 查询附件列表
   * @param {object} params - 查询参数
   * @param {string} params.busType - 业务类型
   * @param {string} params.busId - 业务ID
   * @param {number} params.limit - 每页数量
   * @param {number} params.offset - 偏移量
   */
  list({ busType, busId, limit = 20, offset = 0 }) {
    return fileRequest.get("/upload/list", {
      params: { busType, busId, limit, offset },
    });
  },

  /**
   * 更新附件信息
   * @param {string} id - 附件ID
   * @param {object} data - 更新数据 { remark, tags }
   */
  update(id, data) {
    return fileRequest.post(`/upload/${id}`, { type: "update", ...data });
  },

  /**
   * 批量删除附件
   * @param {string[]} ids - 附件ID数组
   */
  batchDelete(ids) {
    return fileRequest.post("/upload/batch-delete", { ids });
  },

  /**
   * 搜索附件
   * @param {object} params - 搜索参数
   * @param {string} params.type - 业务类型
   * @param {string} params.key - 搜索关键词
   * @param {number} params.limit - 每页数量
   * @param {number} params.offset - 偏移量
   */
  search({ type, key, limit = 50, offset = 0 }) {
    return fileRequest.get("/upload/search", {
      params: { type, key, limit, offset },
    });
  },
};
