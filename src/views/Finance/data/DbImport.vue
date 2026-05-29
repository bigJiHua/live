<template>
  <div class="page-db-import">
    <div class="page-content">
      <div class="page-header">
        <van-icon name="arrow-up" class="header-icon" />
        <div class="header-title">数据导入</div>
        <div class="header-desc">从文件导入数据到数据库</div>
      </div>

      <!-- 导入格式切换 -->
      <div class="format-section">
        <div class="section-title">导入格式</div>
        <van-cell-group inset class="app-card">
          <van-cell>
            <template #title>
              <van-radio-group v-model="importFormat" direction="horizontal" class="format-radio">
                <van-radio name="json" shape="square">JSON (单表)</van-radio>
                <van-radio name="sql" shape="square">SQL / ZIP (全库)</van-radio>
              </van-radio-group>
            </template>
          </van-cell>
        </van-cell-group>
      </div>

      <!-- JSON 模式 -->
      <template v-if="importFormat === 'json'">
        <div class="table-section">
          <div class="section-title">选择目标表</div>
          <van-cell-group inset class="app-card">
            <van-field
              v-model="searchQuery"
              placeholder="搜索表名..."
              left-icon="search"
              clearable
              @update:model-value="filterTables"
            />
            <div class="table-list">
              <van-radio-group v-model="selectedTable">
                <van-cell
                  v-for="table in filteredTables"
                  :key="table"
                  :title="table"
                  clickable
                  @click="selectTable(table)"
                >
                  <template #right-icon>
                    <van-radio :name="table" />
                  </template>
                </van-cell>
              </van-radio-group>
            </div>
          </van-cell-group>
        </div>

        <div class="upload-section">
          <div class="section-title">选择文件</div>
          <van-cell-group inset class="app-card">
            <van-cell title="支持格式">
              <template #label>
                <span class="label-text">JSON 文件</span>
              </template>
            </van-cell>
            <van-cell title="当前选择表" v-if="selectedTable">
              <template #value>
                <span class="selected-table">{{ selectedTable }}</span>
              </template>
            </van-cell>
          </van-cell-group>
          <van-uploader
            v-model="jsonFileList"
            :max-count="1"
            accept=".json"
            :before-read="beforeJsonRead"
            :after-read="afterJsonRead"
            @delete="handleJsonDelete"
            class="uploader"
          >
            <van-button type="primary" size="small" icon="plus" block>
              选择 JSON 文件
            </van-button>
          </van-uploader>
        </div>

        <div class="preview-section" v-if="jsonPreviewData.length > 0">
          <div class="section-title">数据预览 (前{{ Math.min(previewCount, jsonPreviewData.length) }}条)</div>
          <van-cell-group inset class="app-card">
            <van-cell title="总记录数">
              <template #value>
                <span class="num-font">{{ jsonPreviewData.length }} 条</span>
              </template>
            </van-cell>
            <van-cell title="字段数">
              <template #value>
                <span class="num-font">{{ jsonPreviewFields.length }} 个</span>
              </template>
            </van-cell>
          </van-cell-group>
          <div class="data-preview">
            <div class="preview-item" v-for="(item, idx) in jsonPreviewData.slice(0, previewCount)" :key="idx">
              <div class="preview-index">{{ idx + 1 }}</div>
              <div class="preview-content">
                <div class="preview-field" v-for="(val, key) in item" :key="key">
                  <span class="field-key">{{ key }}:</span>
                  <span class="field-val">{{ formatVal(val) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="validate-section" v-if="jsonPreviewData.length > 0">
          <van-button
            type="default"
            size="large"
            block
            round
            :loading="validating"
            @click="handleValidate"
          >
            校验数据
          </van-button>
        </div>

        <div class="validate-result" v-if="validationResult">
          <div class="section-title">校验结果</div>
          <van-cell-group inset class="app-card">
            <van-cell title="状态">
              <template #value>
                <van-tag :type="validationResult.canImport ? 'success' : 'danger'" size="large">
                  {{ validationResult.canImport ? '可导入' : '有问题' }}
                </van-tag>
              </template>
            </van-cell>
            <van-cell title="目标表字段">
              <template #value>
                <span class="num-font">{{ validationResult.tableColumns?.length || 0 }} 个</span>
              </template>
            </van-cell>
            <van-cell title="有效行数">
              <template #value>
                <span class="num-font">{{ validationResult.validRows || 0 }} 条</span>
              </template>
            </van-cell>
            <van-cell title="无效行数" v-if="validationResult.invalidRows > 0">
              <template #value>
                <span class="num-font text-danger">{{ validationResult.invalidRows }} 条</span>
              </template>
            </van-cell>
          </van-cell-group>
        </div>

        <div class="import-options">
          <van-cell-group inset class="app-card">
            <van-cell title="清空后导入">
              <template #value>
                <van-switch v-model="forceClear" size="20" />
              </template>
            </van-cell>
            <van-cell title="导入前自动备份目标表">
              <template #label>
                <span class="label-text">启用后导入前会先备份目标表数据</span>
              </template>
            </van-cell>
          </van-cell-group>
        </div>
      </template>

      <!-- SQL 模式 -->
      <template v-if="importFormat === 'sql'">
        <div class="upload-section">
          <div class="section-title">选择文件</div>
          <van-cell-group inset class="app-card">
            <van-cell title="支持格式">
              <template #label>
                <span class="label-text">.sql 文件 或 .zip 压缩包（内含 .sql 文件）</span>
              </template>
            </van-cell>
            <van-cell title="导入范围" value="全库导入" />
            <van-cell title="文件大小限制" value="100MB" />
          </van-cell-group>
          <van-uploader
            v-model="sqlFileList"
            :max-count="1"
            accept=".sql,.zip"
            :before-read="beforeSqlRead"
            :after-read="afterSqlRead"
            @delete="handleSqlDelete"
            class="uploader"
          >
            <van-button type="primary" size="small" icon="plus" block>
              选择 SQL / ZIP 文件
            </van-button>
          </van-uploader>
        </div>

        <div class="sql-file-info" v-if="sqlFileInfo">
          <div class="section-title">文件信息</div>
          <van-cell-group inset class="app-card">
            <van-cell title="文件名">
              <template #value>
                <span class="file-name">{{ sqlFileInfo.name }}</span>
              </template>
            </van-cell>
            <van-cell title="文件大小">
              <template #value>
                <span class="num-font">{{ formatFileSize(sqlFileInfo.size) }}</span>
              </template>
            </van-cell>
          </van-cell-group>
        </div>

        <div class="warning-section">
          <van-notice-bar
            color="#ee0a24"
            background="#fff7e6"
            left-icon="warning-o"
            wrapable
          >
            SQL / ZIP 导入会直接执行文件中的 SQL 语句（含 DDL+DML），执行前会自动全库备份。此操作不可逆，请谨慎操作！
          </van-notice-bar>
        </div>
      </template>

      <!-- 通用警告 -->
      <div class="warning-section" v-if="importFormat === 'json'">
        <van-notice-bar
          color="#ff976a"
          background="#fff7e6"
          left-icon="warning-o"
        >
          导入数据会修改数据库，请谨慎操作！
        </van-notice-bar>
      </div>

      <!-- 导入按钮 -->
      <div class="action-section">
        <van-button
          type="primary"
          size="large"
          block
          round
          :loading="loading"
          :disabled="!canImport"
          @click="handleImport"
        >
          {{ importFormat === 'sql' ? '执行 SQL 导入' : '确认导入' }}
        </van-button>
      </div>

      <!-- 导入结果 -->
      <div class="import-result" v-if="importCompleted">
        <div class="section-title">导入结果</div>
        <van-cell-group inset class="app-card">
          <van-cell title="状态">
            <template #value>
              <van-tag :type="importStatus.type" size="large">
                {{ importStatus.text }}
              </van-tag>
            </template>
          </van-cell>
          <van-cell title="导入时间">
            <template #value>
              <span class="time-text">{{ importTime }}</span>
            </template>
          </van-cell>

          <!-- JSON 结果 -->
          <template v-if="importFormat === 'json'">
            <van-cell title="导入记录数" v-if="importStatus.type === 'success'">
              <template #value>
                <span class="num-font">{{ importedCount }} 条</span>
              </template>
            </van-cell>
          </template>

          <!-- SQL 结果 -->
          <template v-if="importFormat === 'sql'">
            <van-cell title="执行语句">
              <template #value>
                <span class="num-font">{{ sqlResult.executedCount }} 条</span>
              </template>
            </van-cell>
            <van-cell title="失败语句" v-if="sqlResult.errorCount > 0">
              <template #value>
                <span class="num-font text-danger">{{ sqlResult.errorCount }} 条</span>
              </template>
            </van-cell>
            <van-cell title="失败详情" v-if="sqlResult.errors && sqlResult.errors.length > 0">
              <template #label>
                <div class="error-list">
                  <div v-for="(e, i) in sqlResult.errors" :key="i" class="error-item">
                    <div>第 {{ e.index }} 条: {{ e.message }}</div>
                    <div class="error-sql">{{ e.sql }}</div>
                  </div>
                </div>
              </template>
            </van-cell>
          </template>

          <van-cell title="备份文件">
            <template #value>
              <span class="backup-file">{{ backupFile }}</span>
            </template>
          </van-cell>
          <van-cell title="备份时间" v-if="backupTime">
            <template #value>
              <span class="time-text">{{ backupTime }}</span>
            </template>
          </van-cell>
        </van-cell-group>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";
import { showToast, showConfirmDialog, showDialog } from "vant";
import { getTableList, validateImportData, importData, importSql } from "@/utils/api/dataManager";

const importFormat = ref("json");

// ---- JSON 导入 ----
const selectedTable = ref("");
const searchQuery = ref("");
const jsonFileList = ref([]);
const jsonPreviewData = ref([]);
const jsonPreviewFields = ref([]);
const previewCount = 5;
const validating = ref(false);
const validationResult = ref(null);
const forceClear = ref(false);

const allTables = ref([]);
const filteredTables = ref([]);

// ---- SQL 导入 ----
const sqlFileList = ref([]);
const sqlFileInfo = ref(null);
const sqlRawContent = ref("");
const sqlResult = ref({ executedCount: 0, errorCount: 0, errors: [] });

// ---- 通用 ----
const loading = ref(false);
const importCompleted = ref(false);
const importTime = ref("");
const importedCount = ref(0);
const importStatus = ref({ type: "", text: "" });
const backupFile = ref("");
const backupTime = ref("");

const canImport = computed(() => {
  if (importFormat.value === "json") {
    return selectedTable.value && jsonPreviewData.value.length > 0;
  }
  return sqlFileInfo.value !== null;
});

const selectTable = (table) => {
  selectedTable.value = table;
  validationResult.value = null;
};

const filterTables = (query) => {
  if (!query) {
    filteredTables.value = allTables.value;
  } else {
    filteredTables.value = allTables.value.filter(t =>
      t.toLowerCase().includes(query.toLowerCase())
    );
  }
};

const formatVal = (val) => {
  if (val === null || val === undefined) return 'null';
  if (typeof val === 'object') return JSON.stringify(val);
  return String(val);
};

const formatFileSize = (bytes) => {
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

// ---- JSON 文件处理 ----
const beforeJsonRead = (file) => {
  const ext = file.name.substring(file.name.lastIndexOf(".")).toLowerCase();
  if (ext !== ".json") {
    showToast({ message: "请上传 JSON 文件", type: "warning" });
    return false;
  }
  return true;
};

const afterJsonRead = async (file) => {
  try {
    if (file.file && file.file.size > 10 * 1024 * 1024) {
      showToast({ message: "文件超过10MB，请拆分后导入", type: "warning" });
      jsonFileList.value = [];
      jsonPreviewData.value = [];
      jsonPreviewFields.value = [];
      return;
    }

    const text = await file.text();
    const data = JSON.parse(text);

    if (!Array.isArray(data)) {
      showToast({ message: "JSON文件格式错误，应为数组", type: "warning" });
      jsonFileList.value = [];
      jsonPreviewData.value = [];
      jsonPreviewFields.value = [];
      return;
    }

    if (data.length > 50000) {
      showToast({ message: "单次导入不能超过5万条数据", type: "warning" });
      jsonFileList.value = [];
      jsonPreviewData.value = [];
      jsonPreviewFields.value = [];
      return;
    }

    jsonPreviewData.value = data;
    jsonPreviewFields.value = data.length > 0 ? Object.keys(data[0]) : [];
    validationResult.value = null;

    showToast({ message: `已加载 ${data.length} 条数据`, type: "success" });
  } catch (e) {
    console.error("[DbImport] parse json error:", e);
    showToast({ message: "JSON解析失败", type: "fail" });
    jsonFileList.value = [];
    jsonPreviewData.value = [];
    jsonPreviewFields.value = [];
  }
};

const handleJsonDelete = () => {
  jsonPreviewData.value = [];
  jsonPreviewFields.value = [];
  validationResult.value = null;
  importCompleted.value = false;
};

const handleValidate = async () => {
  if (!selectedTable.value || jsonPreviewData.value.length === 0) {
    showToast({ message: "请先选择表和文件", type: "warning" });
    return;
  }

  validating.value = true;

  try {
    const res = await validateImportData({
      tableName: selectedTable.value,
      data: jsonPreviewData.value
    });

    validationResult.value = res?.data;

    if (res?.data?.canImport) {
      showToast({ message: "数据校验通过", type: "success" });
    } else {
      showToast({ message: `有 ${res?.data?.invalidRows || 0} 行数据存在问题`, type: "warning" });
    }
  } catch (e) {
    console.error("[DbImport] validate error:", e);
    showToast({ message: e?.message || "校验失败", type: "fail" });
  } finally {
    validating.value = false;
  }
};

// ---- SQL 文件处理 ----
const beforeSqlRead = (file) => {
  const ext = file.name.substring(file.name.lastIndexOf(".")).toLowerCase();
  if (ext !== ".sql" && ext !== ".zip") {
    showToast({ message: "请上传 .sql 或 .zip 文件", type: "warning" });
    return false;
  }
  if (file.size > 100 * 1024 * 1024) {
    showToast({ message: "文件超过 100MB 限制", type: "warning" });
    return false;
  }
  return true;
};

const afterSqlRead = (file) => {
  sqlFileInfo.value = {
    name: file.file.name,
    size: file.file.size,
  };
  importCompleted.value = false;
  showToast({ message: `已选择文件: ${file.file.name}`, type: "success" });
};

const handleSqlDelete = () => {
  sqlFileInfo.value = null;
  sqlRawContent.value = "";
  importCompleted.value = false;
};

// ---- 导入执行 ----
const handleImport = async () => {
  if (importFormat.value === "json") {
    await handleJsonImport();
  } else {
    await handleSqlImport();
  }
};

const handleJsonImport = async () => {
  const msg = forceClear.value
    ? `确定要清空 ${selectedTable.value} 表后导入 ${jsonPreviewData.value.length} 条数据吗？`
    : `确定要向 ${selectedTable.value} 表导入 ${jsonPreviewData.value.length} 条数据吗？`;

  try {
    await showConfirmDialog({ title: "确认导入", message: msg });
  } catch {
    return;
  }

  loading.value = true;
  importCompleted.value = false;

  try {
    const res = await importData({
      tableName: selectedTable.value,
      data: jsonPreviewData.value,
      forceClear: forceClear.value
    });

    const data = res?.data;
    importTime.value = new Date().toLocaleString();
    importedCount.value = data?.importedRows || jsonPreviewData.value.length;
    backupFile.value = data?.backupFile || "";
    importStatus.value = { type: "success", text: "导入成功" };
    importCompleted.value = true;
    showToast({ message: "导入成功", type: "success" });
  } catch (e) {
    console.error("[DbImport] import error:", e);
    importStatus.value = { type: "danger", text: "导入失败" };
    importCompleted.value = true;
    showToast({ message: e?.message || "导入失败", type: "fail" });
  } finally {
    loading.value = false;
  }
};

const handleSqlImport = async () => {
  if (!sqlFileList.value || sqlFileList.value.length === 0) {
    showToast({ message: "请先选择 SQL 文件", type: "warning" });
    return;
  }

  // 第一层确认
  try {
    await showConfirmDialog({
      title: "⚠️ 确认 SQL 导入",
      message: `即将执行文件「${sqlFileInfo.value.name}」中的 SQL 语句（${formatFileSize(sqlFileInfo.value.size)}）。\n\n执行前会自动全库备份，但此操作不可逆，请确认文件来源可靠。`,
      confirmButtonText: "我已备份，继续",
    });
  } catch {
    return;
  }

  // 第二层确认（更严厉）
  try {
    await showConfirmDialog({
      title: "⚠️ 二次确认",
      message: "SQL 将直接修改数据库（包含建表、插数据等）。若执行出错，可使用备份文件恢复。确定继续？",
      confirmButtonText: "确定执行",
    });
  } catch {
    return;
  }

  loading.value = true;
  importCompleted.value = false;

  try {
    const file = sqlFileList.value[0].file;
    const formData = new FormData();
    formData.append("file", file);

    const res = await importSql(formData);
    const data = res?.data || {};

    importTime.value = new Date().toLocaleString();
    backupFile.value = data.backupFile || "";
    backupTime.value = data.backupTimestamp || "";
    sqlResult.value = {
      executedCount: data.executedCount || 0,
      errorCount: data.errorCount || 0,
      errors: data.errors || [],
    };

    if (res?.status === 200) {
      importStatus.value = { type: "success", text: "导入成功" };
      showToast({ message: "SQL 导入成功", type: "success" });
    } else if (res?.status === 207) {
      importStatus.value = { type: "warning", text: "部分失败" };
      showToast({ message: `SQL 导入完成，${data.errorCount} 条语句失败`, type: "warning" });
    } else {
      importStatus.value = { type: "danger", text: "导入失败" };
      showToast({ message: res?.message || "导入失败", type: "fail" });
    }
    importCompleted.value = true;
  } catch (e) {
    console.error("[DbImport] sql import error:", e);
    importStatus.value = { type: "danger", text: "导入失败" };
    importCompleted.value = true;
    showToast({ message: e?.message || "导入失败", type: "fail" });
  } finally {
    loading.value = false;
  }
};

const loadTables = async () => {
  try {
    const res = await getTableList();
    allTables.value = res?.data || [];
    filteredTables.value = [...allTables.value];
  } catch (e) {
    console.error("[DbImport] load tables error:", e);
    showToast({ message: "加载表列表失败", type: "fail" });
  }
};

loadTables();
</script>

<style scoped>
.page-db-import {
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
  color: #ff976a;
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
  font-weight: 500;
  margin-bottom: 8px;
}

.app-card {
  border-radius: 12px;
  overflow: hidden;
}

.format-section {
  margin-bottom: 16px;
}

.format-radio {
  display: flex;
  gap: 16px;
}

.table-section {
  margin-bottom: 16px;
}

.table-list {
  max-height: 180px;
  overflow-y: auto;
}

.selected-table {
  color: #1989fa;
  font-weight: 500;
}

.upload-section {
  margin-bottom: 16px;
}

.uploader {
  margin-top: 12px;
}

.preview-section {
  margin-bottom: 16px;
}

.data-preview {
  background: white;
  border-radius: 12px;
  margin-top: 8px;
  padding: 8px 0;
}

.preview-item {
  display: flex;
  padding: 8px 16px;
  border-bottom: 1px solid #f5f5f5;
}

.preview-item:last-child {
  border-bottom: none;
}

.preview-index {
  width: 28px;
  flex-shrink: 0;
  font-size: 12px;
  color: #969799;
  font-family: "DIN Alternate", sans-serif;
}

.preview-content {
  flex: 1;
  min-width: 0;
}

.preview-field {
  display: flex;
  gap: 4px;
  font-size: 12px;
  line-height: 1.8;
}

.field-key {
  color: #646566;
  white-space: nowrap;
}

.field-val {
  color: #323233;
  word-break: break-all;
}

.validate-section {
  margin-bottom: 16px;
}

.sql-file-info {
  margin-bottom: 16px;
}

.file-name {
  color: #323233;
  font-weight: 500;
  font-size: 13px;
}

.import-options {
  margin-bottom: 16px;
}

.warning-section {
  margin-bottom: 16px;
}

.action-section {
  margin-bottom: 20px;
}

.import-result {
  margin-bottom: 20px;
}

.num-font {
  font-family: "DIN Alternate", sans-serif;
}

.text-danger {
  color: #ee0a24;
}

.time-text {
  font-size: 13px;
  color: #646566;
}

.backup-file {
  color: #1989fa;
  font-size: 12px;
  font-family: "SF Mono", "Fira Code", monospace;
}

.error-list {
  margin-top: 4px;
}

.error-item {
  font-size: 11px;
  color: #ee0a24;
  padding: 4px 0;
  border-bottom: 1px solid #f5f5f5;
}

.error-item:last-child {
  border-bottom: none;
}

.error-sql {
  color: #969799;
  font-family: "SF Mono", "Fira Code", monospace;
  font-size: 10px;
  margin-top: 2px;
  word-break: break-all;
}
</style>