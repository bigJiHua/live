-- 014: card_foreign_register.account_id 补唯一约束
-- 注释声称"幂等：account_id 唯一"，但 011 仅建普通索引 idx_account，无唯一约束，
-- 并发下同一流水可生成重复 pending 登记。
--
-- C7 审计：若历史已存在重复 account_id，直接 ADD UNIQUE KEY 会报 ER_DUP_ENTRY，
-- 迁移运行器已改为对该错误抛错（不再误标记成功）。此处先清理重复行（同一 account_id
-- 保留 id 最小的一条），使唯一键可成功建立。MySQL 5.7 支持多表 DELETE 自连接。

DELETE fr1 FROM `card_foreign_register` fr1
INNER JOIN `card_foreign_register` fr2
  ON fr1.account_id = fr2.account_id AND fr1.id > fr2.id
WHERE fr1.is_deleted = 0;

ALTER TABLE `card_foreign_register`
  ADD UNIQUE KEY `uk_account_id` (`account_id`);
