const joi = require("joi");

// ==================== 卡片基础 (card_base) ====================

// 创建卡片验证规则 - 根据 cardType 动态判断必填字段
exports.createCard = {
  body: joi.object({
    data: joi.object({
      // ========== 【通用必填字段】 ==========
      bankId: joi.string().required().messages({
        "any.required": "bankId 银行ID 不能为空",
      }),
      cardType: joi.string().valid("debit", "credit").required().messages({
        "any.only": "cardType 只能是 debit(借记卡) 或 credit(信用卡)",
        "any.required": "cardType 卡类型 不能为空",
      }),
      last4No: joi.string().required().messages({
        "any.required": "last4No 卡号后四位 不能为空",
      }),
      cardBin: joi.string().length(6).required().messages({
        "string.length": "cardBin 卡BIN前6位必须是6位数",
        "any.required": "cardBin 卡BIN前6位 不能为空",
      }),
      openDate: joi.string().required().messages({
        "any.required": "openDate 开卡日期 不能为空",
      }),
      expireDate: joi.string().required().messages({
        "any.required": "expireDate 过期日期 不能为空",
      }),
      sourceFrom: joi.string().required().messages({
        "any.required": "sourceFrom 数据来源 不能为空",
      }),
      // ========== 【信用卡必填字段】 ==========
      creditLimit: joi.number().when('cardType', {
        is: 'credit',
        then: joi.number().required().min(0).messages({
          "any.required": "creditLimit 信用额度（信用卡必填）不能为空",
          "number.min": "信用额度不能为负数",
        }),
        otherwise: joi.number().default(0),
      }),
      tempLimit: joi.number().default(0),
      billDay: joi.number().when('cardType', {
        is: 'credit',
        then: joi.number().required().messages({
          "any.required": "billDay 账单日（信用卡必填）不能为空",
        }),
        otherwise: joi.number().default(0),
      }),
      repayDay: joi.number().when('cardType', {
        is: 'credit',
        then: joi.number().required().messages({
          "any.required": "repayDay 还款日（信用卡必填）不能为空",
        }),
        otherwise: joi.number().default(0),
      }),
      annualFee: joi.number().when('cardType', {
        is: 'credit',
        then: joi.number().required().messages({
          "any.required": "annualFee 年费（信用卡必填）不能为空",
        }),
        otherwise: joi.number().default(0),
      }),
      feeFreeRule: joi.string().when('cardType', {
        is: 'credit',
        then: joi.string().required().messages({
          "any.required": "feeFreeRule 免年费规则（信用卡必填）不能为空",
        }),
        otherwise: joi.string().allow('', null).default(''),
      }),
      // ========== 【通用非必填字段】 ==========
      cardLevel: joi.string().default('普卡'),
      mainSub: joi.string().default('主卡'),
      cardOrg: joi.string().default('银联'),
      cardLength: joi.string().default('19'),
      alias: joi.string().allow('', null).default(''),
      cardImg: joi.string().allow('', null).default(''),
      currency: joi.string().default('CNY'),
      status: joi.string().default('正常'),
      isDefault: joi.boolean().default(false),
      isHide: joi.boolean().default(false),
      sort: joi.number().default(99),
      tag: joi.string().allow('', null).default(''),
      remark: joi.string().allow('', null).default(''),
      color: joi.string().default('#0052cc'),
    }).unknown(true),
  }).unknown(true),
};

// 更新卡片验证规则（非必填）
exports.updateCard = {
  body: joi.object({
    data: joi.object({
      bankId: joi.string().allow('', null),
      cardType: joi.string().valid("debit", "credit"),
      cardLevel: joi.string().allow('', null),
      mainSub: joi.string().allow('', null),
      cardOrg: joi.string().allow('', null),
      cardLength: joi.string().allow('', null),
      last4No: joi.string().allow('', null),
      cardBin: joi.string().allow('', null),
      alias: joi.string().allow('', null),
      cardImg: joi.string().allow('', null),
      openDate: joi.string().allow('', null),
      expireDate: joi.string().allow('', null),
      billDay: joi.number().allow(null),
      repayDay: joi.number().allow(null),
      currency: joi.string().allow('', null),
      status: joi.string().allow('', null),
      isDefault: joi.boolean(),
      isHide: joi.boolean(),
      sort: joi.number(),
      tag: joi.string().allow('', null),
      remark: joi.string().allow('', null),
      color: joi.string().allow('', null),
      annualFee: joi.number().allow(null),
      feeFreeRule: joi.string().allow('', null),
      sourceFrom: joi.string().allow('', null),
    }).unknown(true),
  }).unknown(true),
};

// ==================== 卡片账单 (card_bill) ====================

// 创建账单验证规则
exports.createBill = {
  body: joi.object({
    data: joi.object({
      // 【前端必填】
      cardId: joi.string().required().messages({
        "any.required": "cardId 卡片ID 不能为空",
      }),
      creditLimit: joi.number().required().messages({
        "any.required": "creditLimit 信用额度 不能为空",
      }),
      tempLimit: joi.number().default(0),
      pointsRate: joi.number().default(1),
      // 【可选】
      billMonth: joi.string().pattern(/^\d{4}-\d{2}$/).messages({
        "string.pattern.base": "billMonth 格式应为 YYYY-MM",
      }),
      // 【后端计算/默认值】
      remindSwitch: joi.boolean().default(true),
      remindDays: joi.number().default(3),
    }).unknown(true),
  }).unknown(true),
};

// 更新账单验证规则
exports.updateBill = {
  body: joi.object({
    data: joi.object({
      creditLimit: joi.number(),
      tempLimit: joi.number(),
      pointsRate: joi.number(),
      repaid: joi.number(),
      remindSwitch: joi.boolean(),
      remindDays: joi.number(),
    }).unknown(true),
  }).unknown(true),
};

// ==================== 还款记录 (card_repay) ====================

// 创建还款记录验证规则
exports.createRepay = {
  body: joi.object({
    data: joi.object({
      cardId: joi.string().required().messages({
        "any.required": "cardId 卡片ID 不能为空",
      }),
      billId: joi.string().required().messages({
        "any.required": "billId 账单ID 不能为空",
      }),
      repayAmount: joi.number().required().min(0.01).messages({
        "any.required": "repayAmount 还款金额 不能为空",
        "number.min": "还款金额必须大于 0",
      }),
      repayMethod: joi.string().required().valid('card', 'balance', 'bank_card', 'cash').messages({
        "any.required": "repayMethod 还款方式 不能为空",
        "any.only": "repayMethod 只支持: card/balance/bank_card/cash",
      }),
      repayMethodCardId: joi.string().when('repayMethod', {
        is: 'bank_card',
        then: joi.string().required().messages({
          "any.required": "银行卡还款时 repayMethodCardId 不能为空",
        }),
        otherwise: joi.string().allow('').optional(),
      }),
      repayTime: joi.string().required().pattern(/^\d{4}-\d{2}-\d{2}$/).messages({
        "any.required": "repayTime 还款时间 不能为空",
        "string.pattern.base": "repayTime 格式应为 YYYY-MM-DD",
      }),
      remark: joi.string().allow('').optional(),
    }).unknown(true),
  }).unknown(true),
};

// 更新还款记录验证规则
exports.updateRepay = {
  body: joi.object({
    data: joi.object({
      repayAmount: joi.number(),
      repayMethod: joi.string(),
      repayTime: joi.string(),
      remark: joi.string(),
    }).unknown(true),
  }).unknown(true),
};

