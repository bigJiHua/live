<template>
  <div class="simple-kb" @selectstart.prevent>
    <!-- 工具栏：仅显示状态，无安全模式切换 -->
    <div class="kb-toolbar">
      <div class="tool-btn">
        <span class="status-dot" />
        普通键盘
      </div>
    </div>

    <!-- 键区：字符直接文本显示，无 canvas / 无加密 / 无随机偏移 -->
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
          <span v-else-if="k === 'space'" class="safe-label">空格</span>
          <template v-else-if="k === 'shift'">
            <span class="sh-aa" :class="{ on: shifted }">A</span><span class="sh-aa" :class="{ on: !shifted }">a</span>
          </template>
          <template v-else-if="k === 'switch'">{{ page === "abc" ? "123" : "ABC" }}</template>
          <template v-else-if="k === 'login'">确认</template>
          <template v-else>{{ displayKey(k) }}</template>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";

// 无需 RSA 的简易安全键盘：字母/数字/符号直接文本渲染，
// 不依赖 JSEncrypt 公钥、无 canvas 防 OCR、无随机偏移。
const props = defineProps({
  modelValue: { type: String, default: "" },
  publicKey: { type: String, default: "" }, // 保留接收，但不做任何加密
});
const emit = defineEmits(["update:modelValue", "login", "input"]);

const page = ref("abc");
const shifted = ref(false);

const ABC_ROWS = [
  ["q","w","e","r","t","y","u","i","o","p"],
  ["a","s","d","f","g","h","j","k","l"],
  ["shift","z","x","c","v","b","n","m","del"],
  ["switch",".","space","login"],
];
const NUM_ROWS = [
  ["1","2","3","4","5","6","7","8","9","0"],
  ["!","@","#","$","%","^","&","*","(",")"],
  ["-","_","+","=",",",".","?",":","'",'"'],
  ["switch",".","space","login"],
];

const rows = computed(() => (page.value === "abc" ? ABC_ROWS : NUM_ROWS));
const displayKey = (k) =>
  page.value === "abc" && /^[a-z]$/.test(k) ? (shifted.value ? k.toUpperCase() : k) : k;

const keyClass = (k) => {
  if (k === "del") return "is-fn is-del";
  if (k === "switch") return "is-fn is-switch";
  if (k === "shift") return "is-fn";
  if (k === "space") return "is-space";
  if (k === "login") return "is-login";
  return "is-char";
};

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
  }
};
</script>

<style scoped>
/* ⚠ 度量必须与登录用 FullKeyboard.vue 保持一致（键高 52 / 行距 6 / 键距 4 / 圆角 8 /
   字符 24·400 / 功能键 17·500 / 键区总高 232）。此前两套键盘各有一套尺寸，
   编辑资料的全键盘明显比登录键盘高出一截。 */
.simple-kb { user-select: none; padding: 0; }
.kb-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 2px 0 8px;
}
.tool-btn {
  display: flex; align-items: center; gap: 5px;
  font-size: 13px; font-weight: 500; color: var(--theme-text-primary, #323233);
  cursor: pointer; user-select: none;
}
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--theme-primary, #07c160); }

.kb-rows { display: flex; flex-direction: column; gap: 6px; min-height: 232px; }
.kb-row { display: grid; grid-template-columns: repeat(10, 1fr); gap: 4px; }
.kb-indent-1 { grid-template-columns: repeat(9, 1fr); justify-content: center; padding: 0 5%; }

.kb-key {
  height: 52px; border: none; border-radius: 8px; font-size: 24px; font-weight: 400;
  background: var(--theme-bg-primary, #f7f8fa); color: var(--theme-text-primary, #323233);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04), inset 0 -1px 0 rgba(0, 0, 0, 0.06);
  cursor: pointer; transition: transform .08s ease, box-shadow .08s ease, background .1s ease;
  -webkit-tap-highlight-color: transparent; min-width: 0; overflow: hidden; white-space: nowrap;
  display: flex; align-items: center; justify-content: center;
}
.kb-key.is-fn { background: var(--theme-bg-tertiary, #e8e9eb); color: var(--theme-text-primary, #323233); font-size: 17px; font-weight: 500; }
.kb-key.is-del { grid-column: span 2; }
.del-arrow { font-size: 24px; line-height: 1; font-weight: 500; }
.kb-key.is-switch { grid-column: span 2; }
.kb-key.is-shift { font-size: 14px; font-weight: 700; }
.sh-aa { font-size: 17px; font-weight: 500; transition: color .15s; }
.sh-aa.on { color: var(--theme-primary, #07c160); }
.kb-key.is-space { grid-column: span 4; cursor: default; pointer-events: none; background: var(--theme-bg-primary, #f7f8fa); }
.safe-label { font-size: 13px; font-weight: 500; color: var(--theme-text-tertiary, #969799); letter-spacing: 2px; }
.kb-key.is-login {
  grid-column: span 3; background: var(--theme-primary, #07c160); color: var(--theme-button-primary-text, #fff); font-size: 17px; font-weight: 600;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04), inset 0 -1px 0 rgba(0, 0, 0, 0.1);
}
.kb-key:active {
  transform: translateY(1px) scale(0.97);
  background: var(--theme-bg-secondary, #d1d4dc);
  box-shadow: 0 0.5px 0 rgba(0, 0, 0, 0.06), inset 0 1px 3px rgba(0, 0, 0, 0.1);
}
.kb-key.is-login:active {
  background: var(--theme-primary-active, #06a050);
  filter: brightness(0.92);
}
.kb-key.is-fn:active { background: var(--theme-bg-quaternary, #8e909a); }
</style>
