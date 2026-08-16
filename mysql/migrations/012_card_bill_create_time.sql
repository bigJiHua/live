-- 012: card_bill 补充 create_time 列
-- CreditCore.syncCardBills 的 INSERT 会写入 create_time，但 live.sql 早期定义未含此列，
-- 导致运行库缺列报 Unknown column 'create_time'。本迁移幂等补齐。
-- 列已存在时 ALTER 报 ER_DUP_FIELDNAME，由迁移运行器自动跳过。

ALTER TABLE `card_bill`
  ADD COLUMN `create_time` varchar(20) DEFAULT NULL COMMENT '创建时间';
