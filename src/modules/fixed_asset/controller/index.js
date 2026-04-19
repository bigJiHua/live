const FixedAsset = require('../model/fixed_asset');

/**
 * 固定资产控制器
 */
class FixedAssetController {

  /**
   * 获取所有固定资产（触发折旧巡检）
   */
  async list(req, res) {
    try {
      const userId = req.userId;

      // 触发折旧巡检
      await FixedAsset.depreciate(userId);

      // 获取所有资产
      const assets = await FixedAsset.findAll(userId);
      
      // 补充计算字段
      const today = FixedAsset.getCurrentDate();
      const result = assets.map(asset => {
        const monthsUsed = FixedAsset.diffMonths(asset.buy_date, today);
        return {
          ...asset,
          months_used: monthsUsed,
          years_used: Math.round(monthsUsed / 12 * 100) / 100,
          depreciable_amount: Math.round((parseFloat(asset.buy_price) - parseFloat(asset.residual_val)) * 100) / 100
        };
      });

      return res.json({ status: 200, message: '查询成功', data: result });
    } catch (error) {
      console.error('获取固定资产错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '查询失败' });
    }
  }

  /**
   * 获取回收站资产
   */
  async recycleBin(req, res) {
    try {
      const userId = req.userId;
      const assets = await FixedAsset.findDeleted(userId);
      return res.json({ status: 200, message: '查询成功', data: assets });
    } catch (error) {
      console.error('获取回收站错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '查询失败' });
    }
  }

  /**
   * 获取单个资产详情
   */
  async getOne(req, res) {
    try {
      const userId = req.userId;
      const { id } = req.params;

      const asset = await FixedAsset.getAssetDetail(id, userId);
      if (!asset) {
        return res.status(404).json({ status: 404, message: '资产记录不存在' });
      }

      return res.json({ status: 200, message: '查询成功', data: asset });
    } catch (error) {
      console.error('获取资产详情错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '查询失败' });
    }
  }

  /**
   * 创建固定资产
   */
  async create(req, res) {
    try {
      const userId = req.userId;
      const { info, tag, img_url, buy_price, secondhand_price, use_years, residual_rate, buy_date, status } = req.body.data;

      // 校验
      if (!info) {
        return res.status(400).json({ status: 400, message: '资产名称不能为空' });
      }
      if (!tag) {
        return res.status(400).json({ status: 400, message: '品类不能为空' });
      }
      if (!img_url) {
        return res.status(400).json({ status: 400, message: '图片不能为空' });
      }
      if (!buy_price || parseFloat(buy_price) <= 0) {
        return res.status(400).json({ status: 400, message: '购买价必须大于0' });
      }
      if (!use_years || parseFloat(use_years) <= 0) {
        return res.status(400).json({ status: 400, message: '预计使用年限必须大于0' });
      }
      if (!residual_rate || parseFloat(residual_rate) < 0 || parseFloat(residual_rate) > 100) {
        return res.status(400).json({ status: 400, message: '残值率必须在0~100之间' });
      }
      if (!buy_date) {
        return res.status(400).json({ status: 400, message: '购买日期不能为空' });
      }

      const asset = await FixedAsset.create({
        userId, info, tag, imgUrl: img_url, buyPrice: buy_price,
        secondhandPrice: secondhand_price, useYears: use_years,
        residualRate: residual_rate, buyDate: buy_date, status
      });

      return res.json({ status: 200, message: '创建成功', data: asset });
    } catch (error) {
      console.error('创建固定资产错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '创建失败' });
    }
  }

  /**
   * 更新固定资产
   */
  async update(req, res) {
    try {
      const userId = req.userId;
      const { id } = req.params;

      const asset = await FixedAsset.update(id, userId, req.body.data);

      return res.json({ status: 200, message: '更新成功', data: asset });
    } catch (error) {
      console.error('更新固定资产错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '更新失败' });
    }
  }

  /**
   * 变更资产状态
   */
  async changeStatus(req, res) {
    try {
      const userId = req.userId;
      const { id } = req.params;
      const { status } = req.body.data;

      if (!status) {
        return res.status(400).json({ status: 400, message: '状态不能为空' });
      }

      const validStatuses = ['using', 'scrapped', 'sold', 'lost'];
      if (!validStatuses.includes(status)) {
        return res.status(400).json({ status: 400, message: '状态只能是 using/scrapped/sold/lost' });
      }

      const asset = await FixedAsset.changeStatus(id, userId, status);

      return res.json({ status: 200, message: '状态更新成功', data: asset });
    } catch (error) {
      console.error('变更状态错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '变更失败' });
    }
  }

  /**
   * 删除资产（软删除）
   */
  async delete(req, res) {
    try {
      const userId = req.userId;
      const { id } = req.params;

      const result = await FixedAsset.delete(id, userId);
      if (!result) {
        return res.status(404).json({ status: 404, message: '资产记录不存在' });
      }
      return res.json({ status: 200, message: '删除成功' });
    } catch (error) {
      console.error('删除固定资产错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '删除失败' });
    }
  }

  /**
   * 恢复删除的资产
   */
  async restore(req, res) {
    try {
      const userId = req.userId;
      const { id } = req.params;

      const result = await FixedAsset.restore(id, userId);
      if (!result) {
        return res.status(404).json({ status: 404, message: '资产记录不存在' });
      }
      return res.json({ status: 200, message: '恢复成功' });
    } catch (error) {
      console.error('恢复资产错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '恢复失败' });
    }
  }

  /**
   * 永久删除资产
   */
  async permanentDelete(req, res) {
    try {
      const userId = req.userId;
      const { id } = req.params;

      const result = await FixedAsset.permanentDelete(id, userId);
      if (!result) {
        return res.status(404).json({ status: 404, message: '资产记录不存在' });
      }
      return res.json({ status: 200, message: '永久删除成功' });
    } catch (error) {
      console.error('永久删除错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '删除失败' });
    }
  }

  /**
   * 手动触发折旧巡检
   */
  async depreciate(req, res) {
    try {
      const userId = req.userId;
      const assets = await FixedAsset.depreciate(userId);
      return res.json({ status: 200, message: '折旧巡检完成', data: assets });
    } catch (error) {
      console.error('折旧巡检错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '折旧巡检失败' });
    }
  }
}

module.exports = new FixedAssetController();
