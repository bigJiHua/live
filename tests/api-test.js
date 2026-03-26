/**
 * API 测试脚本
 * 可以直接运行此文件进行接口测试
 */
const http = require('http');

// 测试配置
const BASE_URL = 'http://localhost:3000';
let authToken = null;

// 辅助函数：发送 HTTP 请求
function makeRequest(method, path, data = null, headers = {}) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, BASE_URL);
    const options = {
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      method,
      headers: {
        'Content-Type': 'application/json',
        ...headers,
      },
    };

    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', (chunk) => {
        body += chunk;
      });
      res.on('end', () => {
        try {
          const parsed = JSON.parse(body);
          resolve({ status: res.statusCode, data: parsed });
        } catch (e) {
          resolve({ status: res.statusCode, data: body });
        }
      });
    });

    req.on('error', reject);

    if (data) {
      req.write(JSON.stringify(data));
    }

    req.end();
  });
}

// 测试函数
async function runTests() {
  console.log('=== 开始 API 测试 ===\n');

  try {
    // 1. 健康检查
    console.log('1. 健康检查');
    const health = await makeRequest('GET', '/health');
    console.log('   状态:', health.status);
    console.log('   数据:', health.data);
    console.log('');

    // 2. 注册用户
    console.log('2. 注册用户');
    const register = await makeRequest('POST', '/api/auth/register', {
      username: 'testuser',
      email: 'test@example.com',
      password: 'password123',
    });
    console.log('   状态:', register.status);
    console.log('   消息:', register.data.message);
    console.log('');

    // 3. 登录
    console.log('3. 用户登录');
    const login = await makeRequest('POST', '/api/auth/login', {
      email: 'test@example.com',
      password: 'password123',
    });
    console.log('   状态:', login.status);
    console.log('   消息:', login.data.message);
    if (login.data.token) {
      authToken = login.data.token;
      console.log('   Token:', authToken.substring(0, 20) + '...');
    }
    console.log('');

    // 4. 获取用户信息
    console.log('4. 获取用户信息');
    const me = await makeRequest('GET', '/api/auth/me', null, {
      Authorization: `Bearer ${authToken}`,
    });
    console.log('   状态:', me.status);
    console.log('   用户:', me.data.user);
    console.log('');

    // 5. 设置 PIN 码
    console.log('5. 设置 PIN 码');
    const setPin = await makeRequest(
      'POST',
      '/api/security/pin/set',
      { pin: '123456', confirmPassword: '123456' },
      { Authorization: `Bearer ${authToken}` }
    );
    console.log('   状态:', setPin.status);
    console.log('   消息:', setPin.data.message);
    console.log('');

    // 6. 创建分类
    console.log('6. 创建分类');
    const createCategory = await makeRequest(
      'POST',
      '/api/account/categories',
      { name: '餐饮', type: 'expense', color: '#ff4d4f', icon: '🍔' },
      { Authorization: `Bearer ${authToken}` }
    );
    console.log('   状态:', createCategory.status);
    console.log('   消息:', createCategory.data.message);
    const categoryId = createCategory.data.category?.id;
    console.log('   分类ID:', categoryId);
    console.log('');

    // 7. 验证 PIN 码（因为账务接口需要 PIN 验证）
    console.log('7. 验证 PIN 码');
    const verifyPin = await makeRequest(
      'POST',
      '/api/security/pin/verify',
      { pin: '123456' },
      { Authorization: `Bearer ${authToken}` }
    );
    console.log('   状态:', verifyPin.status);
    console.log('   消息:', verifyPin.data.message);
    console.log('');

    // 8. 创建账务流水
    console.log('8. 创建账务流水');
    const createTransaction = await makeRequest(
      'POST',
      '/api/account/transactions',
      {
        amount: 50.0,
        type: 'expense',
        categoryId: categoryId,
        description: '午餐',
        date: '2024-03-26',
      },
      { Authorization: `Bearer ${authToken}` }
    );
    console.log('   状态:', createTransaction.status);
    console.log('   消息:', createTransaction.data.message);
    console.log('');

    console.log('=== 测试完成 ===');
  } catch (error) {
    console.error('测试出错:', error);
  }
}

// 运行测试
console.log('提示: 请确保服务已启动 (npm start)');
console.log('等待 2 秒后开始测试...\n');
setTimeout(runTests, 2000);
