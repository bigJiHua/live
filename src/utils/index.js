/**
 * API 接口统一导出
 *
 * 本文件统一导出所有 API 模块，方便在其他地方导入
 */

// 认证相关 API
export * as authApi from './api/v1/auth'

// 安全相关 API (PIN 码管理）
export * as securityApi from './api/v1/security'

// 账务管理 API (需要 PIN 验证）
export * as accountApi from './api/v1/account'

// 用户管理 API
export * as userApi from './api/v1/user'
