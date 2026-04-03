import { createRouter, createWebHistory } from "vue-router";
import { showToast } from "vant";
import RouterMap from "@/router/map";

const router = createRouter({
  history: createWebHistory("/"),
  routes: RouterMap,
});

const whiteList = {
  public: ["/login", "/register", "/429"],
};

router.beforeEach(async (to, from) => {
  document.title = to.meta.title || "Golden Finance";
  const token = localStorage.getItem("finance_token");

  if (whiteList.public.includes(to.path) || whiteList.public.includes(to.name)) {
    return true;
  }

  if (!token) {
    showToast({ message: "请先登录", position: "top" });
    return { name: "Login" };
  }
  if (to.matched.length === 0) {
    showToast({
      message: "页面不存在，已返回首页",
      position: "top",
    });
    return "/";
  }

  return true;
});

export default router;