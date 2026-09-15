package com.live.finance.ui.flow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.feedback.LocalVanToastController
import kotlinx.coroutines.launch

@Composable
fun FlowDetailScreen(nav: NavHostController, id: String) {
    val colors = LocalAppColors.current
    val toast = LocalVanToastController.current
    val scope = rememberCoroutineScope()
    val repo = App.of(LocalContext.current).graph.flow
    var row by remember { mutableStateOf<FlowRow?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(id) {
        loading = true
        row = when (val r = repo.detail(id)) {
            is ApiResult.Ok -> r.data
            else -> null
        }
        loading = false
    }

    ScreenScaffold { _ ->
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (loading) {
                BasicText("加载中…", style = TextStyle(color = colors.textTertiary, fontSize = 14.sp))
                return@Column
            }
            val r = row
            if (r == null) {
                BasicText("未找到该流水", style = TextStyle(color = colors.textSecondary, fontSize = 14.sp))
                return@Column
            }
            val sign = if (r.isIncome) "+" else "-"
            BasicText(
                sign + Money.format(r.amount) + if (r.currency != "CNY") "  ${r.currency}" else "",
                style = TextStyle(color = if (r.isIncome) com.live.finance.ui.common.MoneyColor.income else com.live.finance.ui.common.MoneyColor.expense, fontSize = 28.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(20.dp))
            DetailRow("分类", r.categoryName, colors)
            DetailRow("方式", r.payMethod, colors)
            DetailRow("账户", r.bankLabel, colors)
            DetailRow("交易日", r.transDate, colors)
            DetailRow("创建时间", com.live.finance.core.TimeFmt.dateTime(r.createTime), colors)
            DetailRow("备注", r.remark.ifEmpty { "—" }, colors)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, colors: com.live.vant.theme.VantColors) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        BasicText(label, Modifier, TextStyle(color = colors.textTertiary, fontSize = 14.sp))
        Spacer(Modifier.height(0.dp))
        BasicText(
            value,
            Modifier.padding(start = 24.dp),
            TextStyle(color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
    }
}
