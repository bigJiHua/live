<template>
  <div class="page-db-export">
    <div class="page-content">
      <div class="page-header">
        <van-icon name="arrow-down" class="header-icon" />
        <div class="header-title">导出数据库/表</div>
        <div class="header-desc">将数据库或指定表数据导出为文件</div>
      </div>

      <div class="type-section">
        <div class="section-title">选择导出类型</div>
        <van-radio-group v-model="selectedType">
          <van-cell-group inset class="app-card">
            <app-cell
              title="导出全部数据"
              label="包含所有表数据（SQL或ZIP格式）"
              clickable
              @click="selectedType = 'all'"
            >
              <template #right-icon>
                <van-radio name="all" />
              </template>
            </app-cell>
            <app-cell
              title="导出单张表"
              label="选择一个表导出"
              clickable
              @click="selectedType = 'single'"
            >
              <template #right-icon>
                <van-radio name="single" />
              </template>
            </app-cell>
          </van-cell-group>
        </van-radio-group>
      </div>

      <div class="table-section" v-if="selectedType === 'single'">
        <div class="section-title">选择表</div>
        <van-cell-group inset class="app-card">
          <app-field
            v-model="searchQuery"
            placeholder="搜索表名..."
            left-icon="search"
            clearable
            @update:model-value="filterTables"
          />
          <div class="table-list">
            <van-radio-group v-model="selectedTable">
              <app-cell
                v-for="table in filteredTables"
                :key="table"
                :title="table"
                clickable
                @click="selectedTable = table"
              >
                <template #right-icon>
                  <van-radio :name="table" />
                </template>
              </app-cell>
            </van-radio-group>
          </div>
        </van-cell-group>
      </div>

      <div class="table-info" v-if="selectedType === 'all'">
        <div class="section-title">数据库概览</div>
        <van-cell-group inset class="app-card">
          <app-cell title="表数量">
            <template #value>
              <span class="num-font">{{ tableInfo.count }} 张</span>
            </template>
          </app-cell>
          <app-cell title="总数据行数">
            <template #value>
              <span class="num-font">{{ formatNumber(tableInfo.totalRows) }} 行</span>
            </template>
          </app-cell>
        </van-cell-group>
      </div>

      <div class="table-info" v-if="selectedType === 'single' && selectedTable">
        <div class="section-title">表信息</div>
        <van-cell-group inset class="app-card">
          <app-cell title="表名">
            <template #value>
              <span class="num-font">{{ selectedTable }}</span>
            </template>
          </app-cell>
          <app-cell title="数据行数" v-if="tableStatus[selectedTable]">
            <template #value>
              <span class="num-font">{{ formatNumber(tableStatus[selectedTable].rowCount) }} 行</span>
            </template>
          </app-cell>
        </van-cell-group>
      </div>

      <div class="option-section">
        <div class="section-title">导出选项</div>
        <van-cell-group inset class="app-card">
          <app-cell title="包含数据">
            <template #value>
              <van-switch v-model="includeData" size="20" />
            </template>
          </app-cell>
          <app-cell title="仅结构（不含数据）">
            <template #label>
              <span class="label-text">适用于仅备份表结构</span>
            </template>
          </app-cell>
        </van-cell-group>
      </div>

      <div class="action-section">
        <app-button
          type="primary"
          size="large"
          block
          round
          :loading="loading && !isAsyncTask"
          :disabled="!canExport"
          @click="handleExport"
        >
          {{ isAsyncTask ? '导出中...' : '确认导出' }}
        </app-button>
        <app-button
          v-if="isAsyncTask"
          type="warning"
          size="large"
          block
          round
          class="cancel-btn"
          @click="handleCancelTask"
        >
          取消导出
        </app-button>
      </div>

      <div class="async-progress" v-if="isAsyncTask">
        <div class="section-title">导出进度</div>
        <van-cell-group inset class="app-card">
          <app-cell title="状态">
            <template #value>
              <app-tag type="warning" size="large">后台导出中</app-tag>
            </template>
          </app-cell>
          <app-cell title="进度">
            <template #value>
              <span class="num-font">{{ asyncProgress }}%</span>
            </template>
          </app-cell>
          <van-progress
            :percentage="asyncProgress"
            :show-pivot="true"
            :stroke-width="12"
            color="#07c160"
          />
          <app-cell>
            <app-button
              type="default"
              size="small"
              block
              round
              icon="replay"
              @click="manualRefreshStatus"
            >
              手动刷新状态
            </app-button>
          </app-cell>
        </van-cell-group>
      </div>

      <div class="export-result" v-if="exportCompleted">
        <div class="section-title">导出结果</div>
        <van-cell-group inset class="app-card">
          <app-cell title="状态">
            <template #value>
              <app-tag :type="exportStatus.type" size="large">
                {{ exportStatus.text }}
              </app-tag>
            </template>
          </app-cell>
          <app-cell title="文件名" v-if="exportStatus.type === 'success'">
            <template #value>
              <span class="filename">{{ resultFilename }}</span>
            </template>
          </app-cell>
          <app-cell title="文件大小" v-if="exportStatus.type === 'success'">
            <template #value>
              <span class="num-font">{{ formatFileSize(resultSize) }}</span>
            </template>
          </app-cell>
          <app-cell title="导出时间" v-if="exportStatus.type === 'success'">
            <template #value>
              <span class="time-text">{{ exportTime }}</span>
            </template>
          </app-cell>
          <app-cell title="操作" v-if="exportStatus.type === 'success'">
            <template #value>
              <a class="download-link" @click.prevent="handleDownload">点击下载</a>
            </template>
          </app-cell>
        </van-cell-group>
      </div>

      <div class="backup-entry" @click="goBackup">
        <van-icon name="records" />
        <span>查看数据库备份</span>
        <van-icon name="arrow" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { showToast } from "vant";
