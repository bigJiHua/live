<template>
  <div class="page-data-manage">
    <div class="section">
      <div class="section-title">数据管理功能</div>
      <div class="menu-card">
        <div class="menu-item" @click="goToPage('check')">
          <div class="menu-icon blue"><van-icon name="search" /></div>
          <div class="menu-info">
            <div class="menu-title">数据库检查</div>
            <div class="menu-desc">检查数据库连接状态和完整性</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
        <div class="menu-item" @click="goToPage('export')">
          <div class="menu-icon green"><van-icon name="notes-o" /></div>
          <div class="menu-info">
            <div class="menu-title">导出数据库/表</div>
            <div class="menu-desc">导出数据库或指定表数据</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
        <div class="menu-item" @click="goToPage('import')">
          <div class="menu-icon orange"><van-icon name="add-o" /></div>
          <div class="menu-info">
            <div class="menu-title">数据导入</div>
            <div class="menu-desc">从文件导入数据到数据库</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
        <div class="menu-item" @click="goToPage('backup')">
          <div class="menu-icon teal"><van-icon name="records" /></div>
          <div class="menu-info">
            <div class="menu-title">数据库备份</div>
            <div class="menu-desc">查看和管理已导出的备份文件</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
        <div class="menu-item" @click="goToPage('login-log')">
          <div class="menu-icon red"><van-icon name="manager-o" /></div>
          <div class="menu-info">
            <div class="menu-title">查看登录日志</div>
            <div class="menu-desc">近期登录记录和设备信息</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from "vue-router";

const router = useRouter();

const goToPage = (type) => {
  const routes = {
    check: "/finance/data/check",
    export: "/finance/data/export",
    import: "/finance/data/import",
    backup: "/finance/data/backup",
    "login-log": "/finance/data/login-log",
  };
  if (routes[type]) {
    router.push(routes[type]);
  }
};
</script>

<style scoped>
.page-data-manage {
  min-height: 100vh;
  background: #f7f8fa;
  padding: 12px 16px 30px;
}
.section { margin-bottom: 20px; }
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 10px;
  padding-left: 2px;
}
.menu-card {
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
}
.menu-item:last-child { border-bottom: none; }
.menu-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}
.menu-icon.blue { background: #f0f5ff; color: #1989fa; }
.menu-icon.green { background: #f0fff4; color: #07c160; }
.menu-icon.orange { background: #fff7e6; color: #fa8c16; }
.menu-icon.teal { background: #e6f7f5; color: #07c160; }
.menu-icon.red { background: #fff0f0; color: #ee0a24; }
.menu-info { flex: 1; }
.menu-title {
  font-size: 14px;
  font-weight: 500;
  color: #323233;
}
.menu-desc {
  font-size: 11px;
  color: #969799;
  margin-top: 2px;
}
</style>
