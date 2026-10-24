package com.live.finance.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.SiteProbe
import com.live.finance.core.store.SiteStore
import com.live.finance.theme.FONT_LEVEL_MAX
import com.live.finance.theme.FONT_LEVEL_MIN
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.theme.MONEY_RED_IN
import com.live.finance.theme.MONEY_RED_OUT
import com.live.finance.theme.ThemePresets
import com.live.finance.theme.VanGrayFixed
import com.live.finance.theme.fontOffsetSp
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppDialogShell
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.WebShadow
import com.live.finance.ui.common.cssShadowCircle
import com.live.finance.ui.flow.FText
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.form.VanSwitch
import com.live.vant.icon.VanIcon
import com.live.vant.theme.HapticStrength
import kotlinx.coroutines.launch

/**
 * 应用设置页 —— 对齐 web `src/views/User/settings/AppSettings.vue` 的「UI 主题」+「收支颜色」两个区块
 * （分类设置 / PWA 安装区块不属本轮主题范围）。
 *
 * ⚠ 原本这里多挂了一个「账号 / 退出当前登录」区块（web 没有，web 的退出登录在「我的」页
 * `User/index.vue` 的 `.logout-wrapper`）—— 2026-09-21 按用户要求**整块删除**。
 *
 * ⚠ 「文字大小」与「按键震动」是**原生特有**区块（web 没有这两项设置，只落本地 DataStore
 * `font_level` / `key_haptics`），排在「UI 主题」与「收支颜色」之间；
 * 生效范围见 [com.live.finance.theme.LocalFontOffsetSp] 与 `com.live.vant.theme.LocalKeyHapticEnabled`。
 */
