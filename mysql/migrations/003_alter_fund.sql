-- fund 表：user_id int→varchar(50)，id varchar(32)→varchar(50)，新增 invest
ALTER TABLE `fund`
  MODIFY COLUMN `user_id` varchar(50) NOT NULL DEFAULT '' COMMENT '用户ID',
  MODIFY COLUMN `id` varchar(50) NOT NULL COMMENT 'id主键',
  ADD COLUMN `invest` decimal(12,2) DEFAULT '0.00' COMMENT '投入本金' AFTER `net_value`;
