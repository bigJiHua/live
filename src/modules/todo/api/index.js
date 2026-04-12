const express = require('express');
const router = express.Router();
const todoController = require('../controller');
const authGuard = require('../../../common/middleware/authGuard');
const pinLockGuard = require('../../../common/middleware/pinLockGuard');

// 所有路由都需要认证 + PIN 验证
router.use(authGuard);
router.use(pinLockGuard);

// ========== 待办日程 ==========

// 创建待办
router.post('/', todoController.create);

// 获取待办列表（支持筛选）
router.get('/list', todoController.list);

// 日历月视图数据
router.get('/calendar/month', todoController.calendarMonth);

// 获取即将提醒的待办（未来3-5天）
router.get('/reminders', todoController.reminders);

// 获取详情
router.get('/:id', todoController.detail);

// 更新待办
router.put('/:id', todoController.update);

// 删除待办
router.delete('/:id', todoController.delete);

module.exports = router;