@Composable
fun AppSettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val graph = App.of(ctx).graph
    val scope = rememberCoroutineScope()

    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val choice = graph.themeChoice.value
    val moneyMode = graph.moneyColorMode.value
    val fontLevel = graph.fontLevel.value
    val hapticsOn = graph.haptics.value
    val clickHapticsOn = graph.clickHaptics.value
    val strength = HapticStrength.resolve(graph.hapticStrength.value)
    // 当前生效预设（「系统默认」项的打勾色取它，对应 web themeActiveColor）
    val activePreset = ThemePresets.resolve(choice, tokens.isDark)

    // ── 基础API设定（分发适配：2026-10-14；弹窗设定，无独立界面）──
    // 修改 API 地址会强制退出登录（旧 token 在新站点必然失效），需二次确认；
    // 分享 URL（Web 端）只影响分享外链生成，免登出。
    val siteStore = remember { SiteStore(ctx) }
    val toast = LocalVanToastController.current
    var showApiDialog by remember { mutableStateOf(false) }
    var apiDraft by remember { mutableStateOf("") }
    var apiDraftError by remember { mutableStateOf<String?>(null) }
    var apiDraftFocused by remember { mutableStateOf(false) }
    var shareDraft by remember { mutableStateOf("") }
    var shareDraftError by remember { mutableStateOf<String?>(null) }
    var shareDraftFocused by remember { mutableStateOf(false) }
    var showApiChangeConfirm by remember { mutableStateOf(false) }
    // API 地址保存前探测中（2026-10-17）：按钮显示「正在连接…」并禁用，防重复提交。
    var apiChecking by remember { mutableStateOf(false) }

    ScreenScaffold { inner ->
        Column(
            inner
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // ===== 分类设置（web 区块顺序：分类设置 → UI 主题 → 收支颜色 → 安装站点应用）=====
            SettingsSectionTitle("分类设置")
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp)) // .app-card { border-radius: 12px }
                    .background(colors.bgCard),
            ) {
                SettingsLinkCell("收支分类", "管理支出/收入的分类", "orders-o") { nav.navigate(Routes.CATEGORY_MANAGE) }
                SettingsLinkCell("银行分类", "管理银行卡所属银行分类", "card") { nav.navigate(Routes.BANK_CATEGORY_MANAGE) }
            }

            // ===== 基础API设定（分发适配：2026-10-14；弹窗设定，无独立界面）=====
            SettingsSectionTitle("基础API设定")
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp)) // .app-card { border-radius: 12px }
                    .background(colors.bgCard),
            ) {
                SettingsLinkCell(
                    "API 地址 / 分享 URL",
                    AppConfig.baseUrl.ifEmpty { "未接入站点" },
                    "setting-o",
                ) {
                    // 打开即回填当前值（本地保存值优先，与启动加载口径一致）
                    apiDraft = siteStore.current() ?: AppConfig.baseUrl
                    shareDraft = siteStore.shareUrl().orEmpty()
                    apiDraftError = null
                    shareDraftError = null
                    showApiDialog = true
                }
            }

            // ===== UI 主题（web .section-title + .theme-section）=====
            SettingsSectionTitle("UI 主题")
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))                 // web .app-card { border-radius: 12px }
                    .background(colors.bgCard)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
            ) {
                ThemeGrid(
                    items = listOf(SYSTEM_THEME_ITEM),
                    choice = choice,
                    activeColor = activePreset.primary,
                    onPick = { scope.launch { graph.setThemeChoice(it) } },
                )
                ThemeGroupTitle("白底配色", first = false)
                ThemeGrid(
                    // web：组内按色系排序（绿→蓝→红→金→中性），同色系保持声明顺序
                    items = ThemePresets.ALL.filter { !it.dark }.sortedBy { it.hue.ordinal }.map { it.toItem() },
                    choice = choice,
                    activeColor = activePreset.primary,
                    onPick = { scope.launch { graph.setThemeChoice(it) } },
                )
                ThemeGroupTitle("黑底配色", first = false)
                ThemeGrid(
                    items = ThemePresets.ALL.filter { it.dark }.sortedBy { it.hue.ordinal }.map { it.toItem() },
                    choice = choice,
                    activeColor = activePreset.primary,
                    onPick = { scope.launch { graph.setThemeChoice(it) } },
                )
            }

            // ===== 文字大小（原生特有区块，web 无；一~五档、每档进退 1sp、默认三档=标准=现状-2sp）=====
            SettingsSectionTitle("文字大小")
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgCard)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (FONT_LEVEL_MIN..FONT_LEVEL_MAX).forEach { level ->
                    FontLevelCell(
                        level = level,
                        selected = level == fontLevel,
                        modifier = Modifier.weight(1f),
                        onPick = { scope.launch { graph.setFontLevel(level) } },
                    )
                }
            }

            // ===== 按键震动（原生特有；web 全仓无任何触感代码）=====
            SettingsSectionTitle("按键震动")
            // 卡 1：键盘按键反馈（第三十轮，落 key_haptics）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    FText("键盘按键反馈", 16f, FontWeight.Normal, colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    FText(
                        "四个键盘（数字 / 安全 / 密码 / 简易）按下按键时给一次短震；" +
                            "跟随系统「触摸反馈」，系统里关掉后这里开着也不震",
                        12f,
                        FontWeight.Normal,
                        colors.textTertiary,
                    )
                }
                VanSwitch(
                    checked = hapticsOn,
                    onCheckedChange = { on -> scope.launch { graph.setHaptics(on) } },
                    size = 24,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            // 卡 2：全局操作点击反馈（第三十三轮，落 click_haptics；根层探针见 vant-ui Haptics.kt）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    FText("全局操作点击反馈", 16f, FontWeight.Normal, colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    FText(
                        "App 内所有点按（按钮 / 列表 / 开关 / Tab 等）抬起时给一次短震；" +
                            "与键盘反馈互不叠加，同一次点击只震一下；独立弹窗暂不覆盖",
                        12f,
                        FontWeight.Normal,
                        colors.textTertiary,
                    )
                }
                VanSwitch(
                    checked = clickHapticsOn,
                    onCheckedChange = { on -> scope.launch { graph.setClickHaptics(on) } },
                    size = 24,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            // 卡 3：震动效果（第三十三轮，落 haptic_strength；键盘与全局点击共用）
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgCard)
                    .padding(12.dp),
            ) {
                FText("震动效果（键盘与点击共用）", 16f, FontWeight.Normal, colors.textPrimary)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HapticStrengthCell(
                        name = "轻",
                        desc = "轻触",
                        selected = strength == HapticStrength.LIGHT,
                        modifier = Modifier.weight(1f),
                        onPick = { scope.launch { graph.setHapticStrength(HapticStrength.LIGHT) } },
                    )
                    HapticStrengthCell(
                        name = "中",
                        desc = "标准",
                        selected = strength == HapticStrength.MEDIUM,
                        modifier = Modifier.weight(1f),
                        onPick = { scope.launch { graph.setHapticStrength(HapticStrength.MEDIUM) } },
                    )
                    HapticStrengthCell(
                        name = "重",
                        desc = "较重",
                        selected = strength == HapticStrength.HEAVY,
                        modifier = Modifier.weight(1f),
                        onPick = { scope.launch { graph.setHapticStrength(HapticStrength.HEAVY) } },
                    )
                }
            }

            // ===== 收支颜色（web .money-color-group）=====
            SettingsSectionTitle("收支颜色")
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 预览：选中的字放大（30px），未选中 16px，显示当前「收/支」实际配色
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    FText(
                        "收",
                        if (moneyMode == MONEY_RED_IN) 30f else 16f,
                        FontWeight.Normal,
                        MoneyColor.income,
                        Modifier.alignByBaseline(),
                    )
                    FText(
                        "支",
                        if (moneyMode == MONEY_RED_OUT) 30f else 16f,
                        FontWeight.Normal,
                        MoneyColor.expense,
                        Modifier.alignByBaseline(),
                    )
                }
                // 用户要求：原来的 `van-button size=small` 太窄小，改成与「退出登录」同一套
                // `AppButton(plain + round)` 空心圆角按钮（尺寸取 normal = 高 44），只是不做 block 满宽
                //（它在 Row 里与左侧「收/支」预览并排）。
                AppButton(
                    text = "切换",
                    type = AppButtonType.Primary,
                    plain = true,
                    round = true,
                    onClick = {
                        scope.launch {
                            graph.setMoneyColorMode(if (moneyMode == MONEY_RED_IN) MONEY_RED_OUT else MONEY_RED_IN)
                        }
                    },
                )
            }

            // ===== 存储（原生特有，2026-10-22）=====
            SettingsSectionTitle("存储")
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgCard),
            ) {
                SettingsLinkCell(
                    "空间清理",
                    "查看并清理图片、网页和动态数据缓存",
                    "delete-o",
                ) { nav.navigate(Routes.CACHE_CLEAN) }
            }

            // ===== 实验室（原生特有，2026-10-19）=====
            // 液态玻璃调参 Demo：14 套预设 + 8 个滑块，负责人挑定后写回 MainScreen 的悬浮岛。
            SettingsSectionTitle("实验室")
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgCard),
            ) {
                SettingsLinkCell(
                    "液态玻璃实验室",
                    "三方案对比（A 参数微调 / B 推荐样式 / C 已保存自定义）",
                    "eye-o",
                ) { nav.navigate(Routes.LIQUID_GLASS_LAB) }
                // 卡方实验室（2026-10-22）：统一卡片模组 / 卡包两种卡片页形态，
                // 保存后正式「我的卡片」按所选形态渲染。
                SettingsLinkCell(
                    "卡方实验室",
                    "卡片模组 / 卡包两种卡片页形态；保存后下次进入「我的卡片」生效",
                    "credit-pay",
                ) { nav.navigate(Routes.CARD_STYLE_LAB) }
            }
        }

        // ── 基础API设定弹窗（无独立界面；API 地址 + 分享 URL）──
        AppDialogShell(
            show = showApiDialog,
            title = "基础API设定",
            confirmText = if (apiChecking) "正在连接…" else "保存",
            confirmEnabled = !apiChecking,
            onOverlay = { if (!apiChecking) showApiDialog = false },
            onCancel = { if (!apiChecking) showApiDialog = false },
            onConfirm = {
                if (apiChecking) return@AppDialogShell
                val apiNorm = SiteStore.normalize(apiDraft)
                val shareInput = shareDraft.trim()
                val shareNorm = if (shareInput.isEmpty()) null else SiteStore.normalize(shareInput)
                if (apiNorm == null) {
                    apiDraftError = "须以 http:// 或 https:// 开头，如 https://money.example.com"
                    return@AppDialogShell
                }
                if (shareInput.isNotEmpty() && shareNorm == null) {
                    shareDraftError = "须以 http:// 或 https:// 开头，如 https://money.example.com"
                    return@AppDialogShell
                }
                apiDraftError = null
                shareDraftError = null
                val apiChanged = apiNorm != AppConfig.baseUrl.trimEnd('/')
                val shareChanged = (shareNorm ?: "") != siteStore.shareUrl().orEmpty()
                when {
                    !apiChanged && !shareChanged -> showApiDialog = false // 无变化，直接关
                    // API 变更（2026-10-17）：**先探测连通性**，通过后才弹「退出登录」二次确认。
                    // 探测失败只在弹窗里留红字，不落盘、不退出登录。
                    apiChanged -> {
                        apiChecking = true
                        scope.launch {
                            try {
                                when (val r = SiteProbe.probe(apiNorm)) {
                                    is ApiResult.Ok -> showApiChangeConfirm = true
                                    is ApiResult.Fail -> apiDraftError = r.message
                                    else -> apiDraftError = "站点连接失败，请稍后重试"
                                }
                            } catch (t: Throwable) {
                                if (t is kotlinx.coroutines.CancellationException) throw t
                                apiDraftError = t.message?.takeIf { it.isNotBlank() } ?: "站点连接失败，请稍后重试"
                            } finally {
                                apiChecking = false
                            }
                        }
                    }
                    else -> {
                        // 仅分享 URL 变更：免登出，直接保存
                        if (shareNorm != null) {
                            siteStore.saveShareUrl(shareNorm)
                            AppConfig.siteUrl = shareNorm
                        } else {
                            siteStore.clearShareUrl()
                            // 🚫 无构建期兜底地址（2026-10-17）：清空即空串，分享入口随之阻断
                            AppConfig.siteUrl = ""
                        }
                        toast.show(if (shareNorm != null) "分享 URL 已更新" else "已清除分享 URL，分享功能将不可用")
                        showApiDialog = false
                    }
                }
            },
            // 键盘弹出时整卡上移，避免输入框被系统键盘遮住
            lift = if (apiDraftFocused || shareDraftFocused) 160.dp else 0.dp,
        ) {
            FText("API 地址", 13f, FontWeight.Normal, colors.textSecondary)
            Spacer(Modifier.height(4.dp))
            FText(
                AppConfig.baseUrl.ifEmpty { "未接入站点" },
                15f,
                FontWeight.Medium,
                colors.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            BasicTextField(
                value = apiDraft,
                onValueChange = { apiDraft = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (apiDraftError != null) colors.danger else colors.border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .onFocusChanged { apiDraftFocused = it.isFocused },
            )
            if (apiDraftError != null) {
                Spacer(Modifier.height(4.dp))
                FText(apiDraftError!!, 12f, FontWeight.Normal, colors.danger)
            }
            Spacer(Modifier.height(4.dp))
            FText(
                "保存前会先测试连接，连不上无法保存；" +
                    "修改 API 地址将保存到本机并自动退出当前登录，需要重新登录。",
                12f,
                FontWeight.Normal,
                colors.textTertiary,
            )

            Spacer(Modifier.height(20.dp))

            // ── 分享 URL（Web 端地址）──
            FText("分享 URL（Web 端地址）", 13f, FontWeight.Normal, colors.textSecondary)
            Spacer(Modifier.height(4.dp))
            FText(
                siteStore.shareUrl() ?: "未设置（分享功能不可用）",
                15f,
                FontWeight.Medium,
                colors.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            BasicTextField(
                value = shareDraft,
                onValueChange = { shareDraft = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (shareDraftError != null) colors.danger else colors.border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .onFocusChanged { shareDraftFocused = it.isFocused },
            )
            if (shareDraftError != null) {
                Spacer(Modifier.height(4.dp))
                FText(shareDraftError!!, 12f, FontWeight.Normal, colors.danger)
            }
            Spacer(Modifier.height(4.dp))
            FText(
                "在 APP 内分享需要填写已部署的 Web 端地址：分享链接是网页链接，落地页只有 Web 端有；" +
                    "未填写则分享功能不可用。留空表示清除。",
                12f,
                FontWeight.Normal,
                colors.textTertiary,
            )
        }

        // ── 修改 API 地址的二次确认（强制退出登录告知；分享 URL 若有变更一并保存）──
        VanConfirmDialog(
            show = showApiChangeConfirm,
            title = "提醒",
            message = "修改 API 地址后将自动退出当前登录，需要重新登录。确定修改？",
            confirmButtonText = "确定修改",
            onConfirm = {
                val apiNorm = SiteStore.normalize(apiDraft) ?: return@VanConfirmDialog
                val shareInput = shareDraft.trim()
                val shareNorm = if (shareInput.isEmpty()) null else SiteStore.normalize(shareInput)
                showApiChangeConfirm = false
                showApiDialog = false
                siteStore.save(apiNorm)
                AppConfig.baseUrl = apiNorm
                if (shareNorm != null) {
                    siteStore.saveShareUrl(shareNorm)
                    AppConfig.siteUrl = shareNorm
                } else {
                    siteStore.clearShareUrl()
                    // 🚫 无构建期兜底地址（2026-10-17）
                    AppConfig.siteUrl = ""
                }
                scope.launch {
                    // 旧 token 在新站点必然失效：本地直接清会话（不调旧站点 logout），回登录页
                    graph.session.clear()
                    nav.navigate(Routes.LOGIN) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                }
            },
            onCancel = { showApiChangeConfirm = false },
            onClose = { showApiChangeConfirm = false },
        )
    }
}

// ───────────────────────────── 文字大小区块 ─────────────────────────────

/** 五档的中文序数（一…五），下标 = 档位 - 1。 */
private val FONT_LEVEL_NAMES = listOf("一", "二", "三", "四", "五")

/** 该档的「正文」实际字号 = §7.9 正文档 16sp + 档位偏移（一档 12 … 五档 16）。 */
private const val BODY_SP = 16f

/**
 * 档位格：**序号直接按该档的实际字号渲染**（一…五 五个格子的大小差就是字号差），
 * 下面标出该档的正文 sp 数值 —— 他 2026-09-20 要求「把字体大小直接映射在这上面，一眼知道多大」。
 * 选中态与 [ThemeCell] 同一套（primary 描边 + primaryLight 底）。
 *
 * ⚠ 序号这里**必须**用 `BasicText` 走绝对 sp，不能走 [FText]：`FText` 会再叠一次「当前档位」的偏移，
 * 那样五格就全都被当前档放大、彼此差值看不出来（预览要显示的是**那一档**的大小，不是当前档）。
 * 下面的 sp 数值行仍走 [FText]，跟着当前档一起缩放。
 */
@Composable
private fun FontLevelCell(level: Int, selected: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val shape = RoundedCornerShape(10.dp)
    val bodySp = BODY_SP + fontOffsetSp(level)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) tokens.primaryLight else colors.bgThird)
            .border(1.dp, if (selected) tokens.primary else colors.border, shape)
            .clickable(onClick = onPick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            FONT_LEVEL_NAMES[level - 1],
            style = TextStyle(
                color = if (selected) colors.textPrimary else colors.textSecondary,
                fontSize = bodySp.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
            ),
        )
        FText(
            "${bodySp.toInt()}sp",
            12f,
            FontWeight.Normal,
            colors.textTertiary,
            Modifier.padding(top = 3.dp),
        )
    }
}

