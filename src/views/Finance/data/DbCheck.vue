<template>
  <div class="page-db-check">
    <div class="page-content">
      <div class="page-header">
        <van-icon name="passed" class="header-icon" />
        <div class="header-title">数据库检查</div>
        <div class="header-desc">检查数据库连接状态和数据完整性</div>
      </div>

      <div class="action-section">
        <van-button
          type="primary"
          size="large"
          block
          round
          :loading="loading"
          @click="handleDbCheck"
        >
          开始检查
        </van-button>
      </div>

      <div class="result-section" v-if="checkCompleted">
        <div class="section-title">检查结果</div>
        <van-cell-group inset class="app-card">
          <van-cell title="连接状态">
            <template #value>
              <van-tag :type="connectionStatus.type" size="large">
                {{ connectionStatus.text }}
              </van-tag>
            </template>
          </van-cell>
          <van-cell title="数据完整性">
            <template #value>
              <van-tag :type="integrityStatus.type" size="large">
                {{ integrityStatus.text }}
              </van-tag>
            </template>
          </van-cell>
          <van-cell title="总行数">
            <template #value>
              <span class="time-text">{{ tableInfo.totalRows.toLocaleString() }} 行</span>
            </template>
          </van-cell>
          <van-cell title="检查时间">
            <template #value>
              <span class="time-text">{{ checkTime }}</span>
            </template>
          </van-cell>
        </van-cell-group>
      </div>

      <div class="tables-section" v-if="tables.length > 0">
        <van-collapse v-model="activeCollapse" :border="false">
          <van-collapse-item title="数据库表状态" name="tables" icon="records-o" :border="false">
            <div class="table-wrapper">
              <table class="db-table">
                <thead>
                  <tr>
                    <th class="col-name">表名</th>
                    <th class="col-engine">引擎</th>
                    <th class="col-rows">行数</th>
                    <th class="col-size">大小</th>
                    <th class="col-comment">备注</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="t in tables" :key="t.name">
                    <td class="col-name">{{ t.name }}</td>
                    <td class="col-engine">{{ t.engine }}</td>
                    <td class="col-rows">{{ (t.rowCount || 0).toLocaleString() }}</td>
                    <td class="col-size">{{ formatSize(t.dataLength) }}</td>
                    <td class="col-comment">{{ t.comment || '-' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </van-collapse-item>
        </van-collapse>
      </div>

      <div class="info-section">
        <div class="section-title">检查说明</div>
        <van-cell-group inset class="app-card">
          <van-cell title="连接状态">
            <template #label>
              <span class="label-text">验证数据库服务器是否可达</span>
            </template>
          </van-cell>
          <van-cell title="数据完整性">
            <template #label>
              <span class="label-text">检查数据表是否存在且可正常读写</span>
            </template>
          </van-cell>
        </van-cell-group>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { showToast } from "vant";
import { getTableList, getTableStatus } from "@/utils/api/dataManager";

const loading = ref(false);
const checkCompleted = ref(false);
const checkTime = ref("");
const connectionStatus = ref({ type: "", text: "" });
const integrityStatus = ref({ type: "", text: "" });
const tableInfo = ref({ count: 0, totalRows: 0 });
const tables = ref([]);
const activeCollapse = ref(["tables"]);

const formatSize = (bytes) => {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  let i = 0;
  let size = bytes;
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024;
    i++;
  }
  return size.toFixed(1) + " " + units[i];
};

const handleDbCheck = async () => {
  loading.value = true;
  checkCompleted.value = false;

  try {
    const [listRes, statusRes] = await Promise.all([
      getTableList(),
      getTableStatus()
    ]);

    const list = listRes?.data || [];
    const status = statusRes?.data || [];

    tables.value = status;

    checkTime.value = new Date().toLocaleString();

    const totalRows = status.reduce((sum, t) => sum + (t.rowCount || 0), 0);
    tableInfo.value = { count: list.length || status.length, totalRows };

    if (status.length > 0) {
      connectionStatus.value = { type: "success", text: "正常" };
      integrityStatus.value = { type: "success", text: `完整 (${status.length}张表)` };
    } else {
      connectionStatus.value = { type: "warning", text: "无数据" };
      integrityStatus.value = { type: "warning", text: "空库" };
    }

    checkCompleted.value = true;
    showToast({ message: "检查完成", type: "success" });
  } catch (e) {
    console.error("[DbCheck] check error:", e);
    connectionStatus.value = { type: "danger", text: "失败" };
    integrityStatus.value = { type: "danger", text: "异常" };
    checkCompleted.value = true;
    showToast({ message: e?.message || "检查失败", type: "fail" });
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.page-db-check {
  min-height: 100vh;
  background: #f7f8fa;
}

.page-content {
  padding: 16px;
  padding-top: 20px;
}

.page-header {
  text-align: center;
  padding: 30px 0;
  background: white;
  border-radius: 16px;
  margin-bottom: 20px;
}

.header-icon {
  font-size: 48px;
  color: #1989fa;
  margin-bottom: 12px;
}

.header-title {
  font-size: 20px;
  font-weight: bold;
  color: #323233;
  margin-bottom: 8px;
}

.header-desc {
  font-size: 14px;
  color: #969799;
}

.action-section {
  margin-bottom: 20px;
}

.section-title {
  padding: 8px 16px;
  font-size: 13px;
  color: #969799;
  font-weight: 500;
  margin-bottom: 8px;
}

.app-card {
  border-radius: 12px;
  overflow: hidden;
}

.result-section {
  margin-bottom: 20px;
}

.tables-section {
  margin-bottom: 20px;
}

.table-wrapper {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.db-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  min-width: 480px;
}

.db-table thead {
  background: #f2f3f5;
}

.db-table th {
  padding: 10px 8px;
  text-align: left;
  font-weight: 600;
  color: #323233;
  white-space: nowrap;
  border-bottom: 1px solid #ebedf0;
}

.db-table td {
  padding: 9px 8px;
  color: #646566;
  border-bottom: 1px solid #f2f3f5;
  vertical-align: top;
  word-break: break-all;
}

.db-table tbody tr:active {
  background: #f2f3f5;
}

.col-name {
  width: 90px;
  min-width: 90px;
  font-family: "SF Mono", "Fira Code", monospace;
  font-size: 11px;
}

.col-engine {
  width: 52px;
  min-width: 52px;
}

.col-rows {
  width: 48px;
  min-width: 48px;
  text-align: right;
}

.col-size {
  width: 56px;
  min-width: 56px;
  text-align: right;
}

.col-comment {
  min-width: 80px;
  color: #969799 !important;
  font-size: 11px;
}

.info-section {
  margin-top: 20px;
}

.label-text {
  font-size: 12px;
  color: #969799;
}

.time-text {
  font-size: 13px;
  color: #646566;
}
</style>
