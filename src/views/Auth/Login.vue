<template>
  <div
    class="login-container"
    :class="{ 'keyboard-open': activeField }"
    :style="{ '--kb-height': kbHeight + 'px' }"
    @click="
      activeField &&
      $event.target === $event.currentTarget &&
      (activeField = null)
    "
  >
    <div class="login-header">
      <div class="logo">
        <img src="/logo.png" alt="Gold 财管" class="icon" />
      </div>
      <h1 class="title">Gold 财管</h1>
      <p class="subtitle">开启您的数字资产管理</p>
    </div>

    <app-form @submit="onSubmit" class="login-form">
      <van-cell-group inset>
        <div class="field-wrap" :class="{ active: activeField === 'username' }">
          <app-field
            v-model="username"
            name="username"
            label="账号"
            placeholder="请输入账号或邮箱"
            left-icon="user-o"
            maxlength="50"
            readonly
            autocomplete="username"
            :rules="usernameRules"
            :error="usernameError"
            @click="onFieldClick('username')"
          />
          <span
            class="field-cursor"
            :style="{
              left: curUser + 'px',
              top: curUserTop + 'px',
              transform: 'translateY(-50%)',
            }"
          />
          <span class="field-measure" ref="mUser">{{ username || " " }}</span>
        </div>
        <div class="field-wrap pwd-canvas-field" :class="{ active: activeField === 'password' }">
          <!-- 安全界面：input 值仅为 ● 掩码串（DOM 无明文）；真实明文仅由 canvas 像素绘制，F12 不可读 -->
          <app-field
            :model-value="'●'.repeat((pwdEncrypted.value?.length) || (password.value?.length) || 0)"
            type="password"
            name="password"
            label="密码"
            placeholder="请输入密码"
            left-icon="lock"
            autocomplete="current-password"
            maxlength="30"
            :password-visible="false"
            :error="passwordError"
            readonly
            @click="onFieldClick('password')"
          >
            <template #right-icon>
              <van-icon
                :name="passwordReveal ? 'eye-o' : 'closed-eye'"
                class="pwd-eye"
                @click.stop="toggleReveal"
              />
            </template>
          </app-field>
          <!-- canvas 覆盖输入文字区绘制掩码/明文 + 光标；pointer-events:none 让点击穿透到 input -->
          <canvas ref="pwdCanvas" class="pwd-canvas" />
          <p v-if="passwordReveal" class="pwd-reveal-tip">
            为了您的安全，请勿在公共场合下显示密码
          </p>
        </div>
      </van-cell-group>
      <!-- 
      <div class="password-requirements">
        <strong>密码要求:</strong>
        <ul>
          <li :class="passwordRulesComputed.hasUpperCase ? 'valid' : 'invalid'">
            ✔ 包含大写字母
          </li>
          <li :class="passwordRulesComputed.hasLowerCase ? 'valid' : 'invalid'">
            ✔ 包含小写字母
          </li>
          <li :class="passwordRulesComputed.hasNumber ? 'valid' : 'invalid'">
            ✔ 包含数字
          </li>
          <li :class="passwordRulesComputed.hasSpecial ? 'valid' : 'invalid'">
            ✔ 包含特殊字符
          </li>
          <li :class="passwordRulesComputed.validLength ? 'valid' : 'invalid'">
            ✔ 长度 6-30 位
          </li>
        </ul>
      </div> -->

      <div class="submit-bar">
        <van-row gutter="12">
          <!-- <van-col span="12">
            <app-button round block plain type="primary" @click="goToRegister">
              注册账号
            </app-button>
          </van-col> -->
          <van-col span="24">
            <app-button
              round
              block
              type="primary"
              native-type="submit"
              :loading="loading"
              loading-text="正在安全登录..."
              :disabled="!canSubmit"
            >
              立即登录
            </app-button>
          </van-col>
        </van-row>
      </div>
    </app-form>

    <!-- 安全键盘弹层 -->
    <transition name="kb-up">
      <div v-if="activeField" ref="kbSheet" class="kb-sheet">
        <FullKeyboard
          :model-value="activeValue"
          :public-key="publicKey"
          :secure-only="activeField === 'password'"
          @update:model-value="onKeyInput"
          @secure="onSecureKey"
          @login="activeField = null"
        />
      </div>
    </transition>

    <!-- TODO 注释 -->
    <template v-if="showDemoInfo">
      <div
        style="
          text-align: center;
          padding: 20px;
          font-size: 12px;
          color: var(--theme-text-tertiary);
        "
      >
        本站点仅作演示效果，推荐使用手机Chrome浏览器打开预览。锁定PIN码为 123456
      </div>
    </template>
    <div v-else class="login-footer">
      <div class="footer-brand">
        <img src="/logo.png" :alt="brandName" class="footer-logo" />
        <span class="footer-name">{{ brandName }}</span>
        <a
          href="https://beian.miit.gov.cn/"
          target="_blank"
          rel="noopener noreferrer"
          style="color: var(--theme-text-tertiary)"
          >{{ icpNumber }}</a
        >
      </div>
      <div class="footer-info">
        <a
          href="http://www.beian.gov.cn/"
          target="_blank"
          rel="noopener noreferrer"
        >
          <img src="/icons/ga-beian.png" class="beian-icon" alt="公安备案" />
          {{ psbNumber }}
        </a>
      </div>
      <div class="footer-meta">
        <span>Power By {{ poweredBy }}</span>
        <span class="footer-sep">|</span>
        <span>© {{ copyrightStart }}-{{ currentYear }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from "vue";
import { useRouter } from "vue-router";
import { authApi } from "@/utils/api/auth";
import FullKeyboard from "@/components/KeyBoard/FullKeyboard.vue";
import { getRsaPublicKey } from "@/utils/request/handshake";
import { getClientContext } from "@/utils/request/client";
import { markAuthed } from "@/utils/authState";
import { showToast } from "@/utils/toast";

const router = useRouter();
// 仅在演示模式预填演示账号；生产环境不预填任何凭据，避免硬编码密码泄露
const username = ref(
  import.meta.env.VITE_APP_DEMO === "true"
    ? import.meta.env.VITE_LOGIN_USERNAME || ""
    : "",
);
const password = ref(
  import.meta.env.VITE_APP_DEMO === "true"
    ? import.meta.env.VITE_LOGIN_PASSWORD || ""
    : "",
);
// 安全键盘 RSA 密文字符数组（secureOnly 下密码仅以密文形式存在，明文不经过事件/ref）
const pwdEncrypted = ref([]);
// 眼睛预览用的明文字符数组：仅本地内存，由 secure 事件的 char 重建，不上网络、不进 DOM 输入框
const pwdPlain = ref([]);

const loading = ref(false);
const activeField = ref(null);
const pendingField = ref(null);
const publicKey = ref("");
// 点击字段：弹键盘前确保 RSA 公钥已就绪，避免公钥异步未到达时 FullKeyboard 误判降级
const ensurePublicKey = async () => {
  if (publicKey.value) return;
  try {
    const deviceData = await getClientContext();
    const pk = await getRsaPublicKey(deviceData);
    if (pk) publicKey.value = pk;
  } catch (e) {
    console.warn("[Login] 获取 RSA 公钥失败，安全键盘将降级为普通模式", e);
  }
};
const onFieldClick = (field) => {
  // 公钥就绪前不弹键盘，避免 activeField 在 await 期间错位导致输入路由到错误的字段
  if (!publicKey.value) {
    pendingField.value = field;
    ensurePublicKey();
    return;
  }
  activeField.value = field;
};
// 公钥异步到达后，若点击时正处于等待，立即弹出对应字段的键盘
watch(publicKey, (pk) => {
  if (pk && pendingField.value) {
    activeField.value = pendingField.value;
    pendingField.value = null;
  }
});
// 键盘唤起适配：记录键盘弹层实际高度，用于收缩容器高度把输入框顶到键盘上方
const kbSheet = ref(null);
const kbHeight = ref(0);
watch(activeField, async (v) => {
  if (v) {
    await nextTick();
    requestAnimationFrame(() => {
      kbHeight.value = kbSheet.value?.offsetHeight || 0;
      // 确保激活的输入框滚动到可见区域（小屏设备容器可滚动）
      const wrap = document.querySelector(".field-wrap.active");
      if (wrap) wrap.scrollIntoView({ block: "nearest", behavior: "smooth" });
    });
  } else {
    kbHeight.value = 0;
  }
});
// 密码明文显示开关（眼睛按钮）——仅本地展示，不影响 readonly + 安全键盘输入逻辑
const passwordReveal = ref(false);
const pwdCanvas = ref(null);
const cursorOn = ref(true); // 密码框 canvas 光标闪烁
const toggleReveal = () => {
  passwordReveal.value = !passwordReveal.value;
  nextTick(drawPwdCanvas);
};

// 光标跟随位置（用户名框仍用 DOM measure 模拟；密码框改为 canvas 内绘制）
// 注意：安全界面——字段为 readonly，输入经由 FullKeyboard 安全键盘，密码以 ● 圆点显示。
const mUser = ref(null);
const curUser = ref(8);
const curUserTop = ref(42);

// 通过克隆输入框精确测量密码宽度
function measurePasswordWidth(inputEl) {
  // 创建一个克隆的 input[type=password] 来测量
  const clone = document.createElement("input");
  clone.type = "password";
  clone.value = inputEl.value;

  // 复制实际输入框的样式
  const styles = window.getComputedStyle(inputEl);
  clone.style.cssText = `
    position: absolute;
    visibility: hidden;
    pointer-events: none;
    font: ${styles.font};
    letter-spacing: ${styles.letterSpacing};
    padding: ${styles.padding};
    border: ${styles.border};
    box-sizing: ${styles.boxSizing};
    /* 不能 width:auto：input 宽度由默认 size=20 决定（约189px），内容不超宽时 scrollWidth 恒等于可视宽，
       导致光标钉死在右侧。强制超窄 1px 让内容溢出，scrollWidth 才反映密码字形真实总宽 */
    width: 1px;
    min-width: 0;
  `;

  document.body.appendChild(clone);
  const width = clone.scrollWidth;
  document.body.removeChild(clone);

  return width;
}

function syncCursorCaret(refEl, setLeft, setTop, isPassword = false) {
  if (!refEl) return;
  const wrap = refEl.closest(".field-wrap");
  const input = wrap && wrap.querySelector(".app-field__input");
  if (!input) return;
  // 让隐藏测量元素与输入框文字完全重叠（仅作纵向锚定）
  refEl.style.left = input.offsetLeft + "px";
  refEl.style.top = input.offsetTop + "px";

  let rawWidth;
  if (isPassword && input.type === "password" && input.value) {
    // 对于密码字段，通过克隆输入框精确测量
    rawWidth = measurePasswordWidth(input);
  } else {
    // 明文使用测量元素的 offsetWidth
    rawWidth = refEl.offsetWidth;
  }

  const overflow = rawWidth - input.clientWidth;
  if (overflow > 0) {
    // 内容超宽：程序化滚动目标 input 到末尾，光标停在可视右缘
    input.scrollLeft = overflow;
    setLeft(input.offsetLeft + input.clientWidth);
  } else {
    input.scrollLeft = 0;
    setLeft(input.offsetLeft + rawWidth);
  }
  // 以隐藏测量元素（与输入框文字同字体、已与 input 同位置）的垂直中心对齐光标，
  // 避免 text / password 输入框 clientHeight 差异导致光标错位
  setTop(refEl.offsetTop + refEl.offsetHeight / 2);
}

watch(username, () =>
  nextTick(() => {
    syncCursorCaret(
      mUser.value,
      (v) => (curUser.value = v),
      (v) => (curUserTop.value = v),
      false,
    );
  }),
);
let themeObserver = null;
let cursorTimer = null;
onMounted(() => {
  // 立即绘制 canvas（占位符/掩码），不依赖异步公钥，避免占位符延迟卡顿出现
  nextTick(() => {
    syncCursorCaret(
      mUser.value,
      (v) => (curUser.value = v),
      (v) => (curUserTop.value = v),
      false,
    );
    drawPwdCanvas();
  });
  // 密码框 canvas 光标闪烁
  cursorTimer = setInterval(() => {
    cursorOn.value = !cursorOn.value;
    if (activeField.value === "password") drawPwdCanvas();
  }, 530);
  // 主题/暗色切换时（:root 变量或 html class 变化）重绘 canvas，保持颜色适配
  themeObserver = new MutationObserver(() => drawPwdCanvas());
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["style", "class"],
  });

  // 异步获取 RSA 公钥（安全键盘字符级加密）；失败则键盘自动降级为普通模式
  // 独立异步执行，不阻塞占位符/canvas 的首次绘制
  (async () => {
    try {
      const deviceData = await getClientContext();
      publicKey.value = (await getRsaPublicKey(deviceData)) || "";
    } catch (e) {
      console.warn("[Login] 获取 RSA 公钥失败，安全键盘将降级为普通模式", e);
    }
  })();
});
onBeforeUnmount(() => {
  themeObserver?.disconnect();
  if (cursorTimer) clearInterval(cursorTimer);
});
const currentYear = new Date().getFullYear();
const showDemoInfo = import.meta.env.VITE_APP_DEMO === "true";
const brandName = import.meta.env.VITE_BRAND_NAME;
const icpNumber = import.meta.env.VITE_ICP_NUMBER;
const psbNumber = import.meta.env.VITE_PSB_NUMBER;
const poweredBy = import.meta.env.VITE_POWERED_BY;
const copyrightStart = import.meta.env.VITE_COPYRIGHT_START;

