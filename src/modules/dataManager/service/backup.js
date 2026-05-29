const fs = require('fs');
const path = require('path');
const mysqldump = require('mysqldump');
const archiver = require('archiver');
const db = require('../../../common/config/db');
const dayjs = require('dayjs');

class BackupService {
  static getDbConfig() {
    return {
      host: process.env.DB_HOST || 'localhost',
      port: parseInt(process.env.DB_PORT || '3306', 10),
      user: process.env.DB_USER || 'root',
      password: process.env.DB_PASSWORD || '',
      database: process.env.DB_NAME || 'live',
    };
  }

  static getBackupDir() {
    const backupDir = path.join(process.cwd(), 'data', 'sql', 'table');
    if (!fs.existsSync(backupDir)) {
      fs.mkdirSync(backupDir, { recursive: true });
    }
    return backupDir;
  }

  static buildDumpOptions(tableName, includeData, dumpToFile) {
    const config = this.getDbConfig();
    const opts = {
      connection: {
        host: config.host,
        port: config.port,
        user: config.user,
        password: config.password,
        database: config.database,
      },
      dump: {
        schema: {
          table: {
            dropIfExists: true,
          },
        },
        data: includeData !== false ? {} : false,
        trigger: false,
      },
    };

    if (tableName) {
      opts.dump.tables = [tableName];
    }

    if (dumpToFile) {
      opts.dumpToFile = dumpToFile;
    }

    return opts;
  }

  static async backupSingleTable(tableName, includeData = true) {
    const timestamp = dayjs().format('YYYYMMDD-HHmmss');
    const filename = `${timestamp}-${tableName}.sql`;
    const filepath = path.join(this.getBackupDir(), filename);

    const opts = this.buildDumpOptions(tableName, includeData, filepath);

    try {
      await mysqldump(opts);
      const stats = fs.statSync(filepath);
      return { filename, filepath, size: stats.size };
    } catch (error) {
      if (fs.existsSync(filepath)) {
        fs.unlinkSync(filepath);
      }
      throw error;
    }
  }

  static async backupAllTables(includeData = true) {
    const timestamp = dayjs().format('YYYYMMDD-HHmmss');
    const filename = `${timestamp}-full-backup.sql`;
    const filepath = path.join(this.getBackupDir(), filename);

    const opts = this.buildDumpOptions(null, includeData, filepath);

    try {
      await mysqldump(opts);
      const content = fs.readFileSync(filepath, 'utf8');
      return content;
    } catch (error) {
      if (fs.existsSync(filepath)) {
        fs.unlinkSync(filepath);
      }
      throw error;
    }
  }

  static async backupAllTablesToZip(includeData = true) {
    const timestamp = dayjs().format('YYYYMMDD-HHmmss');
    const sqlFilename = `${timestamp}-full-backup.sql`;
    const sqlFilepath = path.join(this.getBackupDir(), sqlFilename);
    const zipFilename = `${timestamp}-full-backup.sql.zip`;
    const zipFilepath = path.join(this.getBackupDir(), zipFilename);

    const opts = this.buildDumpOptions(null, includeData, sqlFilepath);

    try {
      await mysqldump(opts);
    } catch (error) {
      if (fs.existsSync(sqlFilepath)) {
        fs.unlinkSync(sqlFilepath);
      }
      throw error;
    }

    return new Promise((resolve, reject) => {
      const output = fs.createWriteStream(zipFilepath);
      const archive = new archiver.ZipArchive({ zlib: { level: 9 } });

      output.on('close', () => {
        if (fs.existsSync(sqlFilepath)) {
          fs.unlinkSync(sqlFilepath);
        }
        resolve({
          sqlFilename,
          zipFilename,
          zipFilepath,
          sqlSize: 0,
          zipSize: archive.pointer(),
        });
      });

      archive.on('error', (err) => {
        reject(err);
      });

      archive.pipe(output);
      archive.file(sqlFilepath, { name: sqlFilename });
      archive.finalize();
    });
  }

  static async listBackups() {
    const backupDir = this.getBackupDir();
    const files = fs.readdirSync(backupDir)
      .filter(f => f.endsWith('.sql') || f.endsWith('.zip'))
      .map(f => {
        const filepath = path.join(backupDir, f);
        const stats = fs.statSync(filepath);
        return {
          filename: f,
          filepath: filepath,
          size: stats.size,
          createdAt: stats.birthtime,
          type: f.endsWith('.zip') ? 'zip' : 'sql',
        };
      })
      .sort((a, b) => b.createdAt - a.createdAt);

    return files;
  }

