<template>
  <div class="app-wrapper">
    <van-nav-bar
      :title="pageTitle"
      fixed
      placeholder
      :left-arrow="showBackButton"
    >
      <template #left>
        <div class="nav-bar-left">
          <van-icon name="arrow-left" v-if="showBackButton" class="nav-left-icon" @click.stop="onBack" />
          <van-icon name="wap-home-o" class="nav-left-icon" @click="goHome" />
        </div>
      </template>
      <template #right>
        <van-icon name="lock" size="20" @click="handleLockSystem" style="margin-right: 12px;" />
        <van-icon name="expand" size="20" @click="toggleFullscreen" />
      </template>
    </van-nav-bar>

    <div class="main-body">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <keep-alive :include="cachedViewNames">
            <component :is="Component" />
          </keep-alive>
        </transition>
      </router-view>
    </div>

    <nav v-if="!route.meta.hideTabbar" 
         class="floating-island-nav"
         :class="{ 'nav-hidden': !tabbarVisible }"
         style="backdrop-filter: blur(15px) saturate(160%); -webkit-backdrop-filter: blur(15px) saturate(160%);">
      <router-link to="/home" class="nav-item">
        <van-icon name="wap-home-o" class="nav-icon" />
        <span class="nav-text">首页</span>
      </router-link>

      <router-link to="/finance" class="nav-item">
        <van-icon name="bill-o" class="nav-icon" />
        <span class="nav-text">账本</span>
      </router-link>

      <router-link to="/diary" class="nav-item">
        <van-icon name="notes-o" class="nav-icon" />
        <span class="nav-text">动态</span>
      </router-link>

      <router-link to="/user" class="nav-item">
        <van-icon name="user-o" class="nav-icon" />
        <span class="nav-text">我的</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showToast } from '@/utils/toast';
import { authApi } from "@/utils/api/auth";
import { showPinDialog } from "@/utils/request/pin";

const route = useRoute();
const router = useRouter();

// 底部 tabbar 滚动沉浸：下滑收起、上滑弹起
const tabbarVisible = ref(true);
let lastScrollY = window.scrollY || 0;
const onWindowScroll = () => {
  const y = window.scrollY || 0;
  const delta = y - lastScrollY;
  if (Math.abs(delta) > 6) {
    if (delta > 0) {
      tabbarVisible.value = false;
    } else if (y > 0) {
      tabbarVisible.value = true;
    }
    lastScrollY = y;
  }
};
onMounted(() => window.addEventListener("scroll", onWindowScroll, { passive: true }));
onBeforeUnmount(() => window.removeEventListener("scroll", onWindowScroll));

// 局部 keep-alive：通过 include 按组件 name 精准缓存带 onActivated 滚动恢复的
// 列表页（与 router/map.js 中 keepAlive:true 路由一致）。
// 详情/编辑/tab 根等页不缓存，保证每次进入重新拉取实时数据。
// 缓存页在 onActivated 内通过 flowSync 消费变更，确保缓存期间数据不过期。
const cachedViewNames = [
  "FinanceFlowList",
  "FinanceFlowCalendar",
  "FinanceReportFlowFilter",
  "FinanceReportCardFlow",
  "FinanceReportMonthlyTrend",
  "FinanceReportDebtOverview",
  "CardFlowList",
  "BillLedger",
  "BankCardDebit",
  "BankCardCredit",
  "BillList",
];

// 锁定系统
// ⚠ 不要在本组件里 new 一个 PinVerifyDialog：全项目只有 App.vue 里那个注册进了 pin.js
// （registerPinDialog），自建实例会出现"验证成功了但弹窗关不掉"。
// 这里改用 showPinDialog() 弹全局那一个，锁定成功后立刻要求解锁，不等下次请求。
const handleLockSystem = async () => {
  try {
    const res = await authApi.lockSystem();
    showToast({ message: res?.message || '系统已锁定', position: 'top' });
    showPinDialog();
  } catch (error) {
    showToast({ message: error?.message || '锁定失败', position: 'top' });
  }
};

// 全屏切换（适配全机型）
const toggleFullscreen = () => {
  const docEl = document.documentElement;
  
  // 检测是否为 iOS Safari
  const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) && !window.MSStream;
  const isSafari = /^((?!chrome|android).)*safari/i.test(navigator.userAgent);
  
  // 尝试标准 API
  if (!document.fullscreenElement) {
    if (docEl.requestFullscreen) {
      docEl.requestFullscreen().catch(() => {});
      return;
    }
    
    // iOS Safari 使用 webkit API
    if (isIOS || isSafari) {
      if (docEl.webkitRequestFullscreen) {
        docEl.webkitRequestFullscreen();
        return;
      }
    }
    
    // 降级方案：使用 CSS 模拟全屏
    docEl.style.overflow = 'hidden';
    docEl.style.height = '100vh';
    showToast({ message: '已最大化显示', duration: 1500 });
  } else {
    if (document.exitFullscreen) {
      document.exitFullscreen().catch(() => {});
    } else if (document.webkitExitFullscreen) {
      document.webkitExitFullscreen();
    } else {
      // 恢复降级方案
      docEl.style.overflow = '';
      docEl.style.height = '';
    }
  }
};

// 映射路由标题 - 优先从 meta.title 获取
const pageTitle = computed(() => {
  // 特殊处理：PIN 码设置页面根据 mode 动态显示
  if (route.path === "/user/pin-setup") {
    return route.query.mode === "modify" ? "修改 PIN 码" : "设置 PIN 码";
  }

  // 从路由 meta.title 获取标题
  if (route.meta?.title) {
    return route.meta.title;
  }

  // 默认标题
  return "人生记录";
});

