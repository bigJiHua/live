package com.live.finance.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.core.nav.Routes
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.icon.VanIcon

/** 页面里硬编码的箭头色（web `ResourceManage.vue` 的 `#right-icon` 用 `color="#969799"`）。 */
private val MANAGE_ARROW = Color(0xFF969799)

/**
 * 文件资源管理（资源类型入口）—— 1:1 复刻 web `src/views/User/resource/ResourceManage.vue`：
 * `.manage-content{padding:16px; padding-top:20px}` + `.section-title`（8/16、13px、tertiary、500、mb8）
 * + `van-cell-group inset .app-card`（圆角 12）内 4 个 `app-cell`（icon + label + is-link + center）。
 */
@Composable
fun ResourceManageScreen(nav: NavHostController) {
    val colors = LocalAppColors.current

    ScreenScaffold { inner ->
        Column(inner.verticalScroll(rememberScrollState())) {
            // 页面标题（web 在全局 nav-bar 上；原生沿用各页自绘标题的约定）
            FText(
                "文件资源管理", 18f, FontWeight.Bold, colors.textPrimary,
                Modifier.padding(16.dp),
            )
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp)) {
                FText(
                    "资源类型", 13f, FontWeight.Medium, colors.textTertiary,
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)) // .app-card{ border-radius: 12px }
                        .background(colors.bgCard),
                ) {
                    ResourceTypeCell("动态图片", "发布的动态图片", "photo-o") { nav.navigate(Routes.resourceList("post")) }
                    ResourceTypeCell("资产图片", "资产相关的图片", "coupon-o") { nav.navigate(Routes.resourceList("product")) }
                    ResourceTypeCell("银行 Icon", "银行图标资源", "shop-o") { nav.navigate(Routes.resourceList("bank")) }
                    // web 写的是 "folder-o"，但 Vant 4.9.22 字体无此图标（同 albums-o，web 实际也不渲染）。
                    // 按用户要求补图标：用语义最近、且与其余三项同为 -o 描边风格的 "description-o"（文档）。
                    ResourceTypeCell("其他资源", "其他类型的文件", "description-o") { nav.navigate(Routes.resourceList("other")) }
                }
            }
        }
    }
}

/** `app-cell`（12/16、图标 18 主色、label 12 tertiary、箭头 #969799、通栏底线）。 */
@Composable
private fun ResourceTypeCell(title: String, label: String, icon: String, onClick: () -> Unit) {
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
        VanIcon(name = "arrow", size = 16.sp, color = MANAGE_ARROW)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}
