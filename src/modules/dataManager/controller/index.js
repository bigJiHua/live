const fs = require('fs');
const path = require('path');
const os = require('os');
const AdmZip = require('adm-zip');
const db = require('../../../common/config/db');
const DataManagerModel = require('../model');
const BackupService = require('../service/backup');
const ExportTaskManager = require('../service/exportTask');
const dayjs = require('dayjs');

class DataManagerController {
  // ==================== 表信息查询 ====================

  static async getTableList(req, res) {
    try {
      const tables = await DataManagerModel.getTableList();
      const tableNames = tables.map(t => Object.values(t)[0]);
      res.json({ status: 200, data: tableNames });
    } catch (error) {
      console.error('[DataManager] getTableList error:', error);
      res.json({ status: 500, message: '获取表列表失败' });
    }
  }

  static async getTableStatus(req, res) {
    try {
      const status = await DataManagerModel.getTableStatus();
      res.json({ status: 200, data: status });
    } catch (error) {
      console.error('[DataManager] getTableStatus error:', error);
      res.json({ status: 500, message: '获取表状态失败' });
    }
  }

  static async getTableStructure(req, res) {
    try {
      const { tableName } = req.params;
      if (!tableName) return res.json({ status: 400, message: '表名不能为空' });
      const structure = await DataManagerModel.getTableStructure(tableName);
      res.json({ status: 200, data: structure });
    } catch (error) {
      console.error('[DataManager] getTableStructure error:', error);
      res.json({ status: 500, message: '获取表结构失败' });
    }
  }

  static async getTableData(req, res) {
    try {
      const { tableName } = req.params;
      const { page = 1, pageSize = 100 } = req.query;
      if (!tableName) return res.json({ status: 400, message: '表名不能为空' });
      const result = await DataManagerModel.getTableData(tableName, {
        page: parseInt(page), pageSize: parseInt(pageSize)
      });
      res.json({ status: 200, data: result });
    } catch (error) {
      console.error('[DataManager] getTableData error:', error);
      res.json({ status: 500, message: '获取表数据失败' });
    }
  }

  // ==================== 导出（异步任务模式） ====================

  static async exportSingleTable(req, res) {
    try {
      const { tableName } = req.params;
      const { includeData = 'true' } = req.query;
      if (!tableName) return res.json({ status: 400, message: '表名不能为空' });

      // 并发保护：检查是否已有同类型任务在执行
      const running = ExportTaskManager.getRunningTask('export_table');
      if (running) {
        return res.json({
          status: 202,
          message: '已有表导出任务正在执行中',
          data: { taskId: running.id, status: running.status, reuseExisting: true }
        });
      }

      const task = await ExportTaskManager.createExportTask(tableName, {
        includeData: includeData === 'true'
      });

      res.json({
        status: 202,
        message: '表导出任务已提交，正在后台执行',
        data: { taskId: task.id, status: task.status }
      });
    } catch (error) {
      console.error('[DataManager] exportSingleTable error:', error);
      res.json({ status: 500, message: '导出表失败' });
    }
  }

  static async exportFullDatabase(req, res) {
    try {
      const { format = 'sql', includeData = 'true' } = req.query;

      // 并发保护
      const running = ExportTaskManager.getRunningTask('export_full_database');
      if (running) {
        return res.json({
          status: 202,
          message: '已有全库导出任务正在执行中',
          data: { taskId: running.id, status: running.status, reuseExisting: true }
        });
      }

      const task = await ExportTaskManager.createFullExportTask({
        includeData: includeData === 'true',
        format
      });

      res.json({
        status: 202,
        message: '全库导出任务已提交，正在后台执行',
        data: { taskId: task.id, status: task.status }
      });
    } catch (error) {
      console.error('[DataManager] exportFullDatabase error:', error);
      res.json({ status: 500, message: '备份数据库失败' });
    }
  }

  // ==================== 数据导入 ====================

  static async validateImportData(req, res) {
    try {
      const { tableName, data } = req.body;
      if (!tableName) return res.json({ status: 400, message: '表名不能为空' });
      if (!Array.isArray(data) || data.length === 0) {
        return res.json({ status: 400, message: '数据必须是包含对象的非空数组' });
      }
      const tableColumns = await DataManagerModel.getColumnNames(tableName);
      const validationResult = { tableName, tableColumns, totalRows: data.length, validRows: 0, invalidRows: 0, errors: [] };
      for (let i = 0; i < data.length; i++) {
        const row = data[i], rowErrors = [];
        for (const col of tableColumns) {
          if (!(col in row) && col !== 'id' && col !== 'create_time' && col !== 'update_time') {
            rowErrors.push(`缺少字段: ${col}`);
          }
        }
        const extraColumns = Object.keys(row).filter(k => !tableColumns.includes(k));
        if (extraColumns.length > 0) rowErrors.push(`多余字段将被忽略: ${extraColumns.join(', ')}`);
        if (rowErrors.length === 0) validationResult.validRows++;
        else { validationResult.invalidRows++; validationResult.errors.push({ row: i + 1, errors: rowErrors }); }
      }
      res.json({ status: 200, data: validationResult, canImport: validationResult.invalidRows === 0 });
    } catch (error) {
      console.error('[DataManager] validateImportData error:', error);
      res.json({ status: 500, message: '校验数据失败' });
    }
  }

