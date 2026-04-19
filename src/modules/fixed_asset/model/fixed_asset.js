const db = require("../../../common/config/db");
const idUtils = require("../../../common/utils/idUtils");

/**
 * 固定资产模型（老库兼容版）
 * 
 * 折旧计算逻辑：
 * - use_months = use_years × 12
 * - residual_val = buy_price × residual_rate / 100
 * - month_deprec = (buy_price - residual_val) / use_months
 * - 当前账面价值 = buy_price - total_deprec
 * - 当前价值不低于残值
 * 
 * 日期字段均为 varchar(20)，不依赖数据库原生日期函数
 */
class FixedAsset {
  static tableName = 'fixed_asset';

  /**
   * 计算两个日期之间的月份差
   * @param {string} date1 - 日期字符串 yyyy-MM-dd
   * @param {string} date2 - 日期字符串 yyyy-MM-dd
   */
  static diffMonths(date1, date2) {
    const d1 = new Date(date1);
    const d2 = new Date(date2);
    return (d2.getFullYear() - d1.getFullYear()) * 12 + (d2.getMonth() - d1.getMonth());
  }

  /**
   * 获取当前日期字符串
   */
  static getCurrentDate() {
    return new Date().toISOString().split('T')[0];
  }

  /**
   * 创建固定资产（初始化计算）
   */
  static async create({ userId, info, tag, imgUrl, buyPrice, secondhandPrice, 
    useYears, residualRate, buyDate, status }) {
    
    if (!info) throw new Error('资产名称不能为空');
    if (!tag) throw new Error('品类不能为空');
    if (!imgUrl) throw new Error('图片不能为空');
    if (!buyPrice || parseFloat(buyPrice) <= 0) throw new Error('购买价必须大于0');
    if (!useYears || parseFloat(useYears) <= 0) throw new Error('预计使用年限必须大于0');
    if (!residualRate || parseFloat(residualRate) < 0 || parseFloat(residualRate) > 100) {
      throw new Error('残值率必须在0~100之间');
    }
    if (!buyDate) throw new Error('购买日期不能为空');

    const buyPriceNum = parseFloat(buyPrice);
    const useYearsNum = parseFloat(useYears);
    const residualRateNum = parseFloat(residualRate);

    // 计算字段
    const useMonths = Math.round(useYearsNum * 12);  // 预计使用月数
    const residualVal = Math.round(buyPriceNum * residualRateNum / 100 * 100) / 100;  // 残值
    const totalDepreciable = buyPriceNum - residualVal;  // 总可折旧额度
    const monthDeprec = totalDepreciable > 0 && useMonths > 0 
      ? Math.round(totalDepreciable / useMonths * 100) / 100 
      : 0;  // 月折旧额
    const createTime = this.getCurrentDate();

    const id = idUtils.billId();

    const query = `
      INSERT INTO ${this.tableName} 
      (id, user_id, info, tag, img_url, buy_price, now_val, secondhand_price,
       use_years, use_months, residual_rate, residual_val,
       deprec_method, month_deprec, total_deprec,
       buy_date, last_deprec_date, scrap_date,
       create_time, deprec_finished, status, is_deleted)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
    `;

    await db.execute(query, [
      id, userId, info, tag, imgUrl, buyPrice, buyPrice, secondhandPrice || null,
      useYears, useMonths, residualRate, residualVal,
      'straight', monthDeprec, 0,
      buyDate, buyDate, null,
      createTime, 0, status || 'using'
    ]);

    return this.findById(id, userId);
  }

  /**
   * 根据ID查询
   */
  static async findById(id, userId) {
    const [rows] = await db.execute(
      `SELECT * FROM ${this.tableName} WHERE id = ? AND user_id = ? AND is_deleted = 0`,
      [id, userId]
    );
    return rows[0] || null;
  }

  /**
   * 获取用户所有固定资产
   */
  static async findAll(userId) {
    const [rows] = await db.execute(
      `SELECT * FROM ${this.tableName} WHERE user_id = ? AND is_deleted = 0 ORDER BY create_time DESC`,
      [userId]
    );
    return rows;
  }

  /**
   * 获取已删除的资产（回收站）
   */
  static async findDeleted(userId) {
    const [rows] = await db.execute(
      `SELECT * FROM ${this.tableName} WHERE user_id = ? AND is_deleted = 1 ORDER BY create_time DESC`,
      [userId]
    );
    return rows;
  }

