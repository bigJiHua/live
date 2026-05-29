-- bus_recurring 表：补齐新字段 + category_id/account_id 类型修正
ALTER TABLE `bus_recurring`
  MODIFY COLUMN `id` varchar(50) NOT NULL COMMENT 'id主键',
  MODIFY COLUMN `category_id` varchar(50) DEFAULT NULL COMMENT '关联分类',
  MODIFY COLUMN `account_id` varchar(50) DEFAULT NULL COMMENT '默认扣款账户/卡片';
ALTER TABLE `bus_recurring`
  ADD COLUMN `remind_days` int(11) DEFAULT '0' COMMENT '提前提醒天数' AFTER `next_date`,
  ADD COLUMN `remark` varchar(255) DEFAULT NULL COMMENT '备注' AFTER `remind_days`,
  ADD COLUMN `month_records` json DEFAULT NULL COMMENT '月度状态记录' AFTER `remark`,
  ADD COLUMN `update_time` varchar(20) DEFAULT NULL COMMENT '更新时间' AFTER `create_time`,
  ADD COLUMN `is_deleted` tinyint(4) DEFAULT '0' COMMENT '软删除' AFTER `update_time`;
