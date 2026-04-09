-- phpMyAdmin SQL Dump
-- version 5.2.0
-- https://www.phpmyadmin.net/
--
-- 主机： 127.0.0.1:3306
-- 生成日期： 2026-04-09 14:26:07
-- 服务器版本： 5.7.40
-- PHP 版本： 8.0.26

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- 数据库： `live`
--

-- --------------------------------------------------------

--
-- 表的结构 `account`
--

DROP TABLE IF EXISTS `account`;
CREATE TABLE IF NOT EXISTS `account` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'id主键',
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `direction` tinyint(4) NOT NULL COMMENT '1收入 0支出',
  `category_id` varchar(50) NOT NULL COMMENT '分类ID',
  `pay_type` varchar(50) NOT NULL COMMENT '支出类型',
  `pay_method` varchar(50) NOT NULL COMMENT '支付方式',
  `account_type` varchar(20) DEFAULT 'debit' COMMENT '账户类型：cash现金 / debit资产 / credit负债',
  `amount` decimal(12,2) NOT NULL COMMENT '金额',
  `currency` varchar(10) DEFAULT 'CNY' COMMENT '币种',
  `exchange_rate` decimal(10,4) DEFAULT '1.0000' COMMENT '汇率',
  `trans_date` varchar(20) NOT NULL COMMENT '收支日期',
  `remark` varchar(255) DEFAULT '普通支出' COMMENT '备注',
  `card_id` varchar(32) NOT NULL COMMENT '关联卡片ID',
  `create_time` varchar(20) DEFAULT NULL COMMENT '提交时间',
  `update_time` varchar(20) DEFAULT NULL COMMENT '修改时间',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`,`trans_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='记账明细表';

-- --------------------------------------------------------

--
-- 表的结构 `account_balance`
--

DROP TABLE IF EXISTS `account_balance`;
CREATE TABLE IF NOT EXISTS `account_balance` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '余额ID',
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `card_id` varchar(32) NOT NULL COMMENT '关联卡片ID',
  `balance` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '当前余额（信用卡负=负债）',
  `currency` varchar(10) DEFAULT 'CNY' COMMENT '币种',
  `update_time` varchar(20) DEFAULT NULL COMMENT '最后更新时间',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_card` (`user_id`,`card_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='账户实时余额表（现金/微信/支付宝/储蓄卡/信用卡）';

-- --------------------------------------------------------

--
-- 表的结构 `account_transfer`
--

DROP TABLE IF EXISTS `account_transfer`;
CREATE TABLE IF NOT EXISTS `account_transfer` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '转账ID',
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `from_card_id` varchar(32) NOT NULL COMMENT '转出卡ID',
  `to_card_id` varchar(32) NOT NULL COMMENT '转入卡ID',
  `amount` decimal(12,2) NOT NULL COMMENT '转账金额',
  `trans_date` varchar(20) NOT NULL COMMENT '转账日期',
  `remark` varchar(255) DEFAULT '转账' COMMENT '备注',
  `create_time` varchar(20) DEFAULT NULL COMMENT '创建时间',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`,`trans_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='转账记录表';

-- --------------------------------------------------------

--
-- 表的结构 `app_config`
--

DROP TABLE IF EXISTS `app_config`;
CREATE TABLE IF NOT EXISTS `app_config` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'id主键',
  `user_id` int(11) DEFAULT '1' COMMENT '用户ID',
  `big_amount` int(11) DEFAULT '500' COMMENT '大额流水阈值',
  `currency` varchar(10) DEFAULT 'CNY' COMMENT '默认币种',
  `date_format` varchar(20) DEFAULT 'YYYY-MM-DD' COMMENT '日期格式',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='全局配置表';

-- --------------------------------------------------------

--
-- 表的结构 `asset`
--

DROP TABLE IF EXISTS `asset`;
CREATE TABLE IF NOT EXISTS `asset` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'id主键',
  `user_id` int(11) DEFAULT '1' COMMENT '用户ID',
  `name` varchar(100) DEFAULT NULL COMMENT '项目名称',
  `amount` decimal(12,2) DEFAULT NULL COMMENT '数额',
  `type` varchar(20) DEFAULT NULL COMMENT '资产/欠款',
  `create_time` varchar(20) DEFAULT NULL COMMENT '登记日期',
  `update_time` varchar(20) DEFAULT NULL COMMENT '修改日期',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='资产结构表';