// 键盘 v-model 绑定当前激活字段值
// 密码显示遮罩：input 不持有明文，仅渲染掩码
// secureOnly 下明文不落 ref/事件，掩码长度 = RSA 密文数组长度
const pwdMask = computed(() => {
  if (passwordReveal.value) {
    // 眼睛打开：展示明文（安全键盘走本地明文缓存 pwdPlain，普通模式走 password ref）
    if (pwdEncrypted.value?.length) return pwdPlain.value.join("");
    if (password.value) return password.value;
  }
  return "*".repeat((pwdEncrypted.value?.length) || (password.value?.length) || 0);
});

// 密码框由 canvas 绘制（掩码/明文/光标），下列变化均需重绘
watch([pwdMask, passwordReveal, pwdEncrypted, pwdPlain, activeField], () =>
  nextTick(drawPwdCanvas),
);

const activeValue = computed(() => {
  if (activeField.value === "username") return username.value;
  if (activeField.value === "password") return password.value;
  return "";
});
// 输入上限与后端统一（api/src/modules/auth/rules：nameOrEmail max 50，password 6-30）
const onKeyInput = (val) => {
  if (!activeField.value) return;
  if (activeField.value === "username") username.value = val.slice(0, 50);
  else if (activeField.value === "password") password.value = val.slice(0, 30);
};

