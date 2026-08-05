<template>
  <div class="page-login-log">
    <div class="page-content">
      <div class="list-header">
        <span class="list-title">近期登录记录</span>
        <app-tag plain size="small">{{ logs.length }} 条</app-tag>
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
                <app-tag
                  v-if="log._suspicious"
                  :type="suspiciousType(log._suspicious)"
                  size="small"
                  class="suspicious-tag"
                >{{ suspiciousLabel(log._suspicious) }}</app-tag>
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

const extractRegion = (location, ip) => {
  // 优先用 login_location，其次用 IP 属地反查（IP 首个段做粗略判断）
  if (location && location !== "未知") {
    const parts = location.split("-").map(s => s.trim());
    return { country: parts[0] || "", province: parts[1] || "" };
  }
  // 用 IP 前缀做粗略属地（无法精确到省份，仅做国家区分）
  if (ip) {
    // 常见中国 IP 段
    if (/^(10\.|172\.(1[6-9]|2\d|3[01])|192\.168|127\.|0\.)/.test(ip)) {
      return { country: "中国（内网）", province: "" };
    }
    // 非内网 IP 且无位置信息 → 未知
    return { country: "未知", province: "" };
  }
  return { country: "", province: "" };
};

const suspiciousType = (s) => {
  const map = { "cross-border": "danger", "new-device": "danger", "rapid": "danger", "abnormal": "warning" };
  return map[s?.level || s] || "warning";
};

const suspiciousLabel = (s) => {
  if (typeof s === "object") {
    const map = { "cross-border": `跨境登录(${s.detail})`, "new-device": "新设备登录", "rapid": `频切异地(${s.detail})`, "abnormal": `异地登录(${s.detail})` };
    return map[s.level] || s.level;
  }
  const map = { "cross-border": "跨境登录", "abnormal": "疑似异地登录" };
  return map[s] || s;
};

const markSuspicious = (list) => {
  if (list.length < 2) return;
  const successLogs = list.filter(l => l.status !== 0);

  // 1. 设备指纹统计 — 识别「常住设备」
  const fpCount = {};
  successLogs.forEach(l => { if (l.fingerprint) fpCount[l.fingerprint] = (fpCount[l.fingerprint] || 0) + 1; });
  let mainFP = "", maxFP = 0;
  for (const [fp, n] of Object.entries(fpCount)) { if (n > maxFP) { maxFP = n; mainFP = fp; } }

  // 2. 国家/省份频率统计
  const logRegion = {};
  const countryCount = {}, provinceByCountry = {};
  successLogs.forEach(l => {
    const r = extractRegion(l.login_location, l.login_ip);
    logRegion[l.id] = r;
    if (r.country) { countryCount[r.country] = (countryCount[r.country] || 0) + 1; }
    if (r.country && r.province) {
      const key = `${r.country}|${r.province}`;
      provinceByCountry[key] = (provinceByCountry[key] || 0) + 1;
    }
  });
  let mainCountry = "", maxCountry = 0;
  for (const [c, n] of Object.entries(countryCount)) { if (n > maxCountry) { maxCountry = n; mainCountry = c; } }
  let mainProvince = "";
  if (mainCountry) {
    let maxProv = 0;
    for (const [key, n] of Object.entries(provinceByCountry)) {
      const [c, p] = key.split("|");
      if (c === mainCountry && n > maxProv) { maxProv = n; mainProvince = p; }
    }
  }

  // 3. 时间窗口检测 — 2h 内两省切换 → 异常
  const timeThreshold = 2 * 60 * 60 * 1000;
  const sorted = [...successLogs].sort((a, b) => Number(a.login_time) - Number(b.login_time));
  for (let i = 1; i < sorted.length; i++) {
    const prev = sorted[i - 1], curr = sorted[i];
    const diff = Number(curr.login_time) - Number(prev.login_time);
    if (diff > 0 && diff <= timeThreshold) {
      const p1 = logRegion[prev.id]?.province || extractRegion(prev.login_location, prev.login_ip).province;
      const p2 = logRegion[curr.id]?.province || extractRegion(curr.login_location, curr.login_ip).province;
      if (p1 && p2 && p1 !== p2) {
        if (!prev._suspicious) prev._suspicious = { level: "rapid", detail: `${p1}⇢${p2}` };
        if (!curr._suspicious) curr._suspicious = { level: "rapid", detail: `${p1}⇢${p2}` };
      }
    }
  }

  // 4. 逐条标记
  for (const log of list) {
    if (log.status === 0) continue;
    // 新设备
    if (mainFP && log.fingerprint && log.fingerprint !== mainFP) {
      if (!log._suspicious) log._suspicious = { level: "new-device", detail: "新设备" };
      continue;
    }
    if (log._suspicious) continue; // 已由时间窗口标记
    const r = logRegion[log.id];
    if (!r || !r.country) continue;
    if (r.country !== mainCountry) {
      log._suspicious = { level: "cross-border", detail: r.country };
    } else if (mainProvince && r.province && r.province !== mainProvince) {
      log._suspicious = { level: "abnormal", detail: r.province };
    }
  }
};

onMounted(loadLogs);
</script>

<style scoped>
.page-login-log {
  min-height: 100vh;
  background: var(--theme-bg-primary);
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
  color: var(--theme-text-primary);
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
  background: var(--theme-bg-secondary);
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
.log-type.type-login { background: var(--van-green, #07c160); }
.log-type.type-logout { background: var(--van-gray, #969799); }
.log-type.type-refresh { background: var(--theme-primary); }
.log-type.type-failed { background: var(--van-danger-color, #ee0a24); }
.log-time {
  font-size: 11px;
  color: var(--theme-text-tertiary);
}
.log-body {
  font-size: 12px;
  color: var(--theme-text-primary);
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
  color: var(--theme-text-tertiary);
  margin-right: 3px;
  flex-shrink: 0;
}
.info-text {
  color: var(--theme-text-primary);
}
.info-text.isp {
  color: var(--theme-text-tertiary);
}
.info-sep {
  color: var(--theme-text-tertiary);
  margin: 0 2px;
}
.info-text.device {
  color: var(--theme-primary);
}
.ua-row {
  margin-top: 3px;
}
.ua-text {
  font-size: 10px;
  color: var(--theme-text-tertiary);
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
  background: var(--van-danger-bg);
  border-radius: 4px;
}
.error-icon {
  font-size: 12px;
  color: var(--theme-danger);
  flex-shrink: 0;
}
.error-text {
  font-size: 11px;
  color: var(--theme-danger);
}
</style>