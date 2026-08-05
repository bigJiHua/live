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
            <span class="sh-aa" :class="{ on: shifted }">A</span><span class="sh-aa" :class="{ on: !shifted }">a</span>
          </template>
          <template v-else-if="k === 'switch'">{{ page === "abc" ? "123" : "ABC" }}</template>
          <template v-else-if="k === 'login'">确认</template>
          <!-- 字符键（字母/数字/符号）：canvas 防脚本读取，安全模式带随机偏移防 OCR -->
          <canvas v-else :id="`kb-cv-${uid}-${ri}-${ki}`" class="key-canvas" />
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from "vue";
import JSEncrypt from "jsencrypt";

const props = defineProps({
  modelValue: { type: String, default: "" },
  publicKey: { type: String, default: "" },
  defaultSecure: { type: Boolean, default: true },
  themeKey: { type: String, default: "" },
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
  ["q","w","e","r","t","y","u","i","o","p"],
  ["a","s","d","f","g","h","j","k","l"],
  ["shift","z","x","c","v","b","n","m","del"],
  ["switch","space","login"],
];
const NUM_ROWS = [
  ["1","2","3","4","5","6","7","8","9","0"],
  ["!","@","#","$","%","^","&","*","(",")"],
  ["-","_","+","=",",",".","?",":","'",'"'],
  ["switch","space","login"],
];

const rows = computed(() => (page.value === "abc" ? ABC_ROWS : NUM_ROWS));
const displayKey = (k) =>
  page.value === "abc" && /^[a-z]$/.test(k) ? (shifted.value ? k.toUpperCase() : k) : k;
const isCharKey = (k) => !["switch","shift","space","del","login"].includes(k);

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
  const fontSize = 22;
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
        color = getComputedStyle(parent).getPropertyValue("--theme-text-primary").trim()
          || getComputedStyle(document.documentElement).getPropertyValue("--theme-text-primary").trim()
          || "#323233";
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
      const ox = isSecure.value ? (Math.random() - 0.5) * 1.2 : 0;
      const oy = isSecure.value ? (Math.random() - 0.5) * 1.2 : 0;
      ctx.fillText(displayKey(k), w / 2 + ox, h / 2 + oy);
    });
  });
};

watch([page, shifted, isSecure], () => nextTick(drawKeys));

// 主题切换时 canvas 文字颜色重新读取（applyTheme 修改 html.style）
// 双重保险：①父组件传 themeKey prop（Vue 响应式可靠） ②MutationObserver 兜底
watch(() => props.themeKey, () => nextTick(drawKeys));

let themeObserver;
onMounted(() => {
  // rAF 等布局完成、首帧前绘制，避免默认 300×150 canvas 被 button overflow 裁剪后看到错位
  requestAnimationFrame(drawKeys);
  themeObserver = new MutationObserver(() => nextTick(drawKeys));
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ["style", "data-theme", "data-theme-mode"] });
  window.addEventListener("resize", drawKeys);
});
onBeforeUnmount(() => {
  themeObserver?.disconnect();
  window.removeEventListener("resize", drawKeys);
});

// ── 按键逻辑 ──
const onKey = (k) => {
  if (k === "del") {
    emit("update:modelValue", props.modelValue.slice(0, -1));
  } else if (k === "shift") {
    shifted.value = !shifted.value;
  } else if (k === "switch") {
    page.value = page.value === "abc" ? "num" : "abc";
  } else if (k === "login") {
    emit("login");
  } else {
    const val = String(props.modelValue || "") + displayKey(k);
    emit("update:modelValue", val);
    emit("input", displayKey(k));
    if (isSecure.value && props.publicKey) {
      const crypt = getEnc();
      if (crypt) {
        Promise.resolve().then(() => {
          emit("secure", { char: displayKey(k), encrypted: crypt.encrypt(displayKey(k)) });
        });
      }
    }
  }
};
</script>

<style scoped>
.full-kb { user-select: none; padding: 0; }
.kb-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 4px 10px;
}
.tool-btn {
  display: flex; align-items: center; gap: 5px;
  font-size: 13px; font-weight: 600; color: var(--theme-text-primary, #323233);
  cursor: pointer; user-select: none;
}
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: #999; transition: background .2s, box-shadow .2s; }
.status-dot.on { background: #07c160; box-shadow: 0 0 4px rgba(7,193,96,.4); }
.kb-hint { font-size: 11px; color: var(--theme-text-tertiary, #969799); }

.kb-rows { display: flex; flex-direction: column; gap: 7px; min-height: 253px; }
.kb-row { display: grid; grid-template-columns: repeat(10, 1fr); gap: 6px; }
.kb-indent-1 { padding-left: 5%; }

/* 高级阴影（无顶部高光，多层软阴影 + 底部 subtle inset） */
.kb-key {
  height: 58px; border: none; border-radius: 10px; font-size: 20px; font-weight: 500;
  background: var(--theme-bg-primary, #f7f8fa); color: var(--theme-text-primary, #323233);
  box-shadow: 0 2px 6px rgba(0,0,0,.10), 0 4px 14px rgba(0,0,0,.05), inset 0 -1px 0 rgba(0,0,0,.05);
  cursor: pointer; transition: transform .05s ease, box-shadow .05s ease, background .08s ease;
  -webkit-tap-highlight-color: transparent; min-width: 0; overflow: hidden; white-space: nowrap;
  display: flex; align-items: center; justify-content: center;
}
.kb-key.is-fn { background: var(--theme-bg-tertiary, #e8e9eb); color: var(--theme-text-secondary, #646566); font-size: 19px; }
.kb-key.is-del { grid-column: span 2; }
.del-arrow { font-size: 26px; line-height: 1; font-weight: 700; }
.kb-key.is-switch { grid-column: span 2; }
.kb-key.is-shift { font-size: 15px; font-weight: 700; }
.sh-aa { font-size: 17px; font-weight: 600; transition: color .15s; }
.sh-aa.on { color: var(--theme-primary, #07c160); }
.kb-key.is-space { grid-column: span 5; cursor: default; pointer-events: none; }
.safe-label { font-size: 14px; font-weight: 600; color: var(--theme-text-tertiary, #969799); letter-spacing: 2px; }
.kb-key.is-login {
  grid-column: span 3; background: var(--theme-primary, #07c160); color: var(--theme-button-primary-text, #fff); font-size: 19px; font-weight: 700;
  box-shadow: 0 2px 6px rgba(0,0,0,.14), 0 4px 14px rgba(0,0,0,.08);
}
/* shift 激活高亮（文字颜色区分，背景不换色） */
.kb-key.is-active {}
.kb-key:active {
  transform: translateY(2px);
  box-shadow: 0 1px 3px rgba(0,0,0,.10), inset 0 2px 3px rgba(0,0,0,.08);
}
.key-canvas { display: block; pointer-events: none; }
</style>