// secure 事件：密码以密文数组提交；明文仅本地缓存用于眼睛预览（不上网络、不进 DOM 输入框）
const onSecureKey = (payload) => {
  if (!payload || activeField.value !== "password") return;
  if (payload.type === "char") {
    if (payload.encrypted) pwdEncrypted.value.push(payload.encrypted);
    if (pwdEncrypted.value.length > 30) pwdEncrypted.value.pop();
    if (payload.char) {
      pwdPlain.value.push(payload.char);
      if (pwdPlain.value.length > 30) pwdPlain.value.pop();
    }
  } else if (payload.type === "del") {
    pwdEncrypted.value.pop();
    pwdPlain.value.pop();
  }
  nextTick(drawPwdCanvas);
};

// canvas 绘制密码框：DOM 的 input 文字已设为透明且值仅为 ● 掩码，明文只在此处像素绘制
function drawPwdCanvas() {
  const cv = pwdCanvas.value;
  if (!cv) return;
  const wrap = cv.closest(".field-wrap");
  const input = wrap && wrap.querySelector(".app-field__input");
  if (!input) return;
  const dpr = window.devicePixelRatio || 1;
  const r = wrap.getBoundingClientRect();
  const W = r.width;
  const H = r.height;
  if (cv.width !== Math.round(W * dpr)) {
    cv.width = Math.round(W * dpr);
    cv.height = Math.round(H * dpr);
    cv.style.width = W + "px";
    cv.style.height = H + "px";
  }
  const ctx = cv.getContext("2d");
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, W, H);

  const ir = input.getBoundingClientRect();
  const paddingLeft = parseFloat(getComputedStyle(input).paddingLeft) || 0;
  const x0 = ir.left - r.left + paddingLeft;
  const yMid = ir.top - r.top + ir.height / 2;

  const fontStack =
    '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
  ctx.font = "14px " + fontStack;
  ctx.textBaseline = "middle";

  // 颜色跟随主题变量（暗色背景下也能看清），不硬编码
  const readVar = (name, fb) =>
    (getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fb);
  const inputColor = readVar("--van-field-input-text-color", "#323233");
  const placeholderColor = readVar("--van-field-placeholder-text-color", "#c8c9cc");

  const maskLen = (pwdEncrypted.value?.length) || (password.value?.length) || 0;
  const plain = pwdPlain.value.join("");
  const revealed = passwordReveal.value && plain.length;
  const text = revealed ? plain : "*".repeat(maskLen);
  const empty = !text;

  if (empty) {
    // 空态绘制占位提示（主题占位色，始终可见）
    ctx.fillStyle = placeholderColor;
    ctx.fillText(input.getAttribute("placeholder") || "", x0, yMid);
    return;
  }

  // 超宽滚动：取已输入前缀宽度，超出 clientWidth 则向左偏移
  const fullW = ctx.measureText(text).width;
  const clientW = ir.width - paddingLeft;
  let offset = 0;
  if (fullW > clientW) offset = clientW - fullW;

  ctx.fillStyle = inputColor;
  ctx.fillText(text, x0 + offset, yMid);

  // 光标（仅在密码框激活时，画在末尾；闪烁由 cursorOn 控制）
  if (activeField.value === "password" && cursorOn.value) {
    const prefix = revealed ? plain : "*".repeat(maskLen);
    const caretW = ctx.measureText(prefix).width;
    const cx = x0 + offset + caretW;
    const h = Math.min(18, ir.height - 6);
    const primary = readVar("--theme-primary", "#07c160");
    ctx.fillStyle = primary;
    ctx.fillRect(cx, yMid - h / 2, 2, h);
  }
}

