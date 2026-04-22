const RouterMap = [
  // --- 登录页面 (独立于布局之外，实现全屏展示) ---
  {
    path: "/login",
    name: "Login",
    component: () => import("@/views/Auth/Login.vue"),
    meta: { title: "登录" },
  },

  // --- 注册页面 (独立于布局之外，实现全屏展示) ---
  // {
  //   path: '/register',
  //   name: 'register',
  //   component: () => import('@/views/Auth/register.vue'),
  //   meta: { title: '登录' }
  // },

  // --- 错误页面 ---
  {
    path: "/429",
    name: "Error429",
    component: () => import("@/views/error/429.vue"),
    meta: { title: "请求过于频繁" },
  },

  // --- 主布局容器 (包含底部 Tabbar 导航) ---
  {
    path: "/",
    component: () => import("../layout/MainLayout.vue"),
    redirect: "/home",
    children: [
      {
        path: "home",
        name: "Home",
        component: () => import("../views/Home/index.vue"),
        meta: { title: "首页" },
      },
      // 账本/金融模块
      {
        path: "finance",
        name: "Finance",
        component: () => import("../views/Finance/index.vue"),
        meta: { title: "账本" },
      },
      {
        path: "finance/add",
        name: "FinanceAdd",
        component: () => import("../views/Finance/account/Add.vue"),
        meta: { title: "新增收支明细", hideTabbar: true },
      },
      {
        path: "finance/structure",
        name: "FinanceStructure",
        component: () => import("../views/Finance/account/Structure.vue"),
        meta: { title: "系统账户余额", hideTabbar: true },
      },
      {
        path: "finance/assets/register",
        name: "FinanceAssetsRegister",
        component: () => import("../views/Finance/assets/Register.vue"),
        meta: { title: "资产结构登记", hideTabbar: true },
      },
      {
        path: "finance/assets/list",
        name: "FinanceAssetsList",
        component: () => import("../views/Finance/assets/List.vue"),
        meta: { title: "资产登记记录", hideTabbar: true },
      },
      {
        path: "finance/flow",
        name: "FinanceFlow",
        component: () => import("../views/Finance/flow/List.vue"),
        meta: { title: "流水明细", hideTabbar: true },
      },
      {
        path: "finance/flow/calendar",
        name: "FinanceFlowCalendar",
        component: () => import("../views/Finance/flow/Calendar.vue"),
        meta: { title: "每日流水", hideTabbar: true },
      },
      {
        path: "finance/flow/:id",
        name: "FinanceFlowDetail",
        component: () => import("../views/Finance/flow/Detail.vue"),
        meta: { title: "收支详情", hideTabbar: true },
      },
      {
        path: "finance/fixed-asset",
        name: "FinanceFixedAsset",
        component: () => import("../views/Finance/fixedAsset/List.vue"),
        meta: { title: "固定资产", hideTabbar: true },
      },
      {
        path: "finance/fixed-asset/recycle",
        name: "FinanceFixedAssetRecycle",
        component: () => import("../views/Finance/fixedAsset/RecycleBin.vue"),
        meta: { title: "回收站", hideTabbar: true },
      },
      {
        path: "finance/fixed-asset/detail/:id",
        name: "FinanceFixedAssetDetail",
        component: () => import("../views/Finance/fixedAsset/Detail.vue"),
        meta: { title: "资产详情", hideTabbar: true },
      },
      {
        path: "finance/fixed-asset/edit/:id",
        name: "FinanceFixedAssetEdit",
        component: () => import("../views/Finance/fixedAsset/Edit.vue"),
        meta: { title: "编辑资产", hideTabbar: true },
      },
      // 预算管理模块
      {
        path: "finance/budget",
        name: "FinanceBudget",
        component: () => import("../views/Finance/budget/List.vue"),
        meta: { title: "预算登记", hideTabbar: true },
      },
      // 预算类型选择
      {
        path: "finance/budget/type-select",
        name: "FinanceBudgetTypeSelect",
        component: () => import("../views/Finance/budget/TypeSelect.vue"),
        meta: { title: "选择预算类型", hideTabbar: true },
      },
      // 购物预算
      {
        path: "finance/budget/shopping",
        name: "FinanceBudgetShopping",
        component: () => import("../views/Finance/budget/Shopping.vue"),
        meta: { title: "购物预算", hideTabbar: true },
      },
      {
        path: "finance/budget/shopping/:id",
        name: "FinanceBudgetShoppingEdit",
        component: () => import("../views/Finance/budget/Shopping.vue"),
        meta: { title: "编辑购物预算", hideTabbar: true },
      },
      // 出行预算
      {
        path: "finance/budget/travel",
        name: "FinanceBudgetTravel",
        component: () => import("../views/Finance/budget/Travel.vue"),
        meta: { title: "出行预算", hideTabbar: true },
      },
      {
        path: "finance/budget/travel/:id",
        name: "FinanceBudgetTravelEdit",
        component: () => import("../views/Finance/budget/Travel.vue"),
        meta: { title: "编辑出行预算", hideTabbar: true },
      },
      // 餐饮预算
      {
        path: "finance/budget/eat",
        name: "FinanceBudgetEat",
        component: () => import("../views/Finance/budget/Eat.vue"),
        meta: { title: "餐饮预算", hideTabbar: true },
      },
      {
        path: "finance/budget/eat/:id",
        name: "FinanceBudgetEatEdit",
        component: () => import("../views/Finance/budget/Eat.vue"),
        meta: { title: "编辑餐饮预算", hideTabbar: true },
      },
      // 预算详情
      {
        path: "finance/budget/detail/:id",
        name: "FinanceBudgetDetail",
        component: () => import("../views/Finance/budget/Detail.vue"),
        meta: { title: "预算详情", hideTabbar: true },
      },
      // 动态/日记模块
      {
        path: "diary",
        name: "Diary",
        component: () => import("../views/Diary/index.vue"),
        meta: { title: "动态" },
      },
      {
        path: "diary/add",
        name: "DiaryAdd",
        component: () => import("../views/Diary/Add.vue"),
        meta: { title: "发布动态", hideTabbar: true },
      },
      {
        path: "diary/detail",
        name: "DiaryDetail",
        component: () => import("../views/Diary/Detail.vue"),
        meta: { title: "动态详情", hideTabbar: true },
      },
      // 日历待办
      {
        path: "todo/calendar",
        name: "TodoCalendar",
        component: () => import("../views/Todo/Calendar.vue"),
        meta: { title: "日程日历", hideTabbar: true },
      },
      // 个人中心
      {
        path: "user",
        name: "User",
        component: () => import("../views/User/index.vue"),
        meta: { title: "我的" },
      },
      {
        path: "user/pin-manage",
        name: "PinManage",
        component: () => import("../views/User/security/PinManage.vue"),
        meta: { title: "PIN 码管理", hideTabbar: true },
      },
      {
        path: "user/pin-setup",
        name: "PinSetup",
        component: () => import("../views/User/security/PinSetup.vue"),
        meta: { title: "设置 PIN 码", hideTabbar: true },
      },

      {
        path: "user/resource-manage",
        name: "ResourceManage",
        component: () => import("../views/User/resource/ResourceManage.vue"),
        meta: { title: "文件资源管理", hideTabbar: true },
      },
      {
        path: "user/resource-list",
        name: "ResourceList",
        component: () => import("../views/User/resource/ResourceList.vue"),
        meta: { title: "资源列表", hideTabbar: true },
      },
      {
        path: "user/app-settings",
        name: "AppSettings",
        component: () => import("../views/User/settings/AppSettings.vue"),
        meta: { title: "应用设置", hideTabbar: true },
      },
      {
        path: "user/category-manage",
        name: "CategoryManage",
        component: () => import("../views/User/category/CategoryManage.vue"),
        meta: { title: "分类管理", hideTabbar: true },
      },
      {
        path: "user/bank-category-manage",
        name: "BankCategoryManage",
        component: () =>
          import("../views/User/category/BankCategoryManage.vue"),
        meta: { title: "银行分类管理", hideTabbar: true },
      },
      // 银行卡管理
      {
        path: "card",
        name: "BankCard",
        component: () => import("../views/BankCard/index.vue"),
        redirect: "/card/debit",
        meta: { title: "银行卡管理", hideTabbar: true },
        children: [
          {
            path: "debit",
            name: "BankCardDebit",
            component: () => import("../views/BankCard/debit.vue"),
          },
          {
            path: "credit",
            name: "BankCardCredit",
            component: () => import("../views/BankCard/credit.vue"),
          },
        ],
      },
      // 信用卡专项（独立功能页面，不在 /card 下）
      {
        path: "credit-center",
        name: "CreditCenter",
        component: () => import("../views/BankCard/CreditCenter.vue"),
        meta: { title: "信用卡专项", hideTabbar: true },
      },
      // 【临时测试】信用卡全功能录入
      {
        path: "credit-full",
        name: "CreditFull",
        component: () => import("../views/BankCard/credit/AddFull.vue"),
        meta: { title: "信用卡全功能录入", hideTabbar: true },
      },
      {
        path: "card/add",
        name: "BankCardAdd",
        component: () => import("../views/BankCard/card/Add.vue"),
        meta: { title: "添加卡片", hideTabbar: true },
      },
      {
        path: "card/edit",
        name: "BankCardEdit",
        component: () => import("../views/BankCard/card/Edit.vue"),
        meta: { title: "编辑卡片", hideTabbar: true },
      },
      {
        path: "card/flow",
        name: "CardFlow",
        component: () => import("../views/BankCard/card/flow/List.vue"),
        meta: { title: "卡片流水", hideTabbar: true },
      },
      // 账单管理
      {
        path: "card/bill/list",
        name: "BillList",
        component: () => import("../views/BankCard/bill/List.vue"),
        meta: { title: "信用卡账单列表", hideTabbar: true },
      },
      {
        path: "card/bill/detail",
        name: "BillDetail",
        component: () => import("../views/BankCard/bill/Detail.vue"),
        meta: { title: "信用卡账单详情", hideTabbar: true },
      },
      {
        path: "card/bill/add",
        name: "BillAdd",
        component: () => import("../views/BankCard/bill/Add.vue"),
        meta: { title: "信用卡添加账单", hideTabbar: true },
      },
      {
        path: "card/bill/edit",
        name: "BillEdit",
        component: () => import("../views/BankCard/bill/Edit.vue"),
        meta: { title: "信用卡编辑账单", hideTabbar: true },
      },
      // 还款记录
      {
        path: "card/repay/list",
        name: "RepayList",
        component: () => import("../views/BankCard/repay/List.vue"),
        meta: { title: "信用卡还款记录", hideTabbar: true },
      },
      {
        path: "card/repay/detail",
        name: "RepayDetail",
        component: () => import("../views/BankCard/repay/Detail.vue"),
        meta: { title: "信用卡还款详情", hideTabbar: true },
      },
      {
        path: "card/repay/add",
        name: "RepayAdd",
        component: () => import("../views/BankCard/repay/Add.vue"),
        meta: { title: "信用卡添加还款", hideTabbar: true },
      },
      {
        path: "card/repay/edit",
        name: "RepayEdit",
        component: () => import("../views/BankCard/repay/Edit.vue"),
        meta: { title: "信用卡编辑还款", hideTabbar: true },
      },
      {
        path: "profile-edit",
        name: "ProfileEdit",
        component: () => import("../views/User/ProfileEdit.vue"),
        meta: { title: "编辑资料", hideTabbar: true },
      },

      // 工资核算模块
      {
        path: "work/job-setting",
        name: "WorkJobSetting",
        component: () => import("../views/Work/JobSetting.vue"),
        meta: { title: "工作信息设置", hideTabbar: true },
      },
      {
        path: "work/salary-calendar",
        name: "WorkSalaryCalendar",
        component: () => import("../views/Work/SalaryCalendar.vue"),
        meta: { title: "工资日历", hideTabbar: true },
      },
      {
        path: "work/salary-day",
        name: "WorkSalaryDay",
        component: () => import("../views/Work/SalaryDay.vue"),
        meta: { title: "每日工资详情", hideTabbar: true },
      },
      {
        path: "work/salary-stat",
        name: "WorkSalaryStat",
        component: () => import("../views/Work/SalaryStat.vue"),
        meta: { title: "月度工资统计", hideTabbar: true },
      },
    ],
  },
];

// 导出路由表
export default RouterMap