  static async importData(req, res) {
    const { tableName, data, forceClear = false } = req.body;
    if (!tableName) return res.json({ status: 400, message: '表名不能为空' });
    if (!Array.isArray(data) || data.length === 0) {
      return res.json({ status: 400, message: '数据必须是包含对象的非空数组' });
    }
    let backupFilename = null;
    try {
      const tableColumns = await DataManagerModel.getColumnNames(tableName);
      const { filename } = await BackupService.backupSingleTable(tableName, true);
      backupFilename = filename;
      const filteredData = data.map(row => {
        const filtered = {};
        for (const col of tableColumns) { if (col in row) filtered[col] = row[col]; }
        return filtered;
      });
      if (forceClear) await DataManagerModel.truncateTable(tableName);
      const result = await DataManagerModel.insertBatch(tableName, filteredData);
      res.json({ status: 200, message: '数据导入成功', data: { importedRows: result.affectedRows, totalRows: data.length, backupFile: backupFilename } });
    } catch (error) {
      console.error('[DataManager] importData error:', error);
      res.json({ status: 500, message: '导入数据失败', backupFile: backupFilename });
    }
  }

  static async importSql(req, res) {
    const MAX_FILE_SIZE = 100 * 1024 * 1024;
    try {
      if (!req.file) return res.json({ status: 400, message: '请上传 SQL 或 ZIP 文件' });
      const ext = path.extname(req.file.originalname).toLowerCase();
      if (ext !== '.sql' && ext !== '.zip') return res.json({ status: 400, message: '仅支持 .sql 或 .zip 文件' });
      if (req.file.size > MAX_FILE_SIZE) return res.json({ status: 400, message: '文件超过 100MB 限制' });

      let sqlContent = '';
      if (ext === '.zip') {
        const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'sql-import-'));
        try {
          const zip = new AdmZip(req.file.path);
          const sqlEntry = zip.getEntries().find(e => !e.isDirectory && path.extname(e.entryName).toLowerCase() === '.sql');
          if (!sqlEntry) return res.json({ status: 400, message: 'ZIP 文件中未找到 .sql 文件' });
          sqlContent = sqlEntry.getData().toString('utf8');
        } finally { fs.rmSync(tempDir, { recursive: true, force: true }); }
      } else {
        sqlContent = fs.readFileSync(req.file.path, 'utf8');
      }

      if (!sqlContent || sqlContent.trim().length === 0) return res.json({ status: 400, message: 'SQL 内容为空' });
      if (!/(CREATE|INSERT|ALTER|DROP)\s/i.test(sqlContent)) return res.json({ status: 400, message: '未检测到有效的 SQL 语句' });

      console.log('[DataManager] SQL导入: 开始全库备份...');
      const backup = await BackupService.backupAllTablesToZip(true);
      console.log(`[DataManager] SQL导入: 备份完成 ${backup.zipFilename}`);

      const statements = sqlContent.split(/;\s*\r?\n/).map(s => s.trim()).filter(s => s.length > 0 && !s.startsWith('--') && !s.startsWith('#') && !s.startsWith('/*'));
      let executedCount = 0, errorCount = 0;
      const errors = [];
      for (let i = 0; i < statements.length; i++) {
        const stmt = statements[i];
        if (!stmt) continue;
        try { await db.execute(stmt); executedCount++; }
        catch (stmtErr) { errorCount++; errors.push({ index: i + 1, sql: stmt.substring(0, 100), message: stmtErr.message }); }
      }