import { getTableList, getTableStatus, exportSingleTable, exportFullDatabase, getExportTaskStatus, cancelExportTask, downloadBackup } from "@/utils/api/dataManager";

const selectedType = ref("all");
const selectedTable = ref("");
const includeData = ref(true);
const loading = ref(false);
const exportCompleted = ref(false);
const exportTime = ref("");
const resultFilename = ref("");
const resultSize = ref(0);
const exportStatus = ref({ type: "", text: "" });
const downloadUrl = ref("");

const router = useRouter();
const goBackup = () => router.push("/finance/data/backup");

const isAsyncTask = ref(false);
const currentTaskId = ref("");
const asyncProgress = ref(0);
const pollingTimer = ref(null);

const allTables = ref([]);
const filteredTables = ref([]);
const searchQuery = ref("");
const tableInfo = ref({ count: 0, totalRows: 0 });
const tableStatus = ref({});

const canExport = computed(() => {
  if (selectedType.value === "all") return true;
  return selectedType.value === "single" && selectedTable.value;
});

const filterTables = (query) => {
  if (!query) {
    filteredTables.value = allTables.value;
  } else {
    filteredTables.value = allTables.value.filter(t =>
      t.toLowerCase().includes(query.toLowerCase())
    );
  }
};

const formatNumber = (num) => {
  if (!num) return "0";
  return num.toLocaleString();
};

const formatFileSize = (bytes) => {
  if (!bytes) return "0 B";
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + " KB";
  return (bytes / (1024 * 1024)).toFixed(2) + " MB";
};

const loadTableData = async () => {
  try {
    const [listRes, statusRes] = await Promise.all([
      getTableList(),
      getTableStatus()
    ]);

    allTables.value = listRes?.data || [];
    filteredTables.value = [...allTables.value];

    const status = statusRes?.data || [];
    tableStatus.value = {};
    let totalRows = 0;

    status.forEach(t => {
      tableStatus.value[t.name] = t;
      totalRows += t.rowCount || 0;
    });

    tableInfo.value = {
      count: allTables.value.length,
      totalRows
    };
  } catch (e) {
    console.error("[DbExport] load table data error:", e);
    showToast({ message: "加载表数据失败", type: "fail" });
  }
};

