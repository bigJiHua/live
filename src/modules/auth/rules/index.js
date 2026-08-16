const joi = require("joi");

// 6 位纯数字统一规则
const pinRule = joi.string().length(6).pattern(/^\d+$/).required().messages({
  "string.length": "PIN 码必须是 6 位",
  "string.pattern.base": "PIN 码只能是纯数字",
  "any.required": "PIN 码不能为空",
});

// PIN 兼容 RSA 密文数组：安全键盘 secureOnly 模式提交 [密文1, 密文2...]（长度=6），
// controller 层用私钥解密还原 6 位明文后再做格式/校验。纯数组不做 6 位校验（每项是长密文）。
const pinEncryptedRule = joi
  .array()
  .items(joi.string().min(8).max(1024))
  .length(6)
  .messages({
    "array.length": "PIN 码必须是 6 位",
    "string.min": "PIN 密文格式异常",
  });

const pinAnyRule = joi
  .alternatives()
  .try(pinRule, pinEncryptedRule)
  .required()
  .messages({
    "any.required": "PIN 码不能为空",
    "alternatives.match": "PIN 码格式不正确",
  });

// 登录规则（保留你原来的）
// password 兼容：普通字符串（明文/AES 整包）或 RSA 密文字符数组（安全键盘 secureOnly）
const plainPwdRule = joi
  .string()
  .pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*(),.?":{}|<>])[^]{6,30}$/)
  .messages({
    "string.pattern.base": "密码必须包含大小写字母、数字和特殊字符，且长度为6-30位",
  });
const encryptedPwdRule = joi
  .array()
  .items(joi.string().min(8).max(1024))
  .min(6)
  .max(30)
  .messages({
    "array.min": "密码长度必须为 6-30 位",
    "array.max": "密码长度必须为 6-30 位",
  });
const dataSchema = joi.object({
  nameOrEmail: joi.string().min(3).max(50).required(),
  password: joi.alternatives().try(plainPwdRule, encryptedPwdRule).required().messages({
    "any.required": "密码不能为空",
    "alternatives.match": "密码格式不正确",
  }),
});

// ==================== PIN 规则 ====================

// 验证 PIN
exports.verifyPin = {
  body: joi
    .object({
      data: joi.object({ pin: pinAnyRule }),
    })
    .unknown(true),
};

// 风险路由 PIN 验证
exports.verifyRoutePin = {
  body: joi
    .object({
      data: joi.object({
        pin: pinAnyRule,
        challengeId: joi.alternatives(joi.number(), joi.string()).required(),
        requestUrl: joi.string().max(255).required(),
        method: joi.string().max(16).required(),
      }),
    })
    .unknown(true),
};

// 设置 PIN
exports.setPin = {
  body: joi
    .object({
      data: joi.object({ pin: pinAnyRule }),
    })
    .unknown(true),
};

// 修改 PIN
exports.changePin = {
  body: joi
    .object({
      data: joi.object({
        oldPin: pinAnyRule,
        newPin: pinAnyRule,
      }),
    })
    .unknown(true),
};

// 重置 PIN（verificationCode 为邮箱验证码，不加密）
exports.resetPin = {
  body: joi
    .object({
      data: joi.object({
        verificationCode: pinRule,
        newPin: pinAnyRule,
      }),
    })
    .unknown(true),
};

// 登录
exports.login = {
  body: joi.object({ data: dataSchema }).unknown(true),
};