  /**
   * 获取需要折旧巡检的资产
   * 条件：未删除、使用中、未折旧完毕
   */
  static async findForDeprec(userId) {
    const [rows] = await db.execute(
      `SELECT * FROM ${this.tableName} 
       WHERE user_id = ? AND is_deleted = 0 AND status = 'using' AND deprec_finished = 0`,
      [userId]
    );
    return rows;
  }

  /**
   * 更新固定资产（部分字段可修改）
   */
  static async update(id, userId, updates) {
    const fields = [];
    const params = [];

    // 可修改字段（除系统字段和计算字段外）
    const allowedFields = ['info', 'tag', 'img_url', 'now_val', 'secondhand_price', 
      'use_years', 'residual_rate', 'scrap_date', 'status'];

    Object.keys(updates).forEach(key => {
      if (allowedFields.includes(key) && updates[key] !== undefined) {
        fields.push(`${key} = ?`);
        params.push(updates[key]);
      }
    });

    if (fields.length === 0) return this.findById(id, userId);

    const existing = await this.findById(id, userId);
    if (!existing) throw new Error('资产记录不存在');

    // 如果修改了影响折旧的字段，重新计算相关值
    const deprecFields = ['use_years', 'residual_rate'];
    const needsRecalc = deprecFields.some(f => fields.includes(f));

    if (needsRecalc) {
      const buyPrice = parseFloat(existing.buy_price);
      const useYears = updates.use_years !== undefined ? parseFloat(updates.use_years) : parseFloat(existing.use_years);
      const residualRate = updates.residual_rate !== undefined ? parseFloat(updates.residual_rate) : parseFloat(existing.residual_rate);
      const useMonths = Math.round(useYears * 12);
      const residualVal = Math.round(buyPrice * residualRate / 100 * 100) / 100;
      const totalDepreciable = buyPrice - residualVal;
      const monthDeprec = totalDepreciable > 0 && useMonths > 0 
        ? Math.round(totalDepreciable / useMonths * 100) / 100 
        : 0;

      if (!fields.includes('use_months')) {
        fields.push('use_months = ?');
        params.push(useMonths);
      }
      if (!fields.includes('residual_val')) {
        fields.push('residual_val = ?');
        params.push(residualVal);
      }
      if (!fields.includes('month_deprec')) {
        fields.push('month_deprec = ?');
        params.push(monthDeprec);
      }
    }

    params.push(id, userId);
    await db.execute(
      `UPDATE ${this.tableName} SET ${fields.join(', ')} WHERE id = ? AND user_id = ?`,
      params
    );

    return this.findById(id, userId);
  }

  /**
   * 软删除资产
   */
  static async delete(id, userId) {
    const [result] = await db.execute(
      `UPDATE ${this.tableName} SET is_deleted = 1 WHERE id = ? AND user_id = ?`,
      [id, userId]
    );
    return result.affectedRows > 0;
  }

  /**
   * 恢复删除的资产
   */
  static async restore(id, userId) {
    const [result] = await db.execute(
      `UPDATE ${this.tableName} SET is_deleted = 0 WHERE id = ? AND user_id = ?`,
      [id, userId]
    );
    return result.affectedRows > 0;
  }

  /**
   * 永久删除资产
   */
  static async permanentDelete(id, userId) {
    const [result] = await db.execute(
      `DELETE FROM ${this.tableName} WHERE id = ? AND user_id = ?`,
      [id, userId]
    );
    return result.affectedRows > 0;
  }

