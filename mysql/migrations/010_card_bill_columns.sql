-- 010: card_bill 补充 bill_day / repay_day 列
-- CreditCore.syncCardBills 的 INSERT 会写入这两个冗余列（便于按账单日/还款日查询），
-- 但 live.sql 早期版本未定义，导致运行库缺列。本迁移幂等补齐。
-- 列已存在时 ALTER 报 ER_DUP_FIELDNAME，由迁移运行器自动跳过。

ALTER TABLE `card_bill`
  ADD COLUMN `bill_day` int(11) DEFAULT '0' COMMENT '账单日（冗余自 card_base，便于查询）';

ALTER TABLE `card_bill`
  ADD COLUMN `repay_day` int(11) DEFAULT '0' COMMENT '还款日（冗余自 card_base，便于查询）';
