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
      if (!tableName) {
        return res.json({ status: 400, message: '表名不能为空' });
      }
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

      if (!tableName) {
        return res.json({ status: 400, message: '表名不能为空' });
      }

      const result = await DataManagerModel.getTableData(tableName, {
        page: parseInt(page),
        pageSize: parseInt(pageSize)
      });

      res.json({ status: 200, data: result });
    } catch (error) {
      console.error('[DataManager] getTableData error:', error);
      res.json({ status: 500, message: '获取表数据失败' });
    }
  }

  static async exportSingleTable(req, res) {
    try {
      const { tableName } = req.params;
      const { includeData = 'true' } = req.query;

      if (!tableName) {
        return res.json({ status: 400, message: '表名不能为空' });
      }

      const task = await ExportTaskManager.createExportTask(tableName, {
        includeData: includeData === 'true'
      });

      if (task.isLargeTable) {
        return res.json({
          status: 202,
          message: '数据量较大，正在后台异步导出',
          data: {
            taskId: task.id,
            status: task.status,
            tableSize: task.tableSize,
            isLargeTable: true,
            estimatedProgress: 0
          }
        });
      }

      const backup = await BackupService.backupSingleTable(
        tableName,
        includeData === 'true'
      );

      res.json({
        status: 200,
        message: '表导出成功',
        data: {
          filename: backup.filename,
          filepath: backup.filepath,
          size: backup.size,
          downloadPath: `/data/sql/table/${backup.filename}`
        }
      });
    } catch (error) {
      console.error('[DataManager] exportSingleTable error:', error);
      res.json({ status: 500, message: '导出表失败' });
    }
  }

  static async exportFullDatabase(req, res) {
    try {
      const { format = 'sql', includeData = 'true' } = req.query;

      const task = await ExportTaskManager.createFullExportTask({
        includeData: includeData === 'true',
        format
      });

      if (task.isLargeTable) {
        return res.json({
          status: 202,
          message: '数据库较大，正在后台异步导出',
          data: {
            taskId: task.id,
            status: task.status,
            tableSize: task.tableSize,
            isLargeTable: true,
            estimatedProgress: 0
          }
        });
      }

      if (format === 'zip') {
        const backup = await BackupService.backupAllTablesToZip(includeData === 'true');
        res.json({
          status: 200,
          message: '数据库备份成功',
          data: {
            filename: backup.zipFilename,
            filepath: backup.zipFilepath,
            size: backup.zipSize,
            downloadPath: `/data/sql/table/${backup.zipFilename}`
          }
        });
      } else {
        const backup = await BackupService.backupAllTables(includeData === 'true');
        const timestamp = dayjs().format('YYYYMMDD-HHmmss');
        const filename = `${timestamp}-full-backup.sql`;
        const filepath = path.join(BackupService.getBackupDir(), filename);

        fs.writeFileSync(filepath, backup, 'utf8');

        res.json({
          status: 200,
          message: '数据库备份成功',
          data: {
            filename,
            filepath,
            size: backup.length,
            downloadPath: `/data/sql/table/${filename}`
          }
        });
      }
    } catch (error) {
      console.error('[DataManager] exportFullDatabase error:', error);
      res.json({ status: 500, message: '备份数据库失败' });
    }
  }

  static async validateImportData(req, res) {
    try {
      const { tableName, data } = req.body;

      if (!tableName) {
        return res.json({ status: 400, message: '表名不能为空' });
      }

      if (!Array.isArray(data) || data.length === 0) {
        return res.json({ status: 400, message: '数据必须是包含对象的非空数组' });
      }

      const tableColumns = await DataManagerModel.getColumnNames(tableName);
      const validationResult = {
        tableName,
        tableColumns,
        totalRows: data.length,
        validRows: 0,
        invalidRows: 0,
        errors: []
      };

      for (let i = 0; i < data.length; i++) {
        const row = data[i];
        const rowErrors = [];

        for (const col of tableColumns) {
          if (!(col in row) && col !== 'id' && col !== 'create_time' && col !== 'update_time') {
            rowErrors.push(`缺少字段: ${col}`);
          }
        }

        const extraColumns = Object.keys(row).filter(k => !tableColumns.includes(k));
        if (extraColumns.length > 0) {
          rowErrors.push(`多余字段将被忽略: ${extraColumns.join(', ')}`);
        }

        if (rowErrors.length === 0) {
          validationResult.validRows++;
        } else {
          validationResult.invalidRows++;
          validationResult.errors.push({ row: i + 1, errors: rowErrors });
        }
      }

      res.json({
        status: 200,
        data: validationResult,
        canImport: validationResult.invalidRows === 0
      });
    } catch (error) {
      console.error('[DataManager] validateImportData error:', error);
      res.json({ status: 500, message: '校验数据失败' });
    }
  }

  static async importData(req, res) {
    const { tableName, data, forceClear = false } = req.body;

    if (!tableName) {
      return res.json({ status: 400, message: '表名不能为空' });
    }

    if (!Array.isArray(data) || data.length === 0) {
      return res.json({ status: 400, message: '数据必须是包含对象的非空数组' });
    }

    let backupFilename = null;
    try {
      const tableColumns = await DataManagerModel.getColumnNames(tableName);

      backupFilename = await BackupService.backupSingleTable(tableName, true);

      const filteredData = data.map(row => {
        const filtered = {};
        for (const col of tableColumns) {
          if (col in row) {
            filtered[col] = row[col];
          }
        }
        return filtered;
      });

      if (forceClear) {
        await DataManagerModel.truncateTable(tableName);
      }

      const result = await DataManagerModel.insertBatch(tableName, filteredData);

      res.json({
        status: 200,
        message: '数据导入成功',
        data: {
          importedRows: result.affectedRows,
          totalRows: data.length,
          backupFile: backupFilename ? backupFilename.filename : null
        }
      });
    } catch (error) {
      console.error('[DataManager] importData error:', error);
      res.json({
        status: 500,
        message: '导入数据失败',
        backupFile: backupFilename ? backupFilename.filename : null
      });
    }
  }

  static async importSql(req, res) {
    const MAX_FILE_SIZE = 100 * 1024 * 1024;

    try {
      if (!req.file) {
        return res.json({ status: 400, message: '请上传 SQL 或 ZIP 文件' });
      }

      const originalName = req.file.originalname;
      const ext = path.extname(originalName).toLowerCase();

      if (ext !== '.sql' && ext !== '.zip') {
        return res.json({ status: 400, message: '仅支持 .sql 或 .zip 文件' });
      }

      if (req.file.size > MAX_FILE_SIZE) {
        return res.json({ status: 400, message: '文件超过 100MB 限制' });
      }

      // 提取 SQL 内容
      let sqlContent = '';
      if (ext === '.zip') {
        const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'sql-import-'));
        try {
          const zip = new AdmZip(req.file.path);
          const zipEntries = zip.getEntries();
          const sqlEntry = zipEntries.find(
            (e) => !e.isDirectory && path.extname(e.entryName).toLowerCase() === '.sql'
          );
          if (!sqlEntry) {
            return res.json({ status: 400, message: 'ZIP 文件中未找到 .sql 文件' });
          }
          sqlContent = sqlEntry.getData().toString('utf8');
        } finally {
          fs.rmSync(tempDir, { recursive: true, force: true });
        }
      } else {
        sqlContent = fs.readFileSync(req.file.path, 'utf8');
      }

      // 基本校验
      if (!sqlContent || sqlContent.trim().length === 0) {
        return res.json({ status: 400, message: 'SQL 内容为空' });
      }

      // 粗略校验：检查是否包含可识别的 SQL 语句
      const hasContent = /(CREATE|INSERT|ALTER|DROP)\s/i.test(sqlContent);
      if (!hasContent) {
        return res.json({ status: 400, message: '未检测到有效的 SQL 语句' });
      }

      // 第一步：全库备份
      console.log('[DataManager] SQL导入: 开始全库备份...');
      const backup = await BackupService.backupAllTablesToZip(true);
      console.log(`[DataManager] SQL导入: 备份完成 ${backup.zipFilename}`);

      // 第二步：逐句执行 SQL
      const statements = sqlContent
        .split(/;\s*\r?\n/)
        .map((s) => s.trim())
        .filter((s) => s.length > 0 && !s.startsWith('--') && !s.startsWith('#') && !s.startsWith('/*'));

      let executedCount = 0;
      let errorCount = 0;
      const errors = [];

      for (let i = 0; i < statements.length; i++) {
        const stmt = statements[i];
        if (!stmt) continue;

        try {
          await db.execute(stmt);
          executedCount++;
        } catch (stmtErr) {
          errorCount++;
          errors.push({ index: i + 1, sql: stmt.substring(0, 100), message: stmtErr.message });
          console.warn(`[DataManager] SQL导入: 第${i + 1}条语句执行失败:`, stmtErr.message);
        }
      }

      // 清理上传的临时文件
      try { fs.unlinkSync(req.file.path); } catch (_) {}

      const success = errorCount === 0;
      res.json({
        status: success ? 200 : 207,
        message: success
          ? `SQL 导入成功，共执行 ${executedCount} 条语句`
          : `执行完成，${executedCount} 条成功，${errorCount} 条失败`,
        data: {
          executedCount,
          errorCount,
          errors: errors.slice(0, 20),
          backupFile: backup.zipFilename,
          backupTimestamp: dayjs().format('YYYY-MM-DD HH:mm:ss'),
        },
      });
    } catch (error) {
      console.error('[DataManager] importSql error:', error);
      return res.json({ status: 500, message: error.message || 'SQL 导入失败' });
    }
  }

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
      const result = await BackupService.createFullSystemBackup();
      res.json({ status: 200, message: '系统备份成功', data: result });
    } catch (error) {
      console.error('[DataManager] createSystemBackup error:', error);
      const message = error.message || '系统备份失败';
      res.json({ status: 500, message });
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
      if (error.message === '备份文件不存在') {
        return res.json({ status: 404, message: '备份文件不存在' });
      }
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

      if (!resolvedPath.startsWith(resolvedBase)) {
        return res.json({ status: 403, message: '禁止访问' });
      }

      if (!fs.existsSync(filepath)) {
        return res.json({ status: 404, message: '备份文件不存在' });
      }

      res.download(filepath);
    } catch (error) {
      console.error('[DataManager] downloadBackup error:', error);
      res.json({ status: 500, message: '下载备份失败' });
    }
  }

  static async getExportTaskStatus(req, res) {
    try {
      const { taskId } = req.params;
      if (!taskId) {
        return res.json({ status: 400, message: '任务ID不能为空' });
      }

      const task = ExportTaskManager.getTaskStatus(taskId);
      if (!task) {
        return res.json({ status: 404, message: '任务不存在或已过期' });
      }

      res.json({
        status: 200,
        data: {
          id: task.id,
          status: task.status,
          tableName: task.tableName,
          tableSize: task.tableSize,
          progress: task.progress,
          error: task.error,
          result: task.result ? {
            filename: task.result.filename,
            size: task.result.size,
            downloadPath: `/data/sql/table/${task.result.filename}`
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
      if (!taskId) {
        return res.json({ status: 400, message: '任务ID不能为空' });
      }

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