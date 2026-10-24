package com.live.finance.ui.bankcard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.store.CardPageStyle
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.feedback.LocalVanToastController

/**
 * 卡方实验室（2026-10-22）—— 两种卡片页形态的统一入口。
 *
 * 顶部 tab 切换「卡片模组 / 卡包」直接预览两套原生页面；点「保存设定」把当前选择写入
 * [com.live.finance.core.store.CardStyleStore]，之后进入「我的卡片」由 `CardPageHost`
 * 按所选形态渲染。未保存前切换 tab 只影响实验室预览。
 *
 * 预览内嵌的是正式页面本身（含它们各自的顶栏/交互）；外层已做 systemBars 消费，
 * 避免内层 ScreenScaffold 重复加系统栏内边距。
 */
@Composable
fun CardStyleLabScreen(nav: NavHostController) {
    val graph = App.of(LocalContext.current).graph
    val colors = LocalAppColors.current
    val toast = LocalVanToastController.current

    // 默认落在当前已保存的形态；未保存过就是卡片模组。
    var selected by remember { mutableStateOf(graph.cardPageStyle.value) }

    ScreenScaffold { inner ->
        Column(Modifier.fillMaxSize().then(inner)) {
            // ===== 顶部：双 tab + 保存设定 =====
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.bgThird)
                        .padding(4.dp),
                ) {
                    StyleTab(
                        label = "卡片模组",
                        selected = selected == CardPageStyle.Module,
                        modifier = Modifier.weight(1f),
                    ) { selected = CardPageStyle.Module }
                    StyleTab(
                        label = "卡包",
                        selected = selected == CardPageStyle.Wallet,
                        modifier = Modifier.weight(1f),
                    ) { selected = CardPageStyle.Wallet }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.primary)
                        .clickable {
                            graph.setCardPageStyle(selected)
                            graph.persistCardPageStyle()
                            toast.show("已保存：下次进入「我的卡片」使用${selected.label}")
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FText("保存设定", 13f, FontWeight.SemiBold, Color.White)
                }
            }
            FText(
                "当前保存：${graph.cardPageStyle.value.label} · 保存后下次进入「我的卡片」生效。",
                11f, FontWeight.Normal, colors.textTertiary,
                Modifier.padding(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 8.dp),
            )

            // ===== 预览：直接内嵌正式页面 =====
            // 外层 ScreenScaffold 已处理系统栏；consume 后内层不再重复加内边距。
            Box(
                Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(WindowInsets.systemBars),
            ) {
                when (selected) {
                    CardPageStyle.Module -> CardLabScreen(nav, initialTab = "debit")
                    CardPageStyle.Wallet -> CardWalletLabScreen(nav, initialTab = "debit")
                }
            }
        }
    }
}

@Composable
private fun StyleTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FText(
            label,
            13f,
            FontWeight.SemiBold,
            if (selected) Color.White else colors.textSecondary,
        )
    }
}
