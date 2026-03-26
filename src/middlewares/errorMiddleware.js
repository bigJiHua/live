/**
 * 全局错误处理中间件
 */
function errorHandler(err, req, res, next) {
  const statusCode = err.status || 500;

  console.error('错误:', {
    statusCode,
    message: err.message,
    stack: err.stack,
    path: req.path,
    method: req.method
  });

  res.status(statusCode).json({
    code: statusCode,
    message: err.message || '服务器内部错误',
    stack: process.env.NODE_ENV === 'development' ? err.stack : undefined
  });
}

/**
 * 404 错误处理中间件
 */
function notFoundHandler(req, res) {
  res.status(404).json({
    code: 404,
    message: `接口不存在: ${req.method} ${req.path}`
  });
}

module.exports = {
  errorHandler,
  notFoundHandler
};
