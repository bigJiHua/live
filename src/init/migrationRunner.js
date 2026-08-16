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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='迁移记录表'
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
    .replace(/\r\n/g, '\n') // 统一换行符，兼容 Windows CRLF
    // 以 `;` 后跟换行或文件结尾作为语句分隔
    // 旧正则 /;\s*\n/ 无法识别"最后一条语句以 ; 结尾但文件无尾随换行"的情况，
    // 会导致末条语句被静默丢弃却仍被 markApplied（数据丢失且不再重试）。
    .split(/;\s*(?:\n|$)/)
    .map(s => s.trim())
    .filter(s => s.length > 0);

  for (const stmt of statements) {
    // 去掉行内注释
    const clean = stmt.split('\n').filter(l => !l.trim().startsWith('--')).join('\n').trim();
    if (!clean) continue;

    try {
      // 迁移语句为纯 DDL/DML，无需预编译参数；使用 query 协议更直观，避免对 prepared statement 协议的任何限制误解
      await conn.query(clean);
    } catch (err) {
      // MySQL 5.7 兼容：重复列/键视为已存在（幂等重跑）
      // ⚠ C7 审计：ER_DUP_ENTRY 不再静默跳过——ADD UNIQUE KEY 若因历史重复数据报 ER_DUP_ENTRY，
      //   说明唯一约束并未建立；跳过并 markApplied 会永久缺少约束。此时必须抛错暴露，由人工清重后重跑。
      const skipped = ['ER_DUP_FIELDNAME', 'ER_DUP_KEYNAME'];
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
