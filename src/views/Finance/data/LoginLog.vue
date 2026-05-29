<template>
  <div class="page-login-log">
    <div class="page-content">
      <div class="list-header">
        <span class="list-title">近期登录记录</span>
        <van-tag plain size="small">{{ logs.length }} 条</van-tag>
      </div>

      <van-loading v-if="loading" class="loading-center" size="24px">加载中...</van-loading>

      <template v-else-if="logs.length === 0">
        <van-empty description="暂无登录记录" />
      </template>

      <template v-else>
        <div class="log-list">
          <div
            v-for="log in logs"
            :key="log.id"
            class="log-item"
            :class="{ 'is-failed': log.status === 0 }"
          >
            <div class="log-header">
              <div class="log-header-left">
                <span class="log-type" :class="'type-' + (log.type || 'login')">
                  {{ typeLabel(log.type) }}
                </span>
                <van-tag
                  v-if="log._suspicious"
                  :type="log._suspicious === 'cross-border' ? 'danger' : 'warning'"
                  size="small"
                  class="suspicious-tag"
                >{{ log._suspicious === 'cross-border' ? '跨境登录' : '疑似异地登录' }}</van-tag>
              </div>
              <span class="log-time">{{ formatTime(log.login_time || log.create_time) }}</span>
            </div>

            <div class="log-body">
              <div class="info-row">
                <van-icon name="location-o" class="info-icon" />
                <span class="info-text">{{ log.login_ip || '未知IP' }}</span>
                <span class="info-sep">·</span>
                <span class="info-text">{{ log.login_location || '未知位置' }}</span>
                <span v-if="log.login_isp && log.login_isp !== '未知'" class="info-sep">·</span>
                <span v-if="log.login_isp && log.login_isp !== '未知'" class="info-text isp">{{ log.login_isp }}</span>
              </div>

              <div class="device-row" v-if="log.os_info || log.device_model || log.browser_info">
                <van-icon name="computer-o" class="info-icon" />
                <span class="info-text">{{ log.os_info || '' }}</span>
                <span v-if="log.browser_info" class="info-sep">·</span>
                <span class="info-text" v-if="log.browser_info">{{ log.browser_info }}</span>
                <span v-if="log.device_model" class="info-sep">·</span>
                <span class="info-text device" v-if="log.device_model">{{ log.device_model }}</span>
              </div>

              <div class="ua-row" v-if="log.user_agent">
                <span class="ua-text">{{ log.user_agent }}</span>
              </div>

              <div class="error-row" v-if="log.status === 0 && log.error_message">
                <van-icon name="warning-o" class="error-icon" />
                <span class="error-text">{{ log.error_message }}</span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { authApi } from "@/utils/api/auth";
import { showToast } from "vant";

const loading = ref(false);
const logs = ref([]);

const typeLabel = (type) => {
  const map = {
    login: "登录",
    logout: "登出",
    refresh: "刷新",
    failed: "失败",
  };
  return map[type] || type;
};

const formatTime = (ts) => {
  if (!ts) return "";
  const d = new Date(Number(ts));
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

const loadLogs = async () => {
  loading.value = true;
  try {
    const res = await authApi.getLoginLogs();
    if (res.status === 200) {
      const raw = res.data || [];
      markSuspicious(raw);
      logs.value = raw;
    } else {
      showToast({ message: res.message || "获取日志失败", type: "fail" });
    }
  } catch (e) {
    console.error("[LoginLog] load error:", e);
    showToast({ message: "获取登录日志失败", type: "fail" });
  } finally {
    loading.value = false;
  }
};

const extractRegion = (location) => {
  if (!location || location === "未知") return { country: "", province: "" };
  const parts = location.split("-").map(s => s.trim());
  return {
    country: parts[0] || "",
    province: parts[1] || "",
  };
};

const markSuspicious = (list) => {
  const countryCount = {};
  const provinceCount = {};
  for (const log of list) {
    if (log.status !== 0) {
      const { country, province } = extractRegion(log.login_location);
      if (country) countryCount[country] = (countryCount[country] || 0) + 1;
      if (country && province) {
        const key = country + "|" + province;
        provinceCount[key] = (provinceCount[key] || 0) + 1;
      }
    }
  }
  let mainCountry = "";
  let maxCountry = 0;
  for (const [c, count] of Object.entries(countryCount)) {
    if (count > maxCountry) {
      maxCountry = count;
      mainCountry = c;
    }
  }
  if (!mainCountry) return;
  let mainProvince = "";
  let maxProvince = 0;
  for (const [key, count] of Object.entries(provinceCount)) {
    const [c] = key.split("|");
    if (c === mainCountry && count > maxProvince) {
      maxProvince = count;
      mainProvince = key.split("|")[1];
    }
  }
  for (const log of list) {
    if (log.status !== 0) {
      const { country, province } = extractRegion(log.login_location);
      if (!country) continue;
      if (country !== mainCountry) {
        log._suspicious = "cross-border";
      } else if (mainProvince && province && province !== mainProvince) {
        log._suspicious = "abnormal";
      }
    }
  }
};

onMounted(loadLogs);
</script>

<style scoped>
.page-login-log {
  min-height: 100vh;
  background: #f7f8fa;
}
.page-content {
  padding: 12px 16px 30px;
}
.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.list-title {
  font-size: 15px;
  font-weight: 600;
  color: #323233;
}
.loading-center {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}
.log-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.log-item {
  background: #fff;
  border-radius: 8px;
  padding: 10px 12px;
  border-left: 3px solid #07c160;
}
.log-item.is-failed {
  border-left-color: #ee0a24;
}
.log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.log-header-left {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.suspicious-tag {
  flex-shrink: 0;
}
.log-type {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 8px;
  border-radius: 3px;
  color: #fff;
}
.log-type.type-login { background: #07c160; }
.log-type.type-logout { background: #969799; }
.log-type.type-refresh { background: #1989fa; }
.log-type.type-failed { background: #ee0a24; }
.log-time {
  font-size: 11px;
  color: #969799;
}
.log-body {
  font-size: 12px;
  color: #323233;
  line-height: 1.6;
}
.info-row, .device-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
}
.info-icon {
  font-size: 12px;
  color: #969799;
  margin-right: 3px;
  flex-shrink: 0;
}
.info-text {
  color: #323233;
}
.info-text.isp {
  color: #969799;
}
.info-sep {
  color: #c8c9cc;
  margin: 0 2px;
}
.info-text.device {
  color: #1989fa;
}
.ua-row {
  margin-top: 3px;
}
.ua-text {
  font-size: 10px;
  color: #969799;
  word-break: break-all;
  display: block;
  line-height: 1.4;
  max-height: 2.8em;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.error-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  padding: 4px 8px;
  background: #fff2f0;
  border-radius: 4px;
}
.error-icon {
  font-size: 12px;
  color: #ee0a24;
  flex-shrink: 0;
}
.error-text {
  font-size: 11px;
  color: #ee0a24;
}
</style>