const handleExport = async () => {
  loading.value = true;
  exportCompleted.value = false;
  isAsyncTask.value = false;
  asyncProgress.value = 0;

  try {
    let res;

    if (selectedType.value === "all") {
      res = await exportFullDatabase({
        includeData: includeData.value ? "true" : "false"
      });
    } else {
      res = await exportSingleTable(selectedTable.value, {
        includeData: includeData.value ? "true" : "false"
      });
    }

    const data = res?.data;

    // 后端统一返回 202 + taskId（异步模式）
    if (res?.status === 202 && data?.taskId) {
      isAsyncTask.value = true;
      currentTaskId.value = data.taskId;

      if (data.reuseExisting) {
        showToast({ message: "已有导出任务正在执行，自动加入监控", type: "warning" });
      } else {
        showToast({ message: "导出任务已提交，后台处理中...", type: "success" });
      }

      exportStatus.value = { type: "warning", text: "后台导出中..." };
      startPolling(data.taskId);
      return;
    }

    // 兜底：如果后端意外返回了同步结果（兼容旧版）
    if (data?.filename) {
      exportTime.value = new Date().toLocaleString();
      resultFilename.value = data.filename || data.sqlFilename;
      resultSize.value = data.size || data.sqlSize;
      downloadUrl.value = data.downloadPath;
      exportStatus.value = { type: "success", text: "导出成功" };
      exportCompleted.value = true;
      showToast({ message: "导出成功，请点击下载", type: "success" });
    } else {
      throw new Error(data?.message || "导出失败");
    }
  } catch (e) {
    console.error("[DbExport] export error:", e);
    exportStatus.value = { type: "danger", text: "导出失败" };
    exportCompleted.value = true;
    showToast({ message: e?.response?.data?.message || e?.message || "导出失败", type: "fail" });
  } finally {
    loading.value = false;
  }
};

const startPolling = (taskId) => {
  stopPolling(); // 先清掉旧定时器
  isAsyncTask.value = true;
  currentTaskId.value = taskId;
  asyncProgress.value = 0;

  pollingTimer.value = setInterval(async () => {
    try {
      const res = await getExportTaskStatus(taskId);
      const task = res?.data;

      if (!task) {
        stopPolling();
        exportStatus.value = { type: "danger", text: "任务丢失" };
        exportCompleted.value = true;
        return;
      }

      asyncProgress.value = task.progress || 0;

      if (task.status === "completed") {
        stopPolling();
        isAsyncTask.value = false; // 切到结果区显示
        exportTime.value = new Date().toLocaleString();
        // 系统备份任务返回 full / schemaOnly，导出任务返回 filename
        const resultData = task.result || {};
        resultFilename.value = resultData.filename
          || resultData.full?.filename
          || "";
        resultSize.value = resultData.size || resultData.full?.size || 0;
        exportStatus.value = { type: "success", text: "导出完成" };
        exportCompleted.value = true;
        showToast({ message: "导出完成，请点击下载", type: "success" });
        // 不再自动触发下载
        return;
      }

      if (task.status === "failed") {
        stopPolling();
        exportStatus.value = { type: "danger", text: "导出失败" };
        exportCompleted.value = true;
        showToast({ message: task.error || "导出失败", type: "fail" });
        return;
      }

      if (task.status === "cancelled") {
        stopPolling();
        exportStatus.value = { type: "warning", text: "已取消" };
        exportCompleted.value = true;
        showToast({ message: "任务已取消", type: "warning" });
        return;
      }
    } catch (e) {
      console.error("[DbExport] polling error:", e);
    }
  }, 5000); // 5秒轮询，平衡性能与体验
};

const manualRefreshStatus = async () => {
  if (!currentTaskId.value) return;
  try {
    const res = await getExportTaskStatus(currentTaskId.value);
    const task = res?.data;
    if (!task) return;
    asyncProgress.value = task.progress || 0;
    if (task.status === "completed" || task.status === "failed" || task.status === "cancelled") {
      // 手动刷新触发的结果处理
      if (task.status === "completed") {
        stopPolling();
        isAsyncTask.value = false;
        exportTime.value = new Date().toLocaleString();
        const rd = task.result || {};
        resultFilename.value = rd.filename || rd.full?.filename || "";
        resultSize.value = rd.size || rd.full?.size || 0;
        exportStatus.value = { type: "success", text: "导出完成" };
        exportCompleted.value = true;
        showToast({ message: "导出完成，请点击下载", type: "success" });
      } else if (task.status === "failed") {
        stopPolling();
        isAsyncTask.value = false;
        exportStatus.value = { type: "danger", text: "导出失败" };
        exportCompleted.value = true;
        showToast({ message: task.error || "导出失败", type: "fail" });
      } else {
        stopPolling();
        isAsyncTask.value = false;
        exportStatus.value = { type: "warning", text: "已取消" };
        exportCompleted.value = true;
      }
    } else {
      showToast({ message: `当前进度: ${task.progress || 0}%`, type: "success" });
    }
  } catch (e) {
    console.error("[DbExport] manual refresh error:", e);
  }
};

