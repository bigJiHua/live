const Fund = require('../model');
const db = require('../../../common/config/db');

function toNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function isBlank(value) {
  return value === undefined || value === null || String(value).trim() === '';
}

function validateDelta(value, label, errors) {
  if (!isBlank(value) && !Number.isFinite(Number(value))) {
    errors.push(`${label}必须是有效数字`);
  }
}

async function syncFundLatestHistory(fundId, userId) {
  const fund = await Fund.findRawById(fundId, userId);
  if (!fund) return;

  const summary = await Fund.getHistorySummary(fundId, userId);
  const baseInvest = toNumber(fund.invest);
  const capitalDelta = toNumber(summary.capital_sum);
  const profitDelta = toNumber(summary.profit_sum);
  const currentInvest = baseInvest + capitalDelta;
  const marketVal = currentInvest + profitDelta;
  const newRate = currentInvest > 0 ? ((profitDelta / currentInvest) * 100).toFixed(2) + '%' : '0.00%';

  await Fund.update(fundId, userId, {
    net_value: profitDelta.toFixed(4),
    market_val: marketVal.toFixed(2),
    rate: newRate,
  });
}

class FundController {
  async getList(req, res) {
    try {
      const list = await Fund.findAll(req.userId);
      res.json({ status: 200, message: '获取成功', data: { list } });
    } catch (e) {
      console.error('获取基金列表错误:', e);
      res.status(500).json({ status: 500, message: '获取失败', error: e.message });
    }
  }

  async getById(req, res) {
    try {
      const item = await Fund.findById(req.params.id, req.userId);
      if (!item) return res.status(404).json({ status: 404, message: '记录不存在' });
      res.json({ status: 200, message: '获取成功', data: item });
    } catch (e) {
      console.error('获取基金详情错误:', e);
      res.status(500).json({ status: 500, message: '获取失败', error: e.message });
    }
  }

  async create(req, res) {
    try {
      const { fundName, share, fundAccount, tradeAccount, sellOrg, fundCompany, buyDate, netValue, invest, marketVal, rate } = req.body.data || req.body;
      const errors = [];
      if (!fundName) errors.push('基金名称');
      if (!share || isNaN(Number(share)) || Number(share) <= 0) errors.push('持有份额');
      if (!invest || isNaN(Number(invest)) || Number(invest) <= 0) errors.push('初始本金');
      if (!buyDate) errors.push('购入日期');
      if (netValue !== undefined && netValue !== '' && isNaN(Number(netValue))) errors.push('累计收益');
      if (marketVal !== undefined && marketVal !== '' && isNaN(Number(marketVal))) errors.push('初始市值');
      if (errors.length > 0) {
        return res.status(400).json({ status: 400, message: `以下字段不能为空: ${errors.join('、')}` });
      }
      const baseInvest = Number(invest).toFixed(2);
      const item = await Fund.create({
        userId: req.userId,
        fundName,
        share,
        fundAccount,
        tradeAccount,
        sellOrg,
        fundCompany: fundCompany || '未填写',
        buyDate,
        netValue: netValue || '0',
        invest: baseInvest,
        marketVal: marketVal || baseInvest,
        rate: rate || '0.00%'
      });
      res.json({ status: 200, message: '创建成功', data: item });
    } catch (e) {
      console.error('创建基金错误:', e);
      res.status(500).json({ status: 500, message: '创建失败', error: e.message });
    }
  }

  async update(req, res) {
    try {
      const existing = await Fund.findById(req.params.id, req.userId);
      if (!existing) return res.status(404).json({ status: 404, message: '记录不存在' });

      const fieldMap = {
        fundName: 'fund_name', share: 'share', fundAccount: 'fund_account',
        tradeAccount: 'trade_account', sellOrg: 'sell_org', fundCompany: 'fund_company',
        buyDate: 'buy_date', netValue: 'net_value', invest: 'invest',
        marketVal: 'market_val', rate: 'rate'
      };
      const bodyData = req.body.data || req.body;
      const updates = {};
      Object.keys(bodyData).forEach(key => {
        const dbKey = fieldMap[key];
        if (dbKey && bodyData[key] !== undefined) {
          updates[dbKey] = bodyData[key];
        }
      });

      const item = await Fund.update(req.params.id, req.userId, updates);
      res.json({ status: 200, message: '更新成功', data: item });
    } catch (e) {
      console.error('更新基金错误:', e);
      res.status(500).json({ status: 500, message: '更新失败', error: e.message });
    }
  }

  async delete(req, res) {
    try {
      const ok = await Fund.delete(req.params.id, req.userId);
      if (!ok) return res.status(404).json({ status: 404, message: '记录不存在' });
      res.json({ status: 200, message: '删除成功' });
    } catch (e) {
      console.error('删除基金错误:', e);
      res.status(500).json({ status: 500, message: '删除失败', error: e.message });
    }
  }

