-- 011: card_foreign_register（外币消费登记/对账表，痛点4）
-- 该表原先仅由 ForeignRegister.ensureTable() 运行时自愈创建，缺少迁移脚本。
-- 现改为严格由迁移落地（与 live.sql 定义一致）。CREATE TABLE IF NOT EXISTS 幂等，
-- 表已存在则跳过，可重复执行。

CREATE TABLE IF NOT EXISTS `card_foreign_register` (
  `id` varchar(50) NOT NULL COMMENT '主键',
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `card_id` varchar(50) NOT NULL COMMENT '信用卡ID',
  `account_id` varchar(50) NOT NULL COMMENT '关联account流水ID',
  `currency` varchar(10) NOT NULL COMMENT '原始币种(USD/HKD)',
  `foreign_amount` decimal(14,4) NOT NULL DEFAULT '0.0000' COMMENT '原始外币金额',
  `registered_rate` decimal(10,4) NOT NULL DEFAULT '0.0000' COMMENT '登记时汇率(每100外币=人民币)',
  `registered_rmb` decimal(14,4) NOT NULL DEFAULT '0.0000' COMMENT '登记时折合人民币',
  `actual_rate` decimal(10,4) DEFAULT NULL COMMENT '实际结算汇率(每100外币=人民币)',
  `actual_rmb` decimal(14,4) DEFAULT NULL COMMENT '实际结算人民币(入账)',
  `settle_date` varchar(20) DEFAULT NULL COMMENT '结算日期YYYY-MM-DD',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending|reconciled',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` varchar(20) NOT NULL COMMENT '【保留varchar】创建时间',
  `update_time` varchar(20) NOT NULL COMMENT '【保留varchar】更新时间',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_account` (`account_id`),
  KEY `idx_card_status` (`card_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='外币消费登记/对账表';
