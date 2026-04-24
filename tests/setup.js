// 测试全局设置文件
// 在所有测试运行前执行

// 设置测试环境变量
process.env.NODE_ENV = 'test';
process.env.DB_HOST = 'localhost';
process.env.DB_USER = 'root';
process.env.DB_PASSWORD = '';
process.env.DB_NAME = 'life_manager_test';
process.env.JWT_SECRET = 'test-jwt-secret-key';

// 全局后置钩子
afterAll(async () => {
  // 测试完成后清理资源
  console.log('测试完成');
});
