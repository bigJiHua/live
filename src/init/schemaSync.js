/**
 * 数据库备份与 Schema 同步
 *
 * 流程：
 * 1. 全量备份：将现有 live 库所有表 + 数据导出为 SQL 文件
 * 2. Schema 对比：解析 live.sql 的目标结构 vs 实际库结构
 * 3. 差异修复：补列 / 删列 / 改类型 / 补索引
 *
 * 原则：
 * - 同名列保留数据
 * - SQL 有 DB 无 → ADD COLUMN（新数据用 DEFAULT）
 * - DB 有 SQL 无 → DROP COLUMN（⚠ 该列数据丢失）
 * - 类型不一致 → MODIFY COLUMN（可能截断）
 */

const fs = require('fs');
const path = require('path');

const SOURCE_SQL = path.join(__dirname, '../../mysql/live.sql');
const BACKUP_DIR  = path.join(__dirname, '../../mysql/backups');

// ---------- SQL 解析 ----------

function quoteId(name) {
  return `\`${String(name).replace(/`/g, '``')}\``;
}

function normalizeType(type) {
  return String(type || '').toLowerCase().replace(/\s+/g, '');
}

function normalizeExtra(extra) {
  return String(extra || '')
    .toLowerCase()
    .replace(/default_generated/g, '')
    .replace(/current_timestamp\(\)/g, 'current_timestamp')
    .replace(/\s+/g, ' ')
    .trim();
}

function normalizeDefault(value) {
  if (value === undefined || value === null) return '';
  let text = String(value).trim();
  if (/^null$/i.test(text)) return '';
  text = text.replace(/^'(.*)'$/s, '$1');
  text = text.replace(/current_timestamp\(\)/ig, 'CURRENT_TIMESTAMP');
  return text.toLowerCase() === 'current_timestamp' ? 'CURRENT_TIMESTAMP' : text;
}

function splitCreateBody(body) {
  const parts = [];
  let current = '';
  let depth = 0;
  let quote = null;

  for (let i = 0; i < body.length; i++) {
    const ch = body[i];
    const prev = body[i - 1];

    if ((ch === '\'' || ch === '"' || ch === '`') && prev !== '\\') {
      quote = quote === ch ? null : (quote || ch);
    }

    if (!quote) {
      if (ch === '(') depth++;
      if (ch === ')') depth--;
      if (ch === ',' && depth === 0) {
        parts.push(current.trim());
        current = '';
        continue;
      }
    }

    current += ch;
  }

  if (current.trim()) parts.push(current.trim());
  return parts;
}

