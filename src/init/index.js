/**
 * 系统初始化模块
 * 
 * 完整的初始化流程：
 * 1. 检查是否启用初始化
 * 2. 检查数据库是否存在
 * 3. 检查表数量是否正确
 * 4. 检查表字段是否完整
 * 5. 创建管理员账户
 * 6. 清理 .env 配置（删除敏感信息）
 */

const fs = require('fs');
const path = require('path');
const bcrypt = require('bcryptjs');
const mysql = require('mysql2/promise');
const dayjs = require('dayjs');

// 期望的表列表（共 25 张）
const EXPECTED_TABLES = [
  'account',
  'account_balance',
  'account_transfer',
  'app_config',
  'asset',
  'asset_register',
  'asset_snapshot',
  'budget',
  'bus_category',
  'bus_fund_history',
  'bus_recurring',
  'card_base',
  'card_bill',
  'card_log',
  'card_repay',
  'device_crypto',
  'fixed_asset',
  'fund',
  'moment',
  'sys_attachment',
  'todo',
  'user_info',
  'user_log',
  'work_job',
  'work_salary',
];

// 关键表的必填字段（表名 -> 字段列表）
const REQUIRED_FIELDS = {
  user_info: ['id', 'username', 'email', 'login_pwd', 'identity', 'create_time', 'update_time'],
  budget: ['id', 'user_id', 'title', 'budget_type', 'budget_amount', 'cycle', 'create_time', 'update_time'],
  account: ['id', 'user_id', 'category_id', 'direction', 'amount', 'trans_date', 'create_time'],
  card_base: ['id', 'user_id', 'bank_id', 'card_type', 'last4_no', 'open_date', 'expire_date'],
};

// 状态统计
const stats = {
  startTime: null,
  endTime: null,
  checks: {
    database: null,
    tables: null,
    fields: null,
    admin: null,
    cleanup: null,
  },
};

/**
 * 打印分隔线
 */
function printDivider(char = '─', length = 50) {
  console.log(char.repeat(length));
}

/**
 * 打印标题
 */
function printTitle(text) {
  const border = '═'.repeat(50);
  console.log(`\n╔${border}╗`);
  const padding = Math.max(0, (50 - text.length) / 2);
  const leftPad = ' '.repeat(Math.floor(padding));
  const rightPad = ' '.repeat(Math.ceil(padding));
  console.log(`║${leftPad}${text}${rightPad}║`);
  console.log(`╚${border}╝\n`);
}

/**
 * 记录初始化日志
 */
function logInit(message, type = 'info') {
  const timestamp = dayjs().format('HH:mm:ss');
  const prefix = type === 'error' ? '❌' : type === 'success' ? '✅' : type === 'warn' ? '⚠️' : type === 'skip' ? '⏭️' : 'ℹ️';
  console.log(`  ${prefix} [${timestamp}] ${message}`);
}

/**
 * 打印子步骤
 */
function logSub(message, status = null) {
  if (status === 'ok') {
    console.log(`     ✓ ${message}`);
  } else if (status === 'fail') {
    console.log(`     ✗ ${message}`);
  } else if (status === 'warn') {
    console.log(`     ⚠ ${message}`);
  } else {
    console.log(`     · ${message}`);
  }
}

/**
 * 检查是否启用初始化
 */
function isInitEnabled() {
  return process.env.INIT_ENABLE === 'true';
}

/**
 * 打印当前配置信息
 */
function printConfig() {
  console.log('  📋 当前配置:');
  console.log(`     数据库: ${process.env.DB_HOST}:${process.env.DB_PORT}/${process.env.DB_NAME}`);
  console.log(`     用户名: ${process.env.DB_USER}`);
  console.log(`     管理员: ${process.env.INIT_ADMIN_USER || 'admin'}`);
  printDivider();
}

/**
 * 获取数据库连接（不带 database）
 */
async function getConnectionWithoutDb() {
  return mysql.createConnection({
    host: process.env.DB_HOST || 'localhost',
    port: process.env.DB_PORT || 3306,
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || '',
    multipleStatements: true,
  });
}

