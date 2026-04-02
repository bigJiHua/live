1. 用户信息表
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='用户信息表';
```
2. 卡片基础信息表
```sql
CREATE TABLE card_base (
  id INT NOT NULL COMMENT '卡片ID',
  user_id INT DEFAULT 1 COMMENT '用户ID',
  bank_id INT COMMENT '银行ID',
  card_type VARCHAR(20) COMMENT '卡类型 信用卡/借记卡',
  card_level VARCHAR(20) COMMENT '卡等级',
  main_sub VARCHAR(10) COMMENT '主副卡',
  card_org VARCHAR(20) COMMENT '卡组织',
  last4_no VARCHAR(10) COMMENT '卡号后四位',
  mask_no VARCHAR(50) COMMENT '卡号脱敏',
  alias VARCHAR(50) COMMENT '卡片别名',
  card_img VARCHAR(255) COMMENT '卡面URL',
  open_date VARCHAR(20) COMMENT '下卡日期',
  expire_date VARCHAR(20) COMMENT '过期日期',
  bill_day INT COMMENT '账单日',
  repay_day INT COMMENT '还款日',
  currency VARCHAR(10) COMMENT '币种',
  status VARCHAR(20) COMMENT '卡片状态',
  is_default TINYINT COMMENT '是否默认卡',
  is_hide TINYINT COMMENT '是否隐藏',
  sort INT DEFAULT 99 COMMENT '排序',
  tag VARCHAR(50) COMMENT '标签',
  remark VARCHAR(255) COMMENT '备注',
  annual_fee DECIMAL(12,2) COMMENT '年费',
  fee_free_rule VARCHAR(255) COMMENT '免年费条件',
  source_from VARCHAR(20) COMMENT '数据来源 手动/导入',
  create_time VARCHAR(20) COMMENT '创建时间',
  update_time VARCHAR(20) COMMENT '更新时间',
  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (id),
  INDEX idx_user_id (user_id),
  INDEX idx_user_hide (user_id, is_hide)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='卡片基础信息表';
```
3. 卡片额度 & 账单表（补：账单周期）
```sql
CREATE TABLE card_bill (
  id INT NOT NULL COMMENT '账单ID',
  card_id INT COMMENT '关联卡片ID',
  user_id INT DEFAULT 1 COMMENT '用户ID',
  credit_limit DECIMAL(12,2) COMMENT '信用额度',
  avail_limit DECIMAL(12,2) COMMENT '可用额度',
  used_limit DECIMAL(12,2) COMMENT '已用额度',
  temp_limit DECIMAL(12,2) COMMENT '临时额度',
  bill_start_date VARCHAR(20) COMMENT '账单周期开始',
  bill_end_date VARCHAR(20) COMMENT '账单周期结束',
  bill_amount DECIMAL(12,2) COMMENT '本期账单',
  min_repay DECIMAL(12,2) COMMENT '最低还款',
  repaid DECIMAL(12,2) COMMENT '已还金额',
  need_repay DECIMAL(12,2) COMMENT '待还金额',
  points INT COMMENT '积分',
  points_expire VARCHAR(20) COMMENT '积分到期日',
  repay_status VARCHAR(20) COMMENT '还款状态',
  is_overdue TINYINT COMMENT '是否逾期',
  overdue_days INT COMMENT '逾期天数',
  remind_switch TINYINT COMMENT '提醒开关',
  remind_days INT COMMENT '提醒天数',
  update_time VARCHAR(20) COMMENT '更新时间',
  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (id),
  INDEX idx_user_card (user_id, card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='卡片额度账单表';
```
4. 卡片操作日志表（补：操作类型）
```sql
CREATE TABLE card_log (
  id INT NOT NULL COMMENT '日志ID',
  card_id INT COMMENT '卡片ID',
  user_id INT DEFAULT 1 COMMENT '用户ID',
  operate_type VARCHAR(20) COMMENT '操作类型 新增/编辑/删除/还款',
  operate_time VARCHAR(20) COMMENT '操作时间',
  operate_ip VARCHAR(50) COMMENT '操作IP',
  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='卡片操作日志';
```
5. 还款记录表（财务核心必加）
```sql
CREATE TABLE card_repay (
  id INT NOT NULL COMMENT '还款记录ID',
  card_id INT COMMENT '卡片ID',
  user_id INT DEFAULT 1 COMMENT '用户ID',
  bill_id INT COMMENT '关联账单ID',
  repay_amount DECIMAL(12,2) COMMENT '还款金额',
  repay_method VARCHAR(20) COMMENT '还款方式',
  repay_time VARCHAR(20) COMMENT '还款时间',
  remark VARCHAR(255) COMMENT '备注',
  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (id),
  INDEX idx_user_card (user_id, card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='信用卡还款记录表';
```
6. 理财基金表
```sql
CREATE TABLE fund (
  id INT NOT NULL COMMENT '理财ID',
  user_id INT DEFAULT 1 COMMENT '用户ID',
  fund_name VARCHAR(100) COMMENT '基金名称',
  share DECIMAL(12,2) COMMENT '持有份额',
  fund_account VARCHAR(10) COMMENT '基金账户尾6位',
  trade_account VARCHAR(15) COMMENT '交易账户尾10位',
  sell_org VARCHAR(50) COMMENT '销售机构',
  fund_company VARCHAR(50) COMMENT '基金管理人',
  buy_date VARCHAR(20) COMMENT '购买日期',
  last_report_date VARCHAR(20) COMMENT '最后上报日期',
  net_value DECIMAL(10,4) COMMENT '当前净值',
  market_val DECIMAL(12,2) COMMENT '持仓市值',
  rate VARCHAR(20) COMMENT '收益率',
  create_time VARCHAR(20) COMMENT '写入时间',
  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (id),
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='理财基金表';
```
7. 待办事项表
```sql
CREATE TABLE todo (
  id INT NOT NULL COMMENT '待办ID',
  user_id INT DEFAULT 1 COMMENT '用户ID',
  content VARCHAR(255) COMMENT '事件内容',
  happen_date VARCHAR(20) COMMENT '发生日期',
  create_time VARCHAR(20) COMMENT '提交日期',
  status VARCHAR(20) COMMENT '状态 待完成/已完成/逾期',
  need_remind TINYINT COMMENT '是否提醒',
  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (id),
  INDEX idx_user_date (user_id, happen_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='待办事项表';
```
8. 记账明细表（补：分类 ID、汇率、方向）
```sql
CREATE TABLE account (
  id INT NOT NULL COMMENT '账单ID',
  user_id INT DEFAULT 1 COMMENT '用户ID',
  in_out_type VARCHAR(20) COMMENT '收入/支出',
  direction TINYINT COMMENT '1收入 0支出',
  category_id INT COMMENT '分类ID',
  pay_type VARCHAR(50) COMMENT '支出类型',
  pay_method VARCHAR(50) COMMENT '支付方式',
  amount DECIMAL(12,2) COMMENT '金额',
  currency VARCHAR(10) COMMENT '币种',
  exchange_rate DECIMAL(10,4) COMMENT '汇率',
  trans_date VARCHAR(20) COMMENT '收支日期',
  remark VARCHAR(255) COMMENT '备注',
  card_id INT COMMENT '关联卡片ID',
  create_time VARCHAR(20) COMMENT '提交时间',
  update_time VARCHAR(20) COMMENT '修改时间',
  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (id),
  INDEX idx_user_date (user_id, trans_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='记账明细表';
```
9. 资产结构表
```sql
CREATE TABLE asset (
  id INT NOT NULL COMMENT '资产ID',
  user_id INT DEFAULT 1 COMMENT '用户ID',
  name VARCHAR(100) COMMENT '项目名称',
  amount DECIMAL(12,2) COMMENT '数额',
  type VARCHAR(20) COMMENT '资产/欠款',
  create_time VARCHAR(20) COMMENT '登记日期',
  update_time VARCHAR(20) COMMENT '修改日期',
  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (id),
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='资产结构表';
```
10. 预算控制表（补：是否超支）
```sql
CREATE TABLE budget (
  id INT NOT NULL COMMENT '预算ID',
  user_id INT DEFAULT 1 COMMENT '用户ID',
  budget_type VARCHAR(20) COMMENT '预算类型 吃/买/行',
  budget_amount DECIMAL(12,2) COMMENT '预算金额',
  used_amount DECIMAL(12,2) DEFAULT 0.00 COMMENT '已使用',
  currency VARCHAR(10) COMMENT '币种',
  cycle VARCHAR(20) COMMENT '周期 月/季/年',
  plan_date VARCHAR(20) COMMENT '预计日期',
  is_over_budget TINYINT DEFAULT 0 COMMENT '是否超支',
  create_time VARCHAR(20) COMMENT '创建时间',
  update_time VARCHAR(20) COMMENT '修改时间',
  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (id),
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='预算控制表';
```
11. 固定资产表（补：折旧方法）
```sql
CREATE TABLE fixed_asset (
  id INT NOT NULL COMMENT '固定资产ID',
  user_id INT DEFAULT 1 COMMENT '用户ID',
  info VARCHAR(255) COMMENT '资产信息',
  tag VARCHAR(50) COMMENT '品类标签',
  img_url VARCHAR(255) COMMENT '图片',
  buy_price DECIMAL(12,2) COMMENT '购买价',
  now_val DECIMAL(12,2) COMMENT '当前估值',
  use_year INT COMMENT '使用年限',
  deprec_method VARCHAR(20) COMMENT '折旧方法 直线/加速',
  month_deprec DECIMAL(12,2) COMMENT '月折旧',
  total_deprec DECIMAL(12,2) COMMENT '累计折旧',
  status VARCHAR(20) COMMENT '状态',
  buy_date VARCHAR(20) COMMENT '购买日期',
  scrap_date VARCHAR(20) COMMENT '报废日期',
  residual_val DECIMAL(12,2) COMMENT '残值',
  create_time VARCHAR(20) COMMENT '登记时间',
  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='固定资产表';
```
12. 工作成本核算表
```sql
CREATE TABLE work_salary (
  id INT NOT NULL COMMENT '记录ID',
  user_id INT DEFAULT 1 COMMENT '用户ID',
  work_date VARCHAR(20) COMMENT '日期',
  income DECIMAL(12,2) COMMENT '收入',
  outcome DECIMAL(12,2) COMMENT '支出',
  day_salary DECIMAL(12,2) COMMENT '日薪',
  subsidy DECIMAL(12,2) COMMENT '补贴',
  cut DECIMAL(12,2) COMMENT '扣款',
  social_security DECIMAL(12,2) COMMENT '社保公积金',
  tax DECIMAL(12,2) COMMENT '个税',
  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (id),
  INDEX idx_user_date (user_id, work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='工作薪酬核算';
```
13. 动态发布表（补：可见范围）
```sql
CREATE TABLE moment (
  id INT NOT NULL COMMENT '动态ID',
  user_id INT DEFAULT 1 COMMENT '用户ID',
  content TEXT COMMENT '内容',
  img_url VARCHAR(255) COMMENT '图片',
  mood VARCHAR(50) COMMENT '心情',
  location VARCHAR(255) COMMENT '位置',
  visible_type TINYINT DEFAULT 0 COMMENT '0仅自己可见',
  parent_id INT DEFAULT 0 COMMENT '父ID 单日聚合',
  create_time VARCHAR(20) COMMENT '发布时间',
  update_time VARCHAR(20) COMMENT '修改时间',
  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (id),
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='动态发布表';
```
14. 全局配置表
```sql
CREATE TABLE app_config (
  id INT NOT NULL COMMENT '配置ID',
  user_id INT DEFAULT 1 COMMENT '用户ID',
  big_amount INT DEFAULT 500 COMMENT '大额流水阈值',
  currency VARCHAR(10) DEFAULT 'CNY' COMMENT '默认币种',
  date_format VARCHAR(20) DEFAULT 'YYYY-MM-DD' COMMENT '日期格式',
  is_deleted TINYINT DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='全局配置表';
```
15. 追加
```sql
-- 1. 分类字典表 (奠定统计维度)
CREATE TABLE IF NOT EXISTS `bus_category` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT DEFAULT 1 COMMENT '用户ID',
  `parent_id` INT DEFAULT 0 COMMENT '父分类ID(0为一级分类)',
  `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
  `type` VARCHAR(20) COMMENT '类型: income/expense/asset/fixed',
  `icon_name` VARCHAR(50) COMMENT 'Vant图标名称',
  `color` VARCHAR(20) DEFAULT '#1989fa' COMMENT '图标颜色',
  `sort` INT DEFAULT 99 COMMENT '排序',
  `is_deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_user_type` (`user_id`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类字典表';

-- 2. 理财净值历史表 (奠定收益曲线)
CREATE TABLE IF NOT EXISTS `bus_fund_history` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `fund_id` INT NOT NULL COMMENT '关联理财ID',
  `net_value` DECIMAL(10,4) NOT NULL COMMENT '当日净值',
  `market_val` DECIMAL(12,2) COMMENT '当日持仓市值',
  `record_date` VARCHAR(20) NOT NULL COMMENT '记录日期(YYYY-MM-DD)',
  `create_time` VARCHAR(20) NOT NULL COMMENT '写入时间',
  PRIMARY KEY (`id`),
  INDEX `idx_fund_date` (`fund_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='理财净值历史表';

-- 3. 周期性计划任务表 (奠定自动化逻辑)
CREATE TABLE IF NOT EXISTS `bus_recurring` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT DEFAULT 1,
  `name` VARCHAR(100) NOT NULL COMMENT '任务名称(如:房租/服务器续费)',
  `amount` DECIMAL(12,2) NOT NULL,
  `category_id` INT COMMENT '关联分类',
  `account_id` INT COMMENT '默认扣款账户/卡片',
  `cycle` VARCHAR(20) COMMENT '周期: month/year/week',
  `day_of_cycle` INT COMMENT '周期内的哪一天',
  `next_date` VARCHAR(20) COMMENT '下次触发日期',
  `is_active` TINYINT DEFAULT 1 COMMENT '是否启用',
  `create_time` VARCHAR(20) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='周期性计划任务表';

-- 4. 文件附件索引表 (奠定资源管理)
CREATE TABLE IF NOT EXISTS `sys_attachment` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT DEFAULT 1,
  `bus_id` INT COMMENT '关联业务ID(如moment_id/fixed_asset_id)',
  `bus_type` VARCHAR(50) COMMENT '业务类型: avatar/moment/asset_img',
  `file_name` VARCHAR(255) COMMENT '原始文件名',
  `file_path` VARCHAR(255) NOT NULL COMMENT '存储路径',
  `file_size` INT COMMENT '大小(byte)',
  `file_ext` VARCHAR(10) COMMENT '后缀名',
  `create_time` VARCHAR(20) NOT NULL,
  `is_deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_bus_ref` (`bus_type`, `bus_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件附件索引表';
```