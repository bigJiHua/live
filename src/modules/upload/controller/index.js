const path = require("path");
const fs = require("fs");
const sharp = require("sharp");
const SysAttachment = require("../model/attachment");

/**
 * 生成文件存储路径（图片统一转 WebP）
 * 格式: busType/YYYY/MM/DD/filename_timestamp.webp
 */
function generateFilePath(file, busType, isImage = true) {
  const busTypeFolderMap = {
    post: "post", // 动态图片
    product: "product", // 资产图片
    bank: "bank", // 银行 Icon
    other: "other", // 其他资源
  };

  const folder = busTypeFolderMap[busType] || "other";
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");

  // 统一生成文件名：时间戳 + 随机字符串，保证不重复
  const timestamp = Date.now();
  const randomStr = Math.random().toString(36).substring(2, 10); // 8位随机
  const baseName = `${timestamp}_${randomStr}`;

  // SVG 保持原始扩展名，不转 WebP
  const ext = path.extname(file.originalname).toLowerCase();
  const isSvg = ext === ".svg";
  const finalExt = isSvg ? ext : isImage ? ".webp" : ext;

  return `${folder}/${year}/${month}/${day}/${baseName}${finalExt}`;
}

/**
 * 生成缩略图路径
 * 格式: uploads/temp/thumbs/busType/filename.webp
 */
function generateThumbPath(filePath, busType) {
  const baseName = path.basename(filePath, path.extname(filePath));
  return `uploads/temp/thumbs/${busType}/${baseName}.webp`;
}

/**
 * 生成缩略图（超压缩，用于列表展示，懒加载）
 */