/**
 * 获取数据库连接（带 database）
 */
async function getConnectionWithDb() {
  return mysql.createConnection({
    host: process.env.DB_HOST || 'localhost',
    port: process.env.DB_PORT || 3306,
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || '',
    database: process.env.DB_NAME || 'live',
    multipleStatements: true,
  });
}

/**
 * 步骤 1: 检查数据库是否存在，不存在则自动创建
 */
async function checkDatabase() {
  console.log('\n📦 [步骤 1/5] 检查数据库');
  printDivider();
  
  const dbName = process.env.DB_NAME || 'live';
  logInit(`目标数据库: ${dbName}`);
  logInit('正在连接 MySQL 服务器...');
  
  try {
    const startTime = Date.now();
    const conn = await getConnectionWithoutDb();
    
    logInit('已建立连接，正在查询...');
    const [rows] = await conn.query(`SHOW DATABASES LIKE '${dbName}'`);
    await conn.end();
    
    const elapsed = Date.now() - startTime;
    
    if (rows.length === 0) {
      logInit(`⚠️ 数据库 ${dbName} 不存在，尝试自动创建...`, 'warn');
      
      // 导入自动数据库初始化模块
      const { autoInitDatabase } = require('./autoDatabase');
      const autoResult = await autoInitDatabase();
      
      if (autoResult.success) {
        logInit(`✅ 数据库 ${dbName} 自动创建成功`, 'success');
        stats.checks.database = true;
        return true;
      } else {
        logInit(`❌ 自动创建失败: ${autoResult.reason}`, 'error');
        stats.checks.database = false;
        return false;
      }
    }
    
    logInit(`✅ 数据库 ${dbName} 存在`, 'success');
    logInit(`查询耗时: ${elapsed}ms`);
    stats.checks.database = true;
    return true;
  } catch (error) {
    logInit(`❌ 连接失败: ${error.message}`, 'error');
    logInit('请检查数据库配置和网络连接', 'warn');
    stats.checks.database = false;
    return false;
  }
}

/**
 * 步骤 2: 检查表数量
 */
async function checkTableCount() {
  console.log('\n📋 [步骤 2/5] 检查数据表');
  printDivider();
  
  const expectedCount = EXPECTED_TABLES.length;
  logInit(`期望表数量: ${expectedCount}`);
  logInit('正在查询数据库表...');
  
  try {
    const conn = await getConnectionWithDb();
    const [rows] = await conn.query(`SHOW TABLES`);
    await conn.end();
    
    const existingTables = rows.map(row => Object.values(row)[0]);
    const existingCount = existingTables.length;
    
    logInit(`实际表数量: ${existingCount}`);
    
    if (existingCount === 0) {
      logInit('❌ 数据库中没有表', 'error');
      logInit('请先执行 SQL 文件创建表结构', 'warn');
      stats.checks.tables = false;
      return false;
    }
    
    if (existingCount < expectedCount) {
      const missing = EXPECTED_TABLES.filter(t => !existingTables.includes(t));
      logInit(`⚠️ 缺少 ${missing.length} 张表:`, 'warn');
      missing.forEach(t => logSub(t, 'fail'));
      stats.checks.tables = false;
      return false;
    }
    
    if (existingCount > expectedCount) {
      logInit(`⚠️ 表数量超出预期 (${existingCount} > ${expectedCount})`, 'warn');
      logInit('多出的表可能是自定义表，不影响使用', 'warn');
    }
    
    // 列出所有存在的表
    console.log('\n  表清单:');
    EXPECTED_TABLES.forEach((tableName, index) => {
      const exists = existingTables.includes(tableName);
      logSub(`${String(index + 1).padStart(2, '0')}. ${tableName}`, exists ? 'ok' : 'fail');
    });
    
    logInit(`✅ 表数量检查通过 (${existingCount}/${expectedCount})`, 'success');
    stats.checks.tables = true;
    return true;
  } catch (error) {
    logInit(`❌ 查询失败: ${error.message}`, 'error');
    logInit('表查询失败', 'warn');
    stats.checks.tables = false;
    return false;
  }
}

