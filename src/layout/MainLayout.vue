<template>
  <div class="app-wrapper">
    <van-nav-bar
      :title="pageTitle"
      fixed
      placeholder
      :left-arrow="showBackButton"
      @click-left="onBack"
    />

    <div class="main-body">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </div>

    <nav v-if="!route.meta.hideTabbar" class="floating-island-nav">
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
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();

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

// 执行返回 (保持原样)
const onBack = () => {
  if (showBackButton.value) {
    router.back();
  }
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

.main-body {
  width: 100%;
  /* 精准 Padding：岛屿高度60px + 悬浮20px + 预留间距 = 90px */
  padding-bottom: 90px;
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
  /* 使用半透明白色背景 (0.75 比较规整，平衡清晰度与果味) */
  background: rgba(255, 255, 255, 0);
  /* 核心：模糊背景 + 提升饱和度，非常果味 */
  backdrop-filter: blur(15px) saturate(160%);
  -webkit-backdrop-filter: blur(15px) saturate(160%); /* 兼容 iOS */

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
