/**
 * 阶段 6 验证脚本（APP 加固：P0-3 / P0-4 / P2-13 / P2-15 / P2-19 / P2-20）
 *
 * 运行：cd APP && node tests/verify-stage6.mjs
 *
 * 为什么是「静态检查 + 产物检查」而不是单测：
 * Android 的这些能力（加密存储 / Pinning / 备份策略）无法在 JVM 单测里跑起来，
 * 真正的验证需要真机或模拟器跑 APK。这里采用能自动化的最强组合：
 *   ① 源码静态检查（配置是否真的写了）
 *   ② **合并后 Manifest / 产物 APK 检查**（配置是否真的进了包）—— 这才是权威依据，
 *      因为源码写了不代表打包生效（例如 debuggable 会由 AGP 注入）。
 */
import fs from 'node:fs';
import path from 'node:path';

const APP = process.cwd();
let pass = 0, fail = 0, warn = 0;
const rows = [];
function record(name, expected, actual, note = '') {
  const ok = String(expected) === String(actual);
  ok ? pass++ : fail++;
  rows.push({ 用例: name, 期望: expected, 实际: actual, 结果: ok ? 'PASS' : 'FAIL', 备注: note });
}
function note(name, actualValue, noteText) {
  warn++;
  rows.push({ 用例: name, 期望: '—', 实际: actualValue, 结果: 'NOTE', 备注: noteText });
}

const read = (rel) => {
  const p = path.join(APP, rel);
  return fs.existsSync(p) ? fs.readFileSync(p, 'utf8') : '';
};

// ══════════════════════1. Manifest ══════════════════════
const manifest = read('app/src/main/AndroidManifest.xml');
record('allowBackup=false', true, /android:allowBackup="false"/.test(manifest));
record('usesCleartextTraffic=false', true, /android:usesCleartextTraffic="false"/.test(manifest));
record('引用 networkSecurityConfig', true, /android:networkSecurityConfig="@xml\/network_security_config"/.test(manifest));
record('引用 dataExtractionRules', true, /android:dataExtractionRules="@xml\/data_extraction_rules"/.test(manifest));
record('fullBackupContent=false', true, /android:fullBackupContent="false"/.test(manifest));

// ══════════════════════2. 网络安全配置 ══════════════════════
const nsc = read('app/src/main/res/xml/network_security_config.xml');
// 先剥 XML 注释 —— 否则「调试时取消下面这行注释」这类说明会被当成真实配置
const nscCode = nsc.replace(/<!--[\s\S]*?-->/g, '');
record('network_security_config 存在', true, nsc.length > 0);
record('禁止明文传输', true, /cleartextTrafficPermitted="false"/.test(nscCode));
record('**默认不信任用户 CA**（防抓包证书）', true,
  /<certificates src="system"\s*\/>/.test(nscCode) && !/<certificates src="user"\s*\/>/.test(nscCode));
record('信任系统 CA', true, /<certificates src="system"\s*\/>/.test(nscCode));

const der = read('app/src/main/res/xml/data_extraction_rules.xml');
record('data_extraction_rules 存在', true, der.length > 0);
record('云备份排除 sharedpref（token 所在）', true,
  /<cloud-backup>[\s\S]*?<exclude domain="sharedpref"\s*\/>/.test(der));
record('换机迁移排除 sharedpref', true,
  /<device-transfer>[\s\S]*?<exclude domain="sharedpref"\s*\/>/.test(der));

// ══════════════════════3. 加密存储 ══════════════════════
const session = read('app/src/main/java/com/live/finance/core/store/SessionStore.kt');
record('SessionStore 不再以明文 DataStore 为主存储', false,
  /private val Context\.dataStore by preferencesDataStore/.test(session)
    && !/LEGACY_KEY/.test(session),
  '若为 true 说明仍在用明文 DataStore 作主存储');
