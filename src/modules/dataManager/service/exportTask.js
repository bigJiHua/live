const fs = require('fs');
const path = require('path');
const mysqldump = require('mysqldump');
const db = require('../../../common/config/db');
const dayjs = require('dayjs');

const TASK_STATUS = {
  PENDING: 'pending',
  RUNNING: 'running',
  COMPLETED: 'completed',
  FAILED: 'failed',
  CANCELLED: 'cancelled'
};

const LARGE_TABLE_THRESHOLD = 5000;

class ExportTaskManager {
  static tasks = new Map();

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
    return `export_${dayjs().format('YYYYMMDDHHmmss')}_${Math.random().toString(36).substring(2, 9)}`;
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

  static async createExportTask(tableName, options = {}) {
    const taskId = this.generateTaskId();
    const tableSize = await this.analyzeTableSize(tableName);
    const isLargeTable = tableSize > LARGE_TABLE_THRESHOLD;

    const task = {
      id: taskId,
      type: 'export_table',
      tableName,
      tableSize,
      isLargeTable,
      status: TASK_STATUS.PENDING,
      progress: 0,
      options,
      createdAt: new Date(),
      startedAt: null,
      completedAt: null,
      error: null,
      result: null,
      _abortController: null,
    };

    this.tasks.set(taskId, task);

    if (isLargeTable) {
      setImmediate(() => this.executeLargeExportTask(taskId));
    }

    return task;
  }

  static async executeLargeExportTask(taskId) {
    const task = this.tasks.get(taskId);
    if (!task) return;

    task.status = TASK_STATUS.RUNNING;
    task.startedAt = new Date();
    task.progress = 5;
    this.tasks.set(taskId, task);

    const includeData = task.options.includeData !== false;
    const filename = `${dayjs().format('YYYYMMDD-HHmmss')}-${task.tableName}.sql`;
    const backupDir = path.join(process.cwd(), 'data', 'sql', 'table');
    if (!fs.existsSync(backupDir)) {
      fs.mkdirSync(backupDir, { recursive: true });
    }
    const filepath = path.join(backupDir, filename);

    try {
      const opts = this.buildDumpOptions(task.tableName, includeData, filepath);

      task.progress = 15;
      this.tasks.set(taskId, task);

      await mysqldump(opts);

      if (task.status === TASK_STATUS.CANCELLED) {
        this.cleanupFile(filepath);
        return;
      }

      const stats = fs.statSync(filepath);

      task.status = TASK_STATUS.COMPLETED;
      task.progress = 100;
      task.completedAt = new Date();
      task.result = {
        filename,
        filepath,
        size: stats.size,
        rowCount: task.tableSize
      };

    } catch (error) {
      console.error(`[ExportTaskManager] Task ${taskId} failed:`, error);
      this.cleanupFile(filepath);
      task.status = TASK_STATUS.FAILED;
      task.error = error.message;
      task.completedAt = new Date();
    }

    this.tasks.set(taskId, task);
  }

  static cleanupFile(filepath) {
    try {
      if (fs.existsSync(filepath)) {
        fs.unlinkSync(filepath);
      }
    } catch (e) {
      console.error(`[ExportTaskManager] Cleanup failed for ${filepath}:`, e);
    }
  }

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
      const age = now - task.createdAt.getTime();
      if (age > maxAgeMs && task.status !== TASK_STATUS.RUNNING) {
        this.tasks.delete(taskId);
      }
    }
  }

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

    const isLargeDatabase = totalSize > LARGE_TABLE_THRESHOLD;

    const task = {
      id: taskId,
      type: 'export_full_database',
      tableSize: totalSize,
      isLargeTable: isLargeDatabase,
      status: TASK_STATUS.PENDING,
      progress: 0,
      options,
      tableSizes,
      createdAt: new Date(),
      startedAt: null,
      completedAt: null,
      error: null,
      result: null,
      _abortController: null,
    };

    this.tasks.set(taskId, task);

    if (isLargeDatabase) {
      setImmediate(() => this.executeFullExportTask(taskId));
    }

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
    const filename = `${timestamp}-full-backup.sql`;
    const backupDir = path.join(process.cwd(), 'data', 'sql', 'table');
    if (!fs.existsSync(backupDir)) {
      fs.mkdirSync(backupDir, { recursive: true });
    }
    const filepath = path.join(backupDir, filename);

    try {
      const opts = this.buildDumpOptions(null, includeData, filepath);

      task.progress = 10;
      this.tasks.set(taskId, task);

      await mysqldump(opts);

      if (task.status === TASK_STATUS.CANCELLED) {
        this.cleanupFile(filepath);
        return;
      }

      const stats = fs.statSync(filepath);

      task.status = TASK_STATUS.COMPLETED;
      task.progress = 100;
      task.completedAt = new Date();
      task.result = {
        filename,
        filepath,
        size: stats.size,
        rowCount: task.tableSize
      };

    } catch (error) {
      console.error(`[ExportTaskManager] Full export task ${taskId} failed:`, error);
      this.cleanupFile(filepath);
      task.status = TASK_STATUS.FAILED;
      task.error = error.message;
      task.completedAt = new Date();
    }

    this.tasks.set(taskId, task);
  }
}

module.exports = ExportTaskManager;