const jwt = require('jsonwebtoken');
require('dotenv').config();

/**
 * JWT 配置
 */
const jwtConfig = {
  secret: process.env.JWT_SECRET || 'your-secret-key-change-this-in-production',
  expiresIn: process.env.JWT_EXPIRES_IN || '7d',
  refreshExpiresIn: process.env.JWT_REFRESH_EXPIRES_IN || '30d',
};

/**
 * 生成 JWT Token
 * @param {object} payload - Token 载荷
 * @param {string} expiresIn - 过期时间（可选）
 * @returns {string} JWT Token
 */
function generateToken(payload, expiresIn = jwtConfig.expiresIn) {
  return jwt.sign(payload, jwtConfig.secret, { expiresIn });
}

/**
 * 验证 JWT Token
 * @param {string} token - JWT Token
 * @returns {object} 解码后的载荷
 */
function verifyToken(token) {
  return jwt.verify(token, jwtConfig.secret);
}

/**
 * 解码 JWT Token（不验证签名）
 * @param {string} token - JWT Token
 * @returns {object} 解码后的载荷
 */
function decodeToken(token) {
  return jwt.decode(token);
}

/**
 * 生成访问 Token
 * @param {object} payload - Token 载荷
 * @returns {string} 访问 Token
 */
function generateAccessToken(payload) {
  return generateToken(payload, jwtConfig.expiresIn);
}

/**
 * 生成刷新 Token
 * @param {object} payload - Token 载荷
 * @returns {string} 刷新 Token
 */
function generateRefreshToken(payload) {
  return generateToken(payload, jwtConfig.refreshExpiresIn);
}

/**
 * 检查 Token 是否过期
 * @param {string} token - JWT Token
 * @returns {boolean} 是否过期
 */
function isTokenExpired(token) {
  try {
    const decoded = jwt.decode(token);
    if (!decoded || !decoded.exp) {
      return true;
    }
    return Date.now() >= decoded.exp * 1000;
  } catch (error) {
    return true;
  }
}

module.exports = {
  jwtConfig,
  generateToken,
  verifyToken,
  decodeToken,
  generateAccessToken,
  generateRefreshToken,
  isTokenExpired,
};