// 判断是否显示返回键 (保持原样)
const showBackButton = computed(() => {
  const mainPages = ["/home", "/finance", "/diary", "/user"];
  return !mainPages.includes(route.path);
});

let cNum = ref(0)
// 执行返回 (保持原样)
const onBack = () => {
  cNum++;
  if (cNum >= 3) return router.push('/')
  if (showBackButton.value) {
    router.back();
  }
};

const goHome = () => {
  router.replace('/home');
};
</script>

<style scoped>
/* 3. 规整的主体逻辑 CSS */
.app-wrapper {
  width: 100%;
  /* ⚠⚠ 2026-10-12 负责人报的 bug：移动端 100vh（大视口）> 当前可视视口时文档会外滚，
     卡片页随文档整体上滑，.page-card-list 顶部窜到固定 nav 后面，而卡片栈容器
     .card-stack-container z-index:9999 > nav 1000 ⇒ 卡片直接盖住顶部导航栏
     （「借记卡/管理你的银行卡/右侧按钮凸显」同源问题）。100dvh = 当前可视高，
     从几何上杜绝文档外溢；不支持 dvh 的旧浏览器自动回落上一行 vh。 */
  min-height: 100vh;
  min-height: 100dvh;
  /* 跟随主题背景（浅色/深色自动切换） */
  background-color: var(--theme-bg-primary);
}

/* ⚠⚠ 2026-10-12 负责人报的 bug：移动端 100vh 大于可视视口时文档会外滚，
   卡片页页头（.card-manage-header z-index:10）趁机压住顶部导航栏
   （Vant nav-bar 默认 z-index:1）——「借记卡/管理你的银行卡/右侧按钮凸显在导航上」。
   根因是页头 z-index 10 > 导航 1。这里把顶部导航栏层级提到页面内容之上
   （底部悬浮岛 999 不冲突；卡片弹出层 95000+ 是模态，仍应盖住导航，不受影响）。 */
:deep(.van-nav-bar) {
  z-index: 1000;
}

/* TODO 待定 */
.main-body {
  width: 100%;
  /* 精准 Padding：岛屿高度60px + 悬浮20px + 预留间距 = 90px */
  /* 同上：dvh 随当前可视视口，杜绝移动端文档外溢把卡片页带上滑（2026-10-12） */
  height: calc(100vh - 90px);
  height: calc(100dvh - 90px);
}

/* 4. 纯手撸悬浮岛导航栏核心 CSS (纯果味实现) */
.floating-island-nav {
  /* 精准：手动控制全悬浮布局 */
  position: fixed;
  bottom: 20px; /* 距离底部 20px 悬浮 */
  left: 16px; /* 距离左侧 16px */
  right: 16px; /* 距离右侧 16px */

  /* 岛屿形状与高度 */
  height: 60px; /* 规整高度 */
  border-radius: 30px; /* 大倒角，实现圆柱形岛屿 */

  /* 规整布局：Flex 均分 */
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 0 10px; /* 内部左右 Padding */
  box-sizing: border-box;
  z-index: 999; /* 保证在最顶层 */

  /* ———— 极致核心：毛玻璃效果 (Backdrop Blur) ———— */
  /* 透明背景 + 毛玻璃模糊（跟随主题的玻璃色，深色下为深半透明） */
  background-color: var(--tabbar-glass-bg);
  background-image: none;
  /* 核心：模糊背景 + 提升饱和度，非常果味 */
  backdrop-filter: blur(15px) saturate(160%) !important;
  -webkit-backdrop-filter: blur(15px) saturate(160%) !important;

  /* ———— 阴影与边框，增加精致感和悬浮感 ———— */
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  border: 1px solid var(--tabbar-glass-border); /* 跟随主题的高亮边框 */

  /* 滚动沉浸：收起/弹起过渡 */
  transition: transform 0.3s ease, opacity 0.3s ease;
}

/* 下滑浏览时收起底部岛 */
.floating-island-nav.nav-hidden {
  transform: translateY(120px);
  opacity: 0;
  pointer-events: none;
}

/* 5. 导航项 (nav-item) 样式 */
.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1; /* 均分宽度 */
  height: 100%;
  text-decoration: none; /* 去掉下划线 */
  color: var(--tabbar-text); /* 未选中时的颜色（跟随主题） */
  transition: color 0.2s ease; /* 颜色过渡动画 */
  border-radius: 30px; /* 预留，以便选中态显示背景高亮 */
}

/* 导航图标 */
.nav-icon {
  font-size: 22px; /* 图标大小 */
  margin-bottom: 2px; /* 与文字的间距 */
}

/* 导航文字 */
.nav-text {
  font-size: 11px; /* 文字大小 */
  font-weight: 500;
}

/* 6. 核心：router-link 选中时的样式 (.router-link-active) */
.nav-item.router-link-active {
  color: var(--theme-primary); /* 选中时的颜色（跟随主题主色） */
}

/* (可选) 选中时增加一个微弱的背景高亮，更显规整 */
/* 必须保证背景也是半透明的，否则擋住毛玻璃 */
/* .nav-item.router-link-active { */
/* background-color: rgba(25, 137, 250, 0.08); */
/* } */

/* 路由切换过渡动画 (规整保留) */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 导航栏左侧按钮：放大点击热区，且图标间互不冲突 */
:deep(.van-nav-bar__left) {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
}

/* 每个图标独立热区：padding 放大点击范围，
   由 flex gap 分隔，热区边界清晰、不重叠 */
:deep(.van-nav-bar__left .nav-left-icon) {
  font-size: 20px; /* 明确图标大小，避免回落默认导致视觉变小 */
  padding: 10px;
}
</style>
