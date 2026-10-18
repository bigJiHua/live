package com.live.finance.core.nav

import android.os.Bundle

/**
 * 全局顶栏标题表 —— 逐条对齐 web `src/router/map.js` 的 `meta.title`。
 *
 * key 用「注册路径去掉 `?query` 模板后的部分」，与 `NavDestination.route.substringBefore('?')` 对齐；
 * vue-router 会把父子 meta 合并，所以 `/card/debit`、`/card/credit` 继承父级「银行卡管理」。
 * 顶部四个 Tab（首页/账本/动态/我的）在原生是 `Routes.MAIN` 单目的地内的局部状态，不走本表，
 * 由 [com.live.finance.ui.main.GlobalNavBar] 按 tab 序号取。
 */
object RouteTitles {

    /** web MainLayout `pageTitle` 计算属性在 `meta.title` 缺失时的兜底值。 */
    const val FALLBACK = "人生记录"

    /** web 里挂在 MainLayout **之外**的三页（无顶栏、无底部岛）。 */
    private val NO_BAR = setOf(Routes.LOGIN, Routes.ERROR_429, Routes.DIARY_SHARE)

    private val TITLES: Map<String, String> = mapOf(
        // 账本 / 流水
        Routes.FLOW_LIST to "流水明细",
        Routes.FLOW_CALENDAR to "每日流水",
        "finance/flow/{id}" to "收支详情",
        Routes.ACCOUNT_ADD to "新增收支明细",
        Routes.ACCOUNT_QUICK_ADD to "快速登记",
        Routes.ACCOUNT_STRUCTURE to "系统账户余额",
        Routes.ACCOUNT_BALANCE_FLOW to "余额流水明细",

        // 资产
        Routes.ASSETS_REGISTER to "资产结构登记",
        Routes.ASSETS_EDIT to "编辑资产登记",
        Routes.ASSETS_LIST to "资产登记记录",
        Routes.ASSETS_TREND to "资产走势",

        // 固定支出 / 事件 / 固定资产
        Routes.RECURRING to "固定支出",
        Routes.EVENTS to "固定事件",
        Routes.FIXED_ASSET to "固定资产",
        Routes.FIXED_ASSET_RECYCLE to "回收站",
        Routes.FIXED_ASSET_DETAIL to "资产详情",
        Routes.FIXED_ASSET_EDIT to "编辑资产",

        // 报表
        Routes.REPORT to "财务报表",
        Routes.REPORT_FLOW_FILTER to "流水筛选",
        Routes.REPORT_STATS_OVERVIEW to "收支总额与结余",
        Routes.REPORT_CATEGORY_RATIO to "类目消费占比",
        Routes.REPORT_MONTHLY_TREND to "月度收支趋势",
        Routes.REPORT_CARD_FLOW to "银行卡收支明细",
        Routes.REPORT_TRANSFER_LIST to "转账明细查询",
        Routes.REPORT_DEBT_OVERVIEW to "负债统计",
        Routes.FUND to "理财总览",
        Routes.FUND_REGISTER to "理财登记",
        Routes.FUND_TREND to "变动走势图",
        Routes.FUND_DAILY to "每日净值登记",
        Routes.FUND_EARNINGS to "收益明细",

        // 预算（带 ?id= 即编辑态，web 用两条路由分别给标题）
        Routes.BUDGET to "预算登记",
        Routes.BUDGET_TYPE_SELECT to "选择预算类型",
        Routes.BUDGET_SHOPPING to "购物预算",
        Routes.BUDGET_TRAVEL to "出行预算",
        Routes.BUDGET_EAT to "餐饮预算",
        Routes.BUDGET_DETAIL to "预算详情",

        // 数据管理
        Routes.DATA_MANAGE to "数据管理",
        Routes.DATA_CHECK to "数据库检查",
        Routes.DATA_EXPORT to "导出数据库",
        Routes.DATA_IMPORT to "数据导入",
        Routes.DATA_BACKUP to "数据库备份",
        Routes.DATA_LOGIN_LOG to "登录日志",

        // 动态 / 待办
        Routes.DIARY to "动态",
        Routes.DIARY_ADD to "发布动态",
        Routes.DIARY_DETAIL to "动态详情",
        Routes.TODO_CALENDAR to "日程日历",

        // 工作 / 薪酬
        Routes.WORK_JOB_SETTING to "工作信息设置",
        Routes.WORK_SALARY_CALENDAR to "工资日历",
        Routes.WORK_SALARY_DAY to "每日工资详情",
        Routes.WORK_SALARY_STAT to "月度工资统计",

        // 用户 / 安全 / 资源 / 设置
        Routes.USER to "我的",
        Routes.PROFILE_EDIT to "编辑资料",
        Routes.PIN_MANAGE to "PIN 码管理",
        Routes.PIN_SETUP to "设置 PIN 码",
        Routes.RESOURCE_MANAGE to "文件资源管理",
        Routes.RESOURCE_LIST to "资源列表",
        Routes.APP_SETTINGS to "应用设置",
        Routes.CATEGORY_MANAGE to "分类管理",
        Routes.BANK_CATEGORY_MANAGE to "银行分类管理",

        // 银行卡 / 信用卡
        Routes.CARD to "银行卡管理",
        Routes.CARD_DEBIT to "银行卡管理",
        Routes.CARD_CREDIT to "银行卡管理",
        Routes.CARD_ADD to "添加卡片",
        Routes.CARD_EDIT_PAT to "编辑卡片",
        Routes.CREDIT_CENTER to "信用卡专项",
        Routes.CREDIT_INSTALLMENT to "创建分期",
        Routes.CREDIT_INSTALLMENT_LIST to "分期列表",
        Routes.CREDIT_FULL to "信用卡全功能录入",
        Routes.CREDIT_LIMIT_MANAGE to "额度与共享池管理",
        Routes.CREDIT_FOREIGN_REGISTER to "外币消费登记对账",
        Routes.BILL_LIST to "信用卡账单列表",
        Routes.BILL_DETAIL to "信用卡账单详情",
        Routes.BILL_LEDGER to "账单流水明细",
        Routes.REPAY_LIST to "信用卡还款记录",
        Routes.REPAY_DETAIL to "信用卡还款详情",
        Routes.REPAY_ADD to "信用卡添加还款",
        Routes.REPAY_EDIT to "信用卡编辑还款",
    )

    /** web `MainLayout` 只在布局容器内的路由上渲染顶栏。 */
    fun hasBar(route: String?): Boolean =
        route != null && route.substringBefore('?') !in NO_BAR

    /**
     * [route] 取 `NavDestination.route`（注册时的模板，可带 `?x={x}`）；
     * [args] 用于 web 里按参数分叉的三处标题：PIN 设置 mode、预算三类型的编辑态。
     */
    fun title(route: String?, args: Bundle? = null): String {
        val path = route?.substringBefore('?') ?: return FALLBACK
        // web `/user/pin-setup`：MainLayout 按 query.mode 动态给标题
        if (path == Routes.PIN_SETUP) {
            return if (args?.getString("mode") == "modify") "修改 PIN 码" else "设置 PIN 码"
        }
        // web `finance/budget/{shopping,travel,eat}/:id` 与不带 id 是两条路由、两个标题
        val editing = !args?.getString("id").isNullOrEmpty()
        val editPrefix = when (path) {
            Routes.BUDGET_SHOPPING, Routes.BUDGET_TRAVEL, Routes.BUDGET_EAT -> editing
            else -> false
        }
        val base = TITLES[path] ?: return FALLBACK
        return if (editPrefix) "编辑$base" else base
    }
}
