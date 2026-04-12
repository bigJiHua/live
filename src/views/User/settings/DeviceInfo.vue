<template>
  <div class="page-device-info">
    <div class="section-title">设备信息检测</div>

    <!-- UA 信息 -->
    <div class="info-card">
      <div class="card-title">User-Agent 解析</div>
      <van-cell-group inset>
        <van-cell
          title="浏览器"
          :value="uaInfo.browser.name + ' ' + (uaInfo.browser.version || '')"
        />
        <van-cell
          title="操作系统"
          :value="uaInfo.os.name + ' ' + (uaInfo.os.version || '')"
        />
        <van-cell title="设备类型" :value="uaInfo.device.type" />
        <van-cell title="设备厂商" :value="uaInfo.device.vendor || '未知'" />
        <van-cell title="设备型号" :value="uaInfo.device.model || '未知'" />
        <van-cell
          title="渲染引擎"
          :value="uaInfo.engine.name + ' ' + (uaInfo.engine.version || '')"
        />
      </van-cell-group>
    </div>

    <!-- 浏览器指纹 -->
    <div class="info-card">
      <div class="card-title">浏览器指纹</div>
      <van-cell-group inset>
        <van-cell title="指纹 Hash" :value="fingerprint.hash" />
        <van-cell title="Canvas 指纹" :value="fingerprint.canvas.hash" />
        <van-cell
          title="屏幕分辨率"
          :value="fingerprint.features.screenResolution"
        />
        <van-cell
          title="色彩深度"
          :value="fingerprint.features.screenColorDepth + ' bit'"
        />
        <van-cell title="像素比" :value="fingerprint.features.pixelRatio" />
        <van-cell title="时区" :value="fingerprint.features.timezone" />
        <van-cell title="语言" :value="fingerprint.features.language" />
        <van-cell title="平台" :value="fingerprint.features.platform" />
        <van-cell title="WebGL" :value="fingerprint.features.webgl" />
        <van-cell
          title="触摸支持"
          :value="fingerprint.features.touchSupport ? '是' : '否'"
        />
        <van-cell
          title="Cookie"
          :value="fingerprint.features.cookiesEnabled ? '启用' : '禁用'"
        />
      </van-cell-group>
    </div>

    <!-- 网络信息 -->
    <div class="info-card">
      <div class="card-title">网络信息</div>
      <van-pull-refresh v-model="refreshing" @refresh="refreshNetworkInfo">
        <van-cell-group inset>
          <van-cell
            title="IP 地址"
            :value="networkInfo.external?.ip || '加载中...'"
            :loading="loadingNetwork"
          />
          <van-cell
            title="国家"
            :value="networkInfo.external?.country || '-'"
          />
          <van-cell title="城市" :value="networkInfo.external?.city || '-'" />
          <van-cell title="ISP" :value="networkInfo.external?.isp || '-'" />
          <van-cell title="ASN" :value="networkInfo.external?.asn || '-'" />
          <van-cell
            title="时区"
            :value="networkInfo.external?.timezone || '-'"
          />
          <van-cell
            title="经纬度"
            :value="
              networkInfo.external?.latitude && networkInfo.external?.longitude
                ? `${networkInfo.external.latitude}, ${networkInfo.external.longitude}`
                : '-'
            "
          />
          <van-divider>本地网络</van-divider>
          <van-cell
            title="在线状态"
            :value="networkInfo.local?.online ? '在线' : '离线'"
          />
          <van-cell
            title="连接类型"
            :value="networkInfo.local?.connectionType || '-'"
          />
          <van-cell
            title="下行速度"
            :value="
              networkInfo.local?.downlink
                ? networkInfo.local.downlink + ' Mbps'
                : '-'
            "
          />
          <van-cell
            title="延迟"
            :value="
              networkInfo.local?.rtt ? networkInfo.local.rtt + ' ms' : '-'
            "
          />
          <van-cell
            title="省流量模式"
            :value="networkInfo.local?.saveData ? '开启' : '关闭'"
          />
        </van-cell-group>
      </van-pull-refresh>
    </div>

    <!-- 测试请求 -->
    <div class="info-card">
      <div class="card-title">测试请求</div>
      <div class="test-request">
        <van-button
          type="primary"
          block
          @click="testRequest"
          :loading="testing"
        >
          发送测试请求（携带所有头部信息）
        </van-button>
        <div v-if="requestResult" class="request-result">
          <div class="result-title">请求头信息（已发送到后端）：</div>
          <pre class="result-content">{{ formatRequestHeaders() }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import UAParser from "ua-parser-js";
import { generateBrowserFingerprint } from "@/utils/fingerprint";
import { getFullNetworkInfo } from "@/utils/network";
import { authApi } from "@/utils";
import { showToast } from "vant";
import ENV from "@/utils/env";

const uaParser = new UAParser();
const uaInfo = ref({});
const fingerprint = ref({ hash: "", canvas: { hash: "" }, features: {} });
const networkInfo = ref({ external: {}, local: {} });
const loadingNetwork = ref(false);
const refreshing = ref(false);
const testing = ref(false);
const requestResult = ref(null);

onMounted(() => {
  // 获取 UA 信息
  const ua = uaParser.getResult();
  uaInfo.value = {
    browser: {
      name: ua.browser.name || "unknown",
      version: ua.browser.version || "unknown",
    },
    os: {
      name: ua.os.name || "unknown",
      version: ua.os.version || "unknown",
    },
    device: {
      type: ua.device.type || "desktop",
      model: ua.device.model || "",
      vendor: ua.device.vendor || "",
    },
    engine: {
      name: ua.engine.name || "unknown",
      version: ua.engine.version || "unknown",
    },
  };

  // 获取浏览器指纹
  fingerprint.value = generateBrowserFingerprint();

  // 获取网络信息
  loadNetworkInfo();
});

const loadNetworkInfo = async () => {
  loadingNetwork.value = true;
  try {
    networkInfo.value = await getFullNetworkInfo();
  } catch (e) {
    console.error("加载网络信息失败:", e);
    showToast("加载网络信息失败");
  } finally {
    loadingNetwork.value = false;
  }
};

const refreshNetworkInfo = async () => {
  await loadNetworkInfo();
  refreshing.value = false;
  showToast("网络信息已更新");
};

const testRequest = async () => {
  testing.value = true;
  requestResult.value = null;

  try {
    // 模拟一个请求，实际会携带所有头部信息
    const response = await fetch(
      `${ENV.API_BASE_URL}/api/v1/test-headers`,
      {
        method: "GET",
        headers: {
          "Content-Type": "application/json",
        },
      }
    );

    const data = await response.json();

    // 这里我们展示理论上会发送的请求头
    requestResult.value = {
      status: response.status,
      success: response.ok,
      data,
    };

    showToast("测试请求已发送");
  } catch (e) {
    console.error("测试请求失败:", e);
    showToast("测试请求失败: " + e.message);
  } finally {
    testing.value = false;
  }
};

const formatRequestHeaders = () => {
  const fp = fingerprint.value;
  const net = networkInfo.value;

  return `Authorization: Bearer ${localStorage
    .getItem("finance_token")
    ?.substring(0, 20)}...
X-Client-Browser: ${JSON.stringify(uaInfo.value.browser)}
X-Client-OS: ${JSON.stringify(uaInfo.value.os)}
X-Client-Device: ${JSON.stringify(uaInfo.value.device)}
X-Client-UA: ${uaParser.getUA()}
X-Fingerprint-Hash: ${fp.hash}
X-Fingerprint-Canvas: ${fp.canvas.hash}
X-Client-IP: ${net.external?.ip || "加载中..."}
X-Client-Country: ${net.external?.country || "-"}
X-Client-City: ${net.external?.city || "-"}
X-Client-ISP: ${net.external?.isp || "-"}
X-Client-Timezone: ${net.external?.timezone || "-"}
X-Client-Connection-Type: ${net.local?.connectionType || "-"}
X-Client-Timestamp: ${Date.now()}
X-Client-Language: ${navigator.language}
X-Client-Timezone-Offset: ${new Date().getTimezoneOffset()}`;
};
</script>

<style scoped>
.page-device-info {
  padding: 16px;
  min-height: 100vh;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-text-primary);
  margin-bottom: 16px;
}

.info-card {
  margin-bottom: 20px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text-primary);
  margin-bottom: 12px;
  padding: 0 8px;
}

.test-request {
  padding: 16px;
}

.request-result {
  margin-top: 16px;
}

.result-title {
  font-size: 12px;
  color: var(--theme-text-secondary);
  margin-bottom: 8px;
}

.result-content {
  background: var(--theme-bg-tertiary);
  padding: 12px;
  border-radius: 8px;
  font-size: 11px;
  color: var(--theme-text-primary);
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
}
</style>
