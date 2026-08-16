<template>
  <div class="full-kb" @selectstart.prevent>
    <!-- 工具栏：安全/普通模式切换 -->
    <div class="kb-toolbar">
      <div class="tool-btn" @click="isSecure = !isSecure">
        <span class="status-dot" :class="{ on: isSecure }" />
        {{ isSecure ? "安全模式" : "普通模式" }}
      </div>
    </div>

    <!-- 键区 -->
    <div class="kb-rows">
      <div
        v-for="(row, ri) in rows"
        :key="ri"
        class="kb-row"
        :class="{ 'kb-indent-1': page === 'abc' && ri === 1 }"
      >
        <button
          v-for="(k, ki) in row"
          :key="ki"
          type="button"
          class="kb-key"
          :class="keyClass(k)"
          @click="k !== 'space' && onKey(k)"
        >
          <span v-if="k === 'del'" class="del-arrow">←</span>
          <span v-else-if="k === 'space'" class="safe-label">安全键盘</span>
          <template v-else-if="k === 'shift'">
            <span class="sh-aa" :class="{ on: shifted }">A</span
            ><span class="sh-aa" :class="{ on: !shifted }">a</span>
          </template>
          <template v-else-if="k === 'switch'">{{
            page === "abc" ? "123" : "ABC"
          }}</template>
          <template v-else-if="k === 'login'">确认</template>
          <!-- 字符键（字母/数字/符号）：canvas 防脚本读取，安全模式带随机偏移防 OCR -->
          <canvas v-else :id="`kb-cv-${uid}-${ri}-${ki}`" class="key-canvas" />
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import {
  ref,
  computed,
  watch,
  onMounted,
  onBeforeUnmount,
  nextTick,
} from "vue";
import JSEncrypt from "jsencrypt";

const props = defineProps({
  modelValue: { type: String, default: "" },
  publicKey: { type: String, default: "" },
  defaultSecure: { type: Boolean, default: true },
  themeKey: { type: String, default: "" },
  // secureOnly: true 时 secure 模式不 emit update:modelValue/input 明文，
  // 仅通过 secure 事件推送加密值，由父组件累加密文。生产登录建议开启。
  secureOnly: { type: Boolean, default: false },
});
const emit = defineEmits(["update:modelValue", "login", "secure", "input"]);

const page = ref("abc");
const shifted = ref(false);
const uid = Math.random().toString(36).substring(2, 8);
const isSecure = ref(props.defaultSecure);

let encryptor = null;
const getEnc = () => {
  if (!encryptor && props.publicKey) {
    encryptor = new JSEncrypt();
    encryptor.setPublicKey(props.publicKey.trim());
  }
  return encryptor;
};

const ABC_ROWS = [
  ["q", "w", "e", "r", "t", "y", "u", "i", "o", "p"],
  ["a", "s", "d", "f", "g", "h", "j", "k", "l"],
  ["shift", "z", "x", "c", "v", "b", "n", "m", "del"],
  ["switch", ".", "space", "login"],
];
const NUM_ROWS = [
  ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"],
  ["!", "@", "#", "$", "%", "^", "&", "*", "(", ")"],
  ["-", "_", "+", "=", ",", ".", "?", ":", "'", '"'],
  ["switch", ".", "space", "login"],
];

const rows = computed(() => (page.value === "abc" ? ABC_ROWS : NUM_ROWS));
const displayKey = (k) =>
  page.value === "abc" && /^[a-z]$/.test(k)
    ? shifted.value
      ? k.toUpperCase()
      : k
    : k;
const isCharKey = (k) =>
  !["switch", "shift", "space", "del", "login"].includes(k);

const keyClass = (k) => {
  if (k === "del") return "is-fn is-del";
  if (k === "switch") return "is-fn is-switch";
  if (k === "shift") return "is-fn";
  if (k === "space") return "is-space";
  if (k === "login") return "is-login";
  return "is-char";
};