/**
 * 步骤 3: 检查关键表字段
 */
async function checkTableFields() {
  console.log('\n🔍 [步骤 3/5] 检查表字段');
  printDivider();
  
  const tableCount = Object.keys(REQUIRED_FIELDS).length;
  logInit(`将检查 ${tableCount} 张关键表的字段完整性`);
  
  let allFieldsOk = true;
  let checkedCount = 0;
  let okCount = 0;
  let warnCount = 0;
  
  try {
    const conn = await getConnectionWithDb();
    
    for (const [tableName, requiredFields] of Object.entries(REQUIRED_FIELDS)) {
      checkedCount++;
      logInit(`\n  检查 [${checkedCount}/${tableCount}] ${tableName}...`);
      
      try {
        const [rows] = await conn.query(`SHOW COLUMNS FROM ${tableName}`);
        const actualFields = rows.map(row => row.Field);
        
        const missingFields = requiredFields.filter(f => !actualFields.includes(f));
        
        if (missingFields.length > 0) {
          logSub(`缺少字段: ${missingFields.join(', ')}`, 'fail');
          allFieldsOk = false;
          warnCount++;
        } else {
          logSub(`字段完整 (${actualFields.length} 个)`, 'ok');
          okCount++;
        }
        
        // 列出必填字段状态
        requiredFields.forEach(field => {
          const exists = actualFields.includes(field);
          logSub(`   ${exists ? '✓' : '✗'} ${field}`, exists ? 'ok' : 'fail');
        });
      } catch (tableError) {
        logSub(`表不存在或查询失败`, 'fail');
        allFieldsOk = false;
        warnCount++;
      }
    }
    
    await conn.end();
    
    printDivider();
    logInit(`字段检查完成: ${okCount} 张通过, ${warnCount} 张有问题`);
    
    if (allFieldsOk) {
      logInit('✅ 所有关键表字段完整', 'success');
    } else {
      logInit('⚠️ 部分表字段缺失，建议重新执行 SQL 文件', 'warn');
    }
    
    stats.checks.fields = allFieldsOk;
    return allFieldsOk;
  } catch (error) {
    logInit(`❌ 检查失败: ${error.message}`, 'error');
    stats.checks.fields = false;
    return false;
  }
}

/**
 * 步骤 4: 检查/创建管理员
 */
async function createAdmin() {
  console.log('\n👤 [步骤 4/5] 管理账户');
  printDivider();
  
  const username = process.env.INIT_ADMIN_USER || 'admin';
  const password = process.env.INIT_ADMIN_PASS || 'admin123';
  const email = process.env.INIT_ADMIN_EMAIL || 'admin@example.com';
  
  logInit(`目标账户: ${username}`);
  logInit(`密码强度: ${password.length >= 8 ? '安全' : '建议使用更长的密码'} (${password.length} 位)`);
  logInit(`邮箱: ${email}`);
  printDivider();
  
  // 检查是否已存在
  logInit('正在检查账户...');
  
  try {
    const conn = await getConnectionWithDb();
    const [rows] = await conn.query(`SELECT id, username, identity, create_time FROM user_info WHERE username = ? LIMIT 1`, [username]);
    
    if (rows.length > 0) {
      const admin = rows[0];
      logInit('⏭️ 管理员已存在，跳过创建', 'skip');
      console.log('\n  账户信息:');
      logSub(`用户名: ${admin.username}`);
      logSub(`身份: ${admin.identity}`);
      logSub(`创建时间: ${admin.create_time}`);
      await conn.end();
      stats.checks.admin = true;
      return true;
    }
    
    await conn.end();
  } catch (error) {
    logInit(`查询失败: ${error.message}`, 'warn');
  }
  
  printDivider();
  logInit('开始创建管理员账户...');
  
  try {
    const idUtils = require('../../common/utils/idUtils');
    const id = idUtils.userId();
    const saltRounds = parseInt(process.env.BCRYPT_SALT_ROUNDS || '10', 10);
    
    logInit(`正在加密密码 (salt rounds: ${saltRounds})...`);
    const hashedPassword = await bcrypt.hash(password, saltRounds);
    
    const conn = await getConnectionWithDb();
    
    logInit('正在写入数据库...');
    await conn.query(`
      INSERT INTO user_info (id, username, email, login_pwd, identity, create_time, update_time)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `, [
      id,
      username,
      email,
      hashedPassword,
      'admin',
      Date.now(),
      dayjs().format('YYYY-MM-DD HH:mm:ss'),
    ]);
    await conn.end();
    
    console.log('\n  ✅ 管理员创建成功!');
    console.log('\n  账户信息:');
    logSub(`用户名: ${username}`);
    logSub(`密码: ${password}`);
    logSub(`身份: admin`);
    
    stats.checks.admin = true;
    return true;
  } catch (error) {
    logInit(`❌ 创建失败: ${error.message}`, 'error');
    stats.checks.admin = false;
    return false;
  }
}

