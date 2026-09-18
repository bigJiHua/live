package com.live.finance.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppDialogShell
import com.live.finance.ui.common.PinCells
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanNoticeBar
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.form.VanSafeKeyboard
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// web `User/security/PinManage.vue` 里写死的两套色（均为 var 无定义时的 fallback）
private val PIN_GREEN = Color(0xFF07C160)     // var(--van-green, #07c160)
private val PIN_GREEN_BG = Color(0xFFE8F9F0)  // var(--van-green-bg, #e8f9f0)
private val PIN_ORANGE = Color(0xFFFF976A)    // var(--van-orange) = Vant 默认 #ff976a
private val PIN_ARROW = Color(0xFF969799)     // 页面里硬编码的 van-icon color

/**
 * PIN 码管理页 —— 1:1 复刻 web `src/views/User/security/PinManage.vue`。
 *
 * 结构：`.status-card`（盾牌图标 + 状态文案 + 右上角「已启用/已禁用」角标）
 *      → `.section-title` 操作选项 + `van-cell-group inset .app-card`（设置/修改/关闭 PIN）
 *      → `van-notice-bar`（橙色安全提示）
 *      → 「关闭 PIN 码」弹窗（6 格 PIN + 数字安全键盘，newPin 固定传 000000）
 */
@Composable
fun PinManageScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val graph = App.of(ctx).graph
    val scope = rememberCoroutineScope()

    var hasPinSet by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // 关闭 PIN 相关
    var showDisableDialog by remember { mutableStateOf(false) }
    var showDisableKeyboard by remember { mutableStateOf(false) }
    var disablePin by remember { mutableStateOf("") }
    var disableError by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    // checkPinStatus（对齐 web：只认 200=已设置）
    LaunchedEffect(refreshKey) { hasPinSet = graph.security.hasPinSet() }

    // 弹窗打开 300ms 后自动弹键盘；关闭即收起（web watch + setTimeout）
    LaunchedEffect(showDisableDialog) {
        if (showDisableDialog) { delay(300); showDisableKeyboard = true } else showDisableKeyboard = false
    }

    fun closeDialog() {
        showDisableKeyboard = false
        disablePin = ""
        disableError = ""
        showDisableDialog = false
    }

    /** 确认关闭：6 位校验 → changePin(oldPin, "000000")；失败保持打开（web before-close 语义）。 */
    fun confirmDisable() {
        showDisableKeyboard = false
        if (disablePin.length != 6) { disableError = "请输入完整的 PIN 码"; return }
        if (submitting) return
        submitting = true
        scope.launch {
            when (val r = graph.security.changePin(disablePin, "000000")) {
                is ApiResult.Ok -> {
                    submitting = false
                    disablePin = ""; disableError = ""
                    showDisableDialog = false
                    toast.show("PIN 码已关闭")
                    refreshKey++ // 重新拉状态
                }
                is ApiResult.Fail -> { submitting = false; disableError = r.message.ifBlank { "PIN 码错误" }; disablePin = "" }
                is ApiResult.Locked -> { submitting = false; disableError = "PIN 码已锁定，请稍后再试"; disablePin = "" }
                else -> { submitting = false; disableError = "关闭失败，请重试"; disablePin = "" }
            }
        }
    }

    ScreenScaffold { inner ->
        Column(
            modifier = inner.verticalScroll(rememberScrollState()),
        ) {
            // 页面标题（web 在全局 nav-bar 上；原生沿用各页自绘标题的约定）
            FText(
                "PIN 码管理", 18f, FontWeight.Bold, LocalAppColors.current.textPrimary,
                Modifier.padding(16.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp)) { // .manage-content{ padding:16px }
                PinStatusCard(hasPinSet)

                // 操作选项
                FText(
                    "操作选项", 13f, FontWeight.Medium, LocalAppColors.current.textTertiary,
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                )
                PinGroup {
                    if (!hasPinSet) {
                        PinOptCell("设置 PIN 码", "lock") { nav.navigate(Routes.pinSetup("new")) }
                    } else {
                        PinOptCell("修改 PIN 码", "edit") { nav.navigate(Routes.pinSetup("modify")) }
                        PinOptCell("关闭 PIN 码", "cross") { showDisableDialog = true }
                    }
                }
                Spacer(Modifier.height(24.dp)) // .action-section{ margin-bottom:24px }

                // 安全提示
                // web 传 :background="var(--van-orange-bg)" 但该变量未定义 → 背景透明；文字色 var(--van-orange)=#ff976a
                VanNoticeBar(
                    text = "PIN 码用于保护您的敏感操作，请妥善保管，不要告知他人。",
                    leftIcon = "info-o",
                    color = PIN_ORANGE,
                    background = Color.Transparent,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ===== 关闭 PIN 弹窗 =====
    AppDialogShell(
        show = showDisableDialog,
        title = "关闭 PIN 码",
        confirmText = "确认关闭",
        onOverlay = { closeDialog() },
        onCancel = { closeDialog() },
        onConfirm = { confirmDisable() },
        bottomOverlay = if (showDisableKeyboard) {
            {
                // 键盘遮罩：透明（无压暗，对齐 web PinManage.vue 的 .keyboard-overlay 交互诉求），
                // 键盘自身吞掉点击（对应 web @click.stop）；关 PIN 弹窗中点击卡片外区域由
                // AppDialogShell 遮罩收起整个弹窗（含键盘）。
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .clickable { showDisableKeyboard = false },
                ) {
                    Box(Modifier.clickable { /* swallow */ }) {
                        VanSafeKeyboard(
                            onChar = { d ->
                                disableError = ""
                                if (disablePin.length < 6) disablePin += d
                            },
                            onDelete = {
                                disableError = ""
                                disablePin = disablePin.dropLast(1)
                            },
                            onClose = { showDisableKeyboard = false },
                        )
                    }
                }
            }
        } else null,
    ) {
        // .disable-dialog-content{ padding:20px 16px; text-align:center }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FText(
                "请输入当前 PIN 码以确认关闭", 14f, FontWeight.Normal, LocalAppColors.current.textTertiary,
                Modifier.padding(bottom = 16.dp),
            )
            PinCells(
                value = disablePin,
                focused = true,
                gutter = 10.dp, // :gutter="10"
            )
            if (disableError.isNotEmpty()) {
                FText(
                    disableError, 12f, FontWeight.Normal, LocalAppColors.current.danger,
                    Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** `.status-card`（`.app-card` 的 12 圆角在样式表里定义于其后，同权重后者取胜）。 */
@Composable
private fun PinStatusCard(hasPinSet: Boolean) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgCard),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        ) {
            // .status-icon：72 圆、页面底色、mb 16
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(colors.bgPage),
            ) {
                VanIcon(
                    name = "shield-o",
                    size = 48.sp, // size="48"
                    color = if (hasPinSet) PIN_GREEN else PIN_ORANGE,
                )
            }
            Spacer(Modifier.height(16.dp))
            FText(
                if (hasPinSet) "PIN 码已设置" else "未设置 PIN 码",
                18f, FontWeight.Bold, colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            FText(
                if (hasPinSet) "您的账户已启用 PIN 码保护" else "设置 PIN 码可以更好地保护您的账户安全",
                14f, FontWeight.Normal, colors.textTertiary,
                Modifier.padding(bottom = 16.dp), // .status-info{ margin-bottom:16px }
            )
        }

        // 右上角状态角标（绝对定位）
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (hasPinSet) PIN_GREEN_BG else colors.bgThird)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            FText(
                if (hasPinSet) "已启用" else "已禁用",
                12f, FontWeight.Medium,
                if (hasPinSet) PIN_GREEN else colors.textTertiary,
            )
        }
    }
    Spacer(Modifier.height(24.dp)) // .status-card{ margin-bottom:24px }
}

/** `<van-cell-group inset class="app-card">`（本页 .app-card 覆写圆角为 12）。 */
@Composable
private fun PinGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LocalAppColors.current.bgCard),
        content = content,
    )
}

/** `app-cell`（12/16、14px 标题、18px 主色图标、箭头硬编码 #969799、通栏底线）。 */
@Composable
private fun PinOptCell(title: String, icon: String, onClick: () -> Unit) {
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
        FText(title, 14f, FontWeight.Normal, colors.textPrimary, Modifier.weight(1f))
        VanIcon(name = "arrow", size = 16.sp, color = PIN_ARROW)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}
