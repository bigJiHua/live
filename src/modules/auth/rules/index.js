const joi = require("joi");

// 6 位纯数字统一规则
const pinRule = joi.string().length(6).pattern(/^\d+$/).required().messages({
  "string.length": "PIN 码必须是 6 位",
  "string.pattern.base": "PIN 码只能是纯数字",
  "any.required": "PIN 码不能为空",
});

// 登录规则（保留你原来的）
const dataSchema = joi.object({
  nameOrEmail: joi.string().min(3).max(15).required(),
  password: joi
    .string()
    .pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[^]{8,30}$/)
    .required()
    .messages({
      "string.pattern.base": "密码必须包含大小写字母和数字，且长度为8-30位",
    }),
});

// ==================== PIN 规则 ====================

// 验证 PIN
exports.verifyPin = {
  body: joi
    .object({
      data: joi.object({ pin: pinRule }),
    })
    .unknown(true),
};

// 设置 PIN
exports.setPin = {
  body: joi
    .object({
      data: joi.object({ pin: pinRule }),
    })
    .unknown(true),
};

// 修改 PIN
exports.changePin = {
  body: joi
    .object({
      data: joi.object({
        oldPin: pinRule,
        newPin: pinRule,
      }),
    })
    .unknown(true),
};

// 重置 PIN
exports.resetPin = {
  body: joi
    .object({
      data: joi.object({
        verificationCode: pinRule,
        newPin: pinRule,
      }),
    })
    .unknown(true),
};

// 登录
exports.login = {
  body: joi.object({ data: dataSchema }).unknown(true),
};