      try { fs.unlinkSync(req.file.path); } catch (_) {}
      const success = errorCount === 0;
      res.json({
        status: success ? 200 : 207,
        message: success ? `SQL 导入成功，共执行 ${executedCount} 条语句` : `执行完成，${executedCount} 条成功，${errorCount} 条失败`,
        data: { executedCount, errorCount, errors: errors.slice(0, 20), backupFile: backup.zipFilename, backupTimestamp: dayjs().format('YYYY-MM-DD HH:mm:ss') },
      });
    } catch (error) {
      console.error('[DataManager] importSql error:', error);
      return res.json({ status: 500, message: error.message || 'SQL 导入失败' });
    }
  }

  // ==================== 备份文件管理 ====================

  static async getBackupList(req, res) {
    try {
      const backups = await BackupService.listBackups();
      res.json({ status: 200, data: backups });
    } catch (error) {
      console.error('[DataManager] getBackupList error:', error);
      res.json({ status: 500, message: '获取备份列表失败' });
    }
  }

  static async createSystemBackup(req, res) {
    try {
      // 并发保护
      const running = ExportTaskManager.getRunningTask('system_backup');
      if (running) {
        return res.json({
          status: 202,
          message: '已有系统备份任务正在执行中',
          data: { taskId: running.id, status: running.status, reuseExisting: true }
        });
      }

      const task = await ExportTaskManager.createSystemBackupTask({ includeData: true });

      res.json({
        status: 202,
        message: '系统备份任务已提交，正在后台执行（完整备份 + 仅结构备份）',
        data: { taskId: task.id, status: task.status }
      });
    } catch (error) {
      console.error('[DataManager] createSystemBackup error:', error);
      res.json({ status: 500, message: error.message || '系统备份失败' });
    }
  }

  static async getSystemBackups(req, res) {
    try {
      const list = await BackupService.listSystemBackups();
      res.json({ status: 200, data: list });
    } catch (error) {
      console.error('[DataManager] getSystemBackups error:', error);
      res.json({ status: 500, message: '获取系统备份列表失败' });
    }
  }

  static async deleteBackup(req, res) {
    try {
      const { filename } = req.params;
      if (!filename || filename.includes('..') || filename.includes('/') || filename.includes('\\')) {
        return res.json({ status: 400, message: '文件名不合法' });
      }
      await BackupService.deleteBackup(filename);
      res.json({ status: 200, message: '删除成功' });
    } catch (error) {
      console.error('[DataManager] deleteBackup error:', error);
      if (error.message === '备份文件不存在') return res.json({ status: 404, message: '备份文件不存在' });
      res.json({ status: 500, message: '删除备份失败' });
    }
  }

  static async downloadBackup(req, res) {
    try {
      const { filename } = req.params;
      if (!filename || filename.includes('..') || filename.includes('/') || filename.includes('\\')) {
        return res.json({ status: 400, message: '文件名不合法' });
      }
      const filepath = path.join(BackupService.getBackupDir(), filename);
      const resolvedPath = path.resolve(filepath);
      const resolvedBase = path.resolve(BackupService.getBackupDir());
      if (!resolvedPath.startsWith(resolvedBase)) return res.json({ status: 403, message: '禁止访问' });
      if (!fs.existsSync(filepath)) return res.json({ status: 404, message: '备份文件不存在' });
      res.download(filepath);
    } catch (error) {
      console.error('[DataManager] downloadBackup error:', error);
      res.json({ status: 500, message: '下载备份失败' });
    }
  }

  // ==================== 任务状态查询 ====================

  static async getExportTaskStatus(req, res) {
    try {
      const { taskId } = req.params;
      if (!taskId) return res.json({ status: 400, message: '任务ID不能为空' });
      const task = ExportTaskManager.getTaskStatus(taskId);
      if (!task) return res.json({ status: 404, message: '任务不存在或已过期' });
      res.json({
        status: 200,
        data: {
          id: task.id,
          type: task.type,
          status: task.status,
          tableName: task.tableName,
          tableSize: task.tableSize,
          progress: task.progress,
          error: task.error,
          result: task.result ? {
            filename: task.result.filename,
            full: task.result.full,
            schemaOnly: task.result.schemaOnly,
            size: task.result.size,
            date: task.result.date,
          } : null,
          createdAt: task.createdAt,
          startedAt: task.startedAt,
          completedAt: task.completedAt
        }
      });
    } catch (error) {
      console.error('[DataManager] getExportTaskStatus error:', error);
      res.json({ status: 500, message: '获取任务状态失败' });
    }
  }

  static async cancelExportTask(req, res) {
    try {
      const { taskId } = req.params;
      if (!taskId) return res.json({ status: 400, message: '任务ID不能为空' });
      const cancelled = await ExportTaskManager.cancelTask(taskId);
      if (cancelled) {
        res.json({ status: 200, message: '任务已取消' });
      } else {
        res.json({ status: 400, message: '任务无法取消（可能已完成或不存在）' });
      }
    } catch (error) {
      console.error('[DataManager] cancelExportTask error:', error);
      res.json({ status: 500, message: '取消任务失败' });
    }
  }
}

module.exports = DataManagerController;
