const joi = require("joi");

// ==================== 预算管理 (budget) ====================

// 创建预算验证规则
exports.create = {
  body: joi.object({
    data: joi.object({
      title: joi.string().required().max(50).messages({
        "any.required": "预算标题不能为空",
        "string.max": "预算标题最多50个字符",
      }),
      budget_type: joi.string().valid('吃', '买', '行').required().messages({
        "any.required": "预算类型不能为空",
        "any.only": "预算类型只能是 吃/买/行",
      }),
      budget_amount: joi.number().positive().required().messages({
        "any.required": "预算金额不能为空",
        "number.positive": "预算金额必须大于0",
      }),
      cycle: joi.string().valid('月', '季', '年').required().messages({
        "any.required": "周期不能为空",
        "any.only": "周期只能是 月/季/年",
      }),
      plan_date: joi.string().pattern(/^\d{4}-\d{2}-\d{2}$/).required().messages({
        "any.required": "预计日期不能为空",
        "string.pattern.base": "预计日期格式为 yyyy-MM-dd",
      }),
      route: joi.string().allow(null, '').max(50),
      budget_details: joi.object().allow(null),
    }).unknown(true),
  }).unknown(true),
};

// 更新预算验证规则
exports.update = {
  body: joi.object({
    data: joi.object({
      title: joi.string().max(50).messages({
        "string.max": "预算标题最多50个字符",
      }),
      route: joi.string().allow(null, '').max(50).messages({
        "string.max": "路线最多50个字符",
      }),
      budget_type: joi.string().valid('吃', '买', '行').messages({
        "any.only": "预算类型只能是 吃/买/行",
      }),
      budget_amount: joi.number().positive().messages({
        "number.positive": "预算金额必须大于0",
      }),
      used_amount: joi.number().min(0).messages({
        "number.min": "已使用金额不能为负数",
      }),
      budget_details: joi.object().allow(null),
      cycle: joi.string().valid('月', '季', '年').messages({
        "any.only": "周期只能是 月/季/年",
      }),
      plan_date: joi.string().pattern(/^\d{4}-\d{2}-\d{2}$/).messages({
        "string.pattern.base": "预计日期格式为 yyyy-MM-dd",
      }),
      is_over_budget: joi.number().valid(0, 1).messages({
        "any.only": "是否超支只能是 0 或 1",
      }),
      is_excute: joi.number().valid(0, 1).messages({
        "any.only": "是否执行只能是 0 或 1",
      }),
    }).unknown(true),
  }).unknown(true),
};

// ID参数验证
exports.idParam = {
  params: joi.object({
    id: joi.string().required().messages({
      "any.required": "ID不能为空",
    }),
  }),
};
