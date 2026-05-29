const express = require('express');
const router = express.Router();
const ctrl = require('../controller');
const authGuard = require('../../../common/middleware/authGuard');
const pinLockGuard = require('../../../common/middleware/pinLockGuard');

router.use(authGuard);

router.get('/list', ctrl.getList);
router.get('/history/all', ctrl.getAllHistory);
router.get('/:id', ctrl.getById);
router.post('/', ctrl.create);
router.put('/:id', ctrl.update);
router.delete('/:id', pinLockGuard, ctrl.delete);
router.get('/:id/history', ctrl.getHistory);
router.post('/:id/history', ctrl.addHistory);
router.put('/history/:id', ctrl.updateHistory);
router.delete('/history/:id', pinLockGuard, ctrl.deleteHistory);

module.exports = router;
