# 服务层 (Services)

服务层包含复杂的业务逻辑,处理跨越多个模型的操作,计算密集型任务,以及数据转换等复杂操作。

## 设计原则

1. **业务逻辑封装**: 将复杂的业务逻辑从控制器中分离出来
2. **可重用性**: 提供可被多个控制器调用的通用功能
3. **独立性**: 不直接处理 HTTP 请求和响应
4. **测试友好**: 纯逻辑代码,易于单元测试

## 文件说明

### financeService.js
**财务服务**

提供财务相关的复杂业务逻辑,包括报表生成、IRR 计算等。

**主要方法:**

### generateReport(userId, { startDate, endDate })
**生成财务报表**

生成完整的财务报告,包括汇总统计、分类汇总、每日汇总等。

**参数:**
- `userId`: 用户 ID
- `options.startDate`: 开始日期 (可选)
- `options.endDate`: 结束日期 (可选)

**返回:**
```javascript
{
  summary: {
    income: 5000,
    expense: 3000,
    balance: 2000
  },
  categorySummary: {
    income: [
      { name: '工资', total: 4000, count: 1, color: '#52c41a', icon: '💰' },
      { name: '奖金', total: 1000, count: 1, color: '#52c41a', icon: '🎁' }
    ],
    expense: [
      { name: '餐饮', total: 1000, count: 5, color: '#ff4d4f', icon: '🍔' },
      { name: '交通', total: 500, count: 3, color: '#ff4d4f', icon: '🚗' }
    ]
  },
  dailySummary: {
    '2024-03-26': { income: 0, expense: 100, balance: -100, count: 2 },
    '2024-03-25': { income: 4000, expense: 0, balance: 4000, count: 1 }
  },
  reportDate: '2024-03-26T12:00:00.000Z'
}
```

**使用示例:**
```javascript
const report = await financeService.generateReport(1, {
  startDate: '2024-01-01',
  endDate: '2024-12-31'
});
```

---

### getCategorySummary(userId, { startDate, endDate })
**获取分类汇总**

按分类统计收入和支出,返回每个分类的总金额和笔数。

**使用场景:**
- 生成饼图数据
- 分类排行榜
- 支出结构分析

**返回示例:**
```javascript
{
  income: [
    { name: '工资', total: 4000, count: 1, color: '#52c41a', icon: '💰' }
  ],
  expense: [
    { name: '餐饮', total: 1000, count: 5, color: '#ff4d4f', icon: '🍔' }
  ]
}
```

---

### getDailySummary(userId, { startDate, endDate })
**获取每日汇总**

按日期统计收入、支出和余额,用于生成趋势图。

**使用场景:**
- 生成折线图数据
- 日收支趋势分析
- 余额变化追踪

**返回示例:**
```javascript
{
  '2024-03-26': { income: 0, expense: 100, balance: -100, count: 2 },
  '2024-03-25': { income: 4000, expense: 0, balance: 4000, count: 1 }
}
```

---

### calculateIRR(cashFlows, tolerance, maxIterations)
**计算内部收益率 (IRR)**

使用牛顿迭代法计算投资项目的内部收益率。

**什么是 IRR?**
IRR (Internal Rate of Return) 是投资项目预期收益率的重要指标。它是使投资项目的净现值 (NPV) 等于零时的折现率。

**参数:**
- `cashFlows`: 现金流数组 (负数表示投资,正数表示收益)
  - 例如: `[-10000, 3000, 4000, 5000, 6000]`
  - 表示: 初始投资 10000,之后每年分别收益 3000, 4000, 5000, 6000
- `tolerance`: 精度容差 (默认 0.0001)
- `maxIterations`: 最大迭代次数 (默认 100)

**返回:**
- `number`: IRR 值 (小数形式,如 0.125 表示 12.5%)

**使用示例:**
```javascript
// 计算投资回报率
const cashFlows = [-10000, 3000, 4000, 5000, 6000];
const irr = financeService.calculateIRR(cashFlows);

// 返回 0.125 (12.5%)
console.log(`内部收益率: ${(irr * 100).toFixed(2)}%`);
```

**算法说明:**
使用牛顿迭代法求解方程: NPV(r) = 0

```
NPV(r) = Σ [CFt / (1 + r)^t] = 0

其中:
- CFt: 第 t 期的现金流
- r: 折现率 (IRR)
- t: 时间期数
```

**迭代公式:**
```
r_new = r_old - NPV(r_old) / dNPV/dr(r_old)
```

**判断标准:**
- IRR > 0: 投资有正收益
- IRR = 0: 投资刚好回本
- IRR < 0: 投资亏损
- IRR > 资金成本: 投资可行

---

### calculateNPV(cashFlows, rate)
**计算净现值 (NPV)**

计算投资项目的净现值。

**参数:**
- `cashFlows`: 现金流数组
- `rate`: 折现率

**使用示例:**
```javascript
const cashFlows = [-10000, 3000, 4000, 5000, 6000];
const rate = 0.1; // 10% 的折现率
const npv = financeService.calculateNPV(cashFlows, rate);
```

---

### calculatePaybackPeriod(cashFlows)
**计算投资回收期**

计算投资本金回收所需的时间。

**参数:**
- `cashFlows`: 现金流数组

**返回:**
- `number | null`: 回收期 (年数),如果永不回收返回 null

**使用示例:**
```javascript
const cashFlows = [-10000, 3000, 4000, 5000, 6000];
const payback = financeService.calculatePaybackPeriod(cashFlows);
// 返回 3.5 (3.5 年收回投资)
```

## 在控制器中使用

```javascript
const financeService = require('../services/financeService');

class FinanceController {
  async getFinanceReport(req, res) {
    try {
      const { startDate, endDate } = req.query;
      const report = await financeService.generateReport(req.userId, {
        startDate,
        endDate
      });

      res.json({ report });
    } catch (error) {
      res.status(500).json({ message: '获取报表失败' });
    }
  }

  async calculateIRR(req, res) {
    try {
      const { cashFlows } = req.body;
      const irr = financeService.calculateIRR(cashFlows);

      res.json({
        irr: irr * 100,
        message: `内部收益率为 ${(irr * 100).toFixed(2)}%`
      });
    } catch (error) {
      res.status(500).json({ message: '计算 IRR 失败' });
    }
  }
}
```

## 扩展服务层

可以创建更多的服务类:

```javascript
// 示例: 通知服务
class NotificationService {
  async sendNotification(userId, message) {
    // 发送通知逻辑
  }

  async scheduleNotification(userId, message, date) {
    // 定时通知逻辑
  }
}

// 示例: 报表导出服务
class ExportService {
  async exportToCSV(data) {
    // 导出为 CSV
  }

  async exportToPDF(data) {
    // 导出为 PDF
  }

  async exportToExcel(data) {
    // 导出为 Excel
  }
}
```

## 服务层的优势

1. **代码复用**: 业务逻辑可以在多个控制器中复用
2. **测试友好**: 可以独立测试业务逻辑,不需要模拟 HTTP 请求
3. **职责分离**: 控制器只处理 HTTP 相关逻辑,服务层处理业务逻辑
4. **易于维护**: 业务逻辑集中管理,修改更方便
5. **性能优化**: 可以在服务层实现缓存等优化策略
