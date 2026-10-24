package com.live.finance.ui.auth

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.BuildConfig
import com.live.finance.R
import com.live.finance.core.security.RsaUtil
import com.live.finance.core.AppConfig
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.SiteProbe
import com.live.finance.core.net.failMessage
import com.live.finance.core.store.SiteStore
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.scaledSp
import com.live.finance.theme.vanGreen
import com.live.finance.ui.common.AppDialogShell
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.form.VanSecureKeyboard
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.live.finance.core.net.showFail
import com.live.vant.feedback.VanToastType

/**
 * 登录页（1:1 复刻 web/src/views/Auth/Login.vue）。
 * 组件位置/输入逻辑/安全键盘对齐：
 *  - 顶部 logo + 「Gold 财管」+ 副标题；账号/密码 **readonly 字段** + 点击唤起安全键盘（非系统键盘）。
 *  - 密码框以 ● 掩码显示，眼睛按钮切换明文（明文时给安全提示），激活字段显示闪烁光标。
 *  - 校验：仅密码长度 6–30 时「立即登录」可用（对齐 web passwordRulesComputed.validLength）。
 *  - 底部：演示模式显示提示，否则显示品牌 footer。
 *  - 安全键盘见 [VanSecureKeyboard]（复刻 FullKeyboard）。
 *  - **站点（BYO-backend，原生独有）**：表单里**没有**站点行，入口收敛到页脚
 *    品牌名右侧的「切换站点」（本地没存过 ⇒「接入站点」）→ 点它弹「站点设置」填址。
 *    没接入就点「立即登录」⇒ 弹【请点击下方设置 接入站点】。
 *
 * ## 2026-10-17 强化（负责人定稿）
 *  1. **构建期无默认地址**：`AppConfig.baseUrl` 初始为空 ⇒ 首启自动弹出「站点设置」。
 *  2. **保存前必须连通**：[SiteProbe] 打 `GET {base}/api/health`，失败就红字留在弹窗、
 *     **不落盘**（没有「仍然保存」的后路）；成功才写 SiteStore。
 *  3. **未接入站点不能登录**：按钮下方常驻提示 + 点击登录时弹【未接入站点】。
 *  4. **不再无限转圈**：登录请求包在 `try/finally` 里，任何异常都会把 loading 归位；
 *     网络类失败弹「无法连接站点」弹窗（附「检查站点」入口），而不是只飘一个 toast。
 *  5. **站点输入改版（2026-10-18）**：站点设置弹窗弃用「只读展示 + 安全键盘」（模拟 web
 *     canvas 绘制的那套），改为普通输入框——系统键盘实时点击编辑；前置 `http://` /
 *     `https://` 下拉前缀选择，用户只需填域名（整段粘贴带前缀的地址也兼容）。
 *
 * 字符上限与后端统一（nameOrEmail ≤50，password 6–30）。
 */
