/**
 * 邮件发送工具类
 */
const nodemailer = require('nodemailer');

// 创建邮件发送器
let transporter = null;

/**
 * 初始化邮件发送器
 */
function initTransporter() {
  if (transporter) {
    return transporter;
  }

  transporter = nodemailer.createTransport({
    host: process.env.EMAIL_HOST || 'smtp.gmail.com',
    port: process.env.EMAIL_PORT || 587,
    secure: false, // true for 465, false for other ports
    auth: {
      user: process.env.EMAIL_USER,
      pass: process.env.EMAIL_PASS,
    },
  });

  return transporter;
}

/**
 * 发送验证邮件
 * @param {string} email - 收件人邮箱
 * @param {string} code - 验证码
 * @returns {Promise<boolean>} 是否发送成功
 */
async function sendVerificationEmail(email, code) {
  try {
    const transporter = initTransporter();

    const mailOptions = {
      from: `"${process.env.APP_NAME || '生活管理'}" <${process.env.EMAIL_USER}>`,
      to: email,
      subject: '验证您的邮箱',
      html: `
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
          <h2 style="color: #333;">邮箱验证</h2>
          <p>您好！</p>
          <p>您的验证码是：</p>
          <div style="background: #f5f5f5; padding: 15px; text-align: center; font-size: 24px; font-weight: bold; margin: 20px 0; border-radius: 5px;">
            ${code}
          </div>
          <p>验证码有效期为 10 分钟，请尽快使用。</p>
          <p style="color: #999; font-size: 12px;">如果这不是您的操作，请忽略此邮件。</p>
        </div>
      `,
    };

    await transporter.sendMail(mailOptions);
    return true;
  } catch (error) {
    console.error('发送验证邮件失败:', error);
    throw error;
  }
}

/**
 * 发送重置密码邮件
 * @param {string} email - 收件人邮箱
 * @param {string} resetToken - 重置令牌
 * @returns {Promise<boolean>} 是否发送成功
 */
async function sendResetPasswordEmail(email, resetToken) {
  try {
    const transporter = initTransporter();

    const resetUrl = `${process.env.FRONTEND_URL || 'http://localhost:3000'}/reset-password?token=${resetToken}`;

    const mailOptions = {
      from: `"${process.env.APP_NAME || '生活管理'}" <${process.env.EMAIL_USER}>`,
      to: email,
      subject: '重置您的密码',
      html: `
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
          <h2 style="color: #333;">重置密码</h2>
          <p>您好！</p>
          <p>我们收到了您的密码重置请求。点击下面的按钮重置您的密码：</p>
          <div style="margin: 20px 0; text-align: center;">
            <a href="${resetUrl}" style="display: inline-block; padding: 12px 24px; background-color: #007bff; color: white; text-decoration: none; border-radius: 5px;">
              重置密码
            </a>
          </div>
          <p>或者复制以下链接到浏览器：</p>
          <p style="word-break: break-all; color: #666;">${resetUrl}</p>
          <p style="color: #999; font-size: 12px;">链接有效期为 1 小时，请尽快使用。</p>
          <p style="color: #999; font-size: 12px;">如果这不是您的操作，请忽略此邮件。</p>
        </div>
      `,
    };

    await transporter.sendMail(mailOptions);
    return true;
  } catch (error) {
    console.error('发送重置密码邮件失败:', error);
    throw error;
  }
}

/**
 * 发送欢迎邮件
 * @param {string} email - 收件人邮箱
 * @param {string} username - 用户名
 * @returns {Promise<boolean>} 是否发送成功
 */
async function sendWelcomeEmail(email, username) {
  try {
    const transporter = initTransporter();

    const mailOptions = {
      from: `"${process.env.APP_NAME || '生活管理'}" <${process.env.EMAIL_USER}>`,
      to: email,
      subject: '欢迎注册生活管理系统',
      html: `
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
          <h2 style="color: #333;">欢迎加入！</h2>
          <p>您好，${username}！</p>
          <p>感谢您注册生活管理系统。我们很高兴您能成为我们的一员。</p>
          <p>现在您可以开始使用我们的服务来管理您的财务和生活了。</p>
          <div style="margin: 20px 0; text-align: center;">
            <a href="${process.env.FRONTEND_URL || 'http://localhost:3000'}" style="display: inline-block; padding: 12px 24px; background-color: #28a745; color: white; text-decoration: none; border-radius: 5px;">
              开始使用
            </a>
          </div>
          <p style="color: #999; font-size: 12px;">如果您有任何问题，请随时联系我们。</p>
        </div>
      `,
    };

    await transporter.sendMail(mailOptions);
    return true;
  } catch (error) {
    console.error('发送欢迎邮件失败:', error);
    throw error;
  }
}

module.exports = {
  sendVerificationEmail,
  sendResetPasswordEmail,
  sendWelcomeEmail,
};
