const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const fs = require('fs');

const uploadDir = path.join(process.cwd(), 'data', 'uploads');
if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir, { recursive: true });
}
const upload = multer({ dest: uploadDir });

const DataManagerController = require('../controller');
const authGuard = require('../../../common/middleware/authGuard');
const pinLockGuard = require('../../../common/middleware/pinLockGuard');

router.use(authGuard);

router.get('/tables', DataManagerController.getTableList);

router.get('/status', DataManagerController.getTableStatus);

router.get('/structure/:tableName', DataManagerController.getTableStructure);

router.get('/data/:tableName', DataManagerController.getTableData);

router.get('/export/table/:tableName', DataManagerController.exportSingleTable);

router.get('/export/full', DataManagerController.exportFullDatabase);

router.post('/validate', DataManagerController.validateImportData);

router.post('/import', DataManagerController.importData);

router.post('/import/sql', upload.single('file'), DataManagerController.importSql);

router.get('/backups', DataManagerController.getBackupList);

router.post('/backups/system', DataManagerController.createSystemBackup);

router.get('/backups/system', DataManagerController.getSystemBackups);

router.delete('/backups/:filename', pinLockGuard, DataManagerController.deleteBackup);

router.get('/download/:filename', DataManagerController.downloadBackup);

router.get('/export/task/:taskId', DataManagerController.getExportTaskStatus);

router.delete('/export/task/:taskId', pinLockGuard, DataManagerController.cancelExportTask);

module.exports = router;