@Composable
fun LoginScreen(nav: NavHostController, mainTab: MutableIntState) {
    val colors = LocalAppColors.current
    val toast = LocalVanToastController.current
    val graph = App.of(LocalContext.current).graph
    val auth = graph.auth
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf(if (AppConfig.useFake) "demo@example.com" else "") }
    var pwd by remember { mutableStateOf(if (AppConfig.useFake) "123456" else "") }
    // 站点地址（BYO-backend）——**入口收敛到页脚**（负责人 2026-10-14 定稿）：
    //   登录表单里不放站点行，账号/密码/登录键保持最简一屏。
    //   唯一入口是页脚品牌名右侧的「切换站点」（本地没存过 ⇒ 文案变「接入站点」）。
    //   2026-10-17：构建期已无默认地址 ⇒ 首启自动弹「站点设置」；保存必须探测通过。
    val siteCtx = LocalContext.current
    val siteStore = remember(siteCtx) { SiteStore(siteCtx) }
    val savedSite = remember(siteCtx) { siteStore.current() }
    // 站点草稿（2026-10-18 改版）：`sitePrefix` = 下拉选中的前缀（http:// / https://），
    // `site` = 输入框里的域名部分（不含前缀）。保存时二者拼回完整 URL 再归一化。
    val initSite = splitSite(savedSite)
    var sitePrefix by remember { mutableStateOf(initSite.first) }
    var site by remember { mutableStateOf(initSite.second) }
    var siteError by remember { mutableStateOf<String?>(null) }
    // 站点输入框是否持有焦点（弹窗 lift 上移避让系统键盘，同 AppSettingsScreen 口径）
    var siteFieldFocused by remember { mutableStateOf(false) }

    /** 打开弹窗 / 还原草稿：把已存 URL 拆回「前缀 + 域名」两部分（没存过 ⇒ https:// + 空）。 */
    fun loadSiteDraft() {
        val (p, h) = splitSite(siteStore.current())
        sitePrefix = p
        site = h
        siteError = null
    }

    // 站点是否已接入：本地存过 URL，或本次在弹窗里探测并保存成功。
    // 同时决定页脚入口文案 —— false = 还没接入过 ⇒ 「接入站点」。
    var siteConfirmed by remember { mutableStateOf(savedSite != null) }
    // 站点设置弹窗（填址 / 改址的唯一入口）
    var showSiteDialog by remember { mutableStateOf(false) }
    // 「未接入站点」提醒弹窗（账密填完但没站点时点登录触发）
    var showSiteHint by remember { mutableStateOf(false) }
    // 保存前探测中（按钮显示「正在连接…」并禁用，防重复提交）
    var siteChecking by remember { mutableStateOf(false) }
    // 登录时「连不上站点」弹窗文案（网络异常 / 站点未启动；不再只飘 toast）
    var netError by remember { mutableStateOf<String?>(null) }
    // 安全键盘密码密文数组（RSA 逐字符加密；非空时提交密文数组，对齐 web secureOnly 协议）
    var pwdCipher by remember { mutableStateOf<List<String>?>(null) }
    // 密码框激活时异步取到的 RSA 公钥（null = 降级为明文，对应 web 公钥未就绪）
    var pwdRsa by remember { mutableStateOf<String?>(null) }
    var reveal by remember { mutableStateOf(false) }
    var activeField by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    // 首启（本地没有站点记录）：直接弹设置窗，别让用户先填账密再被拦。
    LaunchedEffect(Unit) {
        if (!siteConfirmed) {
            loadSiteDraft()
            showSiteDialog = true
        }
    }

    // 仅密码框且有公钥时启用逐字符加密；isSecure 由键盘内部控制，与加密同门控
    val encryptChar: ((String) -> String)? =
        if (activeField == "password" && pwdRsa != null) ({ ch -> RsaUtil.encryptChar(ch, pwdRsa!!) }) else null

    // 仅密码长度 6–30 才允许提交（web 行为；账号空由后端判）
    val pwdValid = pwd.length in 6..30

    /** 只校验不落盘（弹窗「保存」的公共前置）。返回归一化地址，非法返回 null。 */
    fun validateSite(raw: String): String? {
        val host = raw.trim()
        if (host.isEmpty()) {
            siteError = "请输入站点地址"
            return null
        }
        // 前缀由下拉提供；用户整段粘贴带前缀的地址时直接识别，不重复拼接
        val full = if (host.startsWith("http://") || host.startsWith("https://")) host else sitePrefix + host
        val normalized = SiteStore.normalize(full)
        if (normalized == null) {
            siteError = "站点地址不完整，请检查域名（如 money.example.com）"
            return null
        }
        siteError = null
        return normalized
    }

    /**
     * 落盘已探测通过的站点。
     * 与当前生效站点不同时清掉旧 token —— A 站 token 在 B 站必然失效。
     * ⚠ 即使地址与当前 AppConfig.baseUrl 相同也要写库：首填场景下 baseUrl 本来就是空，
     *   旧实现只在「不同」时 save，「恰好等于构建期默认值」就不落盘（历史 bug）。
     */
    fun commitSite(normalized: String) {
        val changed = normalized != AppConfig.baseUrl.trimEnd('/')
        siteStore.save(normalized)
        AppConfig.baseUrl = normalized
        siteConfirmed = true
        val (p, h) = splitSite(normalized)
        sitePrefix = p
        site = h
        siteError = null
        if (changed) scope.launch { graph.session.clear() }
    }

    // web `.login-container`（100dvh / flex 列 / overflow-y:auto / padding 0 20px）
    // 配 `.login-footer{margin-top:auto}` —— 脚标是**贴屏幕底部**的，不是紧跟在「立即登录」下面。
    // Compose 等价：外层 Box；表单列可滚动并贴顶，脚标 `align(BottomCenter)`，
    // 同时按脚标的实测高度给滚动列留出底部内边距（内容超一屏时两者不重叠）。
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bgPage),
    ) {
        // web `:class="{ 'keyboard-open': activeField }"`：键盘唤起时 header 间距收紧、脚标 display:none
        val kbUp = activeField != null
        var footerH by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current
        val footerShown = !AppConfig.useFake && !kbUp
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = if (footerShown) footerH else 0.dp),
        ) {
            // `.login-header`：正常 80 / 40；键盘唤起时 24 / 16
            Spacer(Modifier.height(if (kbUp) 24.dp else 80.dp))

            // 头部：logo + 标题 + 副标题（居中）
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandLogo()
                Spacer(Modifier.height(20.dp))
                BasicText(
                    "Gold 财管",
                    style = TextStyle(color = colors.textPrimary, fontSize = scaledSp(24f), fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    "开启您的数字资产管理",
                    style = TextStyle(color = colors.textTertiary, fontSize = scaledSp(16f)),
                )
            }

            Spacer(Modifier.height(if (kbUp) 16.dp else 40.dp))

            // ── 账号 / 密码 / 立即登录：一屏最简，**不含任何站点行** ──
            // （站点入口已收敛到页脚「切换站点 / 接入站点」，2026-10-14 负责人定稿）
            // 账号字段（web AppField 无 left-icon：Login.vue 的 `left-icon="user-o"` 是死属性不渲染）
            LoginField(
                label = "账号",
                value = username,
                placeholder = "请输入账号或邮箱",
                active = activeField == "username",
                onClick = { activeField = "username" },
            )
            Spacer(Modifier.height(12.dp))

            // 密码字段（掩码 / 眼睛切换）
            // 掩码字形取 web 真正**画出来**的那个字符：Login.vue 的 DOM input 值虽是 `'●'.repeat(n)`，
            // 但其文字 `opacity:0` 不可见，可见内容由 canvas 以 14px 绘制 `'*'.repeat(maskLen)` —— 故原生用 `*`。
            LoginField(
                label = "密码",
                value = if (reveal) pwd else "*".repeat(pwd.length),
                placeholder = "请输入密码",
                active = activeField == "password",
                rightIcon = if (reveal) "eye-o" else "closed-eye",
                onRightClick = { reveal = !reveal },
                onClick = {
                    activeField = "password"
                    // 激活密码框即异步取 RSA 公钥（握手缓存，失败则降级明文，对应 web 公钥未就绪）
                    scope.launch { pwdRsa = graph.handshake.rsa() }
                },
            )
            if (reveal) {
                Spacer(Modifier.height(6.dp))
                BasicText(
                    "为了您的安全，请勿在公共场合下显示密码",
                    style = TextStyle(color = colors.danger, fontSize = scaledSp(12f), lineHeight = scaledSp(16f)),
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }

            Spacer(Modifier.height(30.dp))

            // 未接入站点的常驻说明（2026-10-17）：不把按钮置灰（灰按钮不解释原因），
            // 但要让用户一眼看到「为什么现在还不能登录」。
            if (!siteConfirmed) {
                BasicText(
                    "尚未接入站点，请先点击下方「接入站点」",
                    style = TextStyle(color = colors.danger, fontSize = scaledSp(12f), lineHeight = scaledSp(16f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                )
            }

            VanButton(
                text = if (loading) "正在安全登录..." else "立即登录",
                onClick = {
                    // 站点先行：没接入 ⇒ 不发起登录，弹提醒指向页脚入口（哪怕账密都填了）。
                    if (!siteConfirmed) {
                        showSiteHint = true
                        return@VanButton
                    }
                    scope.launch {
                        loading = true
                        try {
                            // ⚠ 不要直接用键盘逐字符累积的 pwdCipher：
                            //   中途切换「安全/普通」模式、或 RSA 公钥在输入后才就绪时，
                            //   密文数组会与明文不同步（少字符）⇒ 后端解出的密码非空但错误
                            //   ⇒ 登录返回 404「账号或密码错误」。
                            //   提交时用当前公钥把完整明文重新逐字符加密一次；取不到公钥则回退明文
                            //   （整包仍有 AES-256-CBC 保护，后端 resolvePassword 对字符串原样放行）。
                            val rsaKey = if (pwd.isNotEmpty()) graph.handshake.rsa() else null
                            val passwordPayload: Any =
                                if (rsaKey != null && pwd.isNotEmpty()) {
                                    pwd.map { RsaUtil.encryptChar(it.toString(), rsaKey) }
                                } else {
                                    pwd
                                }
                            when (val r = auth.login(username, passwordPayload)) {
                                is ApiResult.Ok -> {
                                    // 登录成功一律落首页（web `router.push("/")`→/home）。mainTab 是
                                    // AppNavHost 层的 rememberSaveable，退出前停的 Tab（如「我的」）会
                                    // 一直留着，不归零的话重新登录会落回退出前的 Tab。
                                    mainTab.intValue = 0
                                    nav.navigate(Routes.MAIN) {
                                        popUpTo(Routes.LOGIN) { inclusive = true }
                                    }
                                }
                                is ApiResult.Fail -> toast.show(r.message, VanToastType.Fail)
                                ApiResult.Unauthorized -> toast.show("账号或密码错误", VanToastType.Fail)
                                // 连不上站点（DNS/超时/端口错/服务没起）⇒ 弹窗说明 + 指路站点设置，
                                // 不再只飘一个 toast，更不再无限转圈。
                                is ApiResult.Network -> netError = r.message
                                else -> toast.showFail(r, "登录失败")
                            }
                        } catch (t: Throwable) {
                            // 🛡 兜底（2026-10-17）：ApiClient 已在入口拦掉非法 URL，但仓储层
                            //   仍可能有未预料的异常（JSON/State 等）。必须提示 + 归位 loading，
                            //   否则按钮永远停在「正在安全登录...」。
                            if (t is kotlinx.coroutines.CancellationException) throw t
                            netError = t.message?.takeIf { it.isNotBlank() } ?: "登录失败，请检查站点设置"
                        } finally {
                            loading = false
                        }
                    }
                },
                type = VanButtonType.Primary,
                block = true,
                loading = loading,
                disabled = !pwdValid,
            )

            // `.login-form{ margin-bottom: 20px }`
            Spacer(Modifier.height(20.dp))

            // 演示提示（web `v-if="showDemoInfo"`）：普通流元素、跟在表单后面，**不贴底**；
            // 它的容器 padding 是内联样式 `padding:20px`。
            if (AppConfig.useFake) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(
                        "本站点仅作演示效果，推荐使用手机Chrome浏览器打开预览。锁定PIN码为 123456",
                        style = TextStyle(color = colors.textTertiary, fontSize = scaledSp(12f), lineHeight = scaledSp(16f), textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // `.login-footer{ margin-top:auto }` → 贴屏幕底部；`.keyboard-open .login-footer{ display:none }`
        if (footerShown) {
            LoginFooter(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { footerH = with(density) { it.height.toDp() } },
                // 站点入口（2026-10-14）：没接入过 ⇒ 文案「接入站点」，接入过 ⇒ 「切换站点」
                siteActionText = if (siteConfirmed) "切换站点" else "接入站点",
                onSiteAction = {
                    // 打开弹窗前回填已存站点（丢弃上次没保存的编辑）；首次接入时留空
                    loadSiteDraft()
                    showSiteDialog = true
                },
            )
        }
    }

    // 安全键盘：激活字段（账号/密码）时弹出；login 键关闭键盘（真正提交由上方按钮）。
    // 站点字段（2026-10-18）已改为普通输入框（系统键盘），不再走安全键盘。
    VanSecureKeyboard(
        show = activeField != null,
        onChar = { ch ->
            when (activeField) {
                "username" -> username = (username + ch).take(50)
                "password" -> pwd = (pwd + ch).take(30)
            }
        },
        onDelete = {
            when (activeField) {
                "username" -> username = username.dropLast(1)
                "password" -> pwd = pwd.dropLast(1)
            }
        },
        encryptChar = encryptChar,
        onCipherAppend = { if (activeField == "password") pwdCipher = (pwdCipher ?: emptyList()) + it },
        onCipherDelete = { if (activeField == "password") pwdCipher = pwdCipher?.dropLast(1) },
        onLogin = { activeField = null },
    )

    // 站点设置弹窗（页脚「切换站点 / 接入站点」的唯一落地）—— 完整地址只在这里出现。
    // 取消 / 点遮罩：还原已存站点（丢弃弹窗里的临时编辑）。
    // 「保存」：先发探测请求（GET {base}/api/health）——**连不上就不落盘、不关窗**，
    //   失败原因以红字留在弹窗里（2026-10-17 定稿：没有「仍然保存」的后路）。
    if (showSiteDialog) {
        val closeSiteDialog: () -> Unit = {
            if (!siteChecking) {
                showSiteDialog = false
                activeField = null
                siteFieldFocused = false
                loadSiteDraft()
            }
        }
        // 草稿拼回完整 URL（用户整段粘贴带前缀时不再重复拼接），驱动绿✓与落盘校验
        val siteDraft = site.trim()
        val siteFull =
            if (siteDraft.startsWith("http://") || siteDraft.startsWith("https://")) siteDraft
            else sitePrefix + siteDraft
        AppDialogShell(
            show = true,
            title = "站点设置",
            confirmText = if (siteChecking) "正在连接…" else "保存",
            confirmEnabled = !siteChecking,
            onOverlay = closeSiteDialog,
            onCancel = closeSiteDialog,
            // 系统键盘弹出时整卡上移，避免输入框被遮（同 AppSettingsScreen 的 160dp 口径）
            lift = if (siteFieldFocused) 160.dp else 0.dp,
            onConfirm = {
                if (siteChecking) return@AppDialogShell
                // 先做格式校验（不发请求），再做连通性探测
                val normalized = validateSite(site) ?: return@AppDialogShell
                siteFieldFocused = false
                siteChecking = true
                scope.launch {
                    try {
                        when (val r = SiteProbe.probe(normalized)) {
                            is ApiResult.Ok -> {
                                commitSite(normalized)
                                showSiteDialog = false
                                toast.show("站点连接成功", VanToastType.Success)
                            }
                            is ApiResult.Fail -> siteError = r.message
                            else -> siteError = "站点连接失败，请稍后重试"
                        }
                    } catch (t: Throwable) {
                        if (t is kotlinx.coroutines.CancellationException) throw t
                        siteError = t.message?.takeIf { it.isNotBlank() } ?: "站点连接失败，请稍后重试"
                    } finally {
                        siteChecking = false
                    }
                }
            },
        ) {
            SiteField(
                prefix = sitePrefix,
                value = site,
                valid = SiteStore.normalize(siteFull) != null,
                error = siteError,
                checking = siteChecking,
                onPrefixChange = { sitePrefix = it; siteError = null },
                onValueChange = { site = it; siteError = null },
                onFocusChange = { siteFieldFocused = it },
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                "选择 http/https 前缀后输入站点域名（如 money.example.com）。" +
                    "保存前会先测试连接，连不上无法保存。",
                style = TextStyle(color = colors.textTertiary, fontSize = scaledSp(12f), lineHeight = scaledSp(16f)),
            )
        }
    }

    // 「未接入站点」提醒（负责人 2026-10-14 定稿文案）：账密填完但站点没接入时点登录触发。
    // 「去设置」直接打开站点弹窗 —— 少一次「哦原来在下面」的寻找。
    if (showSiteHint) {
        AppDialogShell(
            show = true,
            title = "未接入站点",
            confirmText = "去设置",
            onOverlay = { showSiteHint = false },
            onCancel = { showSiteHint = false },
            onConfirm = {
                showSiteHint = false
                loadSiteDraft()
                showSiteDialog = true
            },
        ) {
            BasicText(
                "请点击下方设置 接入站点",
                style = TextStyle(color = colors.textSecondary, fontSize = scaledSp(14f), lineHeight = scaledSp(20f)),
            )
        }
    }

    // 「连不上站点」弹窗（2026-10-17）：登录请求走到网络层失败时弹出，说明具体原因
    // （DNS/超时/端口/服务未启动），并给一个直接去改站点的入口。
    // ⚠ 这是「不再无限转圈」的配套：以前异常逃逸连提示都没有，现在至少能自解释。
    netError?.let { msg ->
        AppDialogShell(
            show = true,
            title = "无法连接站点",
            confirmText = "检查站点",
            onOverlay = { netError = null },
            onCancel = { netError = null },
            onConfirm = {
                netError = null
                loadSiteDraft()
                showSiteDialog = true
            },
        ) {
            BasicText(
                msg,
                style = TextStyle(color = colors.textSecondary, fontSize = scaledSp(14f), lineHeight = scaledSp(20f)),
            )
        }
    }
}

/** 把完整站点 URL 拆成「前缀 + 域名」；没有前缀（未存过/半截）时前缀回落 https://。 */
private fun splitSite(url: String?): Pair<String, String> {
    val u = (url ?: "").trim()
    return when {
        u.startsWith("https://") -> "https://" to u.removePrefix("https://")
        u.startsWith("http://") -> "http://" to u.removePrefix("http://")
        else -> "https://" to u
    }
}

/**
 * 站点地址输入框（2026-10-18 改版）—— 普通输入框，系统键盘实时点击编辑，
 * 摆进「站点设置」弹窗里（首填/改址同一形态）。
 *
 *  - **前缀下拉**：行首固定展示 `http://` / `https://` 两选一（默认 https://），点击展开
 *    内联面板切换，用户无需再手动输入协议前缀；输入区只填域名。
 *  - **系统键盘输入**：[BasicTextField]（KeyboardType.Uri），实时响应每次点击与编辑；
 *    弹窗经 `lift` 上移避让键盘（见调用处），不再走安全键盘（旧版模拟 web canvas 的只读方案已弃）。
 *  - [valid]（前缀 + 域名能归一化）⇒ 行尾绿✓；弹窗「保存」时才校验落盘。
 *  - [error] 非空（保存失败）⇒ 红框 + 红字；重新输入/切换前缀即清掉。
 *  - [checking] 探测中：输入只读、下拉禁用（配合按钮「正在连接…」防重复提交）。
 *  - 视觉与 [LoginField] 对齐：1px 主题边框 + radius 10 + 13px label + 14px 值 + 主色光标。
 */
@Composable
private fun SiteField(
    prefix: String,
    value: String,
    valid: Boolean,
    error: String?,
    checking: Boolean,
    onPrefixChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(10.dp)
    var menuOpen by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val active = focused || menuOpen
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (error != null) colors.danger else colors.border, shape)
            .then(if (active) Modifier.border(2.dp, if (error != null) colors.danger else colors.primary, shape) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        BasicText(
            "站点",
            style = TextStyle(
                color = if (active) colors.primary else colors.textSecondary,
                fontSize = scaledSp(13f),
            ),
        )
        Spacer(Modifier.height(6.dp))
        // 输入行 + 前缀下拉浮层（Box 叠放：面板悬浮在行下方，不撑高容器）
        Box(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // 前缀下拉选择器（固定宽度，http/https 切换时不跳动）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .width(78.dp)
                        .clickable(enabled = !checking) { menuOpen = !menuOpen }
                        .padding(vertical = 2.dp),
                ) {
                    BasicText(
                        prefix,
                        style = TextStyle(color = colors.textPrimary, fontSize = scaledSp(14f)),
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(2.dp))
                    VanIcon(
                        name = if (menuOpen) "arrow-up" else "arrow-down",
                        size = 12.sp,
                        color = colors.textTertiary,
                    )
                }
                Box(Modifier.width(1.dp).height(16.dp).background(colors.border))
                Spacer(Modifier.width(10.dp))
                // 域名输入区：普通输入框（系统键盘 Uri 布局），激活态由焦点驱动
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    readOnly = checking,
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = scaledSp(14f)),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged {
                            focused = it.isFocused
                            onFocusChange(it.isFocused)
                            if (it.isFocused) menuOpen = false
                        },
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) {
                                BasicText(
                                    "money.example.com",
                                    style = TextStyle(color = colors.textTertiary, fontSize = scaledSp(14f)),
                                    maxLines = 1,
                                )
                            }
                            inner()
                        }
                    },
                )
                // 校验通过 ⇒ 绿✓（保存时才真正落盘）
                if (valid) {
                    Spacer(Modifier.width(8.dp))
                    VanIcon(name = "success", size = 18.sp, color = vanGreen())
                }
            }
            // 前缀下拉面板：锚定在选择器正下方的紧凑浮层（阴影 + 边框），点选项即选中收起
            if (menuOpen) {
                val menuShape = RoundedCornerShape(8.dp)
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(y = 6.dp)
                        .width(132.dp)
                        .shadow(8.dp, menuShape)
                        .clip(menuShape)
                        .background(colors.bgCard)
                        .border(1.dp, colors.border, menuShape),
                ) {
                    listOf("http://", "https://").forEachIndexed { i, opt ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                        val selected = opt == prefix
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !checking) {
                                    onPrefixChange(opt)
                                    menuOpen = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            BasicText(
                                opt,
                                style = TextStyle(
                                    color = if (selected) colors.primary else colors.textPrimary,
                                    fontSize = scaledSp(14f),
                                ),
                            )
                            if (selected) {
                                Spacer(Modifier.weight(1f))
                                VanIcon(name = "success", size = 14.sp, color = vanGreen())
                            }
                        }
                    }
                }
            }
        }
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            BasicText(
                error,
                style = TextStyle(color = colors.danger, fontSize = scaledSp(11f), lineHeight = scaledSp(15f)),
            )
        }
    }
}

