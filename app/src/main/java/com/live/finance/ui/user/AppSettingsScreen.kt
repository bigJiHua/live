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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.theme.MONEY_RED_IN
import com.live.finance.theme.MONEY_RED_OUT
import com.live.finance.theme.ThemePresets
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.WebShadow
import com.live.finance.ui.common.cssShadowCircle
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonSize
import com.live.vant.basic.VanButtonType
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch

/**
 * 应用设置页 —— 对齐 web `src/views/User/settings/AppSettings.vue` 的「UI 主题」+「收支颜色」两个区块
 * （分类设置 / PWA 安装区块不属本轮主题范围）。
 *
 * 与 web 的差异说明：web 的退出登录在「我的」页（`User/index.vue` 的 `.logout-wrapper`），
 * 本页保留一个同样式（`plain + round + danger` 空心描边）的退出入口，避免大红实心块。
 */
@Composable
fun AppSettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val graph = App.of(ctx).graph
    val scope = rememberCoroutineScope()

    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val choice = graph.themeChoice.value
    val moneyMode = graph.moneyColorMode.value
    // 当前生效预设（「系统默认」项的打勾色取它，对应 web themeActiveColor）
    val activePreset = ThemePresets.resolve(choice, tokens.isDark)

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
                VanButton(
                    text = "切换",
                    type = VanButtonType.Primary,
                    size = VanButtonSize.Small,
                    onClick = {
                        scope.launch {
                            graph.setMoneyColorMode(if (moneyMode == MONEY_RED_IN) MONEY_RED_OUT else MONEY_RED_IN)
                        }
                    },
                )
            }

            // ===== 账号 =====
            SettingsSectionTitle("账号")
            AppButton(
                text = "退出当前登录",
                type = AppButtonType.Danger,
                plain = true,
                round = true,
                block = true,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                onClick = {
                    scope.launch {
                        graph.auth.logout()
                        toast.show("已安全退出")
                        nav.navigate(Routes.LOGIN) { popUpTo(Routes.MAIN) { inclusive = true } }
                    }
                },
            )
        }
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
        13f,
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
            FText(title, 14f, FontWeight.Normal, colors.textPrimary)
            Spacer(Modifier.height(4.dp)) // .app-cell__label{ margin-top: 4px }
            FText(label, 12f, FontWeight.Normal, colors.textTertiary)
        }
        VanIcon(name = "arrow", size = 16.sp, color = Color(0xFF969799))
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
                11f,
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