function parseColumnDefinition(definition, position) {
  const colMatch = definition.match(/^`([^`]+)`\s+(.+)$/s);
  if (!colMatch) return null;

  const name = colMatch[1];
  const rest = colMatch[2].trim();
  const typeMatch = rest.match(/^([a-zA-Z]+(?:\s*\([^)]+\))?)/);
  const defaultMatch = rest.match(/\bDEFAULT\s+('(?:\\'|[^'])*'|NULL|CURRENT_TIMESTAMP(?:\(\))?|[-+]?\d+(?:\.\d+)?|[^\s]+)/i);
  const commentMatch = rest.match(/\bCOMMENT\s+'((?:\\'|[^'])*)'/i);

  return {
    name,
    type: typeMatch ? typeMatch[1] : rest.split(/\s+/)[0],
    nullable: !/\bNOT\s+NULL\b/i.test(rest),
    defaultValue: defaultMatch ? defaultMatch[1] : null,
    extra: normalizeExtra([
      /\bAUTO_INCREMENT\b/i.test(rest) ? 'auto_increment' : '',
      /\bON\s+UPDATE\s+CURRENT_TIMESTAMP(?:\(\))?/i.test(rest) ? 'on update CURRENT_TIMESTAMP' : '',
    ].filter(Boolean).join(' ')),
    comment: commentMatch ? commentMatch[1] : '',
    definition,
    position,
  };
}

function parseIndexDefinition(definition) {
  const keyMatch = definition.match(/^(UNIQUE\s+)?KEY\s+`([^`]+)`\s*\(/is);
  if (!keyMatch) return null;

  let depth = 1;
  let columnsSql = '';
  const rest = definition.slice(keyMatch[0].length);

  for (let i = 0; i < rest.length; i++) {
    const ch = rest[i];
    if (ch === '(') depth++;
    if (ch === ')') depth--;
    if (depth === 0) break;
    columnsSql += ch;
  }

  const columns = columnsSql
    .split(',')
    .map(c => c.trim().replace(/`/g, '').replace(/\(\d+\)$/, ''));

  return {
    name: keyMatch[2],
    columns,
    unique: !!keyMatch[1],
    definition,
  };
}

/** 解析 live.sql，返回目标表、字段、索引和原始 CREATE TABLE 语句 */
function parseLiveSql() {
  const content = fs.readFileSync(SOURCE_SQL, 'utf-8');
  const tables = {};

  // 匹配 CREATE TABLE 语句
  const tableRegex = /(CREATE TABLE IF NOT EXISTS `([^`]+)`\s*\(([\s\S]*?)\)\s*ENGINE=[^;]+;)/g;
  let match;
  while ((match = tableRegex.exec(content)) !== null) {
    const createSql = match[1];
    const tableName = match[2];
    const body = match[3];

    const columns = [];
    const indexes = [];
    let primaryKey = '';

    for (const item of splitCreateBody(body)) {
      const trimmed = item.trim();
      if (!trimmed || trimmed.startsWith('--')) continue;

      // PRIMARY KEY
      if (trimmed.startsWith('PRIMARY KEY')) {
        const pkMatch = trimmed.match(/PRIMARY KEY \(`([^`]+)`\)/);
        if (pkMatch) primaryKey = pkMatch[1];
        continue;
      }

      // KEY / UNIQUE KEY
      if (trimmed.startsWith('KEY ') || trimmed.startsWith('UNIQUE KEY ')) {
        const index = parseIndexDefinition(trimmed);
        if (index) indexes.push(index);
        continue;
      }

      // 普通列
      const column = parseColumnDefinition(trimmed, columns.length + 1);
      if (column) columns.push(column);
    }

    tables[tableName] = { createSql, columns, primaryKey, indexes };
  }

  return tables;
}

// ---------- 备份 ----------

/**
 * 全量备份当前 live 库所有表 + 数据为 SQL 文件
 */
async function fullBackup(conn, dbName) {
  if (!fs.existsSync(BACKUP_DIR)) {
    fs.mkdirSync(BACKUP_DIR, { recursive: true });
  }

  const now = new Date();
  const timestamp = `${now.getFullYear()}${String(now.getMonth()+1).padStart(2,'0')}${String(now.getDate()).padStart(2,'0')}_${String(now.getHours()).padStart(2,'0')}${String(now.getMinutes()).padStart(2,'0')}${String(now.getSeconds()).padStart(2,'0')}`;
  const backupFile = path.join(BACKUP_DIR, `${dbName}_backup_${timestamp}.sql`);

  console.log(`\n💾 正在全量备份数据库 \`${dbName}\` → ${backupFile} ...`);

  const [tableRows] = await conn.query('SHOW TABLES');
  let sql = `-- 数据库备份: ${dbName}\n-- 备份时间: ${new Date().toISOString()}\n\n`;
  sql += `SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS = 0;\n`;
  sql += `CREATE DATABASE IF NOT EXISTS ${quoteId(dbName)} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\n`;
  sql += `USE ${quoteId(dbName)};\n\n`;

  for (const row of tableRows) {
    const tableName = Object.values(row)[0];
    console.log(`    📋 ${tableName} ...`);

    // 写 DROP + CREATE
    const [createRows] = await conn.query(`SHOW CREATE TABLE ${quoteId(tableName)}`);
    sql += `DROP TABLE IF EXISTS ${quoteId(tableName)};\n`;
    sql += createRows[0]['Create Table'] + ';\n\n';

    // 写数据
    const [dataRows] = await conn.query(`SELECT * FROM ${quoteId(tableName)}`);
    if (dataRows.length > 0) {
      const colNames = Object.keys(dataRows[0]);
      const cols = colNames.map(quoteId).join(', ');
      const values = dataRows.map(row => {
        const vals = colNames.map(c => conn.escape(row[c]));
        return `(${vals.join(', ')})`;
      }).join(',\n');
      sql += `INSERT INTO ${quoteId(tableName)} (${cols}) VALUES\n${values};\n\n`;
    }
  }

  sql += `SET FOREIGN_KEY_CHECKS = 1;\n`;
  fs.writeFileSync(backupFile, sql, 'utf-8');
  const sizeMB = (fs.statSync(backupFile).size / 1024 / 1024).toFixed(2);
  console.log(`  ✅ 备份完成: ${sizeMB} MB`);
  return backupFile;
}

// ---------- Schema 同步 ----------

/** 查询数据库实际表结构 */
async function getDbSchema(conn) {
  const [tableRows] = await conn.query('SHOW TABLES');
  const schema = {};

  for (const row of tableRows) {
    const tableName = Object.values(row)[0];
    const [cols] = await conn.query(`SHOW FULL COLUMNS FROM ${quoteId(tableName)}`);
    schema[tableName] = cols.map(c => ({
      name: c.Field,
      type: c.Type,
      nullable: c.Null === 'YES',
      default: c.Default,
      extra: normalizeExtra(c.Extra),
      comment: c.Comment || '',
      position: cols.indexOf(c) + 1,
    }));

    // 获取索引
    const [idxRows] = await conn.query(`SHOW INDEX FROM ${quoteId(tableName)}`);
    const indexes = [];
    const seen = new Set();
    for (const idx of idxRows) {
      if (idx.Key_name === 'PRIMARY') continue;
      if (!seen.has(idx.Key_name)) {
        seen.add(idx.Key_name);
        const sameIndexRows = idxRows
          .filter(i => i.Key_name === idx.Key_name)
          .sort((a, b) => a.Seq_in_index - b.Seq_in_index);
        indexes.push({
          name: idx.Key_name,
          columns: sameIndexRows.map(i => i.Column_name),
          unique: idx.Non_unique === 0,
        });
      }
    }
    schema[tableName]._indexes = indexes;
  }

  return schema;
}

/**
 * 同步：对比目标（live.sql）和实际（DB），执行 ALTER
 */
async function syncSchema(conn) {
  console.log(`\n🔍 正在分析 ${SOURCE_SQL} ...`);
  const target = parseLiveSql();
  console.log(`   目标表: ${Object.keys(target).length} 张`);

  const actual = await getDbSchema(conn);
  console.log(`   实际表: ${Object.keys(actual).length} 张`);

  let alters = 0;
  let creates = 0;
  let drops = 0;
  let warnings = [];

  await conn.query('SET FOREIGN_KEY_CHECKS = 0');

  // 先把所有已有表转为 ROW_FORMAT=DYNAMIC（MySQL 5.7 COMPACT 行格式下索引键最大 767 字节，
  // DYNAMIC 提升到 3072 字节，避免 varchar(255) + utf8mb4 的索引超限）
  for (const tableName of Object.keys(actual)) {
    if (tableName.startsWith('_')) continue; // 系统表跳过
    try {
      const [rowFormatRows] = await conn.query(`SHOW TABLE STATUS LIKE '${tableName}'`);
      const rowFormat = rowFormatRows[0]?.Row_format || '';
      if (rowFormat.toLowerCase() !== 'dynamic') {
        console.log(`  🔧 ${tableName} ROW_FORMAT=${rowFormat} → DYNAMIC`);
        await conn.query(`ALTER TABLE ${quoteId(tableName)} ROW_FORMAT=DYNAMIC`);
      }
    } catch (e) {
      console.log(`  ⚠ 检查/修改 ${tableName} ROW_FORMAT 失败: ${e.message}`);
    }
  }

  for (const [tableName, targetDef] of Object.entries(target)) {
    const actualCols = actual[tableName];

    // 表不存在 → 从 live.sql 补建表
    if (!actualCols) {
      console.log(`  🧱 ${tableName} 缺失，按 live.sql 创建`);
      await conn.query(targetDef.createSql);
      creates++;
      continue;
    }

    const targetColMap = new Map(targetDef.columns.map(c => [c.name, c]));
    const actualColMap = new Map(actualCols.map(c => [c.name, c]));

    const alters_sql = [];

    // 1. 补列 / 改类型（SQL 有，DB 可能无或类型不同）
    for (const col of targetDef.columns) {
      const actualCol = actualColMap.get(col.name);
      if (!actualCol) {
        // 新增列
        alters_sql.push(`ADD COLUMN ${col.definition}`);

        // 放在正确的 position
        if (col.position > 1) {
          const prevCol = targetDef.columns[col.position - 2]; // 前一个目标列
          if (prevCol) {
            alters_sql[alters_sql.length - 1] += ` AFTER ${quoteId(prevCol.name)}`;
          }
        } else {
          alters_sql[alters_sql.length - 1] += ` FIRST`;
        }
      } else if (
        normalizeType(col.type) !== normalizeType(actualCol.type) ||
        col.nullable !== actualCol.nullable ||
        normalizeDefault(col.defaultValue) !== normalizeDefault(actualCol.default) ||
        normalizeExtra(col.extra) !== normalizeExtra(actualCol.extra)
      ) {
        // 类型不一致
        alters_sql.push(`MODIFY COLUMN ${col.definition}`);
      }
    }

    // 2. 删列（DB 有，SQL 无）
    for (const col of actualCols) {
      if (!targetColMap.has(col.name)) {
        alters_sql.push(`DROP COLUMN ${quoteId(col.name)}`);
        warnings.push(`⚠ ${tableName}.${col.name} → 将删除，数据丢失`);
      }
    }

    // 3. 同步索引
    const actualIndexes = actual[tableName]._indexes || [];
    const actualIdxMap = new Map(actualIndexes.map(i => [i.name, i]));
    const targetIdxMap = new Map((targetDef.indexes || []).map(i => [i.name, i]));

    for (const idx of (targetDef.indexes || [])) {
      const actualIdx = actualIdxMap.get(idx.name);
      const sameColumns = actualIdx && actualIdx.columns.join(',') === idx.columns.join(',');
      const sameUnique = actualIdx && actualIdx.unique === idx.unique;

      if (!actualIdx) {
        alters_sql.push(`ADD ${idx.definition}`);
      } else if (!sameColumns || !sameUnique) {
        alters_sql.push(`DROP INDEX ${quoteId(idx.name)}`);
        alters_sql.push(`ADD ${idx.definition}`);
      }
    }

    for (const idx of actualIndexes) {
      if (!targetIdxMap.has(idx.name)) {
        alters_sql.push(`DROP INDEX ${quoteId(idx.name)}`);
      }
    }

    // 执行
    if (alters_sql.length > 0) {
      console.log(`  🔧 ${tableName} (${alters_sql.length} 处差异)`);
      for (const stmt of alters_sql) {
        try {
          await conn.execute(`ALTER TABLE ${quoteId(tableName)} ${stmt}`);
          console.log(`     ✓ ${stmt.substring(0, 80)}`);
          alters++;
        } catch (err) {
          const ignored = ['ER_DUP_FIELDNAME', 'ER_DUP_KEYNAME', 'ER_CANT_DROP_FIELD_OR_KEY', 'ER_BAD_FIELD_ERROR'];
          if (ignored.includes(err.code)) {
            console.log(`     ⚠ 跳过 (${err.code}): ${stmt.substring(0, 60)}`);
          } else {
            throw err;
          }
        }
      }
    }
  }

  // 删表（DB 有，SQL 无）
  for (const tableName of Object.keys(actual)) {
    if (tableName.startsWith('_')) continue; // 系统表保护
    if (!target[tableName]) {
      console.log(`  🗑️ ${tableName} 在 live.sql 中不存在，删除旧表`);
      await conn.query(`DROP TABLE IF EXISTS ${quoteId(tableName)}`);
      drops++;
      warnings.push(`⚠ 表 ${tableName} 已删除；原数据只保留在本次备份中`);
    }
  }

  await conn.query('SET FOREIGN_KEY_CHECKS = 1');

  console.log(`\n  ✅ 同步完成: ${creates} 张表创建，${alters} 个 ALTER 执行，${drops} 张旧表删除`);
  if (warnings.length > 0) {
    console.log(`  ⚠ ${warnings.length} 条警告:`);
    warnings.forEach(w => console.log(`     ${w}`));
  }

  return { creates, alters, drops, warnings };
}

module.exports = { fullBackup, syncSchema, parseLiveSql };
