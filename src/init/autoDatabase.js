/**
 * 数据库自动初始化脚本
 * 
 * 功能：
 * 1. 检测 mysql/live.sql 文件
 * 2. 自动创建数据库（如不存在）
 * 3. 导入表结构和初始数据
 * 4. 与 init/index.js 配合使用
 */

const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');

const SQL_FILE_PATH = path.join(__dirname, '../../mysql/live.sql');

/**
 * 获取数据库连接（不带 database，用于创建数据库）
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
    console.log('  提示: 请确保 mysql/live.sql 文件存在');
    return false;
  }
  
  const stats = fs.statSync(SQL_FILE_PATH);
  console.log(`  ✓ SQL 文件存在 (${(stats.size / 1024).toFixed(1)} KB)`);
  return true;
}

/**
 * 读取并处理 SQL 文件
 */
function readSqlFile() {
  console.log('  📖 读取 SQL 文件...');
  
  let content = fs.readFileSync(SQL_FILE_PATH, 'utf-8');
  
  // 移除 SQL 注释和版权信息
  content = content.replace(/-- phpMyAdmin SQL Dump[\s\S]*?-- version.*?\n/g, '');
  content = content.replace(/-- https:\/\/.*?\n/g, '');
  content = content.replace(/-- \d{4}-\d{2}-\d{2}.*?\n/g, '');
  
  // 移除 SET 语句（设置模式）
  content = content.replace(/SET SQL_MODE\s*=.*?;/gi, '');
  content = content.replace(/SET time_zone\s*=.*?;/gi, '');
  content = content.replace(/START TRANSACTION;/gi, '');
  content = content.replace(/COMMIT;/gi, '');
  content = content.replace(/SET.*?;/gi, '');
  
  // 移除文件头部的 SET 语句块
  content = content.replace(/\/\*!40101 SET @OLD_CHARACTER_SET_CLIENT.*?\*\/;[\s\n]*/gs, '');
  content = content.replace(/\/\*!40101 SET @OLD_CHARACTER_SET_RESULTS.*?\*\/;[\s\n]*/gs, '');
  content = content.replace(/\/\*!40101 SET @OLD_CHARLECTION_CONNECTION.*?\*\/;[\s\n]*/gs, '');
  content = content.replace(/\/\*!40101 SET NAMES utf8mb4 \*\/;[\s\n]*/gs, '');
  
  // 移除文件尾部的恢复语句
  content = content.replace(/\/\*!40101 SET CHARACTER_SET_CLIENT.*?\*\/;[\s\n]*/gs, '');
  content = content.replace(/\/\*!40101 SET CHARACTER_SET_RESULTS.*?\*\/;[\s\n]*/gs, '');
  content = content.replace(/\/\*!40101 SET COLLATION_CONNECTION.*?\*\/;[\s\n]*/gs, '');
  
  // 清理多余的空行
  content = content.replace(/\n{3,}/g, '\n\n');
  
  // 移除注释行
  const lines = content.split('\n');
  const cleanLines = lines.filter(line => {
    const trimmed = line.trim();
    // 保留空行
    if (!trimmed) return true;
    // 移除单行注释
    if (trimmed.startsWith('--')) return false;
    // 移除块注释
    if (trimmed.startsWith('/*')) return false;
    return true;
  });
  
  content = cleanLines.join('\n').trim();
  
  console.log(`  ✓ SQL 内容已处理 (${content.length} 字符)`);
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
 * 导入 SQL 表结构
 */
async function importTables(sqlContent) {
  console.log('  📦 导入表结构...');
  
  try {
    const conn = await getConnectionWithDb();
    
    // 分割 SQL 语句
    const statements = sqlContent
      .split(/;\s*\n/)
      .map(s => s.trim())
      .filter(s => s.length > 0);
    
    let successCount = 0;
    let failCount = 0;
    
    for (const statement of statements) {
      if (!statement || statement.length < 10) continue;
      
      try {
        await conn.query(statement);
        successCount++;
      } catch (err) {
        // 忽略已存在的表等非致命错误
        if (!err.message.includes('already exists')) {
          console.log(`  ⚠️  执行失败: ${err.message.substring(0, 60)}...`);
        }
        failCount++;
      }
    }
    
    console.log(`  ✅ 表导入完成 (成功: ${successCount}, 跳过: ${failCount})`);
    await conn.end();
    return true;
  } catch (error) {
    console.log(`  ❌ 导入表失败: ${error.message}`);
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
    console.log(`  ✅ 验证通过，共 ${tableCount} 张表`);
    return true;
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
  if (!sqlContent) {
    return { success: false, reason: 'sql_content_empty' };
  }
  
  // 3. 创建数据库
  const dbCreated = await createDatabase();
  if (!dbCreated) {
    return { success: false, reason: 'database_creation_failed' };
  }
  
  // 4. 导入表结构
  const tablesImported = await importTables(sqlContent);
  if (!tablesImported) {
    return { success: false, reason: 'table_import_failed' };
  }
  
  // 5. 验证
  const verified = await verifyTables();
  
  console.log('\n✅ 数据库初始化完成！\n');
  
  return { success: true };
}

// 直接运行脚本
if (require.main === module) {
  (async () => {
    const result = await autoInitDatabase();
    process.exit(result.success ? 0 : 1);
  })();
}

module.exports = { autoInitDatabase };