// 窗口尺寸变化时重绘，避免 canvas 错位
window.addEventListener("resize", () => drawPwdCanvas());


// ══════════════════════════════════════════════════════════════
// 登录校验（安全整改 P1-13，2026-10-08）
//
// ⚠ 所有规则**必须与后端 `api/src/modules/auth/rules/index.js` 逐字一致**，
//   否则用户会遇到「前端过了、后端 400」的"点了没反应"。
//   后端口径：
//     nameOrEmail : min(3).max(50).required()      —— **无格式正则**
//     password 明文: 必须含大写+小写+数字+特殊字符，长度 6-30
//     password 密文: RSA 密文字符数组，长度 6-30（每项 8-1024 字符）
//
// ⚠ 关键：密码**不能**用 AppField 的 rules 校验 —— 该字段的 modelValue 是
//   掩码串 `'●'.repeat(len)`（安全键盘设计），拿不到真实密码。
//   所以密码校验必须在能拿到 `password` / `pwdEncrypted` 的地方做（即 onSubmit）。
// ══════════════════════════════════════════════════════════════
const RE_USERNAME_MIN = 3;
const RE_USERNAME_MAX = 50;
const RE_PWD_MIN = 6;
const RE_PWD_MAX = 30;
// 与后端 plainPwdRule 完全一致（注意 `[^]{6,30}` 表示「任意字符，长度 6-30」）
const RE_PLAIN_PWD = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*(),.?":{}|<>])[^]{6,30}$/;
const RE_SPECIAL = /[!@#$%^&*(),.?":{}|<>]/;

/** 是否处于「安全键盘 RSA 密文」模式 */
const isEncryptedMode = computed(() => (pwdEncrypted.value?.length || 0) > 0);

/** 账号校验（对齐后端 nameOrEmail 规则） */
const validateUsername = (raw) => {
  const v = String(raw ?? '').trim();
  if (!v) return "请输入账号或邮箱";
  if (v.length < RE_USERNAME_MIN) return `账号至少 ${RE_USERNAME_MIN} 个字符`;
  if (v.length > RE_USERNAME_MAX) return `账号不能超过 ${RE_USERNAME_MAX} 个字符`;
  return '';
};

/** 密码校验（按模式分流，对齐后端 alternatives） */
const validatePassword = () => {
  if (isEncryptedMode.value) {
    const len = pwdEncrypted.value.length;
    if (len < RE_PWD_MIN) return `密码至少 ${RE_PWD_MIN} 位`;
    if (len > RE_PWD_MAX) return `密码不能超过 ${RE_PWD_MAX} 位`;
    return '';
  }
  const pwd = String(password.value ?? '');
  if (!pwd) return '请输入密码';
  if (pwd.length < RE_PWD_MIN) return `密码至少 ${RE_PWD_MIN} 位`;
  if (pwd.length > RE_PWD_MAX) return `密码不能超过 ${RE_PWD_MAX} 位`;
  if (!RE_SPECIAL.test(pwd)) return '密码需包含特殊字符（如 ! @ # $ % ^ & *）';
  if (!/[a-z]/.test(pwd)) return '密码需包含小写字母';
  if (!/[A-Z]/.test(pwd)) return '密码需包含大写字母';
  if (!/\d/.test(pwd)) return '密码需包含数字';
  return '';
};

/** 提交前的综合校验，返回第一条错误（空串 = 通过） */
const validateAll = () => validateUsername(username.value) || validatePassword();

/** 账号字段规则（交给 AppField.validate；与 validateUsername 同源） */
const usernameRules = [
  { required: true, message: "请输入账号或邮箱" },
  {
    validator: (v) => validateUsername(v) || true,
    message: "账号格式不正确",
  },
];

/** 账号错误提示（未输入时不打扰） */
const usernameError = computed(() =>
  username.value ? validateUsername(username.value) : ''
);

/** 按钮可用性：账号与密码**都**合法才可点（原先只看密码长度，用户名为空也能提交） */
const canSubmit = computed(() => validateAll() === '');

/** 密码错误提示（供字段下方展示） */
const passwordError = computed(() =>
  // 未开始输入时不打扰用户
  password.value || pwdEncrypted.value?.length ? validatePassword() : ''
);

// 保留旧的强度信息（仅用于展示，判定以 canSubmit 为准）
const passwordRulesComputed = computed(() => {
  const len = (pwdEncrypted.value?.length) || (password.value?.length) || 0;
  return {
    hasUpperCase: /[A-Z]/.test(password.value),
    hasLowerCase: /[a-z]/.test(password.value),
    hasNumber: /\d/.test(password.value),
    hasSpecial: RE_SPECIAL.test(password.value),
    validLength: len >= RE_PWD_MIN && len <= RE_PWD_MAX,
  };
});

const onSubmit = async (values) => {
  // 兜底校验：AppForm 只校验「传了 rules 的字段」，密码字段没传 rules（掩码串），
  // 所以这里必须自己再拦一次，避免空密码/弱密码直接打到后端。
  const err = validateAll();
  if (err) {
    showToast(err);
    return;
  }

  loading.value = true;

  try {
    // 调用登录 API
    // secureOnly 输入：提交 RSA 密文字符数组，后端用私钥解密还原明文
    // 兼容兜底：若未走安全键盘（无密文）则回退明文 ref（如演示预填）
    const payloadPwd = pwdEncrypted.value?.length
      ? pwdEncrypted.value
      : password.value;
    const res = await authApi.login({
      nameOrEmail: username.value.trim(),
      password: payloadPwd,
    });
    // 🔒 安全整改 P0-5：登录成功后**不再**把 JWT 写进 localStorage。
    //    服务端已在登录响应里下发 httpOnly Cookie（浏览器自动保存、JS 读不到），
    //    这里只记录一个**不含凭证**的登录标记，供路由守卫快速判定。
    //    ⇒ 任意 XSS 都读不到令牌，窃取会话这条路被切断。
    //
    //    注：登录响应是扁平结构 `{ message, token, status }`，**不含用户信息**，
    //    用户资料由 `router/middleware.js` 的 `restoreSession()` / `getUserInfo()` 补齐。
    markAuthed({ username: username.value });
    setTimeout(() => {
      loading.value = false;
      router.push("/");
    }, 1500);
  } catch (error) {
    loading.value = false;
  }
};

const goToRegister = () => {
  router.push("/register");
};
</script>

<style scoped>
.login-container {
  height: 100vh;
  height: 100dvh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  background-color: var(--theme-bg-secondary);
  display: flex;
  flex-direction: column;
  padding: 0 20px;
}

/* 键盘唤起时：容器高度收缩为「视口 - 键盘高度」，flex 布局自动把输入区顶到键盘上方 */
.login-container.keyboard-open {
  height: calc(100vh - var(--kb-height, 0px));
  height: calc(100dvh - var(--kb-height, 0px));
}
.login-container.keyboard-open .login-header {
  margin-top: 24px;
  margin-bottom: 16px;
}
.login-container.keyboard-open .login-footer {
  display: none;
}

.login-header {
  margin-top: 80px;
  margin-bottom: 40px;
  text-align: center;
}

.logo {
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;

  > img {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }
}

.title {
  font-size: 24px;
  color: var(--theme-text-primary);
  margin-bottom: 8px;
  font-weight: 600;
}

.subtitle {
  font-size: 14px;
  color: var(--theme-text-tertiary);
}

.login-form {
  margin-bottom: 20px;
}

/* 调整 Vant 单元格组间距 */
:deep(.van-cell-group--inset) {
  margin: 0;
  background: transparent;
}

.submit-bar {
  margin-top: 30px;
}

.password-requirements {
  background: var(--theme-bg-primary);
  border-radius: 8px;
  padding: 12px 16px;
  margin-top: 16px;
  font-size: 12px;
}

.password-requirements strong {
  display: block;
  margin-bottom: 8px;
  color: var(--theme-text-primary);
  font-weight: 600;
}

.password-requirements ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.password-requirements li {
  margin: 6px 0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.password-requirements li.valid {
  color: var(--van-green, #07c160);
  font-weight: 500;
}

.password-requirements li.invalid {
  color: var(--theme-text-tertiary);
}

.login-tip {
  text-align: center;
  padding: 20px 10px;
  margin-top: auto;
}

.login-tip p {
  margin: 0;
  font-size: 12px;
  color: var(--theme-text-secondary);
  line-height: 1.8;
}

.login-footer {
  text-align: center;
  padding: 24px 16px 32px;
  font-size: 11px;
  color: var(--theme-text-tertiary);
  line-height: 1.8;
  margin-top: auto;
}

.footer-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-bottom: 6px;
}

.footer-logo {
  width: 18px;
  height: 18px;
  object-fit: contain;
}

.footer-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-text-secondary);
}

