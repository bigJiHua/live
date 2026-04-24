<template>
  <div class="app-wrapper">
    <van-nav-bar
      :title="pageTitle"
      fixed
      placeholder
      :left-arrow="showBackButton"
      @click-left="onBack"
    >
      <template #left>
        <van-icon name="arrow-left" v-if="showBackButton" @click.stop="onBack" />
        <van-icon name="wap-home-o" size="20" style="margin-left: 8px;" @click="goHome" />
      </template>
      <template #right>
        <van-icon name="lock" size="20" @click="handleLockSystem" style="margin-right: 12px;" />
        <van-icon name="expand" size="20" @click="toggleFullscreen" />
      </template>
    </van-nav-bar>

    <div class="main-body">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </div>

    <nav v-if="!route.meta.hideTabbar" 
         class="floating-island-nav"
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

  <!-- PIN 验证弹窗 -->
  <PinVerifyDialog ref="pinVerifyRef" />
</template>

<script setup>
import { ref, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showToast } from "vant";
import { authApi } from "@/utils/api/auth";
import PinVerifyDialog from "@/components/PinVerifyDialog.vue";

const route = useRoute();
const router = useRouter();

// PIN 验证引用
const pinVerifyRef = ref(null);

// 锁定系统
const handleLockSystem = async () => {
  console.log('🔒 锁定按钮被点击了');
  try {
    console.log('🔒 开始调用 lockSystem API');
    await authApi.lockSystem();
    console.log('🔒 API 调用成功');
    showToast({ message: '系统已锁定', position: 'top' });
    
    if (pinVerifyRef.value) {
      pinVerifyRef.value.show();
    }
  } catch (error) {
    console.error('🔒 锁定失败:', error);
    showToast({ message: error.message || '锁定失败', position: 'top' });
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
    showToast({ message: '已最大化显示', position: 'bottom', duration: 1500 });
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
  min-height: 100vh;
  /* 规整：清爽背景，突出毛玻璃 */
  background-color: #f7f8fa;
}

/* TODO 待定 */
.main-body {
  width: 100%;
  /* 精准 Padding：岛屿高度60px + 悬浮20px + 预留间距 = 90px */
  height: calc(100vh - 90px);
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
  /* 透明背景 + 毛玻璃模糊 */
  background-color: transparent;
  background-image: linear-gradient(135deg, rgba(255,255,255,0.1) 0%, rgba(255,255,255,0.05) 100%);
  /* 核心：模糊背景 + 提升饱和度，非常果味 */
  backdrop-filter: blur(15px) saturate(160%) !important;
  -webkit-backdrop-filter: blur(15px) saturate(160%) !important;

  /* ———— 阴影与边框，增加精致感和悬浮感 ———— */
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(182, 182, 182, 0.696); /* 超细的高亮边框 */
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
  color: #7d7e80; /* 未选中时的颜色 */
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
  color: #1989fa; /* 选中时的颜色 */
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
</style>
