# MySQL 数据库

本目录包含数据库相关的 SQL 文件和表结构说明。

## 文件说明

### live.sql
**完整的数据库表结构**

包含以下表：

| 表名 | 说明 |
|------|------|
| `user_info` | 用户信息表 |
| `card_base` | 卡片基础信息表 |
| `card_bill` | 卡片额度账单表 |
| `card_log` | 卡片操作日志表 |
| `card_repay` | 信用卡还款记录表 |
| `fund` | 理财基金表 |
| `todo` | 待办事项表 |
| `account` | 记账明细表 |
| `asset` | 资产结构表 |
| `budget` | 预算控制表 |
| `fixed_asset` | 固定资产表 |
| `work_salary` | 工作薪酬核算表 |
| `moment` | 动态发布表 |
| `app_config` | 全局配置表 |
| `bus_category` | 分类字典表 |
| `bus_fund_history` | 理财净值历史表 |
| `bus_recurring` | 周期性计划任务表 |
| `sys_attachment` | 文件附件索引表 |

---

## 核心表结构

### user_info (用户信息表)
```sql
CREATE TABLE user_info (
  id INT NOT NULL COMMENT '用户ID',
  nickname VARCHAR(50) COMMENT '昵称',
  avatar VARCHAR(255) COMMENT '头像URL',
  login_pwd VARCHAR(100) COMMENT '登录密码(加密)',
  salt VARCHAR(50) COMMENT '加密盐值',
  pin_code VARCHAR(50) COMMENT 'PIN码(加密)',
  pin_error INT DEFAULT 0 COMMENT 'PIN错误次数',
  status TINYINT DEFAULT 1 COMMENT '状态 1正常 0锁定',
  last_login_time VARCHAR(20) COMMENT '最后登录时间',
  last_login_ip VARCHAR(50) COMMENT '最后登录IP',
  create_time VARCHAR(20) COMMENT '创建时间',
  update_time VARCHAR(20) COMMENT '修改时间',
  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除 0否1是',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';
```

### moment (动态发布表)
```sql
CREATE TABLE moment (
  id VARCHAR(32) NOT NULL COMMENT '动态ID(nanoid)',
  user_id VARCHAR(255) NOT NULL COMMENT '用户ID',
  content TEXT COMMENT '内容',
  img_url TEXT COMMENT '图片(JSON数组)',
  mood VARCHAR(50) COMMENT '心情',
  location VARCHAR(255) COMMENT '位置(JSON)',
  visible_type TINYINT DEFAULT 0 COMMENT '0仅自己可见',
  parent_id VARCHAR(32) DEFAULT 0 COMMENT '父ID 单日聚合(parent_id=id为主动态)',
  create_time VARCHAR(20) NOT NULL COMMENT '发布时间',
  update_time VARCHAR(20) NOT NULL COMMENT '修改时间',
  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (id),
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态发布表';
```

### sys_attachment (文件附件索引表)
```sql
CREATE TABLE sys_attachment (
  id VARCHAR(32) NOT NULL COMMENT 'id主键(nanoid)',
  user_id VARCHAR(255) NOT NULL COMMENT '谁的图片',
  bus_type VARCHAR(50) NOT NULL COMMENT '业务类型: post/product/bank/other',
  remark VARCHAR(255) DEFAULT '用户上传的图片' COMMENT '图片说明',
  tags VARCHAR(255) DEFAULT '默认' COMMENT '标签(JSON数组)',
  file_name VARCHAR(255) COMMENT '文件名',
  file_path VARCHAR(255) NOT NULL COMMENT '存储路径',
  file_size VARCHAR(50) COMMENT '大小(byte)',
  file_ext VARCHAR(10) COMMENT '后缀名',
  create_time VARCHAR(20) NOT NULL,
  is_deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY bus_id (bus_id),
  KEY idx_bus_ref (bus_type, bus_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件附件索引表';
```

---

## 使用说明

1. 执行 `live.sql` 创建所有表
2. 表名统一使用下划线命名法
3. 主键使用 varchar(32) 存储 nanoid
4. 时间字段使用 varchar(20) 存储时间戳字符串
5. is_deleted = 0 表示未删除