  /**
   * 折旧巡检（AI Agent核心）
   * 返回更新后的资产列表
   */
  static async depreciate(userId) {
    const assets = await this.findForDeprec(userId);
    if (assets.length === 0) return [];

    const today = this.getCurrentDate();
    const updatedAssets = [];

    for (const asset of assets) {
      const lastDeprecDate = asset.last_deprec_date || asset.buy_date;
      const diffMonths = this.diffMonths(lastDeprecDate, today);

      // 如果当月已计算过，跳过
      if (diffMonths <= 0) continue;

      const buyPrice = parseFloat(asset.buy_price);
      const residualVal = parseFloat(asset.residual_val);
      const totalDepreciable = buyPrice - residualVal;
      const currentTotalDeprec = parseFloat(asset.total_deprec) || 0;
      const monthDeprec = parseFloat(asset.month_deprec) || 0;

      // 计算应补提折旧
      const addDeprec = monthDeprec * diffMonths;
      let newTotalDeprec = currentTotalDeprec + addDeprec;

      // 边界校验：折旧封顶
      let deprecFinished = 0;
      let nowVal = buyPrice - newTotalDeprec;

      if (newTotalDeprec >= totalDepreciable) {
        // 折旧完毕
        newTotalDeprec = totalDepreciable;
        nowVal = residualVal;
        deprecFinished = 1;
      }

      // 更新数据库
      await db.execute(
        `UPDATE ${this.tableName} 
         SET total_deprec = ?, now_val = ?, last_deprec_date = ?, deprec_finished = ?
         WHERE id = ? AND user_id = ?`,
        [Math.round(newTotalDeprec * 100) / 100, nowVal, today, deprecFinished, asset.id, userId]
      );

      // 获取更新后的资产
      const updated = await this.findById(asset.id, userId);
      if (updated) updatedAssets.push(updated);
    }

    return updatedAssets;
  }

  /**
   * 状态变更
   */
  static async changeStatus(id, userId, newStatus) {
    const asset = await this.findById(id, userId);
    if (!asset) throw new Error('资产记录不存在');

    const validStatuses = ['using', 'scrapped', 'sold', 'lost'];
    if (!validStatuses.includes(newStatus)) {
      throw new Error('状态只能是 using/scrapped/sold/lost');
    }

    const updates = { status: newStatus };
    
    // 报废时记录报废日期
    if (newStatus === 'scrapped' && asset.status !== 'scrapped') {
      updates.scrap_date = this.getCurrentDate();
    }

    // 从非using改为using时，检查是否需要重新纳入折旧巡检
    if (newStatus === 'using' && asset.status !== 'using' && asset.deprec_finished == 0) {
      // 重新纳入巡检，无需特殊处理
    }

    // 从using改为其他状态，停止折旧
    if (newStatus !== 'using' && asset.status === 'using') {
      // 先执行一次折旧巡检
      await this.depreciateAsset(asset, userId);
    }

    await db.execute(
      `UPDATE ${this.tableName} SET status = ?, scrap_date = COALESCE(?, scrap_date) WHERE id = ? AND user_id = ?`,
      [newStatus, updates.scrap_date || null, id, userId]
    );

    return this.findById(id, userId);
  }

  /**
   * 对单个资产执行折旧
   */
  static async depreciateAsset(asset, userId) {
    const today = this.getCurrentDate();
    const lastDeprecDate = asset.last_deprec_date || asset.buy_date;
    const diffMonths = this.diffMonths(lastDeprecDate, today);

    if (diffMonths <= 0) return asset;

    const buyPrice = parseFloat(asset.buy_price);
    const residualVal = parseFloat(asset.residual_val);
    const totalDepreciable = buyPrice - residualVal;
    const currentTotalDeprec = parseFloat(asset.total_deprec) || 0;
    const monthDeprec = parseFloat(asset.month_deprec) || 0;

    const addDeprec = monthDeprec * diffMonths;
    let newTotalDeprec = currentTotalDeprec + addDeprec;

    let deprecFinished = 0;
    let nowVal = buyPrice - newTotalDeprec;

    if (newTotalDeprec >= totalDepreciable) {
      newTotalDeprec = totalDepreciable;
      nowVal = residualVal;
      deprecFinished = 1;
    }

    await db.execute(
      `UPDATE ${this.tableName} 
       SET total_deprec = ?, now_val = ?, last_deprec_date = ?, deprec_finished = ?
       WHERE id = ? AND user_id = ?`,
      [Math.round(newTotalDeprec * 100) / 100, nowVal, today, deprecFinished, asset.id, userId]
    );

    return this.findById(asset.id, userId);
  }

  /**
   * 获取资产详情（含计算字段）
   */
  static async getAssetDetail(id, userId) {
    const asset = await this.findById(id, userId);
    if (!asset) return null;

    const buyDate = asset.buy_date;
    const today = this.getCurrentDate();
    const monthsUsed = this.diffMonths(buyDate, today);

    return {
      ...asset,
      months_used: monthsUsed,
      years_used: Math.round(monthsUsed / 12 * 100) / 100,
      depreciable_amount: Math.round((parseFloat(asset.buy_price) - parseFloat(asset.residual_val)) * 100) / 100
    };
  }
}

module.exports = FixedAsset;
