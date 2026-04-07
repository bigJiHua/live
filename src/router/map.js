const RouterMap = [
  // --- 登录页面 (独立于布局之外，实现全屏展示) ---
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Auth/Login.vue'),
    meta: { title: '登录' }
  },

  // --- 注册页面 (独立于布局之外，实现全屏展示) ---
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/Auth/register.vue'),
    meta: { title: '登录' }
  },

  // --- 错误页面 ---
  {
    path: '/429',
    name: 'Error429',
    component: () => import('@/views/error/429.vue'),
    meta: { title: '请求过于频繁' }
  },

  // --- 主布局容器 (包含底部 Tabbar 导航) ---
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('../views/Home/index.vue'),
        meta: { title: '首页' }
      },
      // 账本/金融模块
      {
        path: 'finance',
        name: 'Finance',
        component: () => import('../views/Finance/index.vue'),
        meta: { title: '账本' }
      },
      {
        path: 'finance/add',
        name: 'FinanceAdd',
        component: () => import('../views/Finance/Add.vue'),
        meta: { title: '新增账单', hideTabbar: true }
      },
      // 动态/日记模块
      {
        path: 'diary',
        name: 'Diary',
        component: () => import('../views/Diary/index.vue'),
        meta: { title: '动态' }
      },
      {
        path: 'diary/add',
        name: 'DiaryAdd',
        component: () => import('../views/Diary/Add.vue'),
        meta: { title: '发布动态', hideTabbar: true }
      },
      {
        path: 'diary/detail',
        name: 'DiaryDetail',
        component: () => import('../views/Diary/Detail.vue'),
        meta: { title: '动态详情', hideTabbar: true }
      },
      // 个人中心
      {
        path: 'user',
        name: 'User',
        component: () => import('../views/User/index.vue'),
        meta: { title: '我的' }
      },
      {
        path: 'user/pin-manage',
        name: 'PinManage',
        component: () => import('../views/User/PinManage.vue'),
        meta: { title: 'PIN 码管理', hideTabbar: true }
      },
      {
        path: 'user/pin-setup',
        name: 'PinSetup',
        component: () => import('../views/User/PinSetup.vue'),
        meta: { title: '设置 PIN 码', hideTabbar: true }
      },
      {
        path: 'user/device-info',
        name: 'DeviceInfo',
        component: () => import('../views/User/DeviceInfo.vue'),
        meta: { title: '设备信息', hideTabbar: true }
      },
      {
        path: 'user/resource-manage',
        name: 'ResourceManage',
        component: () => import('../views/User/ResourceManage.vue'),
        meta: { title: '文件资源管理', hideTabbar: true }
      },
      {
        path: 'user/resource-list',
        name: 'ResourceList',
        component: () => import('../views/User/ResourceList.vue'),
        meta: { title: '资源列表', hideTabbar: true }
      },
      {
        path: 'user/app-settings',
        name: 'AppSettings',
        component: () => import('../views/User/AppSettings.vue'),
        meta: { title: '应用设置', hideTabbar: true }
      },
      {
        path: 'user/category-manage',
        name: 'CategoryManage',
        component: () => import('../views/User/CategoryManage.vue'),
        meta: { title: '分类管理', hideTabbar: true }
      },
      {
        path: 'user/bank-category-manage',
        name: 'BankCategoryManage',
        component: () => import('../views/User/BankCategoryManage.vue'),
        meta: { title: '银行分类管理', hideTabbar: true }
      },
      {
        path: 'card',
        name: 'BankCard',
        component: () => import('../views/BankCard/index.vue'),
        meta: { title: '银行卡管理', hideTabbar: true },
        children: [
          {
            path: 'debit',
            name: 'BankCardDebit',
            component: () => import('../views/BankCard/debit.vue')
          },
          {
            path: 'credit',
            name: 'BankCardCredit',
            component: () => import('../views/BankCard/credit.vue')
          }
        ]
      },
      {
        path: 'card/add',
        name: 'BankCardAdd',
        component: () => import('../views/BankCard/Add.vue'),
        meta: { title: '添加卡片', hideTabbar: true }
      },
      {
        path: 'card/edit',
        name: 'BankCardEdit',
        component: () => import('../views/BankCard/Edit.vue'),
        meta: { title: '编辑卡片', hideTabbar: true }
      },
      // 账单管理
      {
        path: 'card/bill/list',
        name: 'BillList',
        component: () => import('../views/BankCard/BillList.vue'),
        meta: { title: '账单列表', hideTabbar: true }
      },
      {
        path: 'card/bill/detail',
        name: 'BillDetail',
        component: () => import('../views/BankCard/BillDetail.vue'),
        meta: { title: '账单详情', hideTabbar: true }
      },
      {
        path: 'card/bill/add',
        name: 'BillAdd',
        component: () => import('../views/BankCard/BillAdd.vue'),
        meta: { title: '添加账单', hideTabbar: true }
      },
      {
        path: 'card/bill/edit',
        name: 'BillEdit',
        component: () => import('../views/BankCard/BillEdit.vue'),
        meta: { title: '编辑账单', hideTabbar: true }
      },
      // 还款记录
      {
        path: 'card/repay/list',
        name: 'RepayList',
        component: () => import('../views/BankCard/RepayList.vue'),
        meta: { title: '还款记录', hideTabbar: true }
      },
      {
        path: 'card/repay/detail',
        name: 'RepayDetail',
        component: () => import('../views/BankCard/RepayDetail.vue'),
        meta: { title: '还款详情', hideTabbar: true }
      },
      {
        path: 'card/repay/add',
        name: 'RepayAdd',
        component: () => import('../views/BankCard/RepayAdd.vue'),
        meta: { title: '添加还款', hideTabbar: true }
      },
      {
        path: 'card/repay/edit',
        name: 'RepayEdit',
        component: () => import('../views/BankCard/RepayEdit.vue'),
        meta: { title: '编辑还款', hideTabbar: true }
      },
      {
        path: 'profile-edit',
        name: 'ProfileEdit',
        component: () => import('../views/User/ProfileEdit.vue'),
        meta: { title: '编辑资料', hideTabbar: true }
      },
    ],
  },
]

// 导出路由表
export default RouterMap
