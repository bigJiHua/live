/**
 * 反调试 / 防篡改防护（生产环境启用）
 *
 * 目的（防御层级有限，纯前端防护有天花板，配合后端风控）：
 * - 禁右键菜单、禁 DevTools 快捷键，增加恶意用户打开控制台的成本
 * - 检测 DevTools 开启后做提示（不能真正关闭，防脚本 OCR/改包）
 * - 防 hook：定时自检关键函数是否被覆盖（console 相关）
 *
 * ⚠️ 说明：这些是"提高攻击成本"的软防护，无法完全阻止有心人。
 * 真正的安全依赖：安全键盘 RSA 字符加密 + AES 整包 + 后端风控（锁定/限速）。
 */

let inited = false;

const isProd = () => {
  try {
    return import.meta.env?.PROD === true || import.meta.env?.MODE === "production";
  } catch {
    return false;
  }
};

function onContextMenu(e) {
  e.preventDefault();
}

function onKeydown(e) {
  // F12 / Ctrl+Shift+I / Cmd+Opt+I / Ctrl+Shift+J / Ctrl+Shift+C
  const k = e.key?.toUpperCase?.() || "";
  if (
    e.key === "F12" ||
    (e.ctrlKey && e.shiftKey && ["I", "J", "C", "K"].includes(k)) ||
    (e.metaKey && e.altKey && ["I", "J", "C"].includes(k))
  ) {
    e.preventDefault();
    e.stopPropagation();
    return false;
  }
}

/**
 * 检测 DevTools 是否开启（基于窗口尺寸差，低开销轮询）
 */
function startDevtoolsDetector(onDetect) {
  let detected = false;
  const threshold = 160;
  const timer = setInterval(() => {
    const w = window.outerWidth - window.innerWidth;
    const h = window.outerHeight - window.innerHeight;
    if (w > threshold || h > threshold) {
      if (!detected) {
        detected = true;
        onDetect();
      }
    } else {
      detected = false;
    }
  }, 1500);
  return timer;
}

/**
 * 防函数被 hook：对关键方法做引子自检（若被替换则提示）。
 * 返回 true 表示被篡改。
 */
function checkHijack() {
  const checks = [
    ["console.log", () => {
      const src = String(console.log);
      return !src.includes("[native code]");
    }],
  ];
  for (const [name, test] of checks) {
    if (test()) {
      console.warn(`[AntiHijack] 检测到 ${name} 可能被篡改`);
    }
  }
  return false;
}

export function initAntiHijack() {
  if (inited) return;
  inited = true;

  // 仅生产启用（dev 下防护会干扰开发调试）
  if (!isProd()) return;

  document.addEventListener("contextmenu", onContextMenu);
  window.addEventListener("keydown", onKeydown, true);

  startDevtoolsDetector(() => {
    // DevTools 开启时提示（不强制跳转，避免误伤普通用户）
    try {
      const el = document.createElement("div");
      el.textContent = "请勿在受保护的页面使用开发者工具";
      Object.assign(el.style, {
        position: "fixed",
        top: "10px",
        left: "50%",
        transform: "translateX(-50%)",
        zIndex: "99999",
        background: "rgba(0,0,0,0.85)",
        color: "#fff",
        padding: "8px 16px",
        borderRadius: "8px",
        fontSize: "13px",
        pointerEvents: "none",
      });
      document.body.appendChild(el);
      setTimeout(() => el.remove(), 2500);
    } catch {
      /* noop */
    }
  });

  // 周期自检防 hook
  setInterval(checkHijack, 10000);
}

export default { initAntiHijack };
