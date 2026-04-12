/**
 * 环境配置
 * 使用环境变量，参考 .env.example 配置
 */
export const ENV = {
  // 站点域名
  SITE_URL: import.meta.env.VITE_SITE_URL || 'http://localhost',

  // API 基础地址
  API_BASE_URL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:3001',

  // 文件上传基础路径
  FILE_BASE_URL: import.meta.env.VITE_FILE_BASE_URL || 'http://localhost:3001/api/public',
}

export default ENV
