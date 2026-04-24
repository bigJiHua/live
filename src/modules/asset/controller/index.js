const AssetSnapshot = require('../model/snapshot');
const AssetRegister = require('../model/register');

/**
 * 资产控制器
 */
class AssetController {
  /**
   * 获取首页资产数据（最新系统快照）
   */
  async getHomeAsset(req, res) {
    try {
      const userId = req.userId;

      // 获取最新系统快照
      const snapshot = await AssetSnapshot.getLatest(userId);

      return res.json({
        status: 200,
        message: '获取成功',
        data: snapshot
      });
    } catch (error) {
      console.error('获取首页资产错误:', error);
      return res.status(500).json({
        status: 500,
        message: error.message || '获取失败'
      });
    }
  }

  /**
   * 获取资产快照列表
   */
  async getSnapshots(req, res) {
    try {
      const userId = req.userId;
      const { limit = 30, startDate, endDate } = req.query;

      let snapshots;
      if (startDate && endDate) {
        snapshots = await AssetSnapshot.getByDateRange(userId, startDate, endDate);
      } else {
        snapshots = await AssetSnapshot.getRecent(userId, parseInt(limit));
      }

      return res.json({
        status: 200,
        message: '获取成功',
        data: snapshots
      });
    } catch (error) {
      console.error('获取快照列表错误:', error);
      return res.status(500).json({
        status: 500,
        message: error.message || '获取失败'
      });
    }
  }

  /**
   * 获取用户登记列表
   */
  async getRegisters(req, res) {
    try {
      const userId = req.userId;
      const { startDate, endDate } = req.query;

      const registers = await AssetRegister.findAll(userId, { startDate, endDate });

      return res.json({
        status: 200,
        message: '获取成功',
        data: registers
      });
    } catch (error) {
      console.error('获取登记列表错误:', error);
      return res.status(500).json({
        status: 500,
        message: error.message || '获取失败'
      });
    }
  }

  /**
   * 用户登记资产
   */
  async createRegister(req, res) {
    try {
      const userId = req.userId;
      const { total_asset, credit_debt, total_balance, asset_details, remark } = req.body.data;

      if (total_asset === undefined || credit_debt === undefined || total_balance === undefined) {
        return res.status(400).json({
          status: 400,
          message: '缺少必填参数：total_asset, credit_debt, total_balance'
        });
      }

      const result = await AssetRegister.create({
        userId,
        totalAsset: total_asset,
        creditDebt: credit_debt,
        totalBalance: total_balance,
        assetDetails: asset_details || {},
        remark
      });

      return res.json({
        status: 200,
        message: '登记成功',
        data: result
      });
    } catch (error) {
      console.error('登记资产错误:', error);
      return res.status(500).json({
        status: 500,
        message: error.message || '登记失败'
      });
    }
  }

  /**
   * 更新登记记录
   */
  async updateRegister(req, res) {
    try {
      const userId = req.userId;
      const { id } = req.params;
      const { total_asset, credit_debt, total_balance, asset_details, remark } = req.body.data;

      const result = await AssetRegister.update(id, userId, {
        total_asset,
        credit_debt,
        total_balance,
        asset_details,
        remark
      });

      if (!result) {
        return res.status(404).json({
          status: 404,
          message: '记录不存在'
        });
      }

      return res.json({
        status: 200,
        message: '更新成功',
        data: result
      });
    } catch (error) {
      console.error('更新登记错误:', error);
      return res.status(500).json({
        status: 500,
        message: error.message || '更新失败'
      });
    }
  }

  /**
   * 删除登记记录
   */
  async deleteRegister(req, res) {
    try {
      const userId = req.userId;
      const { id } = req.params;

      const success = await AssetRegister.delete(id, userId);

      if (!success) {
        return res.status(404).json({
          status: 404,
          message: '记录不存在'
        });
      }

      return res.json({
        status: 200,
        message: '删除成功'
      });
    } catch (error) {
      console.error('删除登记错误:', error);
      return res.status(500).json({
        status: 500,
        message: error.message || '删除失败'
      });
    }
  }

  /**
   * 获取登记记录详情
   */
  async getRegisterById(req, res) {
    try {
      const userId = req.userId;
      const { id } = req.params;

      const register = await AssetRegister.findById(id, userId);

      if (!register) {
        return res.status(404).json({
          status: 404,
          message: '记录不存在'
        });
      }

      return res.json({
        status: 200,
        message: '获取成功',
        data: register
      });
    } catch (error) {
      console.error('获取登记详情错误:', error);
      return res.status(500).json({
        status: 500,
        message: error.message || '获取失败'
      });
    }
  }
}

module.exports = new AssetController();
