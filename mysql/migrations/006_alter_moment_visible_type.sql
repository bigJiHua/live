-- ========================================
-- 006: moment 表字段迁移 + _migrations 表格式修复
-- ========================================

-- 1. moment 表 visible_type: tinyint(4) → varchar(255) 存储 JSON
ALTER TABLE `moment`
  MODIFY COLUMN `visible_type` VARCHAR(255)
  DEFAULT '{"vt":0,"vs":0,"pw":0}'
  COMMENT '可见性(JSON): vt可见类型/vs批次号/pw密码';

-- 存量数据迁移：旧值 0 → JSON 默认值
UPDATE `moment`
  SET `visible_type` = '{"vt":0,"vs":0,"pw":0}'
  WHERE `visible_type` = '0';

-- 2. _migrations 表修复：字符集 utf8mb4 → utf8
--    _migrations 仅存 ASCII 文件名，utf8mb4 浪费且 varchar(255) 主键超 767 字节
--    → utf8 × varchar(255) = 765 字节，COMPACT / DYNAMIC 均兼容
ALTER TABLE `_migrations`
  DEFAULT CHARSET=utf8,
  MODIFY COLUMN `filename` varchar(255) CHARACTER SET utf8 NOT NULL,
  ROW_FORMAT=DYNAMIC;