async function generateThumbnail(originalPath, busType) {
  const thumbPath = generateThumbPath(originalPath, busType);
  // 缩略图完整路径: public/uploads/uploads/temp/thumbs/...
  // 去掉 uploads/ 前缀，避免路径重复
  const cleanThumbPath = thumbPath.replace(/^uploads\//, "");
  // 最终文件路径: public/uploads/temp/thumbs/busType/xxx.webp
  const fullThumbPath = path.join(
    UploadController.config.uploadDir,
    cleanThumbPath
  );
  // 原图完整路径: public/uploads/post/xxx.webp
  const cleanOriginalPath = originalPath.replace(/^\/uploads\//, "");
  const fullOriginalPath = path.join(
    UploadController.config.uploadDir,
    cleanOriginalPath
  );

  // 如果缩略图已存在，直接返回
  if (fs.existsSync(fullThumbPath)) {
    return thumbPath;
  }

  // 确保缩略图目录存在
  const thumbDir = path.dirname(fullThumbPath);
  if (!fs.existsSync(thumbDir)) {
    fs.mkdirSync(thumbDir, { recursive: true });
  }

  // 生成超压缩缩略图：质量 30%，最大 200x200
  await sharp(fullOriginalPath)
    .rotate()
    .resize(200, 200, {
      fit: "inside",
      withoutEnlargement: true,
    })
    .webp({ quality: 30, effort: 4 })
    .toFile(fullThumbPath);

  return thumbPath;
}

/**
 * 文件上传控制器
 * 支持按业务类型分类存储，数据库记录绑定
 */
class UploadController {
  /**
   * 上传配置
   */
  static config = {
    // 允许的文件类型
    allowedMimeTypes: {
      image: [
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "image/svg+xml",
      ],
      file: [
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      ],
      video: ["video/mp4", "video/webm", "video/ogg"],
      audio: ["audio/mpeg", "audio/wav", "audio/ogg"],
    },
    // 文件大小限制（字节）默认 10MB
    maxFileSize: 10 * 1024 * 1024,
    // 存储根目录
    uploadDir: path.join(process.cwd(), "public", "uploads"),
    // remark 最大字符数
    maxRemarkLength: 50,
    // tags 总字符数限制
    maxTagsLength: 100,
  };

  /**
   * 【已启用】
   * 单文件上传（图片自动压缩为 WebP）
   * @param {string} type - 文件类型：image/file/video/audio
   * @param {string} busType - 业务类型：post/product/bank/other
   * @param {string} remark - 图片说明
   * @param {string} tags - 图片标签，逗号分隔
   */
  async upload(req, res) {
    try {
      const { type = "image", busType = "other", remark, tags } = req.body;
      const file = req.file;

      // 1. 校验文件是否存在
      if (!file) {
        return res
          .status(400)
          .json({ status: 400, message: "请选择要上传的文件" });
      }

      // 2. 校验文件类型
      const allowedTypes =
        UploadController.config.allowedMimeTypes[type] ||
        UploadController.config.allowedMimeTypes.image;
      if (!allowedTypes.includes(file.mimetype)) {
        fs.unlinkSync(file.path);
        return res.status(400).json({
          status: 400,
          message: `不支持的文件类型，仅支持: ${allowedTypes.join(", ")}`,
        });
      }

      // 3. 校验 remark 长度
      if (remark && remark.length > UploadController.config.maxRemarkLength) {
        return res.status(400).json({
          status: 400,
          message: `图片说明不能超过 ${UploadController.config.maxRemarkLength} 个字符`,
        });
      }

      // 4. 校验 tags 总长度
      const tagsStr = Array.isArray(tags) ? tags.join(",") : tags || "";
      if (tagsStr.length > UploadController.config.maxTagsLength) {
        return res.status(400).json({
          status: 400,
          message: `标签总长度不能超过 ${UploadController.config.maxTagsLength} 个字符`,
        });
      }

      // 5. 校验文件大小
      if (file.size > UploadController.config.maxFileSize) {
        fs.unlinkSync(file.path);
        return res.status(400).json({
          status: 400,
          message: `文件大小不能超过 ${
            UploadController.config.maxFileSize / 1024 / 1024
          }MB`,
        });
      }

      // 6. 生成文件路径
      const isImage = allowedTypes.includes(file.mimetype);
      const relativePath = generateFilePath(file, busType, isImage);
      const newFileName = path.basename(relativePath); // 新的文件名
      const targetDir = path.join(
        UploadController.config.uploadDir,
        path.dirname(relativePath)
      );
      const targetPath = path.join(
        UploadController.config.uploadDir,
        relativePath
      );

      // 确保目录存在
      if (!fs.existsSync(targetDir)) {
        fs.mkdirSync(targetDir, { recursive: true });
      }

      // 5. 图片类型：SVG不压缩；小于1MB不压缩；其他压缩为 WebP
      let finalSize = file.size;
      const isSvg = file.mimetype === "image/svg+xml";
      const needCompress = file.size > 1024 * 1024; // 大于1MB才压缩
      if (isImage && !isSvg) {
        if (needCompress) {
          // 大于1MB：压缩为 WebP
          const buffer = fs.readFileSync(file.path);
          await sharp(buffer)
            .toFormat("webp", { quality: 50, lossless: false })
            .toFile(targetPath);
          finalSize = fs.statSync(targetPath).size;
          fs.unlinkSync(file.path);

          // 立即生成缩略图
          const thumbnailPath = await generateThumbnail(
            `/uploads/${relativePath}`,
            busType
          );

          // 写入数据库
          const result = await SysAttachment.create({
            userId: req.userId,
            busType: busType,
            remark: remark || "用户上传的图片",
            tags: tags || "默认",
            fileName: newFileName,
            filePath: `/uploads/${relativePath}`,
            fileSize: finalSize,
            fileExt: ".webp",
          });

          // 返回结果
          return res.status(200).json({
            status: 200,
            message: "上传成功",
            data: {
              id: result.id,
              url: `/uploads/${relativePath}`,
              thumbnail: `/${thumbnailPath}`,
              fileName: newFileName,
              fileSize: finalSize,
              fileExt: ".webp",
              busType: busType,
              compressed: true,
            },
          });
        } else {
          // 小于1MB：不压缩，直接移动
          fs.renameSync(file.path, targetPath);

          // 生成缩略图
          const thumbnailPath = await generateThumbnail(
            `/uploads/${relativePath}`,
            busType
          );

          // 写入数据库
          const result = await SysAttachment.create({
            userId: req.userId,
            busType: busType,
            remark: remark || "用户上传的图片",
            tags: tags || "默认",
            fileName: newFileName,
            filePath: `/uploads/${relativePath}`,
            fileSize: finalSize,
            fileExt: path.extname(newFileName),
          });

          // 返回结果
          return res.status(200).json({
            status: 200,
            message: "上传成功",
            data: {
              id: result.id,
              url: `/uploads/${relativePath}`,
              thumbnail: `/${thumbnailPath}`,
              fileName: newFileName,
              fileSize: finalSize,
              fileExt: path.extname(newFileName),
              busType: busType,
              compressed: false,
            },
          });
        }
      } else {
        // SVG 或非图片类型：直接移动，不生成缩略图
        fs.renameSync(file.path, targetPath);

        // 写入数据库
        const result = await SysAttachment.create({
          userId: req.userId,
          busType: busType,
          remark: remark || "用户上传的图片",
          tags: tags || "默认",
          fileName: newFileName,
          filePath: `/uploads/${relativePath}`,
          fileSize: finalSize,
          fileExt: path.extname(newFileName),
        });

        // 返回结果
        return res.status(200).json({
          status: 200,
          message: "上传成功",
          data: {
            id: result.id,
            url: `/uploads/${relativePath}`,
            thumbnail: `/uploads/${relativePath}`, // SVG 无缩略图，返回原图
            fileName: newFileName,
            fileSize: finalSize,
            fileExt: path.extname(newFileName),
            busType: busType,
          },
        });
      }
    } catch (error) {
      console.error("上传文件失败:", error);
      if (req.file && fs.existsSync(req.file.path)) {
        fs.unlinkSync(req.file.path);
      }
      return res.status(500).json({ status: 500, message: "上传失败" });
    }
  }

  /**
   * 【已启用】
   * 多文件上传（图片自动压缩为 WebP）
   */
  async uploadMultiple(req, res) {
    try {
      const { type = "image", busType = "other", remark, tags } = req.body;
      const files = req.files;

      // 校验 remark 长度
      if (remark && remark.length > UploadController.config.maxRemarkLength) {
        return res.status(400).json({
          status: 400,
          message: `图片说明不能超过 ${UploadController.config.maxRemarkLength} 个字符`,
        });
      }

      // 校验 tags 总长度
      const tagsStr = Array.isArray(tags) ? tags.join(",") : tags || "";
      if (tagsStr.length > UploadController.config.maxTagsLength) {
        return res.status(400).json({
          status: 400,
          message: `标签总长度不能超过 ${UploadController.config.maxTagsLength} 个字符`,
        });
      }

      if (!files || files.length === 0) {
        return res
          .status(400)
          .json({ status: 400, message: "请选择要上传的文件" });
      }

      const allowedTypes =
        UploadController.config.allowedMimeTypes[type] ||
        UploadController.config.allowedMimeTypes.image;
      const results = [];
      const errors = [];

      for (const file of files) {
        // 校验类型和大小
        if (!allowedTypes.includes(file.mimetype)) {
          fs.unlinkSync(file.path);
          errors.push(`${file.originalname}: 不支持的文件类型`);
          continue;
        }

        if (file.size > UploadController.config.maxFileSize) {
          fs.unlinkSync(file.path);
          errors.push(`${file.originalname}: 文件过大`);
          continue;
        }

        // 生成路径
        const isImage = allowedTypes.includes(file.mimetype);
        const relativePath = generateFilePath(file, busType, isImage);
        const newFileName = path.basename(relativePath); // 新的文件名
        const targetDir = path.join(
          UploadController.config.uploadDir,
          path.dirname(relativePath)
        );
        const targetPath = path.join(
          UploadController.config.uploadDir,
          relativePath
        );

        if (!fs.existsSync(targetDir)) {
          fs.mkdirSync(targetDir, { recursive: true });
        }

        // 图片压缩为 WebP（SVG除外，大于1MB才压缩）
        let finalSize = file.size;
        let thumbnailPath = `/uploads/${relativePath}`;
        const isSvg = file.mimetype === "image/svg+xml";
        const needCompress = file.size > 1024 * 1024; // 大于1MB才压缩

        if (isImage && !isSvg) {
          if (needCompress) {
            // 大于1MB：压缩为 WebP
            const buffer = fs.readFileSync(file.path);
            await sharp(buffer)
              .toFormat("webp", { quality: 50, lossless: false })
              .toFile(targetPath);
            finalSize = fs.statSync(targetPath).size;
            fs.unlinkSync(file.path);

            // 立即生成缩略图
            thumbnailPath = await generateThumbnail(
              `/uploads/${relativePath}`,
              busType
            );

            // 写入数据库
            const result = await SysAttachment.create({
              userId: req.userId,
              busType: busType,
              remark: remark || "用户上传的图片",
              tags: tags || "默认",
              fileName: newFileName,
              filePath: `/uploads/${relativePath}`,
              fileSize: finalSize,
              fileExt: ".webp",
            });

            results.push({
              id: result.id,
              url: `/uploads/${relativePath}`,
              thumbnail: `/${thumbnailPath}`,
              fileName: newFileName,
              fileSize: finalSize,
              fileExt: ".webp",
              remark: remark || "用户上传的图片",
              tags: tags || "默认",
              compressed: true,
            });
          } else {
            // 小于1MB：不压缩，直接移动
            fs.renameSync(file.path, targetPath);

            // 生成缩略图
            thumbnailPath = await generateThumbnail(
              `/uploads/${relativePath}`,
              busType
            );

            // 写入数据库
            const result = await SysAttachment.create({
              userId: req.userId,
              busType: busType,
              remark: remark || "用户上传的图片",
              tags: tags || "默认",
              fileName: newFileName,
              filePath: `/uploads/${relativePath}`,
              fileSize: finalSize,
              fileExt: path.extname(newFileName),
            });

            results.push({
              id: result.id,
              url: `/uploads/${relativePath}`,
              thumbnail: `/${thumbnailPath}`,
              fileName: newFileName,
              fileSize: finalSize,
              fileExt: path.extname(newFileName),
              remark: remark || "用户上传的图片",
              tags: tags || "默认",
              compressed: false,
            });
          }
        } else {
          // SVG 或非图片：直接移动，不转格式
          fs.renameSync(file.path, targetPath);

          // 写入数据库
          const result = await SysAttachment.create({
            userId: req.userId,
            busType: busType,
            remark: remark || "用户上传的图片",
            tags: tags || "默认",
            fileName: newFileName,
            filePath: `/uploads/${relativePath}`,
            fileSize: finalSize,
            fileExt: isSvg ? ".svg" : path.extname(newFileName).toLowerCase(),
          });

          results.push({
            id: result.id,
            url: `/uploads/${relativePath}`,
            thumbnail: `/uploads/${relativePath}`,
            fileName: newFileName,
            fileSize: finalSize,
            fileExt: isSvg ? ".svg" : path.extname(newFileName).toLowerCase(),
            remark: remark || "用户上传的图片",
            tags: tags || "默认",
            compressed: false,
          });
        }
      }

      return res.status(200).json({
        status: 200,
        message: `成功 ${results.length} 个，失败 ${errors.length} 个`,
        data: results,
        errors: errors.length > 0 ? errors : undefined,
      });
    } catch (error) {
      console.error("批量上传失败:", error);
      return res.status(500).json({ status: 500, message: "上传失败" });
    }
  }

  /**
   * 【已启用】
   * 查询附件列表（自动生成缩略图）
   * 图片返回缩略图 URL，非图片返回原图 URL
   */
  async list(req, res) {
    try {
      const { busType, busId, limit = 50, offset = 0 } = req.query;

      let attachments;
      if (busType && busId) {
        attachments = await SysAttachment.findByBus(busType, busId, req.userId);
      } else {
        attachments = await SysAttachment.findByUser(req.userId, {
          busType,
          limit: parseInt(limit),
          offset: parseInt(offset),
        });
      }

      // 为图片生成缩略图
      const results = await Promise.all(
        attachments.map(async (item) => {
          const isImage = [".webp", ".jpg", ".jpeg", ".png", ".gif"].includes(
            (item.file_ext || "").toLowerCase()
          );

          // 解析 tags（数据库存的是 JSON 字符串）
          let tags = ["默认"];
          if (item.tags) {
            try {
              tags = JSON.parse(item.tags);
            } catch (e) {
              tags = item.tags.split(",");
            }
          }

          if (isImage) {
            const thumbnail = await generateThumbnail(
              item.file_path,
              item.bus_type
            );
            return {
              ...item,
              tags, // 解析后的数组
              url: item.file_path, // 原图路径
              thumbnail: `/${thumbnail}`, // 缩略图路径
            };
          }

          return {
            ...item,
            tags, // 解析后的数组
            url: item.file_path, // 原图路径
            thumbnail: item.file_path, // 非图片无缩略图
          };
        })
      );

      return res.status(200).json({
        status: 200,
        data: results,
      });
    } catch (error) {
      console.error("查询附件失败:", error);
      return res.status(500).json({ status: 500, message: "查询失败" });
    }
  }

  /**
   * 【已启用】
   * 搜索附件（按类型和关键词）
   * @param {string} type - 资源类型：post/product/bank/other
   * @param {string} key - 关键词（搜索 remark 和 tags）
   */
  async search(req, res) {
    try {
      const { type, key, limit = 50, offset = 0 } = req.query;

      const attachments = await SysAttachment.search(req.userId, {
        busType: type || null,
        key: key || null,
        limit: parseInt(limit),
        offset: parseInt(offset),
      });

      // 为图片生成缩略图
      const results = await Promise.all(
        attachments.map(async (item) => {
          const isImage = [".webp", ".jpg", ".jpeg", ".png", ".gif"].includes(
            (item.file_ext || "").toLowerCase()
          );

          // 解析 tags（数据库存的是 JSON 字符串）
          let tags = ["默认"];
          if (item.tags) {
            try {
              tags = JSON.parse(item.tags);
            } catch (e) {
              tags = item.tags.split(",");
            }
          }

          if (isImage) {
            const thumbnail = await generateThumbnail(
              item.file_path,
              item.bus_type
            );
            return {
              id: item.id,
              bus_type: item.bus_type,
              remark: item.remark,
              tags,
              url: `/${item.file_path}`,
              thumbnail: `/${thumbnail}`,
            };
          }

          return {
            id: item.id,
            bus_type: item.bus_type,
            remark: item.remark,
            tags,
            url: `/${item.file_path}`,
            thumbnail: `/${item.file_path}`,
          };
        })
      );

      return res.status(200).json({
        status: 200,
        data: results,
      });
    } catch (error) {
      console.error("搜索附件失败:", error);
      return res.status(500).json({ status: 500, message: "搜索失败" });
    }
  }

  /**
   * 【已启用】
   * 编辑附件（修改 remark 和 tags）
   */
  edit = async (req, res) => {
    try {
      const { id } = req.params;
      const { remark, tags } = req.body;

      // 校验 remark 长度
      if (remark && remark.length > UploadController.config.maxRemarkLength) {
        return res.status(400).json({
          status: 400,
          message: `图片说明不能超过 ${UploadController.config.maxRemarkLength} 个字符`,
        });
      }

      // 校验 tags 总长度
      const tagsStr = Array.isArray(tags) ? tags.join(",") : tags || "";
      if (tagsStr.length > UploadController.config.maxTagsLength) {
        return res.status(400).json({
          status: 400,
          message: `标签总长度不能超过 ${UploadController.config.maxTagsLength} 个字符`,
        });
      }

      // 查询附件信息
      const attachment = await SysAttachment.findById(id);

      if (!attachment) {
        return res.status(404).json({ status: 404, message: "附件不存在" });
      }

      // 校验权限
      if (attachment.user_id !== req.userId) {
        return res.status(403).json({ status: 403, message: "无权修改此附件" });
      }

      const updates = {};
      if (remark !== undefined) updates.remark = remark;
      if (tags !== undefined) updates.tags = tags;

      if (Object.keys(updates).length === 0) {
        return res
          .status(400)
          .json({ status: 400, message: "没有需要更新的字段" });
      }

      const result = await SysAttachment.update(id, req.userId, updates);
      return res.status(result.status).json(result);
    } catch (error) {
      console.error("编辑附件失败:", error);
      return res.status(500).json({ status: 500, message: "编辑失败" });
    }
  };

  /**
   * 【已启用】
   * 批量删除附件
   */
  batchDelete = async (req, res) => {
    try {
      const { ids } = req.body;
      if (!ids || ids.length === 0) return res.say("请选择要删除的附件", 400);

      // 检查附件是否被 moment 引用
      const { referencedIds } = await SysAttachment.checkReferencesBeforeDelete(
        ids
      );

      // 如果有被引用的附件，返回提示
      if (referencedIds.length > 0) {
        return res.status(400).json({
          status: 400,
          message: `以下附件正在被动态使用，无法删除`,
          referencedIds,
        });
      }

      // 查询附件信息
      const attachments = [];
      for (const id of ids) {
        const attachment = await SysAttachment.findById(id);
        if (attachment && attachment.user_id === req.userId) {
          attachments.push(attachment);
        }
      }

      // 删除物理文件（原图 + 缩略图）
      for (const attachment of attachments) {
        try {
          // 删除原图
          const cleanPath = attachment.file_path.replace(/^\/uploads\//, "");
          const filePath = path.join(
            UploadController.config.uploadDir,
            cleanPath
          );
          if (fs.existsSync(filePath)) {
            fs.unlinkSync(filePath);
          }

          // 删除缩略图
          const thumbnail = generateThumbPath(
            attachment.file_path,
            attachment.bus_type
          );
          const thumbFullPath = path.join(
            UploadController.config.uploadDir,
            thumbnail
          );
          if (fs.existsSync(thumbFullPath)) {
            fs.unlinkSync(thumbFullPath);
          }
        } catch (err) {
          // 文件被占用时跳过，继续删除其他文件
          console.warn(
            `删除文件失败（文件可能被占用）: ${attachment.file_path}`
          );
        }
      }

      // 硬删除数据库记录
      const result = await SysAttachment.batchDelete(ids, req.userId);

      return res.status(result.status).json({
        status: result.status,
        message: result.message,
      });
    } catch (error) {
      console.error("批量删除附件失败:", error);
      return res.status(500).json({ status: 500, message: "批量删除失败" });
    }
  };
}

module.exports = new UploadController();
