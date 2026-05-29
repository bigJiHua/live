-- bus_fund_history 表：fund_id int→varchar(50)，id varchar(32)→varchar(50)
-- 注：varchar(50) 对齐 live.sql (fund 表 id 为 varchar(50))
ALTER TABLE `bus_fund_history`
  MODIFY COLUMN `fund_id` varchar(50) NOT NULL COMMENT '关联理财ID',
  MODIFY COLUMN `id` varchar(50) NOT NULL COMMENT 'id主键';