/**
 * web `.login-footer`（`v-if=showDemoInfo` 的 `v-else` 分支）：品牌 / Power By / ©。
 * 文案全部取站长配置 —— 原生等价于 web 的 `VITE_BRAND_NAME` 等环境变量，见 `gradle.properties` 的 `SITE_*`。
 * ⚠ 备案号（ICP / 公安）安卓端不显示，web 端页脚仍保留 ⇒ 两端有意偏差。
 *
 * [siteActionText] / [onSiteAction]（分发适配，2026-10-14）：站点入口从表单里撤掉后收敛到
 * 品牌名右侧的「| 切换站点」（没接入过站点时为「| 接入站点」）—— 页脚是登录页唯一不会
 * 被键盘顶掉的位置，且不占表单纵向空间。主色 + 12sp 让它读起来像链接而非脚标装饰。
 */
@Composable
private fun BrandLogo() {
    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = "Gold 财管",
        modifier = Modifier.size(80.dp),
        contentScale = ContentScale.Fit,
    )
}

/**
 * web `.login-footer`（`v-if=showDemoInfo` 的 `v-else` 分支）：品牌 / Power By / ©。
 * 文案全部取站长配置 —— 原生等价于 web 的 `VITE_BRAND_NAME` 等环境变量，见 `gradle.properties` 的 `SITE_*`。
 * ⚠ 备案号（ICP / 公安）安卓端不显示，web 端页脚仍保留 ⇒ 两端有意偏差。
 *
 * [siteActionText] / [onSiteAction]（分发适配，2026-10-14）：站点入口从表单里撤掉后收敛到
 * 品牌名右侧的「| 切换站点」（没接入过站点时为「| 接入站点」）—— 页脚不占表单纵向空间，
 * 也不会被键盘顶掉。主色 + 12sp 让它读起来像链接而非脚标装饰。
 */