-- --------------------------------------------------------

--
-- 表的结构 `budget`
--

DROP TABLE IF EXISTS `budget`;
CREATE TABLE IF NOT EXISTS `budget` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'id主键',
  `user_id` int(11) DEFAULT '1' COMMENT '用户ID',
  `budget_type` varchar(20) DEFAULT NULL COMMENT '预算类型 吃/买/行',
  `budget_amount` decimal(12,2) DEFAULT NULL COMMENT '预算金额',
  `used_amount` decimal(12,2) DEFAULT '0.00' COMMENT '已使用',
  `currency` varchar(10) DEFAULT NULL COMMENT '币种',
  `cycle` varchar(20) DEFAULT NULL COMMENT '周期 月/季/年',
  `plan_date` varchar(20) DEFAULT NULL COMMENT '预计日期',
  `is_over_budget` tinyint(4) DEFAULT '0' COMMENT '是否超支',
  `create_time` varchar(20) DEFAULT NULL COMMENT '创建时间',
  `update_time` varchar(20) DEFAULT NULL COMMENT '修改时间',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='预算控制表';

-- --------------------------------------------------------

--
-- 表的结构 `bus_category`
--

DROP TABLE IF EXISTS `bus_category`;
CREATE TABLE IF NOT EXISTS `bus_category` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'id主键',
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `name` varchar(50) NOT NULL COMMENT '分类名称',
  `type` varchar(20) NOT NULL COMMENT '类型: income/expense/asset/fixed',
  `icon_url` varchar(255) DEFAULT NULL COMMENT '自定义图标URL',
  `remark` varchar(50) DEFAULT NULL COMMENT '说明',
  `sort` int(11) DEFAULT '99' COMMENT '排序',
  `is_deleted` tinyint(4) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_type` (`user_id`,`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类字典表';

-- --------------------------------------------------------

--
-- 表的结构 `bus_fund_history`
--

