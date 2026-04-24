const joi = require("joi");

// ==================== 账务记录 (account) ====================

// 创建账务记录验证规则
exports.create = {
  body: joi.object({
    data: joi.object({
      // ========== 【必填字段】NOT NULL 无默认值 ==========
      direction: joi.number().valid(0, 1).required().messages({
        "any.required": "direction 收支方向 不能为空",
        "any.only": "direction 只能是 0(支出) 或 1(收入)",
      }),
      categoryId: joi.string().required().messages({
        "any.required": "categoryId 分类ID 不能为空",
      }),
      payType: joi.string().required().messages({
        "any.required": "payType 支出类型 不能为空",
      }),
      payMethod: joi.string().required().messages({
        "any.required": "payMethod 支付方式 不能为空",
      }),
      amount: joi.number().required().messages({
        "any.required": "amount 金额 不能为空",
      }),
      transDate: joi.string().required().messages({
        "any.required": "transDate 收支日期 不能为空",
      }),
      cardId: joi.string().required().messages({
        "any.required": "cardId 关联卡片ID 不能为空",
      }),
      // ========== 【非必填字段】有默认值 ==========
      currency: joi.string().default('CNY'),
      exchangeRate: joi.number().min(0.001).precision(5).default(1),
      remark: joi.string().allow('', null),
    }).unknown(true),
  }).unknown(true),
};

// 更新账务记录验证规则（非必填）
exports.update = {
  body: joi.object({
    data: joi.object({
      direction: joi.number().valid(0, 1),
      categoryId: joi.string().allow('', null),
      payType: joi.string().allow('', null),
      payMethod: joi.string().allow('', null),
      amount: joi.number().allow(null),
      currency: joi.string().allow('', null),
      exchangeRate: joi.number().min(0.001).precision(5).allow(null),
      transDate: joi.string().allow('', null),
      remark: joi.string().allow('', null),
      cardId: joi.string().allow('', null),
    }).unknown(true),
  }).unknown(true),
};
