-- 016: card_credit_pool 新增「信报合一」标记字段
-- 信报合一：同一银行 N 张卡共享一个额度和账户（征信上合并为一个账户）。
-- 开启后账单列表可「合并还款」一次性结清池内全部卡的欠款。
-- 对已升级环境（live_dev 等）幂等：列已存在 ALTER 报 ER_DUP_FIELDNAME 由迁移运行器自动跳过。

ALTER TABLE `card_credit_pool`
  ADD COLUMN `credit_report_merged` tinyint(1) NOT NULL DEFAULT 0 COMMENT '信报合一标记(0否/1是)';
