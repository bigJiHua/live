package com.live.finance.ui.finance

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.live.finance.core.nav.Routes
import com.live.finance.ui.common.AppCard
import com.live.finance.ui.common.MenuRow
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.SectionText

@Composable
fun FinanceScreen(nav: NavHostController) {
    ScreenScaffold { inner ->
        androidx.compose.foundation.lazy.LazyColumn(modifier = inner) {
            item {
                AppCard(Modifier.padding(top = 16.dp)) {
                    SectionText("账本", large = true)
                    MenuRow(title = "新增收支明细", onClick = { nav.navigate(Routes.ACCOUNT_ADD) })
                    MenuRow(title = "系统账户余额", onClick = { nav.navigate(Routes.ACCOUNT_STRUCTURE) })
                    MenuRow(title = "余额流水", onClick = { nav.navigate(Routes.ACCOUNT_BALANCE_FLOW) })
                    MenuRow(title = "流水明细", onClick = { nav.navigate(Routes.FLOW_LIST) })
                    MenuRow(title = "每日流水日历", onClick = { nav.navigate(Routes.FLOW_CALENDAR) })
                    MenuRow(title = "资产登记", onClick = { nav.navigate(Routes.ASSETS_LIST) })
                    MenuRow(title = "银行卡", onClick = { nav.navigate(Routes.CARD) })
                    MenuRow(title = "固定资产", onClick = { nav.navigate(Routes.FIXED_ASSET) })
                    MenuRow(title = "报表", onClick = { nav.navigate(Routes.REPORT_STATS_OVERVIEW) })
                    MenuRow(title = "理财", onClick = { nav.navigate(Routes.FUND) })
                    MenuRow(title = "预算", onClick = { nav.navigate(Routes.BUDGET) })
                    MenuRow(title = "固定支出", onClick = { nav.navigate(Routes.RECURRING) })
                    MenuRow(title = "固定事件", onClick = { nav.navigate(Routes.EVENTS) })
                    MenuRow(title = "数据管理", onClick = { nav.navigate(Routes.DATA_MANAGE) })
                }
            }
        }
    }
}