// ── Canvas 绘制（高清适配 + 安全模式随机偏移防 OCR） ──
const drawKeys = () => {
  const dpr = window.devicePixelRatio || 1;
  const fontSize = 24;
  // 始终读主题变量 --theme-text-primary；demo 用 :style="activeVars" 把变量挂在 .kb-demo 上，
  // 必须从 canvas 父盒子（继承自 .kb-demo）读，documentElement 上是全局旧值会拿到错的颜色
  let color = "";
  rows.value.forEach((row, ri) => {
    row.forEach((k, ki) => {
      if (!isCharKey(k)) return;
      const canvas = document.getElementById(`kb-cv-${uid}-${ri}-${ki}`);
      if (!canvas) return;
      if (!color) {
        const parent = canvas.parentElement;
        color =
          getComputedStyle(parent)
            .getPropertyValue("--theme-text-primary")
            .trim() ||
          getComputedStyle(document.documentElement)
            .getPropertyValue("--theme-text-primary")
            .trim() ||
          "#323233";
      }
      const parent = canvas.parentElement;
      if (!parent) return;
      // 取父盒子整数尺寸，确保 cssSize * dpr === bitmapSize，避免缩放模糊
      const rect = parent.getBoundingClientRect();
      const w = Math.max(1, Math.round(rect.width));
      const h = Math.max(1, Math.round(rect.height));
      canvas.width = w * dpr;
      canvas.height = h * dpr;
      canvas.style.width = w + "px";
      canvas.style.height = h + "px";
      const ctx = canvas.getContext("2d");
      ctx.scale(dpr, dpr);
      ctx.font = `bold ${fontSize}px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif`;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillStyle = color;
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = "high";
      const ox = isSecure.value ? (Math.random() - 0.5) * 2.4 : 0;
      const oy = isSecure.value ? (Math.random() - 0.5) * 2.4 : 0;
      ctx.fillText(displayKey(k), w / 2 + ox, h / 2 + oy);
      // secure 模式：撒细密噪点 + 随机干扰线，破坏 OCR 模板匹配
      if (isSecure.value) {
        const noiseCount = Math.floor((w * h) / 220);
        for (let n = 0; n < noiseCount; n++) {
          ctx.fillStyle =
            Math.random() > 0.5
              ? "rgba(0,0,0,0.08)"
              : "rgba(255,255,255,0.18)";
          ctx.fillRect(
            Math.random() * w,
            Math.random() * h,
            1,
            1,
          );
        }
        if (Math.random() > 0.6) {
          ctx.strokeStyle = "rgba(0,0,0,0.06)";
          ctx.lineWidth = 0.5;
          ctx.beginPath();
          ctx.moveTo(Math.random() * w, Math.random() * h);
          ctx.lineTo(Math.random() * w, Math.random() * h);
          ctx.stroke();
        }
      }
    });
  });
};

watch([page, shifted, isSecure], () => nextTick(drawKeys));

// 主题切换时 canvas 文字颜色重新读取（applyTheme 修改 html.style）
// 双重保险：①父组件传 themeKey prop（Vue 响应式可靠） ②MutationObserver 兜底
watch(
  () => props.themeKey,
  () => nextTick(drawKeys),
);

let themeObserver;
const clearCanvases = () => {
  rows.value.forEach((row, ri) => {
    row.forEach((k, ki) => {
      const c = document.getElementById(`kb-cv-${uid}-${ri}-${ki}`);
      if (c) c.getContext("2d").clearRect(0, 0, c.width, c.height);
    });
  });
};
const onVisibility = () => {
  // secure 模式防截屏：标签切后台/页面隐藏时清空 canvas 字符，防止被截图 OCR
  if (document.hidden && isSecure.value) clearCanvases();
  else nextTick(drawKeys);
};
const onWindowBlur = () => {
  // 窗口失焦（切应用/点地址栏等）同样清空，防截屏 OCR
  if (isSecure.value) clearCanvases();
};
onMounted(() => {
  // rAF 等布局完成、首帧前绘制，避免默认 300×150 canvas 被 button overflow 裁剪后看到错位
  requestAnimationFrame(drawKeys);
  themeObserver = new MutationObserver(() => nextTick(drawKeys));
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["style", "data-theme", "data-theme-mode"],
  });
  window.addEventListener("resize", drawKeys);
  document.addEventListener("visibilitychange", onVisibility);
  window.addEventListener("blur", onWindowBlur);
  // 防御：secure 模式却无公钥 → 静默降级 + 警告，避免父组件忘传 publicKey 导致"假安全"
  if (props.defaultSecure && !props.publicKey) {
    console.warn(
      "[FullKeyboard] 安全模式已开启但未提供 publicKey，已自动降级为普通模式，请检查父组件是否传入 :public-key",
    );
    isSecure.value = false;
  }
});
onBeforeUnmount(() => {
  themeObserver?.disconnect();
  window.removeEventListener("resize", drawKeys);
  document.removeEventListener("visibilitychange", onVisibility);
  window.removeEventListener("blur", onWindowBlur);
});