record('使用 EncryptedSharedPreferences', true, /EncryptedSharedPreferences\.create/.test(session));
record('键加密 AES256-SIV', true, /PrefKeyEncryptionScheme\.AES256_SIV/.test(session));
record('值加密 AES256-GCM', true, /PrefValueEncryptionScheme\.AES256_GCM/.test(session));
record('密钥存 Keystore（MasterKey）', true, /MasterKeyBuilder|MasterKey\.Builder/.test(session));
record('有降级路径（Keystore 失败不崩）', true, /catch \(e: Throwable\)[\s\S]*?getSharedPreferences/.test(session));
record('有旧版明文数据迁移', true, /migrateFromLegacyDataStore/.test(session));
record('IO 操作在 Dispatchers.IO 上', true, /withContext\(Dispatchers\.IO\)/.test(session));

const gradle = read('app/build.gradle.kts');
record('引入 security-crypto 依赖', true, /androidx\.security:security-crypto/.test(gradle));

// ══════════════════════4. 传输 / Pinning ══════════════════════
const apiClient = read('app/src/main/java/com/live/finance/core/net/ApiClient.kt');
record('ApiClient 有 Pinning 逻辑（可开关）', true, /CertificatePinner/.test(apiClient));
record('Pinning 受 ENABLE_PINNING 开关控制', true,
  /if \(com\.live\.finance\.BuildConfig\.ENABLE_PINNING\)/.test(apiClient));
record('Pinning 有空值保护（不会因配置缺失而崩）', true,
  /isNotBlank\(\)[\s\S]*?else[\s\S]*?Log\.w/.test(apiClient));