  static async restoreTableFromSQL(tableName, sqlContent) {
    const statements = sqlContent
      .split(/;\s*\n/)
      .map(s => s.trim())
      .filter(s => s.length > 0 && !s.startsWith('--'));

    const connection = await db.getPool().getConnection();
    try {
      await connection.beginTransaction();

      await connection.execute(`DROP TABLE IF EXISTS \`${tableName}\``);

      for (const statement of statements) {
        if (statement.toUpperCase().includes('CREATE TABLE') || statement.toUpperCase().includes('INSERT')) {
          await connection.query(statement);
        }
      }

      await connection.commit();
      return { success: true };
    } catch (error) {
      await connection.rollback();
      throw error;
    } finally {
      connection.release();
    }
  }

  static async deleteBackup(filename) {
    const filepath = path.join(this.getBackupDir(), filename);
    const resolvedPath = path.resolve(filepath);
    const resolvedBase = path.resolve(this.getBackupDir());

    if (!resolvedPath.startsWith(resolvedBase)) {
      throw new Error('文件名不合法');
    }

    if (!fs.existsSync(filepath)) {
      throw new Error('备份文件不存在');
    }

    fs.unlinkSync(filepath);
    return { success: true };
  }

  static getSystemBackupDir() {
    return path.join(process.cwd(), 'data', 'sql', 'backup');
  }

  static getDateBackupDir(dateStr) {
    const dir = path.join(this.getSystemBackupDir(), dateStr);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    return dir;
  }

  static async createSystemBackup(includeData = true) {
    const dateStr = dayjs().format('YYYY-MM-DD');
    const timestamp = dayjs().format('YYYYMMDD-HHmmss');
    const suffix = includeData ? 'full' : 'schema-only';
    const filename = `${timestamp}-system-backup-${suffix}.sql`;
    const backupDir = this.getDateBackupDir(dateStr);
    const filepath = path.join(backupDir, filename);

    const opts = this.buildDumpOptions(null, includeData, filepath);

    try {
      await mysqldump(opts);
      const stats = fs.statSync(filepath);
      return {
        filename,
        filepath,
        size: stats.size,
        date: dateStr,
        type: suffix,
        createdAt: new Date().toISOString(),
      };
    } catch (error) {
      if (fs.existsSync(filepath)) {
        fs.unlinkSync(filepath);
      }
      throw error;
    }
  }

  static async createFullSystemBackup() {
    const dateStr = dayjs().format('YYYY-MM-DD');
    const todayDir = this.getDateBackupDir(dateStr);
    const existingFiles = fs.readdirSync(todayDir).filter(f => f.endsWith('.sql'));
    const backupCount = existingFiles.length / 2;
    if (backupCount >= 3) {
      throw new Error('备份已达上限！');
    }
    const fullResult = await this.createSystemBackup(true);
    const schemaResult = await this.createSystemBackup(false);
    return {
      full: fullResult,
      schemaOnly: schemaResult,
      date: fullResult.date,
    };
  }

  static async listSystemBackups() {
    const baseDir = this.getSystemBackupDir();
    if (!fs.existsSync(baseDir)) {
      return [];
    }

    const dateDirs = fs.readdirSync(baseDir)
      .filter(name => {
        const fullPath = path.join(baseDir, name);
        return fs.statSync(fullPath).isDirectory() && /^\d{4}-\d{2}-\d{2}$/.test(name);
      })
      .sort()
      .reverse();

    const result = [];

    for (const dateStr of dateDirs) {
      const dirPath = path.join(baseDir, dateStr);
      const files = fs.readdirSync(dirPath)
        .filter(f => f.endsWith('.sql'))
        .map(f => {
          const filepath = path.join(dirPath, f);
          const stats = fs.statSync(filepath);
          const isSchemaOnly = f.includes('schema-only');
          return {
            filename: f,
            filepath,
            size: stats.size,
            createdAt: stats.birthtime || stats.mtime,
            type: isSchemaOnly ? 'schema-only' : 'full',
          };
        })
        .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

      if (files.length > 0) {
        result.push({
          date: dateStr,
          files,
        });
      }
    }

    return result;
  }
}

module.exports = BackupService;