/**
 * 步骤 5: 清理 .env 文件
 */
function cleanupEnvFile() {
  console.log('\n🧹 [步骤 5/5] 清理配置');
  printDivider();
  
  const envPath = path.join(__dirname, '../../.env');
  
  logInit('正在清理初始化配置...');
  
  try {
    if (!fs.existsSync(envPath)) {
      logInit('⚠️ .env 文件不存在，跳过', 'warn');
      stats.checks.cleanup = true;
      return true;
    }
    
    let content = fs.readFileSync(envPath, 'utf-8');
    const originalLength = content.length;
    
    // 删除初始化相关的行
    const linesToRemove = [
      'INIT_ENABLE=',
      'INIT_ADMIN_USER=',
      'INIT_ADMIN_PASS=',
      'INIT_ADMIN_EMAIL=',
    ];
    
    let removedCount = 0;
    linesToRemove.forEach(linePrefix => {
      const regex = new RegExp(`^${linePrefix.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}.*\\n?`, 'gm');
      const matches = content.match(regex);
      if (matches) {
        removedCount += matches.length;
        content = content.replace(regex, '');
      }
    });
    
    // 如果有注释掉的初始配置行，也清理掉
    content = content.replace(/^#.*初始化配置.*\n?/gm, '');
    content = content.replace(/^#.*INIT_ENABLE.*\n?/gm, '');
    content = content.replace(/^#.*初始管理员.*\n?/gm, '');
    content = content.replace(/^#.*INIT_ADMIN.*\n?/gm, '');
    
    // 清理连续空行
    content = content.replace(/\n{3,}/g, '\n\n');
    
    // 移除末尾空行
    content = content.trim() + '\n';
    
    fs.writeFileSync(envPath, content);
    
    const newLength = content.length;
    const removedBytes = originalLength - newLength;
    
    logInit(`✅ 已清理 ${removedCount} 行配置`);
    logSub(`移除内容大小: ${removedBytes} 字节`);
    
    // 验证清理结果
    const verifyContent = fs.readFileSync(envPath, 'utf-8');
    const stillHasInit = verifyContent.includes('INIT_ENABLE') || 
                         verifyContent.includes('INIT_ADMIN_USER') ||
                         verifyContent.includes('INIT_ADMIN_PASS');
    
    if (stillHasInit) {
      logInit('⚠️ 部分配置未清理干净，请手动检查 .env', 'warn');
    }
    
    stats.checks.cleanup = true;
    return true;
  } catch (error) {
    logInit(`❌ 清理失败: ${error.message}`, 'error');
    stats.checks.cleanup = false;
    return false;
  }
}

/**
 * 记录初始化完成标记
 */
function markInitialized() {
  const flagPath = path.join(__dirname, '../../.init.flag');
  try {
    const flagData = {
      initializedAt: Date.now(),
      timestamp: dayjs().format('YYYY-MM-DD HH:mm:ss'),
      version: '1.0.0',
      checks: stats.checks,
    };
    fs.writeFileSync(flagPath, JSON.stringify(flagData, null, 2));
    logSub('标记文件: .init.flag');
  } catch (error) {
    logSub(`标记文件创建失败: ${error.message}`, 'warn');
  }
}

/**
 * 打印初始化摘要
 */
function printSummary(success) {
  stats.endTime = Date.now();
  const totalTime = stats.endTime - stats.startTime;
  
  console.log('\n');
  printDivider('═', 50);
  
  console.log('\n📊 初始化摘要\n');
  
  console.log('  检查项                    状态');
  printDivider('─', 40);
  
  const checkNames = {
    database: '数据库连接',
    tables: '数据表检查',
    fields: '字段完整性',
    admin: '管理员账户',
    cleanup: '配置清理',
  };
  
  for (const [key, name] of Object.entries(checkNames)) {
    const status = stats.checks[key];
    const statusText = status === true ? '✅ 通过' : status === false ? '❌ 失败' : '⏸️ 跳过';
    const statusType = status === true ? 'ok' : status === false ? 'fail' : 'warn';
    logSub(`${name.padEnd(20)} ${statusText}`, statusType);
  }
  
  printDivider('─', 40);
  logSub(`总耗时: ${totalTime}ms`);
  
  console.log('\n');
  printDivider('═', 50);
  
  if (success) {
    console.log('\n  🎉 初始化成功! 系统已就绪\n');
    printTitle('INIT COMPLETE');
  } else {
    console.log('\n  ⚠️ 初始化部分完成，请检查上述错误\n');
    printTitle('INIT PARTIAL');
  }
}

/**
 * 主初始化函数
 */
async function init() {
  stats.startTime = Date.now();
  
  printTitle('SYSTEM INIT');
  
  console.log('🔧 系统初始化程序启动...\n');
  logInit(`开始时间: ${dayjs().format('YYYY-MM-DD HH:mm:ss')}`);
  logInit(`Node 版本: ${process.version}`);
  logInit(`运行环境: ${process.env.NODE_ENV || 'development'}`);
  
  // 显示配置
  printConfig();
  
  // 检查是否启用初始化
  if (!isInitEnabled()) {
    console.log('\n⏭️ [跳过] 初始化未启用 (INIT_ENABLE≠true)');
    logInit('提示: 如需初始化，请在 .env 中设置 INIT_ENABLE=true');
    return { success: true, skipped: true, reason: 'disabled' };
  }

  logInit('初始化已启用，开始执行检测...\n');

  let allPassed = true;

  try {
    // 步骤 1: 检查数据库
    const dbOk = await checkDatabase();
    if (!dbOk) {
      allPassed = false;
      console.log('\n');
      printDivider();
      logInit('❌ 数据库检查失败，初始化终止', 'error');
      logInit('请先确保数据库已创建并可连接', 'warn');
      printSummary(false);
      return { success: false, step: 1 };
    }

    // 步骤 2: 检查表数量
    const tablesOk = await checkTableCount();
    if (!tablesOk) {
      allPassed = false;
      console.log('\n');
      printDivider();
      logInit('⚠️ 表检查未完全通过，继续执行...', 'warn');
    }

    // 步骤 3: 检查表字段
    await checkTableFields();

    // 步骤 4: 创建管理员
    const adminOk = await createAdmin();
    if (!adminOk) {
      allPassed = false;
      console.log('\n');
      printDivider();
      logInit('❌ 管理员创建失败，初始化终止', 'error');
      printSummary(false);
      return { success: false, step: 4 };
    }

    // 步骤 5: 清理配置
    cleanupEnvFile();

    // 标记完成
    markInitialized();

    // 打印摘要
    printSummary(allPassed);

    return { success: allPassed };
  } catch (error) {
    console.log('\n');
    printDivider();
    logInit(`❌ 初始化失败: ${error.message}`, 'error');
    logInit('请检查数据库连接和配置后重试', 'warn');
    printSummary(false);
    
    return { success: false, error: error.message };
  }
}

module.exports = init;