// ───────────────────────────── 震动区块 ─────────────────────────────

/**
 * 「震动效果」三档格（轻/中/重），样式与 [FontLevelCell] 同一套（primary 描边 + primaryLight 底）。
 * 左上角档名、右下角档义 —— 不用 BasicText 绝对字号（这里三格不做字号预览，随当前档缩放即可）。
 */
@Composable
private fun HapticStrengthCell(name: String, desc: String, selected: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) tokens.primaryLight else colors.bgThird)
            .border(1.dp, if (selected) tokens.primary else colors.border, shape)
            .clickable(onClick = onPick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FText(
            name,
            18f,
            if (selected) FontWeight.Medium else FontWeight.Normal,
            if (selected) colors.textPrimary else colors.textSecondary,
        )
        FText(
            desc,
            12f,
            FontWeight.Normal,
            colors.textTertiary,
            Modifier.padding(top = 3.dp),
        )
    }
}

// ───────────────────────────── 主题区块 ─────────────────────────────

/** 设置页用的主题项（key + 名称 + 色块渐变两端）。 */
private data class ThemeItem(val key: String, val name: String, val start: Color, val end: Color)

/** web `systemThemePreview = 'linear-gradient(135deg, #3a66e0, #C9A86A)'`（浅靛 → 深黑金）。 */
private val SYSTEM_THEME_ITEM = ThemeItem(
    key = ThemePresets.CHOICE_SYSTEM,
    name = "系统默认",
    start = Color(0xFF3A66E0),
    end = Color(0xFFC9A86A),
)