.footer-info a,
.footer-links a {
  color: var(--theme-text-tertiary);
  text-decoration: none;
}

.footer-info a:hover,
.footer-links a:hover {
  color: var(--theme-text-secondary);
}

.beian-icon {
  width: 14px;
  height: 14px;
  vertical-align: middle;
  margin-right: 2px;
}

.footer-sep {
  margin: 0 6px;
  color: var(--theme-text-tertiary);
}

.footer-meta {
  color: var(--theme-text-tertiary);
}

.footer-links {
  margin-top: 2px;
}

/* 安全键盘弹层 */
.field-wrap {
  position: relative;
  cursor: pointer;
  border-radius: 10px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}
.field-wrap:not(:last-child) {
  margin-bottom: 12px;
}
.field-wrap :deep(.app-field) {
  background: transparent;
  border: 1px solid var(--theme-border);
  border-radius: 10px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}
.field-wrap:hover :deep(.app-field) {
  border-color: var(--theme-text-tertiary);
}
.field-wrap.active :deep(.app-field) {
  border-color: var(--theme-primary);
  box-shadow: inset 0 0 0 2px var(--theme-primary);
}
.field-wrap.active :deep(.app-field__label) {
  color: var(--theme-primary);
}
.field-wrap.active :deep(.app-field__control) {
  border-color: transparent;
}
.pwd-eye {
  font-size: 18px;
  color: var(--theme-primary);
  padding: 2px;
  border-radius: 50%;
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}
.pwd-eye:hover {
  background: rgba(var(--theme-primary-rgb, 58, 102, 224), 0.12);
}
.field-measure {
  position: absolute;
  visibility: hidden;
  white-space: pre;
  font-size: 14px;
  line-height: 1.5;
  font-family: inherit;
  top: 42px;
}
.field-measure-pwd {
  left: 0;
  font-family:
    -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  letter-spacing: 0;
}
.pwd-reveal-tip {
  margin: 6px 2px 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--van-danger-color, #ee0a24);
}
/* 密码框：DOM input 文字透明（值仅为 ● 掩码，F12 无明文），真实明文由 canvas 绘制 */
.pwd-canvas-field :deep(.app-field__input) {
  /* 整块输入（含原生占位符）完全隐藏，仅由 canvas 像素绘制掩码/明文/占位符 */
  opacity: 0;
  caret-color: transparent;
}
.pwd-canvas {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  pointer-events: none; /* 点击穿透到 input，触发 onFieldClick */
  z-index: 2;
}
.field-cursor {
  display: none;
  position: absolute;
  left: 8px;
  top: 42px;
  width: 2px;
  height: 18px;
  background: var(--theme-primary, #07c160);
  border-radius: 1px;
  animation: cursor-blink 1s step-end infinite;
}
.field-wrap.active .field-cursor {
  display: block;
}
@keyframes cursor-blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

.kb-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2001;
  background: var(--theme-bg-secondary, #fff);
  border-radius: 18px 18px 0 0;
  padding: 5px;
  box-shadow: 0 -6px 24px rgba(0, 0, 0, 0.12);
}

.kb-up-enter-active,
.kb-up-leave-active {
  transition: transform 0.28s cubic-bezier(0.32, 0.72, 0.4, 1);
}
.kb-up-enter-from,
.kb-up-leave-to {
  transform: translateY(100%);
}
</style>
