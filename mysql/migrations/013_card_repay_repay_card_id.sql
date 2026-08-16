-- 013: card_repay.repay_card_id 长度不足
-- 新卡 ID 由 idUtils.billId() 生成（17位时间戳 + 6位随机 = 25位），而 repay_card_id 原为 varchar(20)，
-- 银行卡还款（repay_method='bank_card'）写入 25 位卡 ID 时严格模式报错 / 非严格模式截断。
-- MODIFY 幂等：列已为 varchar(50) 时重复执行不报错。

ALTER TABLE `card_repay`
  MODIFY COLUMN `repay_card_id` varchar(50) NOT NULL DEFAULT '' COMMENT '还款来源的id';