private fun com.live.finance.theme.ThemePreset.toItem() = ThemeItem(key, name, previewStart, previewEnd)

/** web `.section-title`：padding 20/20/10、13px、text-tertiary、weight 500。 */
@Composable
private fun SettingsSectionTitle(text: String) {
    FText(
        text,
        14f,
        FontWeight.Medium,
        LocalAppColors.current.textTertiary,
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
    )
}

/** `app-cell`（12/16、14px 标题、label 12 tertiary、图标 18 主色、通栏底线、箭头 #969799）。 */
@Composable
private fun SettingsLinkCell(title: String, label: String, icon: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        VanIcon(
            name = icon,
            size = 18.sp,
            color = colors.primary,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(Modifier.weight(1f)) {
            FText(title, 16f, FontWeight.Normal, colors.textPrimary)
            Spacer(Modifier.height(4.dp)) // .app-cell__label{ margin-top: 4px }
            FText(label, 12f, FontWeight.Normal, colors.textTertiary)
        }
        VanIcon(name = "arrow", size = 16.sp, color = VanGrayFixed)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}

/** web `.theme-group-title`：12px、text-tertiary、margin 14/0/8（首个不加上边距）。 */
@Composable
private fun ThemeGroupTitle(text: String, first: Boolean = false) {
    FText(
        text,
        12f,
        FontWeight.Normal,
        LocalAppColors.current.textTertiary,
        Modifier.padding(top = if (first) 0.dp else 14.dp, bottom = 8.dp),
    )
}

/** web `.theme-grid`：`repeat(4, minmax(0,1fr))` + gap 10px，末行不足 4 个左对齐补位。 */
@Composable
private fun ThemeGrid(
    items: List<ThemeItem>,
    choice: String,
    activeColor: Color,
    onPick: (String) -> Unit,
) {
    items.chunked(4).forEach { rowItems ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rowItems.forEach { item ->
                ThemeCell(
                    item = item,
                    selected = item.key == choice,
                    activeColor = activeColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onPick(item.key) },
                )
            }
            repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** web `.theme-item`：右上角打勾、圆形色块 30px、名称 11px；选中态 border=主色 + bg=primary-light。 */
@Composable
private fun ThemeCell(
    item: ThemeItem,
    selected: Boolean,
    activeColor: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val shape = RoundedCornerShape(10.dp)

    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (selected) tokens.primaryLight else colors.bgThird)
                .border(1.dp, if (selected) tokens.primary else colors.border, shape)
                .clickable(onClick = onClick)
                .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .cssShadowCircle(WebShadow(0f, 2f, 6f, Color.Black.copy(alpha = 0.12f)))
                    .background(Brush.linearGradient(listOf(item.start, item.end)), CircleShape),
            )
            Spacer(Modifier.height(6.dp))
            FText(
                item.name,
                12f,
                if (selected) FontWeight.Medium else FontWeight.Normal,
                if (selected) colors.textPrimary else colors.textSecondary,
            )
        }
        if (selected) {
            VanIcon(
                name = "success",
                size = 14.sp,
                color = activeColor,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp),
            )
        }
    }
}
