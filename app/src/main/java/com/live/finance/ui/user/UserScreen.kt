package com.live.finance.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import com.live.finance.core.AppConfig
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.User
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppCell
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanImage
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.form.VanSwitch
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch

/** 默认头像：逐字对齐 web `stores/user.js` 的 defaultAvatar。 */
private const val DEFAULT_AVATAR = "https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg"

/** 个人信息卡右上角箭头色：web 硬编码 `color="#969799"`（不受深浅色影响）。 */
private val ARROW_COLOR = Color(0xFF969799)

/**
 * 「我的」页 —— 一比一复刻 web `src/views/User/index.vue`（含其私有样式与 AppCell/AppButton 底座度量）。
 *
 * 结构对照：
 *  1. `.profile-card`（头像 + 昵称 + 邮箱 + 箭头）→ [MineProfileCard]
 *  2. `.section-title` 安全与隐私 → [MineSectionTitle] + `<van-cell-group inset class="app-card">`
 *  3. `.section-title` 系统管理 → 同上
 *  4. `.logout-wrapper` → [MineOutlineButton]（app-button type=danger plain round block）
 */
@Composable
fun UserScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val graph = App.of(ctx).graph
    val scope = rememberCoroutineScope()

    var user by remember { mutableStateOf<User?>(null) }
    // PIN 状态（对齐 web stores/user.js 的 hasPinSet / pinEnabled）
    var hasPinSet by remember { mutableStateOf(false) }
    var pinEnabled by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // 对应 web onMounted：getUserInfo() + checkPinStatus()
    LaunchedEffect(Unit) {
        (graph.auth.getUserinfo() as? ApiResult.Ok)?.data?.let { user = it }
        // checkPinStatus：200=已设置 PIN；随后静默落库（web setPinState(hasPinSet, hasPinSet)）
        val set = graph.security.hasPinSet()
        hasPinSet = set
        pinEnabled = set
    }

    // 对应 web onPinSwitch：本页不允许关闭 PIN，只会提示到设置页修改
    fun onPinSwitch(checked: Boolean) {
        if (checked && !hasPinSet) {
            toast.show("请先设置 PIN 码")
            pinEnabled = false
        } else {
            pinEnabled = true
            toast.show("请到设置PIN码页面修改")
        }
    }

    ScreenScaffold { inner ->
        Column(
            modifier = inner
                .verticalScroll(rememberScrollState())
                .padding(bottom = 30.dp), // .page-user padding-bottom: 30px
        ) {
            // ===== 1. 个人信息卡 =====
            MineProfileCard(
                avatarUrl = AppConfig.fullFileUrl(user?.avatar.orEmpty().ifBlank { DEFAULT_AVATAR }),
                username = user?.username.orEmpty(),
                email = user?.email.orEmpty(),
                onClick = { nav.navigate(Routes.PROFILE_EDIT) },
            )

            // ===== 2. 安全与隐私 =====
            MineSectionTitle("安全与隐私")
            MineGroup {
                AppCell(
                    title = "PIN 码访问锁定",
                    label = "进入系统需二次验证",
                    center = true,
                    rightIcon = {
                        VanSwitch(
                            checked = pinEnabled,
                            onCheckedChange = { onPinSwitch(it) },
                            size = 22, // web size="22px"
                        )
                    },
                )
                AppCell(
                    title = "PIN 码管理",
                    isLink = true,
                    center = true,
                    onClick = { nav.navigate(Routes.PIN_MANAGE) },
                )
            }

            // ===== 3. 系统管理 =====
            MineSectionTitle("系统管理")
            MineGroup {
                AppCell(
                    title = "应用设置",
                    icon = "setting-o",
                    isLink = true,
                    onClick = { nav.navigate(Routes.APP_SETTINGS) },
                )
                AppCell(
                    title = "文件资源管理",
                    icon = "photograph",
                    isLink = true,
                    onClick = { nav.navigate(Routes.RESOURCE_MANAGE) }, // web 我的页跳 /user/resource-manage
                )
            }

            // ===== 4. 退出登录 =====
            AppButton(
                text = "退出当前登录",
                type = AppButtonType.Danger,
                plain = true,   // web: app-button type=danger **plain** round block（空心红描边，不是大红实心块）
                round = true,
                block = true,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 40.dp), // .logout-wrapper margin: 40px 24px
                onClick = { showLogoutConfirm = true },
            )
        }
    }

    // 对应 web showConfirmDialog({title:"提醒", message:"确定退出登录？"})
    VanConfirmDialog(
        show = showLogoutConfirm,
        title = "提醒",
        message = "确定退出登录？",
        onConfirm = {
            scope.launch {
                graph.auth.logout()
                toast.show("已安全退出")
                nav.navigate(Routes.LOGIN) {
                    popUpTo(Routes.MAIN) { inclusive = true }
                }
            }
        },
        onClose = { showLogoutConfirm = false },
    )
}

/**
 * 个人信息卡 —— 对应 web `.profile-card`：
 * bg-secondary、padding 50/20/30、点击态 bg-tertiary、头像 64×64 圆形 + 1px 边框、昵称 20px bold、邮箱 13px。
 */
@Composable
private fun MineProfileCard(
    avatarUrl: String,
    username: String,
    email: String,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (pressed) colors.bgThird else colors.bgCard)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 20.dp, end = 20.dp, top = 50.dp, bottom = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // `.profile-avatar{ margin-right:16px; border:1px solid var(--theme-border) }` + van-image round
        Box(
            modifier = Modifier
                .size(64.dp)
                .border(1.dp, colors.border, CircleShape)
                .padding(1.dp),
        ) {
            VanImage(
                src = avatarUrl,
                width = 62.dp,
                height = 62.dp,
                round = true,
                showError = false,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            FText(username, 20f, FontWeight.Bold, colors.textPrimary)
            Spacer(Modifier.height(4.dp)) // .nickname-row margin-bottom: 4px
            FText("邮箱： $email", 13f, FontWeight.Normal, colors.textTertiary)
        }
        VanIcon(name = "arrow", size = 16.sp, color = ARROW_COLOR)
    }
}

/** 分组标题 —— 对应 web `.section-title`（padding 20/20/10、13px、text-tertiary、weight 500）。 */
@Composable
private fun MineSectionTitle(text: String) {
    FText(
        text,
        13f,
        FontWeight.Medium,
        LocalAppColors.current.textTertiary,
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
    )
}

/**
 * 分组白卡 —— 对应 web `<van-cell-group inset class="app-card">`：
 * 左右 16px 外边距、圆角被 `.app-card` 覆写为 12px（非 Vant 默认 8px）、overflow hidden。
 */
@Composable
private fun MineGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LocalAppColors.current.bgCard),
        content = content,
    )
}

/**
 * 说明：本页原私有 `MineCell`（app-cell 复刻）已上收到共享底座 `ui/common/AppCell.kt`，
 * 与「记一笔/快速登记」等页共用同一份 web `AppCell.vue` 度量实现。
 */

/**
 * 说明：本页原私有 `MineOutlineButton`（app-button 描边按钮复刻）已上收到共享底座
 * `ui/common/AppButton.kt`（`type=Danger + plain + round + block`），页面不再各写一份。
 */
