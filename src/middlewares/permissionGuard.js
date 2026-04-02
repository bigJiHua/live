/**
 * 权限守卫中间件 - 权限控制、角色验证、资源访问控制
 */

const jwt = require('jsonwebtoken');

/**
 * 权限枚举
 */
const Permission = {
  // 用户权限
  READ_USER: 'read:user',           // 读取用户信息
  UPDATE_USER: 'update:user',       // 更新用户信息
  DELETE_USER: 'delete:user',       // 删除用户

  // 管理员权限
  READ_ALL_USERS: 'read:all:users',    // 查看所有用户
  UPDATE_USER_STATUS: 'update:user:status', // 修改用户状态
  DELETE_USER: 'delete:user',        // 删除用户
  MANAGE_DATABASE: 'manage:database', // 管理数据库

  // 数据操作权限
  READ_DATA: 'read:data',          // 读取数据
  CREATE_DATA: 'create:data',       // 创建数据
  UPDATE_DATA: 'update:data',       // 更新数据
  DELETE_DATA: 'delete:data',       // 删除数据
};

/**
 * 角色枚举
 */
const Role = {
  ADMIN: 'admin',           // 管理员
  USER: 'user',            // 普通用户
};

/**
 * 权限检查中间件
 * @param {...string} requiredPermissions - 必需的权限数组
 */
const requirePermissions = (...requiredPermissions) => {
  return async (req, res, next) => {
    try {
      // 从 Token 中获取用户角色和权限
      const user = await getUserFromToken(req);
      if (!user) {
        return res.status(401).json({
          success: false,
          message: '未认证或 Token 无效'
        });
      }

      // 检查是否拥有所需权限
      const userPermissions = user.permissions || [];
      const hasAllPermissions = requiredPermissions.every(perm =>
        userPermissions.includes(perm)
      );

      if (!hasAllPermissions) {
        return res.status(403).json({
          success: false,
          message: '权限不足',
          required: requiredPermissions,
          current: userPermissions
        });
      }

      // 将用户信息添加到请求对象
      req.user = user;
      next();
    } catch (error) {
      console.error('权限检查错误:', error);
      return res.status(500).json({
        success: false,
        message: '服务器内部错误',
        error: process.env.NODE_ENV === 'development' ? error.message : undefined
      });
    }
  };
};

/**
 * 角色检查中间件
 * @param {...string} allowedRoles - 允许的角色数组
 */
const requireRoles = (...allowedRoles) => {
  return async (req, res, next) => {
    try {
      const user = await getUserFromToken(req);
      if (!user) {
        return res.status(401).json({
          success: false,
          message: '未认证或 Token 无效'
        });
      }

      // 检查用户角色
      if (!allowedRoles.includes(user.role)) {
        return res.status(403).json({
          success: false,
          message: '角色权限不足',
          required: allowedRoles,
          current: user.role
        });
      }

      req.user = user;
      next();
    } catch (error) {
      console.error('角色检查错误:', error);
      return res.status(500).json({
        success: false,
        message: '服务器内部错误',
        error: process.env.NODE_ENV === 'development' ? error.message : undefined
      });
    }
  };
};

/**
 * 资源所有权检查中间件
 * 检查用户是否拥有该资源的访问权限
 */
const requireOwnership = (resourceType) => {
  return async (req, res, next) => {
    try {
      const user = await getUserFromToken(req);
      if (!user) {
        return res.status(401).json({
          success: false,
          message: '未认证或 Token 无效'
        });
      }

      const resourceId = req.params.id;
      const User = require('../models/User');

      let isOwner = false;

      // 根据资源类型检查所有权
      switch (resourceType) {
        case 'user':
          // 管理员可以操作所有用户
          if (user.role === Role.ADMIN) {
            isOwner = true;
          } else {
            // 用户只能操作自己的资源
            const targetUser = await User.findById(resourceId);
            isOwner = targetUser && targetUser.id === user.id;
          }
          break;

        case 'data':
          // 数据资源所有权检查
          // 实际应用中需要根据业务逻辑调整
          isOwner = true; // 示例: 假设拥有自己的数据
          break;

        default:
          isOwner = false;
      }

      if (!isOwner) {
        return res.status(403).json({
          success: false,
          message: '无权访问此资源',
          resourceType,
          resourceId
        });
      }

      next();
    } catch (error) {
      console.error('所有权检查错误:', error);
      return res.status(500).json({
        success: false,
        message: '服务器内部错误',
        error: process.env.NODE_ENV === 'development' ? error.message : undefined
      });
    }
  };
};

