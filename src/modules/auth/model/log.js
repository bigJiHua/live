const db = require('../../../common/config/db');
const { userId } = require('../../../common/utils/idUtils'); // 引入你之前的 Nanoid 工具

class UserLog {
  static tableName = 'user_log';

  /**
   * 记录审计日志
   * @param {Object} logData - 包含用户ID、操作类型、设备信息等
   */
  static async append(logData) {    
    const query = `
      INSERT INTO ${this.tableName} (
         user_id, type, token, login_ip, 
        login_location, login_isp, user_agent, os_info, 
        browser_info, device_model, fingerprint, viewport, 
        pixel_ratio, status, error_message, login_lang, 
        path, login_time, create_time
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `;

    const now = Date.now();
    if (!logData.user_id) {
        return console.error('传入日志用户ID 不能为空！')
    }
    const values = [
      logData.user_id || null,
      logData.type || 'login',
      logData.token ? logData.token.substring(0, 50) : null, // 只存 Token 存根，安全起见不存全称
      logData.login_ip || '0.0.0.0',
      logData.login_location || '未知',
      logData.login_isp || '未知',
      logData.user_agent || '',
      logData.os_info || '',
      logData.browser_info || '',
      logData.device_model || '',
      logData.fingerprint || '',
      logData.viewport || '',
      logData.pixel_ratio || '1',
      logData.status ?? 1, // 1成功 0失败
      logData.error_message || '',
      logData.login_lang || 'zh-CN',
      logData.path || '/',
      now,
      now
    ];

    try {
      await db.execute(query, values);
    } catch (error) {
      // 这里的错误不要 throw，只打印，防止日志写失败导致用户登不上去
      console.error("Critical: UserLog record failed!", error);
    }
  }

  /**
   * 获取用户 PIN 错误次数（统计最近错误次数）
   * @param {string} userId - 用户ID
   * @param {number} limitMinutes - 统计时间范围（分钟），默认 30 分钟
   * @returns {Promise<number>} 错误次数
   */
  static async getPinErrorCount(userId, limitMinutes = 30) {
    try {
      const query = `
        SELECT COUNT(*) as errorCount
        FROM ${this.tableName}
        WHERE user_id = ?
          AND type = 'pin'
          AND status = 0
          AND create_time > ?
      `;
      const timeThreshold = Date.now() - limitMinutes * 60 * 1000;
      const [rows] = await db.execute(query, [userId, timeThreshold.toString()]);
      return rows[0]?.errorCount || 0;
    } catch (error) {
      console.error("查询 PIN 错误次数失败:", error);
      return 0;
    }
  }
}

module.exports = UserLog;