@Composable
private fun LoginFooter(
    modifier: Modifier = Modifier,
    siteActionText: String,
    onSiteAction: () -> Unit,
) {
    val colors = LocalAppColors.current
    val tiny = TextStyle(color = colors.textTertiary, fontSize = scaledSp(12f), lineHeight = scaledSp(18f))
    // web `.login-footer`：padding 24/16/32、11px、line-height 1.8、居中
    Column(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // `.footer-brand`：logo 18 + 品牌名 12/600
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = BuildConfig.SITE_BRAND_NAME,
                modifier = Modifier.size(18.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(6.dp))
            BasicText(
                BuildConfig.SITE_BRAND_NAME,
                style = TextStyle(color = colors.textSecondary, fontSize = scaledSp(12f), fontWeight = FontWeight.SemiBold),
            )
            // 站点入口：「品牌名 | 切换站点」—— 分隔符与站内 footer-meta 的 `|` 同一字形口径
            BasicText(
                "|",
                style = tiny,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            BasicText(
                siteActionText,
                style = TextStyle(color = colors.primary, fontSize = scaledSp(12f)),
                modifier = Modifier
                    .clickable { onSiteAction() }
                    .padding(vertical = 2.dp, horizontal = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        // `.footer-meta`：Power By + 分隔符 + © 起始年-当年
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("Power By " + BuildConfig.SITE_POWERED_BY, style = tiny)
            BasicText("|", style = tiny, modifier = Modifier.padding(horizontal = 6.dp))
            BasicText("© " + BuildConfig.SITE_COPYRIGHT_START + "-$currentYear", style = tiny)
        }
    }
}

@Composable
private fun LoginField(
    label: String,
    value: String,
    placeholder: String,
    active: Boolean,
    rightIcon: String? = null,
    onRightClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    // web `.field-wrap :deep(.app-field)`：透明底（透页面底）+ 1px 主题边框 + radius 10；
    // 激活 = border 1px primary + box-shadow inset 2px primary（视觉 3px 环）+ label 变主色。
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (active) colors.primary else colors.border, shape)
            .then(if (active) Modifier.border(2.dp, colors.primary, shape) else Modifier)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // `.app-field__label`：13px textSecondary、mb 6；激活变主色
        BasicText(
            label,
            style = TextStyle(
                color = if (active) colors.primary else colors.textSecondary,
                fontSize = scaledSp(13f),
            ),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // 输入区 + 闪烁光标：光标随输入移动（对齐 web 登录记忆的坑——假光标须定位到
            // 文字末尾 left:curUser，否则「停滞在前」不跟手）。空值激活时落在首位（同 web 空串 0 宽）。
            // 字号 14sp = web 口径（Vant `--van-field-font-size` 与密码 canvas 的 `ctx.font = "14px …"`）；
            // 单行不换行，且超宽时整体左移露出末尾（= web canvas 的 `offset = clientWidth - 文本宽`）。
            val hScroll = rememberScrollState()
            LaunchedEffect(hScroll) { snapshotFlow { hScroll.maxValue }.collect { hScroll.scrollTo(it) } }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).horizontalScroll(hScroll),
            ) {
                val hasText = value.isNotEmpty()
                if (active && !hasText) {
                    BlinkingCursor(colors.primary)
                    Spacer(Modifier.width(2.dp))
                }
                BasicText(
                    if (hasText) value else placeholder,
                    style = TextStyle(
                        color = if (hasText) colors.textPrimary else colors.textTertiary,
                        fontSize = scaledSp(14f),
                    ),
                    maxLines = 1,
                )
                if (active && hasText) {
                    Spacer(Modifier.width(2.dp))
                    BlinkingCursor(colors.primary)
                }
            }

            // `.app-field__right-icon` / `.pwd-eye`：18px 主色
            if (rightIcon != null) {
                Spacer(Modifier.width(8.dp))
                VanIcon(
                    name = rightIcon,
                    size = 18.sp,
                    color = colors.primary,
                    onClick = onRightClick,
                )
            }
        }
    }
}

@Composable
private fun BlinkingCursor(color: Color) {
    val transition = rememberInfiniteTransition("login-cursor")
    val a by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
    )
    Box(
        Modifier
            .width(2.dp)   // web canvas `ctx.fillRect(cx, yMid - h / 2, 2, h)`
            .height(18.dp)
            .alpha(a)
            .background(color),
    )
}
