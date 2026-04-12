const Joi = require("joi");

/**
 * 通用 Joi 校验器工厂函数
 */
const createValidator = (schemas, options = {}) => {
  const defaultOptions = {
    abortEarly: false,
    allowUnknown: true,
    stripUnknown: true,
    convert: true,
  };

  const finalOptions = { ...defaultOptions, ...options };

  return (req, res, next) => {
    try {
      const targets = ["body", "query", "params"];
      let errors = [];

      for (const key of targets) {
        if (!schemas[key]) continue;

        const { error, value } = schemas[key].validate(req[key], finalOptions);

        if (error) {
          const formatted = error.details.map((d) => ({
            field: d.path.join("."),
            message: d.message.replace(/"/g, ""),
          }));
          errors.push(...formatted);
        }

        req[key] = value;
      }

      if (errors.length > 0) {
        return res.say(errors[0].message, 400);
      }

      next();
    } catch (err) {
      console.error("Validator Middleware Crash:", err);
      return res.say("内部校验逻辑异常", 500);
    }
  };
};

module.exports = createValidator;
