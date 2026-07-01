const fs = require('fs');
const path = require('path');
const mysqldump = require('mysqldump');
const archiver = require('archiver');
const db = require('../../../common/config/db');
const dayjs = require('dayjs');

const TASK_STATUS = {
  PENDING: 'pending',
  RUNNING: 'running',
  COMPLETED: 'completed',
  FAILED: 'failed',
  CANCELLED: 'cancelled'
};

/**
 * 备份/导出异步任务管理器
 * - 所有备份导出操作均为异步，立即返回 taskId
 * - 同一类型任务同时只允许一个执行（并发保护）
 */
class ExportTaskManager {
  static tasks = new Map();

  // ==================== 基础工具 ====================

  static getDbConfig() {
    return {
      host: process.env.DB_HOST || 'localhost',
      port: parseInt(process.env.DB_PORT || '3306', 10),
      user: process.env.DB_USER || 'root',
      password: process.env.DB_PASSWORD || '',
      database: process.env.DB_NAME || 'live',
    };
  }

  static generateTaskId() {
    return `task_${dayjs().format('YYYYMMDDHHmmss')}_${Math.random().toString(36).substring(2, 9)}`;
  }

  static async analyzeTableSize(tableName) {
    const [rows] = await db.execute(`SELECT COUNT(*) as count FROM \`${tableName}\``);
    return rows[0]?.count || 0;
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
        schema: { table: { dropIfExists: true } },
        data: includeData !== false ? {} : false,
        trigger: false,
      },
    };
    if (tableName) opts.dump.tables = [tableName];
    if (dumpToFile) opts.dumpToFile = dumpToFile;
    return opts;
  }

  static getBackupDir() {
    const dir = path.join(process.cwd(), 'data', 'sql', 'table');
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    return dir;
  }

  static getSystemBackupDir() {
    const dir = path.join(process.cwd(), 'data', 'sql', 'backup');
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    return dir;
  }

  static getDateBackupDir(dateStr) {
    const dir = path.join(this.getSystemBackupDir(), dateStr);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    return dir;
  }

  static cleanupFile(filepath) {
    try { if (fs.existsSync(filepath)) fs.unlinkSync(filepath); }
    catch (e) { console.error(`[TaskManager] Cleanup failed for ${filepath}:`, e); }
  }

  // ==================== 并发保护 ====================

  /**
   * 检查是否有指定类型的任务正在运行或等待中
   * @param {'export_table'|'export_full_database'|'system_backup'} type
   */
  static hasRunningTask(type) {
    for (const task of this.tasks.values()) {
      if (task.type === type && (task.status === TASK_STATUS.PENDING || task.status === TASK_STATUS.RUNNING)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 获取指定类型正在执行中的任务（用于前端轮询复用）
   */
  static getRunningTask(type) {
    for (const task of this.tasks.values()) {
      if (task.type === type && (task.status === TASK_STATUS.PENDING || task.status === TASK_STATUS.RUNNING)) {
        return task;
      }
    }
    return null;
  }

  // ==================== 单表导出 ====================

  static async createExportTask(tableName, options = {}) {
    const taskId = this.generateTaskId();
    const tableSize = await this.analyzeTableSize(tableName);

    const task = {
      id: taskId,
      type: 'export_table',
      tableName,
      tableSize,
      status: TASK_STATUS.PENDING,
      progress: 0,
      options,
      createdAt: new Date(),
      startedAt: null,
      completedAt: null,
      error: null,
      result: null,
    };

    this.tasks.set(taskId, task);
    // 始终异步执行
    setImmediate(() => this.executeExportTableTask(taskId));
    return task;
  }

  static async executeExportTableTask(taskId) {
    const task = this.tasks.get(taskId);
    if (!task) return;

    task.status = TASK_STATUS.RUNNING;
    task.startedAt = new Date();
    task.progress = 5;
    this.tasks.set(taskId, task);

    const includeData = task.options.includeData !== false;
    const sqlFilename = `${dayjs().format('YYYYMMDD-HHmmss')}-${task.tableName}.sql`;
    const sqlFilepath = path.join(this.getBackupDir(), sqlFilename);
    const zipFilename = `${dayjs().format('YYYYMMDD-HHmmss')}-${task.tableName}.sql.zip`;
    const zipFilepath = path.join(this.getBackupDir(), zipFilename);

    try {
      task.progress = 15; this.tasks.set(taskId, task);
      const opts = this.buildDumpOptions(task.tableName, includeData, sqlFilepath);
      await mysqldump(opts);

      if (task.status === TASK_STATUS.CANCELLED) { this.cleanupFile(sqlFilepath); return; }

      task.progress = 70; this.tasks.set(taskId, task);

      await new Promise((resolve, reject) => {
        const output = fs.createWriteStream(zipFilepath);
        const archive = new archiver.ZipArchive({ zlib: { level: 6 } });
        output.on('close', resolve);
        archive.on('error', reject);
        archive.pipe(output);
        archive.file(sqlFilepath, { name: sqlFilename });
        archive.finalize();
      });

      if (fs.existsSync(sqlFilepath)) fs.unlinkSync(sqlFilepath);

      const stats = fs.statSync(zipFilepath);
      task.status = TASK_STATUS.COMPLETED;
      task.progress = 100;
      task.completedAt = new Date();
      task.result = { filename: zipFilename, filepath: zipFilepath, size: stats.size, rowCount: task.tableSize };
    } catch (error) {
      console.error(`[TaskManager] Export table task ${taskId} failed:`, error);
      this.cleanupFile(sqlFilepath);
      this.cleanupFile(zipFilepath);
      task.status = TASK_STATUS.FAILED;
      task.error = error.message;
      task.completedAt = new Date();
    }
    this.tasks.set(taskId, task);
  }

  // ==================== 全库导出 ====================

  static async createFullExportTask(options = {}) {
    const taskId = this.generateTaskId();
    const [tables] = await db.execute('SHOW TABLES');
    const tableNames = tables.map(t => Object.values(t)[0]);

    const tableSizes = [];
    let totalSize = 0;
    for (const tableName of tableNames) {
      const count = await this.analyzeTableSize(tableName);
      tableSizes.push({ name: tableName, rows: count });
      totalSize += count;
    }

    const task = {
      id: taskId,
      type: 'export_full_database',
      tableSize: totalSize,
      status: TASK_STATUS.PENDING,
      progress: 0,
      options,
      tableSizes,
      createdAt: new Date(),
      startedAt: null,
      completedAt: null,
      error: null,
      result: null,
    };

    this.tasks.set(taskId, task);
    // 始终异步执行
    setImmediate(() => this.executeFullExportTask(taskId));
    return task;
  }

  static async executeFullExportTask(taskId) {
    const task = this.tasks.get(taskId);
    if (!task) return;

    task.status = TASK_STATUS.RUNNING;
    task.startedAt = new Date();
    task.progress = 5;
    this.tasks.set(taskId, task);

    const includeData = task.options.includeData !== false;
    const timestamp = dayjs().format('YYYYMMDD-HHmmss');

    await this.executeFullExportToZip(taskId, task, includeData, timestamp);
  }

  static async executeFullExportToSql(taskId, task, includeData, timestamp) {
    const filename = `${timestamp}-full-backup.sql`;
    const filepath = path.join(this.getBackupDir(), filename);

    try {
      task.progress = 10; this.tasks.set(taskId, task);
      const opts = this.buildDumpOptions(null, includeData, filepath);
      await mysqldump(opts);

      if (task.status === TASK_STATUS.CANCELLED) { this.cleanupFile(filepath); return; }

      const stats = fs.statSync(filepath);
      task.status = TASK_STATUS.COMPLETED;
      task.progress = 100;
      task.completedAt = new Date();
      task.result = { filename, filepath, size: stats.size, rowCount: task.tableSize };
    } catch (error) {
      console.error(`[TaskManager] Full export task ${taskId} failed:`, error);
      this.cleanupFile(filepath);
      task.status = TASK_STATUS.FAILED;
      task.error = error.message;
      task.completedAt = new Date();
    }
    this.tasks.set(taskId, task);
  }

  static async executeFullExportToZip(taskId, task, includeData, timestamp) {
    const sqlFilename = `${timestamp}-full-backup.sql`;
    const sqlFilepath = path.join(this.getBackupDir(), sqlFilename);
    const zipFilename = `${timestamp}-full-backup.sql.zip`;
    const zipFilepath = path.join(this.getBackupDir(), zipFilename);

    try {
      task.progress = 10; this.tasks.set(taskId, task);
      const opts = this.buildDumpOptions(null, includeData, sqlFilepath);
      await mysqldump(opts);

      if (task.status === TASK_STATUS.CANCELLED) { this.cleanupFile(sqlFilepath); return; }

      task.progress = 70; this.tasks.set(taskId, task);

      await new Promise((resolve, reject) => {
        const output = fs.createWriteStream(zipFilepath);
        const archive = new archiver.ZipArchive({ zlib: { level: 6 } });
        output.on('close', resolve);
        archive.on('error', reject);
        archive.pipe(output);
        archive.file(sqlFilepath, { name: sqlFilename });
        archive.finalize();
      });

      if (fs.existsSync(sqlFilepath)) fs.unlinkSync(sqlFilepath);

      const stats = fs.statSync(zipFilepath);
      task.status = TASK_STATUS.COMPLETED;
      task.progress = 100;
      task.completedAt = new Date();
      task.result = { filename: zipFilename, filepath: zipFilepath, size: stats.size, rowCount: task.tableSize };
    } catch (error) {
      console.error(`[TaskManager] Full export ZIP task ${taskId} failed:`, error);
      this.cleanupFile(sqlFilepath);
      this.cleanupFile(zipFilepath);
      task.status = TASK_STATUS.FAILED;
      task.error = error.message;
      task.completedAt = new Date();
    }
    this.tasks.set(taskId, task);
  }

  // ==================== 系统备份 ====================

  static async createSystemBackupTask(options = {}) {
    const taskId = this.generateTaskId();

    const task = {
      id: taskId,
      type: 'system_backup',
      status: TASK_STATUS.PENDING,
      progress: 0,
      options,
      createdAt: new Date(),
      startedAt: null,
      completedAt: null,
      error: null,
      result: null,
    };

    this.tasks.set(taskId, task);
    setImmediate(() => this.executeSystemBackupTask(taskId));
    return task;
  }

  static async executeSystemBackupTask(taskId) {
    const task = this.tasks.get(taskId);
    if (!task) return;

    task.status = TASK_STATUS.RUNNING;
    task.startedAt = new Date();
    task.progress = 5;
    this.tasks.set(taskId, task);

    const dateStr = dayjs().format('YYYY-MM-DD');
    const backupDir = this.getDateBackupDir(dateStr);

    // 检查当日备份上限
    const existingFiles = fs.readdirSync(backupDir).filter(f => f.endsWith('.sql'));
    const backupCount = existingFiles.length / 2;
    if (backupCount >= 3) {
      task.status = TASK_STATUS.FAILED;
      task.error = '今日备份已达上限（3次）';
      task.completedAt = new Date();
      this.tasks.set(taskId, task);
      return;
    }

    const includeData = task.options.includeData !== false;
    const timestamp = dayjs().format('YYYYMMDD-HHmmss');
    let fullResult = null;
    let schemaResult = null;

    try {
      // 完整备份
      task.progress = 10; this.tasks.set(taskId, task);
      const fullFilename = `${timestamp}-system-backup-full.sql`;
      const fullFilepath = path.join(backupDir, fullFilename);
      const fullOpts = this.buildDumpOptions(null, true, fullFilepath);
      await mysqldump(fullOpts);

      if (task.status === TASK_STATUS.CANCELLED) {
        this.cleanupFile(fullFilepath);
        return;
      }

      const fullStats = fs.statSync(fullFilepath);
      fullResult = { filename: fullFilename, filepath: fullFilepath, size: fullStats.size, date: dateStr, type: 'full' };

      // 仅结构备份
      task.progress = 55; this.tasks.set(taskId, task);
      const schemaFilename = `${timestamp}-system-backup-schema-only.sql`;
      const schemaFilepath = path.join(backupDir, schemaFilename);
      const schemaOpts = this.buildDumpOptions(null, false, schemaFilepath);
      await mysqldump(schemaOpts);

      if (task.status === TASK_STATUS.CANCELLED) {
        this.cleanupFile(schemaFilepath);
        return;
      }

      const schemaStats = fs.statSync(schemaFilepath);
      schemaResult = { filename: schemaFilename, filepath: schemaFilepath, size: schemaStats.size, date: dateStr, type: 'schema-only' };

      task.status = TASK_STATUS.COMPLETED;
      task.progress = 100;
      task.completedAt = new Date();
      task.result = {
        full: fullResult,
        schemaOnly: schemaResult,
        date: dateStr,
      };
    } catch (error) {
      console.error(`[TaskManager] System backup task ${taskId} failed:`, error);
      if (fullResult) this.cleanupFile(fullResult.filepath);
      if (schemaResult) this.cleanupFile(schemaResult.filepath);
      task.status = TASK_STATUS.FAILED;
      task.error = error.message;
      task.completedAt = new Date();
    }

    this.tasks.set(taskId, task);
  }

  // ==================== 任务查询与控制 ====================

  static getTaskStatus(taskId) {
    return this.tasks.get(taskId) || null;
  }

  static async cancelTask(taskId) {
    const task = this.tasks.get(taskId);
    if (!task) return false;
    if (task.status === TASK_STATUS.PENDING || task.status === TASK_STATUS.RUNNING) {
      task.status = TASK_STATUS.CANCELLED;
      this.tasks.set(taskId, task);
      return true;
    }
    return false;
  }

  static getAllTasks() {
    return Array.from(this.tasks.values()).sort((a, b) => b.createdAt - a.createdAt);
  }

  static cleanupOldTasks(maxAgeMs = 24 * 60 * 60 * 1000) {
    const now = Date.now();
    for (const [taskId, task] of this.tasks.entries()) {
      if (now - task.createdAt.getTime() > maxAgeMs && task.status !== TASK_STATUS.RUNNING) {
        this.tasks.delete(taskId);
      }
    }
  }
}

module.exports = ExportTaskManager;
