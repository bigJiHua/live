/**
 * 数据库自动初始化脚本
 * 
 * 功能：
 * 1. 检测 mysql/live.sql 文件
 * 2. 自动创建数据库（如不存在）
 * 3. 导入表结构和初始数据
 */

const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');

const SQL_FILE_PATH = path.join(__dirname, '../../mysql/live.sql');

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
 * 检查 SQL 文件是否存在
 */
function checkSqlFile() {
  console.log('  📄 检查 SQL 文件...');
  
  if (!fs.existsSync(SQL_FILE_PATH)) {
    console.log(`  ❌ SQL 文件不存在: ${SQL_FILE_PATH}`);
    return false;
  }
  
  const stats = fs.statSync(SQL_FILE_PATH);
  console.log(`  ✓ SQL 文件存在 (${(stats.size / 1024).toFixed(1)} KB)`);
  return true;
}

/**
 * 读取 SQL 文件内容
 */
function readSqlFile() {
  console.log('  📖 读取 SQL 文件...');
  
  let content = fs.readFileSync(SQL_FILE_PATH, 'utf-8');
  
  // 统一换行符
  content = content.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  
  console.log(`  ✓ SQL 文件已读取 (${content.length} 字符)`);
  return content;
}

/**
 * 创建数据库
 */
async function createDatabase() {
  const dbName = process.env.DB_NAME || 'live';
  console.log(`  🗄️  创建数据库: ${dbName}...`);
  
  try {
    const conn = await getConnectionWithoutDb();
    
    // 检查数据库是否存在
    const [rows] = await conn.query(`SHOW DATABASES LIKE '${dbName}'`);
    
    if (rows.length > 0) {
      console.log(`  ⏭️  数据库 ${dbName} 已存在，跳过创建`);
      await conn.end();
      return true;
    }
    
    // 创建数据库
    await conn.query(`CREATE DATABASE \`${dbName}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`);
    console.log(`  ✅ 数据库 ${dbName} 创建成功`);
    await conn.end();
    return true;
  } catch (error) {
    console.log(`  ❌ 创建数据库失败: ${error.message}`);
    return false;
  }
}

/**
 * 直接执行整个 SQL 文件
 */
async function importSqlFile(sqlContent) {
  console.log('  📦 执行 SQL 文件...');
  
  try {
    const conn = await getConnectionWithDb();
    
    // 直接执行整个 SQL 内容（mysql2 支持 multipleStatements）
    await conn.query(sqlContent);
    
    console.log('  ✅ SQL 文件执行成功');
    await conn.end();
    return true;
  } catch (error) {
    console.log(`  ❌ 执行失败: ${error.message}`);
    return false;
  }
}

/**
 * 验证表是否创建成功
 */
async function verifyTables() {
  console.log('  🔍 验证表结构...');
  
  try {
    const conn = await getConnectionWithDb();
    const [rows] = await conn.query('SHOW TABLES');
    await conn.end();
    
    const tableCount = rows.length;
    console.log(`  ✅ 共 ${tableCount} 张表`);
    
    if (tableCount > 0) {
      rows.forEach((row, i) => {
        const tableName = Object.values(row)[0];
        console.log(`     ${String(i + 1).padStart(2, '0')}. ${tableName}`);
      });
    }
    
    return tableCount > 0;
  } catch (error) {
    console.log(`  ❌ 验证失败: ${error.message}`);
    return false;
  }
}

/**
 * 主函数
 */
async function autoInitDatabase() {
  console.log('\n╔══════════════════════════════════════════════╗');
  console.log('║       数据库自动初始化                        ║');
  console.log('╚══════════════════════════════════════════════╝\n');
  
  // 1. 检查 SQL 文件
  if (!checkSqlFile()) {
    return { success: false, reason: 'sql_file_not_found' };
  }
  
  // 2. 读取 SQL 内容
  const sqlContent = readSqlFile();
  
  // 3. 创建数据库
  const dbCreated = await createDatabase();
  if (!dbCreated) {
    return { success: false, reason: 'database_creation_failed' };
  }
  
  // 4. 执行 SQL 文件
  const sqlExecuted = await importSqlFile(sqlContent);
  if (!sqlExecuted) {
    return { success: false, reason: 'sql_execution_failed' };
  }
  
  // 5. 验证
  const verified = await verifyTables();
  
  if (verified) {
    console.log('\n✅ 数据库初始化完成！\n');
    return { success: true };
  } else {
    return { success: false, reason: 'verification_failed' };
  }
}

// 直接运行脚本
if (require.main === module) {
  (async () => {
    const result = await autoInitDatabase();
    process.exit(result.success ? 0 : 1);
  })();
}

module.exports = { autoInitDatabase };
