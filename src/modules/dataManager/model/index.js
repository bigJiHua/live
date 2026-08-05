const db = require('../../../common/config/db');

// 表名白名单校验，防止 SQL 注入
function safeTableName(tableName) {
  if (!/^[A-Za-z0-9_]+$/.test(tableName || '')) {
    const err = new Error('非法表名');
    err.status = 400;
    throw err;
  }
  return tableName;
}

class DataManagerModel {
  static async getTableList() {
    const [rows] = await db.execute('SHOW TABLES');
    return rows;
  }

  static async getTableStatus() {
    const [rows] = await db.execute('SHOW TABLE STATUS');
    return rows.map(table => ({
      name: table.Name,
      engine: table.Engine,
      rowCount: table.Rows,
      dataLength: table.Data_length,
      indexLength: table.Index_length,
      createTime: table.Create_time,
      updateTime: table.Update_time,
      collation: table.Collation,
      comment: table.Comment
    }));
  }

  static async getTableStructure(tableName) {
    const safe = safeTableName(tableName);
    const [rows] = await db.execute(`DESCRIBE \`${safe}\``);
    return rows.map(col => ({
      field: col.Field,
      type: col.Type,
      null: col.Null,
      key: col.Key,
      default: col.Default,
      extra: col.Extra
    }));
  }

  static async getTableData(tableName, options = {}) {
    const safe = safeTableName(tableName);
    const { page = 1, pageSize = 100, where = '', whereParams = [] } = options;
    const offset = (page - 1) * pageSize;

    let query = `SELECT * FROM \`${safe}\``;
    let countQuery = `SELECT COUNT(*) as total FROM \`${safe}\``;
    const allParams = [...whereParams];

    if (where) {
      query += ` WHERE ${where}`;
      countQuery += ` WHERE ${where}`;
    }

    query += ` LIMIT ? OFFSET ?`;
    allParams.push(parseInt(pageSize), parseInt(offset));

    const [rows] = await db.execute(query, allParams);
    const [countResult] = await db.execute(countQuery, whereParams);

    return {
      data: rows,
      total: countResult[0]?.total || 0,
      page: parseInt(page),
      pageSize: parseInt(pageSize)
    };
  }

  static async getFullTableData(tableName) {
    const safe = safeTableName(tableName);
    const [rows] = await db.execute(`SELECT * FROM \`${safe}\``);
    return rows;
  }

  static async getCreateTableSQL(tableName) {
    const safe = safeTableName(tableName);
    const [rows] = await db.execute(`SHOW CREATE TABLE \`${safe}\``);
    return rows[0]?.['Create Table'] || rows[0]?.['Create View'];
  }

  static async truncateTable(tableName) {
    const safe = safeTableName(tableName);
    await db.execute(`TRUNCATE TABLE \`${safe}\``);
  }

  static async dropTable(tableName) {
    const safe = safeTableName(tableName);
    await db.execute(`DROP TABLE IF EXISTS \`${safe}\``);
  }

  static async insertBatch(tableName, data) {
    if (!data || data.length === 0) return { affectedRows: 0 };
    const safe = safeTableName(tableName);

    const columns = Object.keys(data[0]);
    const placeholders = columns.map(() => '?').join(', ');
    const values = data.map(row => columns.map(col => row[col] === undefined ? null : row[col]));
    const sql = `INSERT INTO \`${safe}\` (\`${columns.join('`,`')}\`) VALUES (${placeholders})`;

    const connection = db.getPool ? (await import('../../../common/config/db.js')).default.getPool().getConnection() : null;
    try {
      if (connection) {
        await connection.beginTransaction();
        for (const row of values) {
          await connection.execute(sql, row);
        }
        await connection.commit();
        return { affectedRows: values.length };
      } else {
        for (const row of values) {
          await db.execute(sql, row);
        }
        return { affectedRows: values.length };
      }
    } catch (error) {
      if (connection) await connection.rollback();
      throw error;
    } finally {
      if (connection) connection.release();
    }
  }

  static async getColumnNames(tableName) {
    const safe = safeTableName(tableName);
    const [rows] = await db.execute(`SHOW COLUMNS FROM \`${safe}\``);
    return rows.map(row => row.Field);
  }
}

module.exports = DataManagerModel;