<template>
  <div class="page-user">
    <div class="profile-card" @click="handleEditProfile">
      <van-image
        round
        width="64"
        height="64"
        :src="userData.avatar"
        class="profile-avatar"
      />
      <div class="profile-info">
        <div class="nickname-row">
          <span class="nickname">{{ userData.username }}</span>
        </div>
        <div class="user-id">邮箱： {{ userData.email }}</div>
      </div>
      <van-icon name="arrow" color="#969799" size="16" />
    </div>

    <div class="menu-sections">
      <div class="section-title">安全与隐私</div>
      <van-cell-group inset class="app-card">
        <van-cell title="PIN 码访问锁定" label="进入系统需二次验证" center>
          <template #right-icon>
            <van-switch
              v-model="security.pinEnabled"
              size="22px"
              @change="onPinSwitch"
            />
          </template>
        </van-cell>
        <van-cell title="PIN 码管理" is-link center @click="goToPinManage" />
      </van-cell-group>

      <div class="section-title">系统管理</div>
      <van-cell-group inset class="app-card">
        <van-cell
          title="应用设置"
          icon="setting-o"
          is-link
          @click="$router.push('/user/app-settings')"
        />
        <van-cell
          title="文件资源管理"
          icon="photograph"
          is-link
          @click="$router.push('/user/resource-manage')"
        />
      </van-cell-group>
    </div>

    <div class="logout-wrapper">
      <van-button block round type="danger" plain @click="onLogout"
        >退出当前登录</van-button
      >
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive } from "vue";
import { useRouter } from "vue-router";
import { showConfirmDialog, showToast } from "vant";
import { useUserStore } from "@/stores/user";
import { authApi } from "@/utils/api/auth";
import { securityApi } from "@/utils/api/security";

const router = useRouter();
const userStore = useUserStore();

const security = reactive({ pinEnabled: false, hasPinSet: false });

// 检查 PIN 设置状态（后端 + 本地双校验）
const checkPinStatus = async () => {
  try {
    const res = await securityApi.checkPin();
    // 200 = 已设置 PIN
    if (res.status === 200) {
      security.hasPinSet = true;
    } else {
      security.hasPinSet = false;
    }
  } catch (err) {
    // 400 或其他错误 = 未设置 PIN
    security.hasPinSet = false;
  }
  // 同步本地状态与后端一致
  localStorage.setItem("pin_enabled", security.hasPinSet ? "true" : "false");
  security.pinEnabled = security.hasPinSet;
};

// 计算属性显示用户信息
const userData = computed(() => ({
  username: userStore.username,
  email: userStore.email,
  avatar: userStore.actualAvatar,
}));

// 获取用户信息（优先使用缓存，手动触发或刷新时获取最新数据）
const getUserInfo = async () => {
  // 如果不是强制刷新且已有缓存，则跳过
  if (userStore.hasUserInfo) return;

  try {
    const res = await authApi.getUserinfo();
    // 使用后端返回的完整用户数据更新 store
    userStore.setUserInfo(res.data);
  } catch (err) {
    console.error("获取用户信息失败:", err);
  }
};

// PIN码开关
const onPinSwitch = (checked) => {
  if (checked && !security.hasPinSet) {
    showToast("请先设置 PIN 码");
    security.pinEnabled = false;
  } else {
    security.pinEnabled = true;
    showToast("请到设置PIN码页面修改");
  }
};
// 跳转个人资料编辑页面
const handleEditProfile = () => {
  router.push("/profile-edit");
};
// 管理PIN码跳转
const goToPinManage = () => {
  router.push("/user/pin-manage");
};
// 退出登录
const onLogout = async () => {
  showConfirmDialog({ title: "提醒", message: "确定退出登录？" }).then(
    async () => {
      userStore.clearUserInfo();
      router.push("/login");
      localStorage.removeItem("finance_token");
      showToast("已安全退出");
    }
  );
};

onMounted(() => {
  getUserInfo();
  checkPinStatus();
});
</script>

<style scoped>
.page-user {
  background: #f7f8fa;
  max-height: 100vh;
  overflow-y: auto;
  padding-bottom: 30px;
}

.profile-card {
  background: white;
  padding: 50px 20px 30px;
  display: flex;
  align-items: center;
  transition: background 0.2s;
}
.profile-card:active {
  background: #f2f3f5;
}

.profile-avatar {
  margin-right: 16px;
  border: 1px solid #f2f3f5;
}

.profile-info {
  flex: 1;
}

.nickname-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.nickname {
  font-size: 20px;
  font-weight: bold;
  color: #323233;
}

.user-id {
  font-size: 13px;
  color: #969799;
}

.section-title {
  padding: 20px 20px 10px;
  font-size: 13px;
  color: #969799;
  font-weight: 500;
}
.app-card {
  border-radius: 12px;
  overflow: hidden;
}
.logout-wrapper {
  margin: 40px 24px;
}
</style>