/**
 * 限流中间件
 * @param {number} maxRequests - 最大请求数
 * @param {number} windowMs - 时间窗口(毫秒)
 */
const rateLimit = (maxRequests = 100, windowMs = 60000) => {
  const requestMap = new Map();

  return async (req, res, next) => {
    const ip = req.ip || req.connection.remoteAddress;
    const now = Date.now();

    // 获取或初始化该IP的请求记录
    const requests = requestMap.get(ip) || { count: 0, resetTime: now };

    // 检查是否需要重置计数
    if (now - requests.resetTime > windowMs) {
      requests.count = 0;
      requests.resetTime = now;
    }

    // 检查是否超过限制
    if (requests.count >= maxRequests) {
      return res.status(429).json({
        success: false,
        message: '请求过于频繁，请稍后再试',
        limit: maxRequests,
        window: windowMs / 1000 // 转换为秒
      });
    }

    requests.count++;
    requestMap.set(ip, requests);

    // 设置响应头
    res.setHeader('X-RateLimit-Limit', maxRequests);
    res.setHeader('X-RateLimit-Remaining', maxRequests - requests.count);
    res.setHeader('X-RateLimit-Reset', new Date(requests.resetTime + windowMs).toISOString());

    next();
  };
};

/**
 * IP 白名单中间件
 */
const ipWhitelist = (allowedIPs = []) => {
  return async (req, res, next) => {
    const ip = req.ip || req.connection.remoteAddress;

    if (!allowedIPs.includes(ip)) {
      return res.status(403).json({
        success: false,
        message: 'IP 地址不在白名单中',
        ip
      });
    }

    next();
  };
};

/**
 * 数据验证中间件
 */
const validateRequest = (schema) => {
  return (req, res, next) => {
    try {
      const { error, value } = schema.validate(req.body);

      if (error) {
        return res.status(400).json({
          success: false,
          message: '请求数据验证失败',
          errors: error.details
        });
      }

      // 将验证后的数据替换原始数据
      req.validatedData = value;
      next();
    } catch (error) {
      console.error('数据验证错误:', error);
      return res.status(500).json({
        success: false,
        message: '服务器内部错误',
        error: process.env.NODE_ENV === 'development' ? error.message : undefined
      });
    }
  };
};

/**
 * 检查用户锁定状态
 */
const requireUnlocked = async (req, res, next) => {
  try {
    const user = await getUserFromToken(req);
    if (!user) {
      return res.status(401).json({
        success: false,
        message: '未认证或 Token 无效'
      });
    }

    if (user.is_locked === 1) {
      return res.status(423).json({
        success: false,
        message: '账号已被锁定,请联系管理员',
        code: 'ACCOUNT_LOCKED'
      });
    }

    next();
  } catch (error) {
    console.error('检查锁定状态错误:', error);
    return res.status(500).json({
      success: false,
      message: '服务器内部错误',
      error: process.env.NODE_ENV === 'development' ? error.message : undefined
    });
  }
};

/**
 * 从 Token 获取用户信息
 */
async function getUserFromToken(req) {
  try {
    const authHeader = req.headers.authorization;

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return null;
    }

    const token = authHeader.substring(7);
    const decoded = jwt.verify(token, process.env.JWT_SECRET);

    // 从数据库获取完整用户信息
    const User = require('../models/User');
    const user = await User.findById(decoded.userId);

    return user;
  } catch (error) {
    if (error.name === 'JsonWebTokenError') {
      return null;
    }
    throw error;
  }
}

module.exports = {
  Permission,
  Role,
  requirePermissions,
  requireRoles,
  requireOwnership,
  rateLimit,
  ipWhitelist,
  validateRequest,
  requireUnlocked
};