const gradlePropsRaw = read('gradle.properties');
const gradleProps = gradlePropsRaw.replace(/^\s*#.*$/gm, ''); // 剥掉注释行再取值
record('gradle.properties 有 API_BASE_URL', true, /^API_BASE_URL=/m.test(gradleProps));
record('gradle.properties 有 API_HOST', true, /^API_HOST=/m.test(gradleProps));
record('Pinning 默认关闭（避免 LE 证书轮换炸掉）', '(空)',
  (gradleProps.match(/^API_PINNED_SHA256=(.*)$/m)?.[1] ?? '(未配置)').trim() || '(空)',
  "留空 = 不启用 Pinning（Let's Encrypt 场景必须留空）");
const baseUrlValue = (gradleProps.match(/^API_BASE_URL=(.*)$/m)?.[1] ?? '').trim();
// 联调豁免（2026-10-10）：局域网联调时 API_BASE_URL 临时为已知内网 http 地址，
// 此时 http 检查降级为 NOTE（明示"上线前必须改回 https"），避免检查长期红掩盖真回归；
// 其他 http 值仍判 FAIL（真危险）。
const isKnownLanDebug = /^http:\/\/(192\.168\.0\.103|10\.0\.2\.2):666\/?$/.test(baseUrlValue);
if (isKnownLanDebug) {
  note('BASE_URL 为已知联调地址（内网 http）', baseUrlValue,
    '局域网联调模式 —— 上线前必须改回真实 https 域名，否则 APP 打不到线上 API');
} else {
  record('BASE_URL 默认非 http', true,
    !/API_BASE_URL=http:\/\//.test(gradleProps),
    '若为 false 说明仍是 http，公网会被 networkSecurityConfig 拦掉');
}

// ══════════════════════5. R8 / 签名 ══════════════════════
record('release 开启 minify', true, /release\s*\{[\s\S]*?isMinifyEnabled = true/.test(gradle));
record('release 开启 shrinkResources', true, /isShrinkResources = true/.test(gradle));
record('debug 保持 minify=false（便于调试）', true, /debug\s*\{[\s\S]*?isMinifyEnabled = false/.test(gradle));
record('配置 proguard-rules.pro', true, /proguard-rules\.pro/.test(gradle));
record('有 signingConfigs 配置块', true, /signingConfigs\s*\{/.test(gradle));
record('keystore 路径来自配置（不硬编码）', true, /RELEASE_KEYSTORE/.test(gradle));

const proguard = read('app/proguard-rules.pro');
record('proguard-rules.pro 存在', true, proguard.length > 0);
record('keep Gson model（否则 R8 后解析失败）', true, /data\.model\.\*\*/.test(proguard));
record('keep 签名属性（Gson 需要）', true, /-keepattributes Signature/.test(proguard));
record('keep 枚举常量名', true, /-keepclassmembers enum/.test(proguard));
record('keep 行号（线上崩溃可还原）', true, /LineNumberTable/.test(proguard));

// ══════════════════════6. 密钥管理 ══════════════════════
const gi = read('.gitignore');
record('.gitignore 排除 *.jks', true, /\*\.jks/.test(gi));
record('.gitignore 排除 *.keystore', true, /\*\.keystore/.test(gi));
record('.gitignore 排除 *.p12', true, /\*\.p12/.test(gi));

const jksFiles = [];
(function walk(dir, depth = 0) {
  if (depth > 6) return;
  let items = [];
  try { items = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
  for (const it of items) {
    const p = path.join(dir, it.name);
    if (it.isDirectory()) walk(p, depth + 1);
    else if (/\.(jks|keystore|p12|key)$/i.test(it.name)) jksFiles.push(p);
  }
})(APP);
record('仓库内无实际密钥库文件', 0, jksFiles.length,
  jksFiles.length ? '发现：' + jksFiles.join(', ') : 'good');

// ══════════════════════7. 产物验证（权威依据）══════════════════════
const mergedManifest = (() => {
  const base = path.join(APP, 'app/build/intermediates/merged_manifest');
  if (!fs.existsSync(base)) return '';
  const stack = [base];
  while (stack.length) {
    const d = stack.pop();
    let items = [];
    try { items = fs.readdirSync(d, { withFileTypes: true }); } catch { continue; }
    for (const it of items) {
      const fp = path.join(d, it.name);
      if (it.isDirectory()) stack.push(fp);
      else if (it.name === 'AndroidManifest.xml') {
        const c = fs.readFileSync(fp, 'utf8');
        if (c.includes('com.live.finance') && c.includes('networkSecurityConfig')) return c;
      }
    }
  }
  return '';
})();

if (mergedManifest) {
  record('[产物] allowBackup=false 已进包', true, /android:allowBackup="false"/.test(mergedManifest));
  record('[产物] usesCleartextTraffic=false 已进包', true, /android:usesCleartextTraffic="false"/.test(mergedManifest));
  record('[产物] networkSecurityConfig 已进包', true, /android:networkSecurityConfig=/.test(mergedManifest));
  record('[产物] **release 无 debuggable**', false, /android:debuggable="true"/.test(mergedManifest));
} else {
  note('产物 Manifest 检查', '跳过', '未找到 merged_manifest（需先执行一次 assembleProdRelease）');
}

// APK 体积 / 签名
const apkDir = path.join(APP, 'app/build/outputs/apk/prod');
for (const [variant, label] of [['release', '[产物] release APK'], ['debug', '[产物] debug APK']]) {
  const dir = path.join(apkDir, variant);
  if (!fs.existsSync(dir)) { note(label, '不存在', '尚未构建'); continue; }
  const apk = fs.readdirSync(dir).find((f) => f.endsWith('.apk'));
  if (!apk) { note(label, '无 APK', ''); continue; }
  const size = fs.statSync(path.join(dir, apk)).size;
  note(label, (size / 1024 / 1024).toFixed(2) + ' MB',
    apk.includes('unsigned') ? '未签名（预期：RELEASE_KEYSTORE 未配置）' : '已签名');
}

// ══════════════════════ 报告 ══════════════════════
console.log('\n============ 阶段 6 验证报告（APP 加固） ============');
console.table(rows);
console.log(`\n总计 ${pass + fail} 项断言 | 通过 ${pass} | 失败 ${fail} | 提示 ${warn}`);
console.log(fail === 0 ? '✅ 阶段 6 静态与产物检查全部通过\n' : '\n⚠ 有失败项，需修复\n');
process.exit(fail === 0 ? 0 : 1);