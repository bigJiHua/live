package com.live.finance.ui.bankcard

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.store.CardPageStyle

/**
 * 正式「我的卡片」入口（CARD / CARD_DEBIT / CARD_CREDIT）。
 *
 * 根据设置里保存的 [CardPageStyle] 决定渲染哪一种原生页面：
 * - `Module`：卡片模组页（CardLabScreen）；
 * - `Wallet`：卡包页（CardWalletLabScreen）。
 *
 * 两页都是原生实现，不再经过 WebView。
 */
@Composable
fun CardPageHost(nav: NavHostController, initialTab: String = "debit") {
    val style = App.of(LocalContext.current).graph.cardPageStyle.value
    when (style) {
        CardPageStyle.Wallet -> CardWalletLabScreen(nav, initialTab)
        CardPageStyle.Module -> CardLabScreen(nav, initialTab)
    }
}
