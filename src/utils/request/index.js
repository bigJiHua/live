/**
 * Request 模块主入口
 * 重构版本 - 模块化架构
 * 
 * 目录结构:
 * ├── index.js           # 主入口
 * ├── config.js          # 基础配置
 * ├── core.js            # 核心实例
 * ├── client.js          # 客户端上下文
 * ├── handshake.js       # 握手 & AES Key
 * ├── crypto.js          # 加密工具
 * ├── helpers.js         # 辅助函数
 * └── interceptors/
 *     ├── request.js     # 请求拦截器
 *     └── response.js    # 响应拦截器
 */

import request from './core'
import * as helpers from './helpers'

export { request, helpers }

// 导出各模块，方便按需使用
export { config } from './config'
export { getClientContext, clearClientCache } from './client'
export { getAesKey, clearAesKey, hasAesKey } from './handshake'
export { encrypt, decrypt } from './crypto'

export default request

