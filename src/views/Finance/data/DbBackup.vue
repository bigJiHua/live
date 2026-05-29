<template>
  <div class="page-db-backup">
    <div class="page-content">
      <div class="page-header">
        <van-icon name="records" class="header-icon" />
        <div class="header-title">数据库备份</div>
        <div class="header-desc">管理和查看数据库备份文件</div>
      </div>

      <div class="sys-backup-section">
        <div class="section-title">系统备份</div>
        <van-cell-group inset class="app-card">
          <van-cell>
            <template #title>
              <van-button
                type="primary"
                size="large"
                block
                round
                :loading="sysLoading"
                @click="handleSystemBackup"
              >
                系统性备份数据库
              </van-button>
            </template>
            <template #label>
              <span class="label-text">将自动执行完整备份（含数据）和仅结构备份，保存至日期目录</span>
            </template>
          </van-cell>
        </van-cell-group>

        <div class="section-title" v-if="sysBackupGroups.length > 0">已完整备份列表</div>

        <van-loading v-if="sysLoadingList" class="page-loading" size="24px">加载中...</van-loading>

        <van-collapse v-model="sysActiveNames" :border="false" v-if="!sysLoadingList && sysBackupGroups.length > 0">
          <van-collapse-item
            v-for="group in sysBackupGroups"
            :key="group.date"
            :name="group.date"
            :border="false"
          >
            <template #title>
              <div class="collapse-title">
                <van-icon name="calendar-o" />
                <span>{{ group.date }}</span>
                <span class="collapse-badge">{{ group.files.length }} 个文件</span>
              </div>
            </template>
            <div class="sys-files-list">
              <div
                v-for="file in group.files"
                :key="file.filename"
                class="sys-file-item"
              >
                <div class="sfi-icon" :class="file.type === 'schema-only' ? 'schema' : 'full'">
                  {{ file.type === 'schema-only' ? '结构' : '完整' }}
                </div>
                <div class="sfi-info">
                  <div class="sfi-name">{{ file.filename }}</div>
                  <div class="sfi-meta">
                    <span>{{ formatFileSize(file.size) }}</span>
                    <span class="sfi-time">{{ formatTime(file.createdAt) }}</span>
                  </div>
                </div>
              </div>
            </div>
          </van-collapse-item>
        </van-collapse>
      </div>

      <div class="divider"></div>

      <div class="manual-backup-section">
        <div class="section-title">手动导出记录</div>

        <div class="action-section">
          <van-button
            type="primary"
            size="large"
            block
            round
            :loading="loading"
            icon="replay"
            @click="loadManualBackups"
          >
            刷新列表
          </van-button>
        </div>

        <van-loading v-if="loading" class="page-loading" size="24px">加载中...</van-loading>

        <template v-if="!loading">
          <div v-if="backupList.length === 0" class="empty-state">
            <van-empty description="暂无手动导出记录" />
          </div>

          <div v-else class="backup-list">
            <div
              v-for="item in backupList"
              :key="item.filename"
              class="backup-item"
            >
              <div class="bi-left">
                <div class="bi-icon" :class="item.type === 'zip' ? 'zip' : 'sql'">
                  {{ item.type === 'zip' ? 'ZIP' : 'SQL' }}
                </div>
                <div class="bi-info">
                  <div class="bi-name">{{ item.filename }}</div>
                  <div class="bi-meta">
                    {{ formatFileSize(item.size) }}
                  </div>
                  <div class="bi-time">{{ formatTime(item.createdAt) }}</div>
                </div>
              </div>
              <div class="bi-right">
                <van-button
                  type="primary"
                  size="small"
                  plain
                  round
                  @click="handleDownload(item.filename)"
                >
                  下载
                </van-button>
                <van-button
                  type="danger"
                  size="small"
                  plain
                  round
                  @click="handleDelete(item.filename)"
                >
                  删除
                </van-button>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { showToast } from "vant";
import dayjs from "dayjs";
import { getBackupList, downloadBackup, deleteBackup, createSystemBackup, getSystemBackups } from "@/utils/api/dataManager";

const loading = ref(false);
const backupList = ref([]);

const sysLoading = ref(false);
const sysLoadingList = ref(false);
const sysBackupGroups = ref([]);
const sysActiveNames = ref([]);

