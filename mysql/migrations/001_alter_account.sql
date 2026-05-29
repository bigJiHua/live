-- account 表：新增转账组ID字段
ALTER TABLE `account`
  ADD COLUMN `transfer_group_id` varchar(255) DEFAULT NULL COMMENT '转账组ID，关联同一笔转账的支出和收入记录' AFTER `is_deleted`,
  ADD INDEX `idx_transfer_group` (`transfer_group_id`(191));
