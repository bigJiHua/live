-- 009: 信用卡共享额度池（痛点2）+ card_base 共享池列
-- 将 live.sql 末尾「信用卡重构补充表」的增量结构变更落地为可执行的迁移脚本。
-- 对已升级环境（live_dev 等）幂等：列已存在 ALTER 报 ER_DUP_FIELDNAME 由迁移运行器自动跳过；
-- 表已存在时 CREATE TABLE IF NOT EXISTS 直接跳过。

-- 1) card_base 补充列：共享额度池ID
ALTER TABLE `card_base`
  ADD COLUMN `share_pool_id` varchar(50) DEFAULT NULL COMMENT '同银行共享额度池ID';

-- 2) card_credit_pool（同银行共享额度池）
CREATE TABLE IF NOT EXISTS `card_credit_pool` (
  `id` varchar(50) NOT NULL COMMENT '主键',
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `bank_id` varchar(50) DEFAULT NULL COMMENT '银行标识',
  `bank_name` varchar(50) DEFAULT NULL COMMENT '银行名称(如农业银行)',
  `total_credit_limit` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '共享固定额度',
  `total_temp_limit` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '共享临时额度',
  `currency` varchar(10) DEFAULT 'CNY' COMMENT '币种',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` varchar(20) NOT NULL COMMENT '【保留varchar】创建时间',
  `update_time` varchar(20) NOT NULL COMMENT '【保留varchar】更新时间',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='同银行共享额度池';
