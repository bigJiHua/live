const RouterMap = [
  // --- 登录页面 (独立于布局之外，实现全屏展示) ---
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login/index.vue'),
    meta: { title: '登录' }
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
        component: () => import('../views/Finance/Add1.vue'),
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
        path: 'user/theme-settings',
        name: 'ThemeSettings',
        component: () => import('../views/User/ThemeSettings.vue'),
        meta: { title: '主题配色', hideTabbar: true }
      },
    ],
  },
]

// 导出路由表
export default RouterMap