const formatFileSize = (bytes) => {
  if (!bytes) return "0 B";
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + " KB";
  return (bytes / (1024 * 1024)).toFixed(2) + " MB";
};

const formatTime = (ts) => {
  if (!ts) return "";
  return dayjs(ts).format("YYYY-MM-DD HH:mm:ss");
};

const loadManualBackups = async () => {
  loading.value = true;
  try {
    const res = await getBackupList();
    backupList.value = res?.data || [];
  } catch (e) {
    console.error("[DbBackup] load error:", e);
    showToast({ message: "加载手动导出记录失败", type: "fail" });
  } finally {
    loading.value = false;
  }
};

const loadSystemBackups = async () => {
  sysLoadingList.value = true;
  try {
    const res = await getSystemBackups();
    sysBackupGroups.value = res?.data || [];
    if (sysBackupGroups.value.length > 0) {
      sysActiveNames.value = [sysBackupGroups.value[0].date];
    }
  } catch (e) {
    console.error("[DbBackup] load system backups error:", e);
  } finally {
    sysLoadingList.value = false;
  }
};

const handleSystemBackup = async () => {
  sysLoading.value = true;
  try {
    await createSystemBackup();
    showToast({ message: "系统备份成功", type: "success" });
    await loadSystemBackups();
  } catch (e) {
    console.error("[DbBackup] system backup error:", e);
    const msg = e?.response?.data?.message || e?.message || "系统备份失败";
    showToast({ message: msg, type: "fail" });
  } finally {
    sysLoading.value = false;
  }
};

const handleDownload = async (filename) => {
  try {
    const res = await downloadBackup(filename);
    const blob = new Blob([res.data]);
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
    showToast({ message: "下载开始", type: "success" });
  } catch (e) {
    console.error("[DbBackup] download error:", e);
    showToast({ message: "下载失败", type: "fail" });
  }
};

const handleDelete = async (filename) => {
  try {
    await deleteBackup(filename);
    showToast({ message: "删除成功", type: "success" });
    backupList.value = backupList.value.filter((item) => item.filename !== filename);
  } catch (e) {
    console.error("[DbBackup] delete error:", e);
    showToast({ message: "删除失败", type: "fail" });
  }
};

onMounted(() => {
  loadManualBackups();
  loadSystemBackups();
});
</script>

<style scoped>
.page-db-backup {
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
  color: #07c160;
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
.section-title {
  padding: 8px 16px;
  font-size: 13px;
  color: #969799;
}
.action-section {
  margin-bottom: 12px;
}
.page-loading {
  display: flex;
  justify-content: center;
  padding: 30px 0;
}
.empty-state {
  margin-top: 20px;
}
.label-text {
  font-size: 12px;
  color: #969799;
}
.sys-backup-actions {
  display: flex;
  gap: 12px;
}
.sys-backup-section {
  margin-bottom: 4px;
}
.collapse-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #323233;
}
.collapse-title .van-icon {
  color: #1989fa;
}
.collapse-badge {
  margin-left: auto;
  font-size: 11px;
  color: #969799;
  font-weight: 400;
}
.sys-files-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sys-file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}
.sfi-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
}
.sfi-icon.full { background: #f0fff4; color: #07c160; }
.sfi-icon.schema { background: #fff7e6; color: #fa8c16; }
.sfi-info {
  flex: 1;
  min-width: 0;
}
.sfi-name {
  font-size: 12px;
  font-weight: 500;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sfi-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
  font-size: 11px;
  color: #969799;
}
.sfi-time {
  font-size: 10px;
  color: #c8c9cc;
}
.divider {
  height: 1px;
  background: #ebedf0;
  margin: 20px 0;
}
.manual-backup-section {
  margin-bottom: 20px;
}
.backup-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.backup-item {
  background: #fff;
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.bi-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}
.bi-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.5px;
  flex-shrink: 0;
}
.bi-icon.sql { background: #f0f5ff; color: #1989fa; }
.bi-icon.zip { background: #fff7e6; color: #fa8c16; }
.bi-info {
  flex: 1;
  min-width: 0;
}
.bi-name {
  font-size: 13px;
  font-weight: 500;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bi-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
  font-size: 11px;
  color: #969799;
}
.bi-time {
  font-size: 10px;
  color: #c8c9cc;
  margin-top: 1px;
}
.bi-right {
  flex-shrink: 0;
  margin-left: 12px;
  display: flex;
  gap: 8px;
}
</style>