DROP TABLE IF EXISTS `bus_fund_history`;
CREATE TABLE IF NOT EXISTS `bus_fund_history` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'id主键',
  `fund_id` int(11) NOT NULL COMMENT '关联理财ID',
  `net_value` decimal(10,4) NOT NULL COMMENT '当日净值',
  `market_val` decimal(12,2) DEFAULT NULL COMMENT '当日持仓市值',
  `record_date` varchar(20) NOT NULL COMMENT '记录日期(YYYY-MM-DD)',
  `create_time` varchar(20) NOT NULL COMMENT '写入时间',
  PRIMARY KEY (`id`),
  KEY `idx_fund_date` (`fund_id`,`record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='理财净值历史表';

-- --------------------------------------------------------

--
-- 表的结构 `bus_recurring`
--

DROP TABLE IF EXISTS `bus_recurring`;
CREATE TABLE IF NOT EXISTS `bus_recurring` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'id主键',
  `user_id` varchar(50) NOT NULL,
  `name` varchar(100) NOT NULL COMMENT '任务名称(如:房租/服务器续费)',
  `amount` decimal(12,2) NOT NULL,
  `category_id` int(11) DEFAULT NULL COMMENT '关联分类',
  `account_id` varchar(32) DEFAULT NULL COMMENT '默认扣款账户/卡片',
  `cycle` varchar(20) DEFAULT NULL COMMENT '周期: month/year/week',
  `day_of_cycle` int(11) DEFAULT NULL COMMENT '周期内的哪一天',
  `next_date` varchar(20) DEFAULT NULL COMMENT '下次触发日期',
  `is_active` tinyint(4) DEFAULT '1' COMMENT '是否启用',
  `create_time` varchar(20) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='周期性计划任务表';

-- --------------------------------------------------------

--
-- 表的结构 `card_base`
--

DROP TABLE IF EXISTS `card_base`;
CREATE TABLE IF NOT EXISTS `card_base` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '主键',
  `user_id` varchar(50) NOT NULL COMMENT '用户ID（必填）',
  `bank_id` varchar(255) NOT NULL COMMENT '银行ID（必填）',
  `card_type` varchar(20) NOT NULL COMMENT '卡类型（必填）',
  `card_level` varchar(20) NOT NULL DEFAULT '普卡' COMMENT '卡等级默认普卡',
  `main_sub` varchar(10) NOT NULL DEFAULT '主卡' COMMENT '主副卡',
  `card_org` varchar(20) NOT NULL DEFAULT '银联' COMMENT '卡组织默认银联',
  `card_length` varchar(30) NOT NULL DEFAULT '19' COMMENT '卡号长度默认19',
  `last4_no` varchar(10) NOT NULL COMMENT '卡号后四位（必填）',
  `card_bin` varchar(10) NOT NULL COMMENT '卡BIN（必填）',
  `credit_limit` decimal(12,2) DEFAULT '0.00' COMMENT '信用额度',
  `temp_limit` decimal(12,2) DEFAULT '0.00' COMMENT '临时额度',
  `alias` varchar(50) DEFAULT '' COMMENT '卡别名（非必填）',
  `card_img` varchar(255) DEFAULT '' COMMENT '卡面图片',
  `open_date` varchar(20) NOT NULL COMMENT '开卡日期（必填）',
  `expire_date` varchar(20) NOT NULL COMMENT '过期日期（必填）',
  `bill_day` int(11) DEFAULT '0' COMMENT '账单日',
  `repay_day` int(11) DEFAULT '0' COMMENT '还款日',
  `currency` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT '币种默认人民币',
  `status` varchar(20) NOT NULL DEFAULT '正常' COMMENT '状态默认正常',
  `is_default` tinyint(4) DEFAULT '0' COMMENT '是否默认卡',
  `is_hide` tinyint(4) DEFAULT '0' COMMENT '是否隐藏',
  `sort` int(11) DEFAULT '99' COMMENT '排序',
  `tag` varchar(50) DEFAULT '' COMMENT '标签',
  `remark` varchar(255) DEFAULT '' COMMENT '备注',
  `color` varchar(10) DEFAULT '#0052cc' COMMENT '颜色',
  `annual_fee` decimal(12,2) DEFAULT '0.00' COMMENT '年费',
  `fee_free_rule` varchar(255) DEFAULT '' COMMENT '免年费规则',
  `source_from` varchar(20) NOT NULL DEFAULT '手动' COMMENT '来源默认手动',
  `create_time` varchar(20) DEFAULT '' COMMENT '创建时间',
  `update_time` varchar(20) DEFAULT '' COMMENT '更新时间',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  `points_rate` decimal(5,2) NOT NULL DEFAULT '1.00' COMMENT '积分比例：1元 = N积分',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_hide` (`user_id`,`is_hide`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='卡片基础信息表';

-- --------------------------------------------------------

--
-- 表的结构 `card_bill`
--

DROP TABLE IF EXISTS `card_bill`;
CREATE TABLE IF NOT EXISTS `card_bill` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'id主键',
  `card_id` varchar(50) NOT NULL COMMENT '关联卡片ID',
  `bill_month` varchar(7) DEFAULT NULL COMMENT '账单月：YYYY-MM',
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `credit_limit` decimal(12,2) NOT NULL COMMENT '信用额度',
  `avail_limit` decimal(12,2) NOT NULL COMMENT '可用额度',
  `used_limit` decimal(12,2) NOT NULL COMMENT '已用额度',
  `temp_limit` decimal(12,2) NOT NULL COMMENT '临时额度',
  `bill_start_date` varchar(20) DEFAULT NULL COMMENT '账单周期开始',
  `bill_end_date` varchar(20) DEFAULT NULL COMMENT '账单周期结束',
  `bill_amount` decimal(12,2) DEFAULT NULL COMMENT '本期账单',
  `min_repay` decimal(12,2) DEFAULT NULL COMMENT '最低还款',
  `repaid` decimal(12,2) DEFAULT NULL COMMENT '已还金额',
  `need_repay` decimal(12,2) DEFAULT NULL COMMENT '待还金额',
  `points` int(255) DEFAULT NULL COMMENT '积分',
  `points_rate` decimal(10,0) NOT NULL DEFAULT '1' COMMENT '积分兑换规则',
  `points_expire` varchar(20) DEFAULT NULL COMMENT '积分到期日',
  `repay_status` varchar(20) DEFAULT NULL COMMENT '还款状态',
  `is_overdue` tinyint(4) DEFAULT NULL COMMENT '是否逾期',
  `overdue_days` int(11) DEFAULT NULL COMMENT '逾期天数',
  `remind_switch` tinyint(4) DEFAULT NULL COMMENT '提醒开关',
  `remind_days` int(11) DEFAULT NULL COMMENT '提醒天数',
  `update_time` varchar(20) DEFAULT NULL COMMENT '更新时间',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_card_month` (`user_id`,`card_id`,`bill_month`),
  KEY `idx_user_card` (`user_id`,`card_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='卡片额度账单表';

-- --------------------------------------------------------

--
-- 表的结构 `card_log`
--

DROP TABLE IF EXISTS `card_log`;
CREATE TABLE IF NOT EXISTS `card_log` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'id主键',
  `card_id` varchar(50) NOT NULL COMMENT '卡片ID',
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `operate_type` varchar(20) NOT NULL COMMENT '操作类型 新增/编辑/删除/还款',
  `operate_time` varchar(20) NOT NULL COMMENT '操作时间',
  `operate_ip` varchar(50) DEFAULT NULL COMMENT '操作IP',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='卡片操作日志';

-- --------------------------------------------------------

--
-- 表的结构 `card_repay`
--

DROP TABLE IF EXISTS `card_repay`;
CREATE TABLE IF NOT EXISTS `card_repay` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'id主键',
  `card_id` varchar(50) NOT NULL COMMENT '卡片ID',
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `bill_id` varchar(32) NOT NULL COMMENT '关联账单ID',
  `bill_month` varchar(7) DEFAULT NULL COMMENT '归属账单月',
  `repay_amount` decimal(12,2) NOT NULL COMMENT '还款金额',
  `repay_method` varchar(20) NOT NULL COMMENT '还款方式',
  `repay_time` varchar(20) NOT NULL COMMENT '还款时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_card` (`user_id`,`card_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='信用卡还款记录表';

-- --------------------------------------------------------

--
-- 表的结构 `device_crypto`
--

DROP TABLE IF EXISTS `device_crypto`;
CREATE TABLE IF NOT EXISTS `device_crypto` (
  `fingerprint` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '浏览器指纹',
  `aes_key` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '临时AES密钥',
  `aes_expires_at` bigint(20) DEFAULT NULL COMMENT 'AES过期时间',
  `captcha_code` varchar(10) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '验证码',
  `captcha_expires_at` bigint(20) DEFAULT NULL COMMENT '验证码过期时间',
  `captcha_attempts` int(11) DEFAULT '0' COMMENT '验证码尝试次数',
  `captcha_verified` tinyint(1) DEFAULT '0' COMMENT '是否已验证（1=已验证）',
  `client_ip` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '最近IP',
  `user_agent` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'UA',
  `fail_count` int(11) DEFAULT '0' COMMENT '失败次数（登录/验证）',
  `locked_until` bigint(20) DEFAULT NULL COMMENT '锁定时间（防爆破）',
  `scene` varchar(32) COLLATE utf8mb4_bin NOT NULL DEFAULT 'login' COMMENT '用途（login/register/reset）',
  `created_at` bigint(20) NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  PRIMARY KEY (`fingerprint`,`scene`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='临时验证码/加密密钥申请记录表';

-- --------------------------------------------------------

--
-- 表的结构 `fixed_asset`
--

DROP TABLE IF EXISTS `fixed_asset`;
CREATE TABLE IF NOT EXISTS `fixed_asset` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'id主键',
  `user_id` int(11) DEFAULT '1' COMMENT '用户ID',
  `info` varchar(255) DEFAULT NULL COMMENT '资产信息',
  `tag` varchar(50) DEFAULT NULL COMMENT '品类标签',
  `img_url` varchar(255) DEFAULT NULL COMMENT '图片',
  `buy_price` decimal(12,2) DEFAULT NULL COMMENT '购买价',
  `now_val` decimal(12,2) DEFAULT NULL COMMENT '当前估值',
  `use_year` int(11) DEFAULT NULL COMMENT '使用年限',
  `deprec_method` varchar(20) DEFAULT NULL COMMENT '折旧方法 直线/加速',
  `month_deprec` decimal(12,2) DEFAULT NULL COMMENT '月折旧',
  `total_deprec` decimal(12,2) DEFAULT NULL COMMENT '累计折旧',
  `status` varchar(20) DEFAULT NULL COMMENT '状态',
  `buy_date` varchar(20) DEFAULT NULL COMMENT '购买日期',
  `scrap_date` varchar(20) DEFAULT NULL COMMENT '报废日期',
  `residual_val` decimal(12,2) DEFAULT NULL COMMENT '残值',
  `create_time` varchar(20) DEFAULT NULL COMMENT '登记时间',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='固定资产表';

-- --------------------------------------------------------

--
-- 表的结构 `fund`
--

DROP TABLE IF EXISTS `fund`;
CREATE TABLE IF NOT EXISTS `fund` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'id主键',
  `user_id` int(11) DEFAULT '1' COMMENT '用户ID',
  `fund_name` varchar(100) DEFAULT NULL COMMENT '基金名称',
  `share` decimal(12,2) DEFAULT NULL COMMENT '持有份额',
  `fund_account` varchar(10) DEFAULT NULL COMMENT '基金账户尾6位',
  `trade_account` varchar(15) DEFAULT NULL COMMENT '交易账户尾10位',
  `sell_org` varchar(50) DEFAULT NULL COMMENT '销售机构',
  `fund_company` varchar(50) DEFAULT NULL COMMENT '基金管理人',
  `buy_date` varchar(20) DEFAULT NULL COMMENT '购买日期',
  `last_report_date` varchar(20) DEFAULT NULL COMMENT '最后上报日期',
  `net_value` decimal(10,4) DEFAULT NULL COMMENT '当前净值',
  `market_val` decimal(12,2) DEFAULT NULL COMMENT '持仓市值',
  `rate` varchar(20) DEFAULT NULL COMMENT '收益率',
  `create_time` varchar(20) DEFAULT NULL COMMENT '写入时间',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='理财基金表';

-- --------------------------------------------------------

--
-- 表的结构 `moment`
--

DROP TABLE IF EXISTS `moment`;
CREATE TABLE IF NOT EXISTS `moment` (
  `id` varchar(50) NOT NULL COMMENT 'id主键',
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `content` text NOT NULL COMMENT '内容',
  `img_url` varchar(255) DEFAULT NULL COMMENT '图片',
  `mood` varchar(50) DEFAULT NULL COMMENT '心情',
  `location` varchar(255) DEFAULT NULL COMMENT '位置',
  `visible_type` tinyint(4) DEFAULT '0' COMMENT '0仅自己可见',
  `parent_id` varchar(50) DEFAULT NULL COMMENT '父ID 单日聚合',
  `create_time` varchar(20) DEFAULT NULL COMMENT '发布时间',
  `update_time` varchar(20) DEFAULT NULL COMMENT '修改时间',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='动态发布表';

-- --------------------------------------------------------

--
-- 表的结构 `sys_attachment`
--

DROP TABLE IF EXISTS `sys_attachment`;
CREATE TABLE IF NOT EXISTS `sys_attachment` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'id主键',
  `user_id` varchar(255) NOT NULL COMMENT '谁的图片',
  `bus_id` varchar(50) DEFAULT NULL COMMENT '关联业务ID(如moment_id/fixed_asset_id)',
  `bus_type` varchar(50) NOT NULL COMMENT '业务类型: moment/动态图片/资产图片 (product)银行Icon(bank)/其他资源 (other)',
  `remark` varchar(255) DEFAULT '用户上传的图片' COMMENT '图片',
  `tags` varchar(255) DEFAULT '默认' COMMENT '图片标签，用于搜索，逗号分隔：家,客厅,小区',
  `file_name` varchar(255) DEFAULT NULL COMMENT '原始文件名',
  `file_path` varchar(255) NOT NULL COMMENT '存储路径',
  `file_size` varchar(50) DEFAULT NULL COMMENT '大小(byte)',
  `file_ext` varchar(10) DEFAULT NULL COMMENT '后缀名',
  `create_time` varchar(20) NOT NULL,
  `is_deleted` tinyint(4) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `bus_id` (`bus_id`),
  KEY `idx_bus_ref` (`bus_type`,`bus_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件附件索引表';

-- --------------------------------------------------------

--
-- 表的结构 `todo`
--

DROP TABLE IF EXISTS `todo`;
CREATE TABLE IF NOT EXISTS `todo` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'id主键',
  `user_id` int(11) DEFAULT '1' COMMENT '用户ID',
  `content` varchar(255) DEFAULT NULL COMMENT '事件内容',
  `happen_date` varchar(20) DEFAULT NULL COMMENT '发生日期',
  `create_time` varchar(20) DEFAULT NULL COMMENT '提交日期',
  `status` varchar(20) DEFAULT NULL COMMENT '状态 待完成/已完成/逾期',
  `need_remind` tinyint(4) DEFAULT NULL COMMENT '是否提醒',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`,`happen_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='待办事项表';

-- --------------------------------------------------------

--
-- 表的结构 `user_info`
--

DROP TABLE IF EXISTS `user_info`;
CREATE TABLE IF NOT EXISTS `user_info` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'id主键',
  `username` varchar(50) NOT NULL COMMENT '昵称',
  `email` varchar(50) NOT NULL COMMENT '用户邮箱',
  `avatar` varchar(255) DEFAULT 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg' COMMENT '头像URL',
  `login_pwd` varchar(100) NOT NULL COMMENT '登录密码(加密)',
  `identity` varchar(25) DEFAULT 'user' COMMENT '用户身份',
  `pin_code` varchar(100) DEFAULT NULL COMMENT 'PIN码(加密)',
  `status` tinyint(4) DEFAULT '1' COMMENT '状态 1正常 0锁定',
  `create_time` varchar(20) DEFAULT NULL COMMENT '创建时间',
  `update_time` varchar(20) DEFAULT NULL COMMENT '修改时间',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除 0否1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='用户信息表';

-- --------------------------------------------------------

--
-- 表的结构 `user_log`
--

DROP TABLE IF EXISTS `user_log`;
CREATE TABLE IF NOT EXISTS `user_log` (
  `id` int(255) NOT NULL AUTO_INCREMENT COMMENT 'log索引 非ID',
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `type` varchar(20) DEFAULT 'login' COMMENT '操作类型: login/logout/refresh/failed',
  `token` text COMMENT '本次登录分配的Token(部分存根)',
  `login_ip` varchar(255) NOT NULL COMMENT '登录IP地址',
  `login_location` varchar(255) DEFAULT NULL COMMENT '地理位置(国家-城市)',
  `login_isp` varchar(255) DEFAULT NULL COMMENT '运营商',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '原始UA信息',
  `os_info` varchar(100) DEFAULT NULL COMMENT '操作系统',
  `browser_info` varchar(100) DEFAULT NULL COMMENT '浏览器信息',
  `device_model` varchar(100) DEFAULT NULL COMMENT '设备型号(如Pixel 7)',
  `fingerprint` varchar(100) DEFAULT NULL COMMENT '设备唯一指纹',
  `viewport` varchar(50) DEFAULT NULL COMMENT '视口大小(WxH)',
  `pixel_ratio` varchar(10) DEFAULT NULL COMMENT '像素比',
  `status` tinyint(4) DEFAULT '1' COMMENT '状态: 1成功 0失败',
  `error_message` varchar(255) DEFAULT NULL COMMENT '失败原因(如:密码错误/PIN锁定)',
  `login_lang` varchar(50) DEFAULT NULL COMMENT '登录语言',
  `path` varchar(255) DEFAULT NULL COMMENT '来源页面',
  `login_time` varchar(20) NOT NULL COMMENT '操作时间',
  `create_time` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`login_time`),
  KEY `idx_fingerprint` (`fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录日志及安全审计表';

-- --------------------------------------------------------

--
-- 表的结构 `work_salary`
--

DROP TABLE IF EXISTS `work_salary`;
CREATE TABLE IF NOT EXISTS `work_salary` (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'id主键',
  `user_id` int(11) DEFAULT '1' COMMENT '用户ID',
  `work_date` varchar(20) DEFAULT NULL COMMENT '日期',
  `income` decimal(12,2) DEFAULT NULL COMMENT '收入',
  `outcome` decimal(12,2) DEFAULT NULL COMMENT '支出',
  `day_salary` decimal(12,2) DEFAULT NULL COMMENT '日薪',
  `subsidy` decimal(12,2) DEFAULT NULL COMMENT '补贴',
  `cut` decimal(12,2) DEFAULT NULL COMMENT '扣款',
  `social_security` decimal(12,2) DEFAULT NULL COMMENT '社保公积金',
  `tax` decimal(12,2) DEFAULT NULL COMMENT '个税',
  `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`,`work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='工作薪酬核算';
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
