const express = require('express');
const router = express.Router();
const workController = require('../controller');
const authGuard = require('../../../common/middleware/authGuard');
const pinLockGuard = require('../../../common/middleware/pinLockGuard');

// 所有路由都需要认证 + PIN 验证
router.use(authGuard);
router.use(pinLockGuard);

// ========== 工作信息 CRUD ==========

// 获取用户工作列表
router.get('/job/list', workController.list);

// 新增工作信息
router.post('/job', workController.createJob);

// 编辑工作信息
router.put('/job/:id', workController.updateJob);

// 删除工作（软删）
router.delete('/job/:id', workController.deleteJob);

// ========== 工资查询与计算 ==========

// 获取某一天工资
router.get('/salary/day', workController.getDaySalary);

// 手动保存当日工资
router.post('/salary', workController.saveDaySalary);

// 按月统计工资
router.get('/salary/month', workController.getMonthSalary);

// 删除某天工资记录
router.delete('/salary', workController.deleteDaySalary);

module.exports = router;
