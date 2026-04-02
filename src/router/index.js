import { createRouter, createWebHistory } from "vue-router";
import { showToast } from "vant"; // 引入 Vant 的轻提示
import RouterMap from "@/router/map";

// 1. 创建路由实例
const router = createRouter({
  history: createWebHistory("/"),
  routes: RouterMap,
});

/**
 * 🔒 纯原生解析 JWT Payload
 * 逻辑：只读取 Token 里的信息（如 role），不涉及服务端签名验证
 */
const parseJwtPayload = (tokenStr) => {
  if (!tokenStr) return null;
  try {
    // 处理可能存在的 Bearer 前缀
    const jwt = tokenStr.replace(/^Bearer\s*/i, "");
    const payloadPart = jwt.split(".")[1];
    if (!payloadPart) return null;

    // Base64 补齐并解码
    const paddedPayload = payloadPart.padEnd(
      payloadPart.length + ((4 - (payloadPart.length % 4)) % 4),
      "="
    );
    return JSON.parse(atob(paddedPayload));
  } catch (err) {
    console.error("Token 解析失败:", err);
    return null;
  }
};

/**
 * 🛡️ 权限白名单配置
 */
const whiteList = {
  // 所有人（包括未登录）可访问
  public: ["/login", "/register"],
};

// 2. 整合后的全局前置守卫 (Vue 3 异步返回写法)
router.beforeEach(async (to, from) => {
  // --- A. 基础设置 ---
  document.title = to.meta.title || "Golden Finance";
  const token = localStorage.getItem("finance_token");

  // --- B. 白名单放行 ---
  // 使用白名单检查，避免硬编码 if 条件
  if (
    whiteList.public.includes(to.path) ||
    whiteList.public.includes(to.name)
  ) {
    return true;
  }

  // --- C. 登录状态校验 ---
  if (!token) {
    showToast({
      message: "非法闯入",
      position: "top"
    });

    // 🌟 不再使用 next('/login')，直接 return 目标路径
    // 这样会自动中断当前导航并重定向，且不会触发 deprecated 警告
    return { name: "Login" };
  }

  // --- D. 角色权限校验 (如果以后需要) ---
  /*
  const payload = parseJwtPayload(token)
  const userRole = payload?.role || 'viewer'
  if (userRole !== 'admin' && to.path.startsWith('/admin')) {
    showToast('权限不足')
    return '/home' // 拦截并重定向
  }
  */

  // --- E. 默认放行 ---
  return true;
});

// 3. 导出实例
export default router;
