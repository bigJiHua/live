<template>
  <div class="safe-keyboard" @selectstart.prevent @click.stop>
    <div class="keyboard-toolbar">
      <div class="tool-btn mode-toggle" @click="toggleMode">
        <span :class="['status-dot', { 'is-secure': isSecure }]"></span>
        {{ isSecure ? "安全模式" : "普通模式" }}
        <van-icon :name="isSecure ? 'shield-o' : 'shield' " class="mode-icon" />
      </div>
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
          <van-icon name="arrow-down" class="close-icon" />
        </template>
        <template v-else>{{ item }}</template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from "vue";
import JSEncrypt from "jsencrypt";

const props = defineProps({
  publicKey: { type: String, default: "" },
  // secureOnly: true 时 secure 模式不 emit input 明文，仅通过 secure-payload 推送加密值
  secureOnly: { type: Boolean, default: false },
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

  // 从根节点读取主题文字色（跟随主题切换）
  const rootStyle = getComputedStyle(document.documentElement);
  const themeColor =
    rootStyle.getPropertyValue("--theme-text-primary").trim() || "#323233";

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
      '600 24px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillStyle = themeColor;
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = "high";

    if (isSecure.value) {
      const ox = (Math.random() - 0.5) * 2.4;
      const oy = (Math.random() - 0.5) * 2.4;
      ctx.fillText(val, 20 + ox, 20 + oy);
      // 细密噪点 + 随机干扰线，破坏 OCR 模板匹配
      const noiseCount = Math.floor((w * h) / 120);
      for (let n = 0; n < noiseCount; n++) {
        ctx.fillStyle =
          Math.random() > 0.5
            ? "rgba(0,0,0,0.08)"
            : "rgba(255,255,255,0.18)";
        ctx.fillRect(Math.random() * w, Math.random() * h, 1, 1);
      }
      if (Math.random() > 0.6) {
        ctx.strokeStyle = "rgba(0,0,0,0.06)";
        ctx.lineWidth = 0.5;
        ctx.beginPath();
        ctx.moveTo(Math.random() * w, Math.random() * h);
        ctx.lineTo(Math.random() * w, Math.random() * h);
        ctx.stroke();
      }
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
    // secureOnly：收起键也走 secure-payload 协议（父组件用其终止/校验）
    if (props.secureOnly && isSecure.value) emit("secure-payload", { type: "close" });
    emit("confirm");
    return;
  }
  if (val === "del") {
    // 删除键：secureOnly 走协议，父组件 pop 密文数组
    if (props.secureOnly && isSecure.value) {
      emit("secure-payload", { type: "del" });
      return;
    }
    emit("input", val);
    return;
  }

  const suppressPlain = props.secureOnly && isSecure.value;
  if (!suppressPlain) emit("input", val);

  if (isSecure.value && props.publicKey && typeof val === "number") {
    const crypt = getEncryptor(props.publicKey);
    if (crypt) {
      const encrypted = crypt.encrypt(val.toString());
      emit("secure-payload", { type: "char", encrypted });
    }
  } else if (suppressPlain) {
    emit("secure-payload", { type: "char", encrypted: null });
  }
};

// ── 防截屏：页面隐藏/窗口失焦时清空 canvas 字符 ──
const clearCanvases = () => {
  keyConfig.value.forEach((val, idx) => {
    if (typeof val !== "number") return;
    const canvas = document.getElementById(`canvas-${uid}-${idx}`);
    if (canvas) canvas.getContext("2d").clearRect(0, 0, canvas.width, canvas.height);
  });
};
const onVisibility = () => {
  if (document.hidden && isSecure.value) clearCanvases();
  else nextTick(drawKeys);
};
const onWindowBlur = () => {
  if (isSecure.value) clearCanvases();
};

onMounted(() => {
  initLayout();
  document.addEventListener("visibilitychange", onVisibility);
  window.addEventListener("blur", onWindowBlur);
  // 防御：secureOnly 要求加密链路却无公钥 → 静默降级 + 警告，避免"假安全"
  if (props.secureOnly && !props.publicKey) {
    console.warn(
      "[SafeKeyboard] secureOnly 已开启但未提供 publicKey，已自动降级为普通模式，请检查父组件是否传入 :public-key"
    );
    isSecure.value = false;
  }
});
onBeforeUnmount(() => {
  document.removeEventListener("visibilitychange", onVisibility);
  window.removeEventListener("blur", onWindowBlur);
});
</script>

<style scoped>
.safe-keyboard {
  background: var(--theme-bg-tertiary, #eef0f3);
  padding-bottom: env(safe-area-inset-bottom);
  user-select: none;
}

/* 工具栏 */
.keyboard-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
}

.tool-btn {
  font-size: 13px;
  font-weight: 500;
  color: var(--theme-text-secondary, #646566);
  display: flex;
  align-items: center;
  gap: 6px;
}

.mode-toggle {
  cursor: pointer;
}

.status-dot {
  width: 7px;
  height: 7px;
  background: var(--theme-text-tertiary, #c8c9cc);
  border-radius: 50%;
  transition: background 0.2s, box-shadow 0.2s;
}

.status-dot.is-secure {
  background: var(--theme-primary, #07c160);
  box-shadow: 0 0 6px var(--theme-primary, #07c160);
}

.mode-icon {
  font-size: 14px;
  color: var(--theme-primary, #07c160);
}

/* 键盘布局网格 */
.keyboard-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 4px 12px 12px;
}

/* 按键样式 */
.key-item {
  height: 54px;
  background: var(--theme-bg-secondary, #ffffff);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 500;
  color: var(--theme-text-primary, #323233);
  border: none;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1), inset 0 -1px 0 rgba(0, 0, 0, 0.05);
  transition: transform 0.06s ease, box-shadow 0.06s ease, background 0.1s ease;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

/* 按下反馈 */
.key-item:active {
  background: var(--theme-bg-tertiary, #e8e9eb);
  transform: translateY(2px);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

/* 功能键样式（收起和删除） */
.is-functional {
  background: var(--theme-bg-primary, #eef0f3);
  font-size: 16px;
  color: var(--theme-text-secondary, #646566);
}

.is-functional:active {
  background: var(--theme-bg-tertiary, #e2e4e8);
}

.key-canvas {
  pointer-events: none;
}

.close-icon {
  font-size: 22px;
  color: var(--theme-text-secondary, #646566);
}
</style>
