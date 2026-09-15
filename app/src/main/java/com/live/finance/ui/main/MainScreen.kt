package com.live.finance.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.live.finance.ui.finance.FinanceScreen
import com.live.finance.ui.home.HomeScreen
import com.live.finance.ui.user.UserScreen
import com.live.vant.nav.VanTabbar
import com.live.vant.nav.VanTabbarItemData

private val TABS = listOf(
    VanTabbarItemData(icon = "wap-home-o", title = "首页"),
    VanTabbarItemData(icon = "balance-list-o", title = "账本"),
    VanTabbarItemData(icon = "user-o", title = "我的"),
)

@Composable
fun MainScreen(nav: NavHostController) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> HomeScreen(nav)
                1 -> FinanceScreen(nav)
                else -> UserScreen(nav)
            }
        }
        VanTabbar(active = tab, onActiveChange = { tab = it }, items = TABS)
    }
}
