const Joi = require("joi");

/**
 * 通用 Joi 校验器工厂函数
 * @param {Object} schemas - 包含 body, query, params 的 Joi 对象
 * @param {Object} options - 自定义校验配置
 */
const createValidator = (schemas, options = {}) => {
  // 默认配置：开启类型转换，自动剔除未定义字段
  const defaultOptions = {
    abortEarly: false, // 收集所有错误，不发现第一个就停止
    allowUnknown: true, // 允许存在未定义的字段（比如解密后的 ip, path 等）
    stripUnknown: true, // 🔥 核心：自动删除 Schema 中未定义的字段，确保数据纯净
    convert: true, // 自动进行类型转换（如字符串 "1" 转为数字 1）
  };

  const finalOptions = { ...defaultOptions, ...options };

  return (req, res, next) => {
    try {
      const targets = ["body", "query", "params"];
      let errors = [];

      // 遍历校验目标
      for (const key of targets) {
        if (!schemas[key]) continue;

        // 执行 Joi 校验
        const { error, value } = schemas[key].validate(req[key], finalOptions);

        if (error) {
          // 🛡️ 错误信息脱敏：去除引号，隐藏原始输入值
          const formatted = error.details.map((d) => ({
            field: d.path.join("."),
            // 核心安全：只给提示文字，不回显用户输入的敏感内容
            message: d.message.replace(/"/g, ""),
          }));
          errors.push(...formatted);
        }

        // ✅ 将校验/清洗后的数据覆盖回 req，保证后续业务拿到的是“干净”的数据
        req[key] = value;
      }

      // 如果有任何校验错误
      if (errors.length > 0) {
        return res.say(errors[0].message, 400);
        // return res.status(400).json({
        //   status: 400,
        //   // 返回第一条错误作为主提示
        //   message: errors[0].message,
        //   // 开发调试时可以看这个数组，上线后可选择性隐藏
        //   //   errors: process.env.NODE_ENV === 'development' ? errors : undefined
        // });
      }

      // 校验通过，放行
      next();
    } catch (err) {
      console.error("Validator Middleware Crash:", err);
      return res.say("内部校验逻辑异常", 500);
    }
  };
};

module.exports = createValidator;
