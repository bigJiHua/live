-- 015: card_repay.account_id 允许 NULL
-- 还款方式 repay_method='card'（本卡还款/账务修正）不产生来源资金流水，account_id 为 NULL。
-- 原 schema 为 NOT NULL，会导致本卡还款 INSERT 失败整单回滚。
-- MODIFY 幂等：列已允许 NULL 时重复执行不报错。

ALTER TABLE `card_repay`
  MODIFY COLUMN `account_id` varchar(50) DEFAULT NULL COMMENT '绑定流水id（card本卡还款为NULL）';
