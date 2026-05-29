/**
 * 数据库迁移运行器
 *
 * 适用场景：INIT_ENABLE=false 时，启动自动跑未执行的增量迁移（日常保护）
 * 当 INIT_ENABLE=true 时，走 schemaSync.js 的 live.sql 全量同步
 */

const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');

const MIGRATIONS_DIR = path.join(__dirname, '../../mysql/migrations');
const TRACK_TABLE   = '_migrations';

async function ensureTrackTable(conn) {
  await conn.execute(`
    CREATE TABLE IF NOT EXISTS \`${TRACK_TABLE}\` (
      \`filename\`    varchar(255) NOT NULL,
      \`applied_at\`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (\`filename\`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='迁移记录表'
  `);
}

async function getApplied(conn) {
  const [rows] = await conn.execute(`SELECT filename FROM \`${TRACK_TABLE}\` ORDER BY filename`);
  return rows.map(r => r.filename);
}

async function markApplied(conn, filename) {
  await conn.execute(`INSERT INTO \`${TRACK_TABLE}\` (filename) VALUES (?)`, [filename]);
}

function getPendingFiles(applied) {
  if (!fs.existsSync(MIGRATIONS_DIR)) return [];
  return fs.readdirSync(MIGRATIONS_DIR)
    .filter(f => f.endsWith('.sql'))
    .sort()
    .filter(f => !applied.includes(f));
}

/**
 * 执行单个迁移文件（整文件执行，MySQL 5.7 兼容）
 */
async function runFile(conn, filename) {
  const filePath = path.join(MIGRATIONS_DIR, filename);
  const sql = fs.readFileSync(filePath, 'utf-8');

  // 拆 DDL 语句，按 ; 后跟换行分割（不拆存储过程中的分号）
  // 注意：不在 split 层过滤 -- 注释，因为注释和 ALTER 常在同一片段内
  //       行级注释清理在下方 for 循环中逐行处理
  const statements = sql
    .split(/;\s*\n/)
    .map(s => s.trim())
    .filter(s => s.length > 0);

  for (const stmt of statements) {
    // 去掉行内注释
    const clean = stmt.split('\n').filter(l => !l.trim().startsWith('--')).join('\n').trim();
    if (!clean) continue;

    try {
      await conn.execute(clean);
    } catch (err) {
      // MySQL 5.7 兼容：重复列/键视为已存在
      const skipped = ['ER_DUP_FIELDNAME', 'ER_DUP_KEYNAME', 'ER_DUP_ENTRY'];
      if (skipped.includes(err.code)) {
        console.log(`     ⚠ 已存在，跳过: ${clean.substring(0, 60)}...`);
        continue;
      }
      throw err;
    }
  }

  await markApplied(conn, filename);
}

/**
 * 主入口：只跑未执行的迁移
 * @param {mysql.Connection} conn - 已选数据库的连接
 */
async function runMigrations(conn) {
  console.log('\n🔄 检查增量迁移...');
  await ensureTrackTable(conn);

  const applied    = await getApplied(conn);
  const pending    = getPendingFiles(applied);

  if (pending.length === 0) {
    console.log('  ✅ 迁移已是最新');
    return { count: 0 };
  }

  console.log(`  📋 待执行: ${pending.length} 个`);
  for (const f of pending) {
    await runFile(conn, f);
    console.log(`     ✅ ${f}`);
  }

  console.log(`  🎉 完成`);
  return { count: pending.length };
}

module.exports = { runMigrations };