const stopPolling = () => {
  if (pollingTimer.value) {
    clearInterval(pollingTimer.value);
    pollingTimer.value = null;
  }
};

const handleCancelTask = async () => {
  if (!currentTaskId.value) return;

  try {
    await cancelExportTask(currentTaskId.value);
    stopPolling();
    isAsyncTask.value = false;
    showToast({ message: "已取消导出", type: "warning" });
  } catch (e) {
    console.error("[DbExport] cancel error:", e);
    showToast({ message: "取消失败", type: "fail" });
  }
};

const handleDownload = async () => {
  if (!resultFilename.value) {
    showToast({ message: "没有可下载的文件", type: "fail" });
    return;
  }

  try {
    console.log("[DbExport] Downloading:", resultFilename.value);
    const res = await downloadBackup(resultFilename.value);

    // 拿到的是 blob，转为可下载的 URL
    const blob = res.data instanceof Blob
      ? res.data
      : new Blob([res.data]);
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = resultFilename.value;
    a.style.display = "none";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    // 延迟 revoke，给浏览器一点时间真正发起下载
    setTimeout(() => window.URL.revokeObjectURL(url), 1000);
    showToast({ message: "下载已开始", type: "success" });
  } catch (e) {
    console.error("[DbExport] download error:", e);
    showToast({ message: "下载失败：" + (e?.message || "未知错误"), type: "fail" });
  }
};

onMounted(() => {
  loadTableData();
});

onUnmounted(() => {
  stopPolling();
});
</script>

<style scoped>
.page-db-export {
  min-height: 100vh;
  background: var(--theme-bg-primary);
}

.page-content {
  padding: 16px;
  padding-top: 20px;
}

.page-header {
  text-align: center;
  padding: 30px 0;
  background: var(--theme-bg-secondary);
  border-radius: 16px;
  margin-bottom: 20px;
}

.header-icon {
  font-size: 48px;
  color: #07c160;
  margin-bottom: 12px;
}

.header-title {
  font-size: 20px;
  font-weight: bold;
  color: var(--theme-text-primary);
  margin-bottom: 8px;
}

.header-desc {
  font-size: 14px;
  color: var(--theme-text-tertiary);
}

.section-title {
  padding: 8px 16px;
  font-size: 13px;
  color: var(--theme-text-tertiary);
  font-weight: 500;
  margin-bottom: 8px;
}

.app-card {
  border-radius: 12px;
  overflow: hidden;
}

.type-section {
  margin-bottom: 16px;
}

.table-section {
  margin-bottom: 16px;
}

.table-list {
  max-height: 200px;
  overflow-y: auto;
}

.table-info {
  margin-bottom: 16px;
}

.format-section {
  margin-bottom: 16px;
}

.option-section {
  margin-bottom: 16px;
}

.action-section {
  margin-bottom: 20px;
}

.export-result {
  margin-top: 20px;
}

.filename {
  font-size: 12px;
  color: var(--theme-text-secondary);
  word-break: break-all;
}

.time-text {
  font-size: 13px;
  color: var(--theme-text-secondary);
}

.label-text {
  font-size: 12px;
  color: var(--theme-text-tertiary);
}

.cancel-btn {
  margin-top: 12px;
}

.async-progress {
  margin-bottom: 16px;
}

.async-progress :deep(.van-progress) {
  margin-top: 8px;
  margin-left: 16px;
  margin-right: 16px;
}

.num-font {
  font-family: "DIN Condensed", "Helvetica Neue", Arial, sans-serif;
}

.download-link {
  color: var(--theme-primary);
  text-decoration: underline;
}

.backup-entry {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 16px;
  margin-top: 20px;
  margin-bottom: 10px;
  background: var(--theme-bg-secondary);
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  color: #07c160;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.backup-entry:active {
  opacity: 0.7;
}
</style>