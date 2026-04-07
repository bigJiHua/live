const joi = require("joi");

// 类型枚举：income/expense/asset/fixed/bank
const validTypes = ["income", "expense", "asset", "fixed", "bank"];

// 创建分类验证规则
exports.create = {
  body: joi.object({
    data: joi.object({
      name: joi.string().required().messages({
        "any.required": "name 分类名称 不能为空",
      }),
      type: joi.string().valid(...validTypes).required().messages({
        "any.only": `type 只能是 ${validTypes.join('/')}`,
        "any.required": "type 类型 不能为空",
      }),
      iconUrl: joi.string().allow(null, ""),
      remark: joi.string().allow(null, ""),
    }).unknown(true),
  }).unknown(true),
};

// 更新分类验证规则（非必填）
exports.update = {
  body: joi.object({
    data: joi.object({
      name: joi.string(),
      type: joi.string().valid(...validTypes),
      iconUrl: joi.string().allow(null, ""),
      remark: joi.string().allow(null, ""),
    }).unknown(true),
  }).unknown(true),
};
