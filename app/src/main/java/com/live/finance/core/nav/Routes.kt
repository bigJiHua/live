package com.live.finance.core.nav

/**
 * 全量路由常量，逐条对齐 web/src/router/map.js。
 * 各功能域只引用这里的常量来注册/跳转，不再各自散落字符串 —— 这是并行/多人时「路由不打架」的收口点。
 * Wave-0 已注册主干（main/login/429/flow），其余常量先行占位，后续按域补屏幕。
 */
object Routes {
    // 独立全屏页
    const val LOGIN = "login"
    const val ERROR_429 = "error429"
    const val DIARY_SHARE = "share/diary/detail"

    // 主外壳（底部 Tabbar）内的顶层 Tab
    const val MAIN = "main"
    const val TAB_HOME = "home"
    const val TAB_FINANCE = "finance"
    const val TAB_USER = "user"

    // 账本/流水
    const val FLOW_LIST = "finance/flow"
    const val FLOW_CALENDAR = "finance/flow/calendar"
    fun flowDetail(id: String) = "finance/flow/$id"

    // 账户
    const val ACCOUNT_ADD = "finance/add"
    const val ACCOUNT_QUICK_ADD = "finance/quick-add"
    const val ACCOUNT_STRUCTURE = "finance/structure"
    const val ACCOUNT_BALANCE_FLOW = "finance/balance-flow"

    // 资产
    const val ASSETS_REGISTER = "finance/assets/register"
    const val ASSETS_EDIT = "finance/assets/edit"
    const val ASSETS_LIST = "finance/assets/list"
    const val ASSETS_TREND = "finance/assets/trend"

    // 固定支出/事件/固定资产
    const val RECURRING = "finance/recurring"
    const val EVENTS = "finance/events"
    const val FIXED_ASSET = "finance/fixed-asset"
    const val FIXED_ASSET_RECYCLE = "finance/fixed-asset/recycle"
    fun fixedAssetDetail(id: String) = "finance/fixed-asset/detail/$id"
    fun fixedAssetEdit(id: String) = "finance/fixed-asset/edit/$id"

    // 报表
    const val REPORT = "finance/report"
    const val REPORT_FLOW_FILTER = "finance/report/flow-filter"
    const val REPORT_STATS_OVERVIEW = "finance/report/stats-overview"
    const val REPORT_CATEGORY_RATIO = "finance/report/category-ratio"
    const val REPORT_MONTHLY_TREND = "finance/report/monthly-trend"
    const val REPORT_CARD_FLOW = "finance/report/card-flow"
    const val REPORT_TRANSFER_LIST = "finance/report/transfer-list"
    const val REPORT_DEBT_OVERVIEW = "finance/report/debt-overview"
    const val FUND = "finance/report/fund"
    const val FUND_REGISTER = "finance/report/fund/register"
    const val FUND_TREND = "finance/report/fund/trend"
    const val FUND_DAILY = "finance/report/fund/daily"
    const val FUND_EARNINGS = "finance/report/fund/earnings"

    // 预算
    const val BUDGET = "finance/budget"
    const val BUDGET_TYPE_SELECT = "finance/budget/type-select"
    const val BUDGET_SHOPPING = "finance/budget/shopping"
    const val BUDGET_TRAVEL = "finance/budget/travel"
    const val BUDGET_EAT = "finance/budget/eat"
    fun budgetDetail(id: String) = "finance/budget/detail/$id"

    // 数据管理
    const val DATA_MANAGE = "finance/data"
    const val DATA_CHECK = "finance/data/check"
    const val DATA_EXPORT = "finance/data/export"
    const val DATA_IMPORT = "finance/data/import"
    const val DATA_BACKUP = "finance/data/backup"
    const val DATA_LOGIN_LOG = "finance/data/login-log"

    // 银行卡
    const val CARD = "card"
    const val CARD_DEBIT = "card/debit"
    const val CARD_CREDIT = "card/credit"
    const val CARD_ADD = "card/add"
    const val CARD_EDIT = "card/edit"
    const val CARD_EDIT_PAT = "card/edit/{id}"
    fun cardEdit(id: String) = "card/edit/$id"
    const val CARD_FLOW = "card/flow"
    const val CREDIT_CENTER = "credit-center"
    const val CREDIT_INSTALLMENT = "credit-center/installment"
    const val CREDIT_INSTALLMENT_LIST = "credit-center/installment/list"
    const val CREDIT_FULL = "credit-full"
    const val CREDIT_LIMIT_MANAGE = "credit/limit-manage"
    const val CREDIT_FOREIGN_REGISTER = "credit/foreign-register"
    const val BILL_LIST = "card/bill/list"
    const val BILL_DETAIL = "card/bill/detail"
    const val BILL_LEDGER = "card/bill/ledger"
    const val REPAY_LIST = "card/repay/list"
    const val REPAY_DETAIL = "card/repay/detail"
    const val REPAY_ADD = "card/repay/add"
    const val REPAY_EDIT = "card/repay/edit"

    // 日记 / 待办
    const val DIARY = "diary"
    const val DIARY_ADD = "diary/add"
    const val DIARY_DETAIL = "diary/detail"
    const val TODO_CALENDAR = "todo/calendar"

    // 工作
    const val WORK_JOB_SETTING = "work/job-setting"
    const val WORK_SALARY_CALENDAR = "work/salary-calendar"
    const val WORK_SALARY_DAY = "work/salary-day"
    const val WORK_SALARY_STAT = "work/salary-stat"

    // 用户
    const val USER = "user"
    const val PROFILE_EDIT = "profile-edit"
    const val PIN_MANAGE = "user/pin-manage"
    const val PIN_SETUP = "user/pin-setup"
    const val RESOURCE_MANAGE = "user/resource-manage"
    const val RESOURCE_LIST = "user/resource-list"
    const val APP_SETTINGS = "user/app-settings"
    const val CATEGORY_MANAGE = "user/category-manage"
    const val BANK_CATEGORY_MANAGE = "user/bank-category-manage"

    /** 顶层 Tab 白名单（决定底部栏是否显示 / 是否需登录）。 */
    val TABS = listOf(TAB_HOME, TAB_FINANCE, TAB_USER)
}
