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
.simple-kb { user-select: none; padding: 0; }
.kb-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 4px 10px;
}
.tool-btn {
  display: flex; align-items: center; gap: 5px;
  font-size: 13px; font-weight: 600; color: var(--theme-text-primary, #323233);
  cursor: pointer; user-select: none;
}
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--theme-primary, #07c160); }

.kb-rows { display: flex; flex-direction: column; gap: 7px; min-height: 253px; }
.kb-row { display: grid; grid-template-columns: repeat(10, 1fr); gap: 6px; }
.kb-indent-1 { padding-left: 5.5%; }

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
.kb-key.is-space { grid-column: span 4; cursor: default; pointer-events: none; }
.safe-label { font-size: 14px; font-weight: 600; color: var(--theme-text-tertiary, #969799); letter-spacing: 2px; }
.kb-key.is-login {
  grid-column: span 3; background: var(--theme-primary, #07c160); color: var(--theme-button-primary-text, #fff); font-size: 19px; font-weight: 700;
  box-shadow: 0 2px 6px rgba(0,0,0,.14), 0 4px 14px rgba(0,0,0,.08);
}
.kb-key:active {
  transform: translateY(2px);
  box-shadow: 0 1px 3px rgba(0,0,0,.10), inset 0 2px 3px rgba(0,0,0,.08);
}
</style>
