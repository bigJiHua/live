-- 008: 添加 password_changed_at 字段用于 Token 吊销
-- 当密码变更时更新此字段，refreshToken 通过比对 iat 与此字段判断令牌是否应被吊销

ALTER TABLE `user_info`
ADD COLUMN `password_changed_at` BIGINT NULL DEFAULT NULL COMMENT '密码最后修改时间戳(ms)'
AFTER `update_time`;

-- 初始化：将现有用户的 password_changed_at 设为当前时间
UPDATE `user_info` SET `password_changed_at` = UNIX_TIMESTAMP() * 1000 WHERE `password_changed_at` IS NULL;
