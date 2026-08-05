-- 提高金额精度，避免外币折算成人民币时被四舍五入
-- 原 card_bill 聚合列与 account.amount 为 decimal(12,2)，
-- 1 HKD @ 86.58 = 0.8658 元会被截断为 0.87。扩到 decimal(14,4)
-- （14,4 = 10 位整数 + 4 位小数，既保留原 10 位整数容量，又支持 4 位小数精度）。

ALTER TABLE `card_bill`
  MODIFY COLUMN `used_limit` decimal(14,4) NOT NULL COMMENT '已用额度',
  MODIFY COLUMN `bill_amount` decimal(14,4) DEFAULT NULL COMMENT '本期账单',
  MODIFY COLUMN `need_repay` decimal(14,4) DEFAULT NULL COMMENT '待还金额';

ALTER TABLE `account`
  MODIFY COLUMN `amount` decimal(14,4) NOT NULL COMMENT '金额（原币种，外币按真实精度存储）';
