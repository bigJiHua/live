const path = require("path");
const fs = require("fs");
const sharp = require("sharp");
const SysAttachment = require("../models/sys_attachment");

/**
 * 生成文件存储路径（图片统一转 WebP）
 * 格式: busType/YYYY/MM/DD/filename_timestamp.webp
 */
function generateFilePath(file, busType, isImage = true) {
  const busTypeFolderMap = {
    avatar: "avatar",
    post: "post",
    comment: "comment",
    product: "product",
    banner: "banner",
    certificate: "certificate",
    other: "other",
  };

  const folder = busTypeFolderMap[busType] || "other";
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");

  const baseName = path.basename(file.originalname, path.extname(file.originalname));
  const timestamp = Date.now();
  const randomStr = Math.random().toString(36).substring(2, 8);
  const ext = isImage ? ".webp" : path.extname(file.originalname).toLowerCase();

  return `${folder}/${year}/${month}/${day}/${baseName}_${timestamp}_${randomStr}${ext}`;
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
  // 缩略图完整路径: public/uploads/uploads/temp/thumbs/... 需要去掉 uploads 前缀
  const cleanThumbPath = thumbPath.replace(/^uploads\//, "");
  const fullThumbPath = path.join(UploadController.config.uploadDir, cleanThumbPath);
  // 原图完整路径: public/uploads/post/xxx.webp
  const cleanOriginalPath = originalPath.replace(/^\/uploads\//, "");
  const fullOriginalPath = path.join(UploadController.config.uploadDir, cleanOriginalPath);

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
      fit: 'inside',
      withoutEnlargement: true
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
  };

  /**
   * 【已启用】
   * 单文件上传（图片自动压缩为 WebP）
   * @param {string} type - 文件类型：image/file/video/audio
   * @param {string} busType - 业务类型：avatar/post/product 等
   * @param {string} busId - 业务ID（可选）
   */
  async upload(req, res) {
    try {
      const { type = "image", busType = "other", busId } = req.body;
      const file = req.file;

      // 1. 校验文件是否存在
      if (!file) {
        return res.status(400).json({ status: 400, message: "请选择要上传的文件" });
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

      // 3. 校验文件大小
      if (file.size > UploadController.config.maxFileSize) {
        fs.unlinkSync(file.path);
        return res.status(400).json({
          status: 400,
          message: `文件大小不能超过 ${UploadController.config.maxFileSize / 1024 / 1024}MB`,
        });
      }

      // 4. 生成文件路径
      const isImage = allowedTypes.includes(file.mimetype);
      const relativePath = generateFilePath(file, busType, isImage);
      const targetDir = path.join(UploadController.config.uploadDir, path.dirname(relativePath));
      const targetPath = path.join(UploadController.config.uploadDir, relativePath);

      // 确保目录存在
      if (!fs.existsSync(targetDir)) {
        fs.mkdirSync(targetDir, { recursive: true });
      }

      // 5. 图片类型：压缩为 WebP；其他类型：直接移动
      let finalSize = file.size;
      if (isImage) {
        // 读取临时文件并压缩为 WebP（质量 50%）
        const buffer = fs.readFileSync(file.path);
        await sharp(buffer)
          .toFormat("webp", { quality: 50, lossless: false })
          .toFile(targetPath);
        finalSize = fs.statSync(targetPath).size;
        // 删除临时文件
        fs.unlinkSync(file.path);

        // 6. 立即生成缩略图
        const thumbnailPath = generateThumbPath(`/uploads/${relativePath}`, busType);
        const fullThumbPath = path.join(UploadController.config.uploadDir, thumbnailPath);
        const thumbDir = path.dirname(fullThumbPath);
        if (!fs.existsSync(thumbDir)) {
          fs.mkdirSync(thumbDir, { recursive: true });
        }
        await sharp(buffer)
          .resize(200, 200, { fit: 'inside', withoutEnlargement: true })
          .webp({ quality: 30, effort: 4 })
          .toFile(fullThumbPath);

        // 7. 写入数据库
        const result = await SysAttachment.create({
          userId: req.userId,
          busId: busId || null,
          busType: busType,
          fileName: file.originalname,
          filePath: `/uploads/${relativePath}`,
          fileSize: finalSize,
          fileExt: ".webp",
        });

        // 8. 返回结果
        return res.status(200).json({
          status: 200,
          message: "上传成功",
          data: {
            id: result.id,
            url: `/uploads/${relativePath}`,           // 原图
            thumbnail: `/${thumbnailPath}`,          // 缩略图
            fileName: file.originalname,
            fileSize: finalSize,
            fileExt: ".webp",
            busType: busType,
          },
        });
      } else {
        // 非图片类型直接移动
        fs.renameSync(file.path, targetPath);

        // 6. 写入数据库
        const result = await SysAttachment.create({
          userId: req.userId,
          busId: busId || null,
          busType: busType,
          fileName: file.originalname,
          filePath: `/uploads/${relativePath}`,
          fileSize: finalSize,
          fileExt: path.extname(file.originalname).toLowerCase(),
        });

        // 7. 返回结果
        return res.status(200).json({
          status: 200,
          message: "上传成功",
          data: {
            id: result.id,
            url: `/uploads/${relativePath}`,
            thumbnail: `/uploads/${relativePath}`,
            fileName: file.originalname,
            fileSize: finalSize,
            fileExt: path.extname(file.originalname).toLowerCase(),
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
      const { type = "image", busType = "other", busId } = req.body;
      const files = req.files;

      if (!files || files.length === 0) {
        return res.status(400).json({ status: 400, message: "请选择要上传的文件" });
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
        const targetDir = path.join(UploadController.config.uploadDir, path.dirname(relativePath));
        const targetPath = path.join(UploadController.config.uploadDir, relativePath);

        if (!fs.existsSync(targetDir)) {
          fs.mkdirSync(targetDir, { recursive: true });
        }

        // 图片压缩为 WebP
        let finalSize = file.size;
        let thumbnailPath = `/uploads/${relativePath}`;

        if (isImage) {
          const buffer = fs.readFileSync(file.path);
          await sharp(buffer)
            .toFormat("webp", { quality: 50, lossless: false })
            .toFile(targetPath);
          finalSize = fs.statSync(targetPath).size;
          fs.unlinkSync(file.path);

          // 立即生成缩略图
          thumbnailPath = generateThumbPath(`/uploads/${relativePath}`, busType);
          const fullThumbPath = path.join(UploadController.config.uploadDir, thumbnailPath);
          const thumbDir = path.dirname(fullThumbPath);
          if (!fs.existsSync(thumbDir)) {
            fs.mkdirSync(thumbDir, { recursive: true });
          }
          await sharp(buffer)
            .resize(200, 200, { fit: 'inside', withoutEnlargement: true })
            .webp({ quality: 30, effort: 4 })
            .toFile(fullThumbPath);
        } else {
          fs.renameSync(file.path, targetPath);
        }

        // 写入数据库
        const result = await SysAttachment.create({
          userId: req.userId,
          busId: busId || null,
          busType: busType,
          fileName: file.originalname,
          filePath: `/uploads/${relativePath}`,
          fileSize: finalSize,
          fileExt: isImage ? ".webp" : path.extname(file.originalname).toLowerCase(),
        });

        results.push({
          id: result.id,
          url: `/uploads/${relativePath}`,      // 原图
          thumbnail: `/${thumbnailPath}`,        // 缩略图
          fileName: file.originalname,
          fileSize: finalSize,
        });
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

          if (isImage) {
            const thumbnail = await generateThumbnail(item.file_path, item.bus_type);
            return {
              ...item,
              url: item.file_path,                    // 原图路径
              thumbnail: `/${thumbnail}`,             // 缩略图路径
            };
          }

          return {
            ...item,
            url: item.file_path,                      // 原图路径
            thumbnail: item.file_path,                // 非图片无缩略图
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
   * 删除附件（硬删除：原图 + 缩略图 + 数据库记录）
   */
  async delete(req, res) {
    try {
      const { id } = req.params;

      // 1. 查询附件信息
      const attachment = await SysAttachment.findById(id);

      if (!attachment) {
        return res.status(404).json({ status: 404, message: "附件不存在" });
      }

      // 2. 校验权限
      if (attachment.user_id !== req.userId) {
        return res.status(403).json({ status: 403, message: "无权删除此附件" });
      }

      // 3. 删除原图
      const cleanPath = attachment.file_path.replace(/^\/uploads\//, "");
      const filePath = path.join(UploadController.config.uploadDir, cleanPath);
      if (fs.existsSync(filePath)) {
        fs.unlinkSync(filePath);
      }

      // 4. 删除缩略图
      const thumbnail = generateThumbPath(attachment.file_path, attachment.bus_type);
      const thumbFullPath = path.join(UploadController.config.uploadDir, thumbnail);
      if (fs.existsSync(thumbFullPath)) {
        fs.unlinkSync(thumbFullPath);
      }

      // 5. 硬删除数据库记录
      const result = await SysAttachment.delete(id, req.userId);

      return res.status(result.status).json({
        status: result.status,
        message: result.message,
      });
    } catch (error) {
      console.error("删除附件失败:", error);
      return res.status(500).json({ status: 500, message: "删除失败" });
    }
  }

  /**
   * 【已启用】
   * 批量删除附件
   */
  async batchDelete(req, res) {
    try {
      const { ids } = req.body;
      if (!ids || ids.length === 0) return res.say("请选择要删除的附件", 400);

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
        // 删除原图
        const cleanPath = attachment.file_path.replace(/^\/uploads\//, "");
        const filePath = path.join(UploadController.config.uploadDir, cleanPath);
        if (fs.existsSync(filePath)) {
          fs.unlinkSync(filePath);
        }

        // 删除缩略图
        const thumbnail = generateThumbPath(attachment.file_path, attachment.bus_type);
        const thumbFullPath = path.join(UploadController.config.uploadDir, thumbnail);
        if (fs.existsSync(thumbFullPath)) {
          fs.unlinkSync(thumbFullPath);
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
  }
}

module.exports = new UploadController();