// ── 按键逻辑 ──
// secure 事件统一协议：
//   字符键: { type:'char', char, encrypted }（secureOnly 下 char=null，不泄露明文）
//   删除键: { type:'del' }
//   功能键(shift/switch/login): { type:'func' }（父组件可忽略，无明文）
const onKey = (k) => {
  if (k === "del") {
    // secureOnly：不发射明文删除事件，改发 secure del 协议，父组件 pop 密文数组
    if (props.secureOnly && isSecure.value) emit("secure", { type: "del" });
    else emit("update:modelValue", props.modelValue.slice(0, -1));
  } else if (k === "shift") {
    shifted.value = !shifted.value;
    emit("secure", { type: "func" });
  } else if (k === "switch") {
    page.value = page.value === "abc" ? "num" : "abc";
    emit("secure", { type: "func" });
  } else if (k === "login") {
    emit("login");
    emit("secure", { type: "func" });
  } else {
    const suppressPlain = props.secureOnly && isSecure.value;
    // 同步生成密文（JSEncrypt.encrypt 同步），保证密文数组顺序与按键顺序一致
    const encrypted =
      isSecure.value && props.publicKey
        ? getEnc()?.encrypt(displayKey(k)) || null
        : null;
    if (!suppressPlain) {
      const val = String(props.modelValue || "") + displayKey(k);
      emit("update:modelValue", val);
      emit("input", displayKey(k));
    }
    emit("secure", {
      type: "char",
      // secureOnly 下不发明文（防 hook 截获），普通模式保留 char 兼容旧调用
      char: suppressPlain ? null : displayKey(k),
      encrypted,
    });
  }
};
</script>

<style scoped>
.full-kb {
  user-select: none;
  padding: 0;
}
.kb-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 0 8px;
}
.tool-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text-primary, #323233);
  cursor: pointer;
  user-select: none;
}
.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #999;
  transition:
    background 0.2s,
    box-shadow 0.2s;
}
.status-dot.on {
  background: #07c160;
  box-shadow: 0 0 4px rgba(7, 193, 96, 0.4);
}
.kb-hint {
  font-size: 11px;
  color: var(--theme-text-tertiary, #969799);
}

.kb-rows {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 232px;
}
.kb-row {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 4px;
}
.kb-indent-1 {
  grid-template-columns: repeat(9, 1fr);
  justify-content: center;
  padding: 0 5%;
}

/* 高级阴影（无顶部高光，多层软阴影 + 底部 subtle inset） */
.kb-key {
  height: 52px;
  border: none;
  border-radius: 8px;
  font-size: 24px;
  font-weight: 400;
  background: var(--theme-bg-primary, #ffffff);
  color: var(--theme-text-primary, #323233);
  box-shadow:
    0 1px 0 rgba(0, 0, 0, 0.04),
    inset 0 -1px 0 rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition:
    transform 0.08s ease,
    box-shadow 0.08s ease,
    background 0.1s ease;
  -webkit-tap-highlight-color: transparent;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  display: flex;
  align-items: center;
  justify-content: center;
}
.kb-key.is-fn {
  background: var(--theme-bg-tertiary, #abafb8);
  color: var(--theme-text-primary, #323233);
  font-size: 17px;
  font-weight: 500;
}
.kb-key.is-del {
  grid-column: span 2;
}
.del-arrow {
  font-size: 24px;
  line-height: 1;
  font-weight: 500;
}
.kb-key.is-switch {
  grid-column: span 2;
}
.kb-key.is-shift {
  font-size: 14px;
  font-weight: 700;
}
.sh-aa {
  font-size: 17px;
  font-weight: 500;
  transition: color 0.15s;
}
.sh-aa.on {
  color: var(--theme-primary, #07c160);
}
.kb-key.is-space {
  grid-column: span 4;
  cursor: default;
  pointer-events: none;
  background: var(--theme-bg-primary, #ffffff);
}
.safe-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--theme-text-tertiary, #969799);
  letter-spacing: 2px;
}
.kb-key.is-login {
  grid-column: span 3;
  background: var(--theme-primary, #07c160);
  color: var(--theme-button-primary-text, #fff);
  font-size: 17px;
  font-weight: 600;
  box-shadow:
    0 1px 0 rgba(0, 0, 0, 0.04),
    inset 0 -1px 0 rgba(0, 0, 0, 0.1);
}
/* shift 激活高亮（文字颜色区分，背景不换色） */
.kb-key.is-active {
}
.kb-key:active {
  transform: translateY(1px) scale(0.97);
  background: var(--theme-bg-secondary, #d1d4dc);
  box-shadow:
    0 0.5px 0 rgba(0, 0, 0, 0.06),
    inset 0 1px 3px rgba(0, 0, 0, 0.1);
}
.kb-key.is-login:active {
  background: var(--theme-primary-active, #06a050);
  filter: brightness(0.92);
}
.kb-key.is-fn:active {
  background: var(--theme-bg-quaternary, #8e909a);
}
.key-canvas {
  display: block;
  pointer-events: none;
}
</style>