  async getHistory(req, res) {
    try {
      const { id } = req.params;
      const { limit, startDate, endDate } = req.query;
      const rows = await Fund.getHistory(id, req.userId, {
        limit: limit ? parseInt(limit) : undefined,
        startDate,
        endDate,
      });
      const summaryBefore = startDate
        ? await Fund.getHistorySummaryBefore(id, req.userId, startDate)
        : { profit_sum: 0, capital_sum: 0 };
      res.json({
        status: 200,
        message: '获取成功',
        data: {
          list: rows,
          range: {
            startDate: startDate || null,
            endDate: endDate || null,
            profitBefore: summaryBefore.profit_sum || 0,
            capitalBefore: summaryBefore.capital_sum || 0,
          },
        },
      });
    } catch (e) {
      console.error('获取净值历史错误:', e);
      res.status(500).json({ status: 500, message: '获取失败', error: e.message });
    }
  }

  async getAllHistory(req, res) {
    try {
      const { limit } = req.query;
      const rows = await Fund.getAllHistory(req.userId, parseInt(limit) || 180);
      res.json({ status: 200, message: '获取成功', data: { list: rows } });
    } catch (e) {
      console.error('获取全部净值历史错误:', e);
      res.status(500).json({ status: 500, message: '获取失败', error: e.message });
    }
  }

  async addHistory(req, res) {
    try {
      const { id } = req.params;
      const existing = await Fund.findById(id, req.userId);
      if (!existing) return res.status(404).json({ status: 404, message: '基金不存在' });
      const { netValue, marketVal, recordDate } = req.body.data || req.body;
      if (!recordDate) return res.status(400).json({ status: 400, message: '日期不能为空' });
      if (isBlank(netValue) && isBlank(marketVal)) return res.status(400).json({ status: 400, message: '今日收益和增持本金至少填写一项' });
      const errors = [];
      validateDelta(netValue, '今日收益', errors);
      validateDelta(marketVal, '增持本金', errors);
      if (errors.length > 0) return res.status(400).json({ status: 400, message: errors.join('，') });

      // 同一基金同一日期只允许一条记录
      const [dup] = await db.execute('SELECT id FROM bus_fund_history WHERE fund_id = ? AND record_date = ?', [id, recordDate]);
      if (dup.length > 0) return res.status(400).json({ status: 400, message: '该日期已有记录，请编辑或删除' });

      const historyId = await Fund.addHistory({
        fundId: id,
        netValue: isBlank(netValue) ? '0' : netValue,
        marketVal: isBlank(marketVal) ? '0' : marketVal,
        recordDate
      });
      await syncFundLatestHistory(id, req.userId);
      res.json({ status: 200, message: '记录成功', data: { id: historyId } });
    } catch (e) {
      console.error('写入净值历史错误:', e);
      res.status(500).json({ status: 500, message: '写入失败', error: e.message });
    }
  }

  async updateHistory(req, res) {
    try {
      const { id } = req.params;
      const existing = await Fund.findHistoryById(id, req.userId);
      if (!existing) return res.status(404).json({ status: 404, message: '记录不存在' });
      const { netValue, marketVal } = req.body.data || req.body;
      const errors = [];
      validateDelta(netValue, '今日收益', errors);
      validateDelta(marketVal, '增持本金', errors);
      if (errors.length > 0) return res.status(400).json({ status: 400, message: errors.join('，') });
      const ok = await Fund.updateHistory(id, req.userId, { netValue, marketVal });
      if (!ok) return res.status(404).json({ status: 404, message: '记录不存在' });
      await syncFundLatestHistory(existing.fund_id, req.userId);
      res.json({ status: 200, message: '修改成功' });
    } catch (e) {
      console.error('修改历史记录错误:', e);
      res.status(500).json({ status: 500, message: '修改失败', error: e.message });
    }
  }

  async deleteHistory(req, res) {
    try {
      const { id } = req.params;
      const existing = await Fund.findHistoryById(id, req.userId);
      if (!existing) return res.status(404).json({ status: 404, message: '记录不存在' });
      const ok = await Fund.deleteHistory(id, req.userId);
      if (!ok) return res.status(404).json({ status: 404, message: '记录不存在' });
      await syncFundLatestHistory(existing.fund_id, req.userId);
      res.json({ status: 200, message: '删除成功' });
    } catch (e) {
      console.error('删除历史记录错误:', e);
      res.status(500).json({ status: 500, message: '删除失败', error: e.message });
    }
  }
}

module.exports = new FundController();
