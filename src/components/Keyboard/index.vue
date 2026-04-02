<template>
  <div class="safe-keyboard" @selectstart.prevent @click.stop>
    <div class="keyboard-toolbar">
      <div class="tool-btn mode-toggle" @click="toggleMode">
        <span :class="['status-dot', { 'is-secure': isSecure }]"></span>
        {{ isSecure ? "安全模式" : "普通模式" }}
      </div>
      <div class="tool-btn"></div>
    </div>

    <div class="keyboard-grid">
      <div
        v-for="(item, index) in keyConfig"
        :key="`key-${uid}-${index}-${item}`"
        class="key-item"
        :class="{
          'is-empty': item === '',
          'is-functional': item === 'del' || item === 'close',
        }"
        @click="handleKeyClick(item)"
      >
        <template v-if="typeof item === 'number'">
          <canvas
            :id="`canvas-${uid}-${index}`"
            width="40"
            height="40"
            class="key-canvas"
          />
        </template>
        <template v-else-if="item === 'del'">删除</template>
        <template v-else-if="item === 'close'">
          <img :src="closeIcon" class="close-icon" alt="收起" />
        </template>
        <template v-else>{{ item }}</template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from "vue";
import JSEncrypt from "jsencrypt";
import closeIcon from "@/assets/icon/keyboard.svg";

const props = defineProps({
  publicKey: { type: String, default: "" },
});

const emit = defineEmits(["input", "secure-payload", "confirm"]);

const uid = Math.random().toString(36).substring(2, 8);
const isSecure = ref(true);
const keyConfig = ref([]);

let encryptor = null;
const getEncryptor = (key) => {
  if (!encryptor && key) {
    encryptor = new JSEncrypt();
    encryptor.setPublicKey(key.trim());
  }
  return encryptor;
};

/**
 * 核心布局逻辑
 * 保证：最后一行左边是收起 中间是 0 (或随机) 右边是 删除
 */
const initLayout = () => {
  const nums = [1, 2, 3, 4, 5, 6, 7, 8, 9];

  if (isSecure.value) {
    // 安全模式：打乱 1-9
    const shuffled9 = [...nums].sort(() => Math.random() - 0.5);
    // 布局：[shuffled 1-9] + [收起] + [0] + [删除]
    keyConfig.value = [...shuffled9, "close", 0, "del"];
  } else {
    // 普通模式：[1-9 顺序] + [收起] + [0] + [删除]
    keyConfig.value = [...nums, "close", 0, "del"];
  }

  nextTick(drawKeys);
};

const drawKeys = () => {
  // 获取设备像素比（核心！解决模糊）
  const dpr = window.devicePixelRatio || 1;

  keyConfig.value.forEach((val, idx) => {
    if (typeof val !== "number") return;
    const canvas = document.getElementById(`canvas-${uid}-${idx}`);
    if (!canvas) return;

    // 关键：按高清屏放大画布尺寸
    const w = 40;
    const h = 40;
    canvas.width = w * dpr;
    canvas.height = h * dpr;
    canvas.style.width = w + "px";
    canvas.style.height = h + "px";

    const ctx = canvas.getContext("2d");
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // 缩放上下文，适配高清
    ctx.scale(dpr, dpr);

    // 字体、位置不变
    ctx.font =
      'bold 26px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillStyle = "#111";
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = "high";

    if (isSecure.value) {
      const ox = (Math.random() - 0.5) * 1.5;
      const oy = (Math.random() - 0.5) * 1.5;
      ctx.fillText(val, 20 + ox, 20 + oy);
    } else {
      ctx.fillText(val, 20, 20);
    }
  });
};

const toggleMode = () => {
  isSecure.value = !isSecure.value;
  initLayout();
};

const handleKeyClick = (val) => {
  if (val === "") return;
  if (val === "close") {
    emit("confirm");
    return;
  }
  emit("input", val);

  if (props.publicKey && typeof val === "number") {
    Promise.resolve().then(() => {
      const crypt = getEncryptor(props.publicKey);
      if (crypt) {
        const encrypted = crypt.encrypt(val.toString());
        emit("secure-payload", encrypted);
      }
    });
  }
};

onMounted(initLayout);
</script>

<style scoped>
.safe-keyboard {
  background: #e5e6eb;
  padding-bottom: env(safe-area-inset-bottom);
  user-select: none;
}

/* 工具栏 */
.keyboard-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: #f7f7f7;
  border-bottom: 1px solid #d9d9d9;
}

.tool-btn {
  font-size: 14px;
  color: #444;
  display: flex;
  align-items: center;
  gap: 4px;
}

.mode-toggle {
  color: #333;
}

.status-dot {
  width: 7px;
  height: 7px;
  background: #999;
  border-radius: 50%;
}

.status-dot.is-secure {
  background: #009944; /* 农行绿 */
  box-shadow: 0 0 3px rgba(0, 153, 68, 0.5);
}

/* 键盘布局网格 */
.keyboard-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  padding: 6px;
  background: #e5e6eb;
}

/* 按键样式 */
.key-item {
  height: 52px;
  background: #ffffff;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #000000;
  border: 0.5px solid #d0d0d0;
  transition: background 0.1s ease;
}

/* 按下反馈 */
.key-item:active {
  background: #d8d8d8 !important;
}

/* 功能键样式（点和删除） */
.is-functional {
  background: #f2f2f2 !important;
  font-size: 16px;
}

.key-canvas {
  pointer-events: none;
}

.close-icon {
  width: 50px;
  height: 38px;
}
</style>
