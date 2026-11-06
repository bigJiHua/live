import imageCompression from 'browser-image-compression';

export const AI_IMAGE_LIMITS = Object.freeze({ originalBytes: 8 * 1024 * 1024,
  dataUrlBytes: 2 * 1024 * 1024, maxDimension: 2400 });
const TYPES = ['image/jpeg', 'image/png', 'image/webp'];

function imageType(bytes) {
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return 'image/jpeg';
  if ([137, 80, 78, 71, 13, 10, 26, 10].every((byte, index) => bytes[index] === byte)) return 'image/png';
  if (String.fromCharCode(...bytes.slice(0, 4)) === 'RIFF' && String.fromCharCode(...bytes.slice(8, 12)) === 'WEBP') return 'image/webp';
  return null;
}

export async function validateAiImageFile(file) {
  if (!file || !TYPES.includes(file.type)) throw new Error('请选择 JPEG、PNG 或 WebP 图片');
  if (!Number.isFinite(file.size) || file.size <= 0 || file.size > AI_IMAGE_LIMITS.originalBytes) {
    throw new Error('图片不能超过 8MB，请截取需要识别的部分');
  }
  const bytes = new Uint8Array(await file.slice(0, 12).arrayBuffer());
  if (imageType(bytes) !== file.type) throw new Error('图片格式与文件内容不符，请重新选择图片');
  return file;
}

export function validateAiImageDataUrl(dataUrl) {
  if (typeof dataUrl !== 'string' || dataUrl.length > AI_IMAGE_LIMITS.dataUrlBytes) {
    throw new Error('处理后的图片仍然过大，请截取需要识别的部分');
  }
  const match = /^data:(image\/(?:jpeg|png|webp));base64,([A-Za-z0-9+/]+={0,2})$/.exec(dataUrl);
  if (!match || match[2].length % 4 !== 0) throw new Error('图片数据格式无效，请重新选择图片');
  let bytes;
  try {
    const decoded = atob(match[2]);
    if (btoa(decoded) !== match[2]) throw new Error('Invalid base64');
    bytes = Uint8Array.from(decoded.slice(0, 12), char => char.charCodeAt(0));
  }
  catch { throw new Error('图片数据格式无效，请重新选择图片'); }
  if (imageType(bytes) !== match[1]) throw new Error('图片数据内容无效，请重新选择图片');
  return dataUrl;
}

async function dimensions(file) {
  const [image, canvas] = await imageCompression.drawFileInCanvas(file);
  const result = { width: canvas.width, height: canvas.height };
  image.close?.(); canvas.width = 0; canvas.height = 0;
  return result;
}

export async function prepareAiImage(file, { compress = imageCompression,
  readDataUrl = imageCompression.getDataUrlFromFile, readDimensions = dimensions } = {}) {
  await validateAiImageFile(file);
  const prepared = await compress(file, { maxSizeMB: 1.4, maxWidthOrHeight: AI_IMAGE_LIMITS.maxDimension,
    initialQuality: 0.95, maxIteration: 10, useWebWorker: false, preserveExif: false, fileType: file.type });
  const size = await readDimensions(prepared);
  if (!Number.isFinite(size.width) || !Number.isFinite(size.height) || size.width <= 0 || size.height <= 0 ||
      Math.max(size.width, size.height) > AI_IMAGE_LIMITS.maxDimension) {
    throw new Error('无法处理这张图片，请换一张清晰截图');
  }
  return { dataUrl: validateAiImageDataUrl(await readDataUrl(prepared)), name: file.name || '图片',
    type: prepared.type, width: size.width, height: size.height };
}
