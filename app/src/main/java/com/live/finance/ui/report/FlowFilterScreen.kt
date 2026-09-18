package com.live.finance.ui.report

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.form.VanDatePicker
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

private const val CATEGORY_REPAY = "CATEGORY_REPAY"
private val PAIR_BLUE = Color(0xFF1989FA)

/** 配对后的展示节点（web displayList：flow / transfer / withdrawal / reversal）。 */
private sealed class FilterNode {
    data class Flow(val data: FlowRow) : FilterNode()
    data class Pair(
        val expense: FlowRow, val income: FlowRow,
        val isWithdrawal: Boolean, val isReversal: Boolean,
    ) : FilterNode()
}

/**
 * 流水筛选（web `views/Finance/report/flow/FlowFilter.vue`）：
 * 日期范围 + 收支/分类/方式过滤 → 按日分组展示；转账/提现/冲正按 web 五趟配对算法合并为左右对账行；
 * 「跳至」按结果日期快速定位；导出为 CSV（原生等价实现，存 Download 目录）。
 */
@Composable
fun FlowFilterScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val toast = LocalVanToastController.current
    val graph = App.of(LocalContext.current).graph
    val scope = rememberCoroutineScope()

    var startDate by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var endDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var direction by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf<Pair<String, String>?>(null) } // id to name
    var selectedMethod by remember { mutableStateOf("") }
    var showStartPick by remember { mutableStateOf(false) }
    var showEndPick by remember { mutableStateOf(false) }
    var showDirPick by remember { mutableStateOf(false) }
    var showCatPick by remember { mutableStateOf(false) }
    var showMethodPick by remember { mutableStateOf(false) }
    var showQuickPick by remember { mutableStateOf(false) }
    var quickDate by remember { mutableStateOf<String?>(null) }

    var incomeCats by remember { mutableStateOf<List<Category>>(emptyList()) }
    var expenseCats by remember { mutableStateOf<List<Category>>(emptyList()) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var allData by remember { mutableStateOf<List<FlowRow>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val showBackTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    LaunchedEffect(Unit) {
        incomeCats = (graph.category.list("income") as? ApiResult.Ok)?.data ?: emptyList()
        expenseCats = (graph.category.list("expense") as? ApiResult.Ok)?.data ?: emptyList()
        cards = (graph.card.list(null) as? ApiResult.Ok)?.data ?: emptyList()
    }

    val displayCats = remember(direction, incomeCats, expenseCats) {
        when (direction) {
            "1" -> incomeCats
            "0" -> expenseCats
            else -> incomeCats + expenseCats
        }
    }

    val resultList = remember(allData, direction, selectedCat, selectedMethod) {
        var list = allData
        if (direction.isNotEmpty()) list = list.filter { it.direction.toString() == direction }
        selectedCat?.let { c -> list = list.filter { it.categoryId == c.first } }
        if (selectedMethod.isNotEmpty()) list = list.filter { it.payMethod == selectedMethod }
        list
    }
    val totalIncome = resultList.filter { it.direction == 1 }.sumOf { it.amount }
    val totalExpense = resultList.filter { it.direction != 1 }.sumOf { it.amount }
    val availableDates = remember(resultList) { resultList.map { it.day }.distinct().sorted() }

    // ===== web 五趟配对算法（逐字移植）=====
    val displayNodes = remember(resultList, cards) {
        val items = resultList
        val usedExpense = mutableSetOf<String>()
        val usedIncome = mutableSetOf<String>()
        val pairs = mutableListOf<FilterNode.Pair>()

        val isVirtual = { id: String -> id == "yyyy" || id == "xxxx" }
        val isCredit = { item: FlowRow ->
            item.accountType == "credit" || cards.firstOrNull { it.id == item.cardId }?.isCredit == true
        }
        val baseMatch = { e: FlowRow, i: FlowRow ->
            e.day == i.day && e.amount == i.amount &&
                (if (e.currency.isEmpty()) "CNY" else e.currency) == (if (i.currency.isEmpty()) "CNY" else i.currency) &&
                e.cardId != i.cardId
        }
        val catOf = { i: FlowRow -> i.payType.ifEmpty { i.categoryName } }

        // 第0趟：transfer_group_id（区分冲正/提现）
        items.filter { it.categoryId != CATEGORY_REPAY && it.transferGroupId.isNotEmpty() }
            .groupBy { it.transferGroupId }
            .forEach { (_, group) ->
                val expense = group.firstOrNull { it.direction == 0 || it.direction == 2 }
                val income = group.firstOrNull { it.direction == 1 }
                if (expense != null && income != null) {
                    val withdrawal = expense.cardId == "yyyy" && !isVirtual(income.cardId)
                    val reversal = expense.reversedId.isNotEmpty() || income.reversedId.isNotEmpty()
                    pairs.add(FilterNode.Pair(expense, income, withdrawal, reversal))
                    usedExpense.add(expense.id); usedIncome.add(income.id)
                }
            }

        // 第1趟：冲正（信用卡支出 + 现金/余额收入，pay_type=冲正强信号或 5 分钟内）
        items.forEach { item ->
            if (item.id in usedExpense || item.id in usedIncome) return@forEach
            if (item.categoryId == CATEGORY_REPAY) return@forEach
            if (item.direction == 1) return@forEach
            if (!isCredit(item)) return@forEach
            val match = items.firstOrNull { inc ->
                inc.id != item.id && inc.id !in usedIncome && inc.categoryId != CATEGORY_REPAY &&
                    inc.direction == 1 && baseMatch(item, inc) && isVirtual(inc.cardId) &&
                    (catOf(inc) == "冲正" || run {
                        val t1 = item.createTime.toLongOrNull()
                        val t2 = inc.createTime.toLongOrNull()
                        t1 == null || t2 == null || kotlin.math.abs(t1 - t2) <= 300_000
                    })
            }
            if (match != null) {
                pairs.add(FilterNode.Pair(item, match, false, true))
                usedExpense.add(item.id); usedIncome.add(match.id)
            }
        }

        // 第2趟：双方「转账」→ 确诊（支出方非信用卡）
        items.forEach { item ->
            if (item.id in usedExpense || item.id in usedIncome) return@forEach
            if (item.categoryId == CATEGORY_REPAY) return@forEach
            if (item.direction == 1 || isCredit(item)) return@forEach
            val match = items.firstOrNull { inc ->
                inc.id != item.id && inc.id !in usedIncome && inc.categoryId != CATEGORY_REPAY &&
                    inc.direction == 1 && baseMatch(item, inc) &&
                    catOf(item) == "转账" && catOf(inc) == "转账"
            }
            if (match != null) {
                pairs.add(FilterNode.Pair(item, match, false, false))
                usedExpense.add(item.id); usedIncome.add(match.id)
            }
        }

        // 第3趟：「其他支出」+「其他收入」→ 疑似转账
        items.forEach { item ->
            if (item.id in usedExpense || item.id in usedIncome) return@forEach
            if (item.categoryId == CATEGORY_REPAY) return@forEach
            if (item.direction == 1 || isCredit(item)) return@forEach
            val match = items.firstOrNull { inc ->
                inc.id != item.id && inc.id !in usedIncome && inc.categoryId != CATEGORY_REPAY &&
                    inc.direction == 1 && baseMatch(item, inc) &&
                    catOf(item) == "其他支出" && catOf(inc) == "其他收入"
            }
            if (match != null) {
                pairs.add(FilterNode.Pair(item, match, false, false))
                usedExpense.add(item.id); usedIncome.add(match.id)
            }
        }

        // 第4趟：其余基础匹配 → 疑似转账（排除余额→实体卡方向 与 信用卡支出方）
        items.forEach { item ->
            if (item.id in usedExpense || item.id in usedIncome) return@forEach
            if (item.categoryId == CATEGORY_REPAY) return@forEach
            if (item.direction == 1 || isCredit(item)) return@forEach
            val match = items.firstOrNull { inc ->
                inc.id != item.id && inc.id !in usedIncome && inc.categoryId != CATEGORY_REPAY &&
                    inc.direction == 1 && baseMatch(item, inc) &&
                    !(isVirtual(item.cardId) && !isVirtual(inc.cardId))
            }
            if (match != null) {
                pairs.add(FilterNode.Pair(item, match, false, false))
                usedExpense.add(item.id); usedIncome.add(match.id)
            }
        }

        // 第5趟：余额→银行卡（同天同额 5 分钟内）→ 提现
        items.forEach { item ->
            if (item.id in usedExpense || item.id in usedIncome) return@forEach
            if (item.categoryId == CATEGORY_REPAY) return@forEach
            if (item.direction == 1) return@forEach
            if (item.cardId != "yyyy") return@forEach
            val match = items.firstOrNull { inc ->
                inc.id != item.id && inc.id !in usedIncome && inc.categoryId != CATEGORY_REPAY &&
                    inc.direction == 1 && baseMatch(item, inc) && !isVirtual(inc.cardId) &&
                    run {
                        val t1 = item.createTime.toLongOrNull()
                        val t2 = inc.createTime.toLongOrNull()
                        t1 == null || t2 == null || kotlin.math.abs(t1 - t2) <= 300_000
                    }
            }
            if (match != null) {
                pairs.add(FilterNode.Pair(item, match, true, false))
                usedExpense.add(item.id); usedIncome.add(match.id)
            }
        }

        // 构建展示列表：支出方按组展示一次；被配对收入方隐藏；其余独立流水
        val pairByExpense = pairs.associateBy { it.expense.id }
        val pairedIncomes = pairs.map { it.income.id }.toSet()
        val nodes = mutableListOf<FilterNode>()
        items.forEach { item ->
            val p = pairByExpense[item.id]
            when {
                p != null -> nodes.add(p)
                item.id !in pairedIncomes -> nodes.add(FilterNode.Flow(item))
            }
        }
        nodes
    }

    // 按日分组（倒序）+ 日期 → LazyColumn index 映射（跳至）
    data class DayGroup(val date: String, val nodes: List<FilterNode>)
    val dayGroups = remember(displayNodes) {
        displayNodes.groupBy { node ->
            when (node) {
                is FilterNode.Flow -> node.data.day
                is FilterNode.Pair -> node.expense.day
            }
        }.filterKeys { it.isNotEmpty() }
            .map { (date, nodes) -> DayGroup(date, nodes) }
            .sortedByDescending { it.date }
    }
    val dateIndexMap = remember(dayGroups) {
        var idx = 0
        dayGroups.associate { g -> g.date to idx++.also { idx += g.nodes.size } }
    }

    fun bankLabelOf(item: FlowRow): String {
        when (item.cardId) {
            "yyyy" -> return "余额"; "xxxx" -> return "现金"
        }
        val c = cards.firstOrNull { it.id == item.cardId }
        return item.bankLabel.ifEmpty { c?.displayName.orEmpty() }.ifEmpty { item.payMethod }
    }

    fun doExport() {
        if (resultList.isEmpty()) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val sb = StringBuilder("日期,收支,分类,方式,金额,币种,卡片,备注\n")
                    resultList.forEach { i ->
                        sb.append("${i.transDate},${if (i.direction == 1) "收入" else "支出"},")
                        sb.append("${i.categoryName},${i.payMethod},${Money.format(i.amount)},${i.currency},")
                        sb.append("${bankLabelOf(i)},\"${i.remark.replace("\"", "\"\"")}\"")
                        sb.append("\n")
                    }
                    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "export")
                    if (!dir.exists()) dir.mkdirs()
                    val f = File(dir, "流水筛选_${startDate}_${endDate}.csv")
                    f.writeText(sb.toString(), Charsets.UTF_8)
                    f.absolutePath
                }.getOrNull()
            }
            toast.show(if (ok != null) "已导出：$ok" else "导出失败")
        }
    }

    fun search() {
        quickDate = null
        scope.launch {
            searching = true; searched = true
            allData = when (val r = graph.flow.listRange(startDate, endDate, page = 1, limit = 1000)) {
                is ApiResult.Ok -> r.data ?: emptyList()
                else -> { toast.show("查询失败"); emptyList() }
            }
            searching = false
        }
    }

    ScreenScaffold { inner ->
        Column(inner.fillMaxSize()) {
            com.live.vant.nav.VanNavBar(title = "流水筛选", leftArrow = true, onClickLeft = { nav.popBackStack() })
            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                // Layer1：日期范围 + 查询
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(colors.bgCard).clickable { showStartPick = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                FText("开始", 11f, color = colors.textTertiary)
                                FText(startDate, 14f, FontWeight.SemiBold, colors.textPrimary)
                            }
                            FText("至", 12f, color = colors.textTertiary)
                            Column(Modifier.clickable { showEndPick = true }, horizontalAlignment = Alignment.End) {
                                FText("结束", 11f, color = colors.textTertiary)
                                FText(endDate, 14f, FontWeight.SemiBold, colors.textPrimary)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        AppButton("查询", { search() }, type = AppButtonType.Primary, round = true, loading = searching)
                    }
                }
                // Layer2：收支/分类/方式
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterCell(
                            label = "收支",
                            value = when (direction) { "0" -> "支出"; "1" -> "收入"; else -> "不限" },
                            onClick = { showDirPick = true }, modifier = Modifier.weight(1f),
                        )
                        FilterCell(
                            label = "分类", value = selectedCat?.second ?: "不限",
                            onClick = { showCatPick = true }, modifier = Modifier.weight(1f),
                        )
                        FilterCell(
                            label = "方式", value = selectedMethod.ifEmpty { "不限" },
                            onClick = { showMethodPick = true }, modifier = Modifier.weight(1f),
                        )
                    }
                }
                // Layer3：跳至 + 导出
                if (searched && availableDates.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FText("跳至", 13f, color = colors.textTertiary)
                                Row(
                                    Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp))
                                        .background(colors.bgCard).clickable { showQuickPick = true }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    FText(quickDate?.replace("-", "/") ?: "选择日期", 13f, color = colors.textPrimary)
                                    VanIcon("arrow-down", size = 11.sp, color = colors.textTertiary,
                                        modifier = Modifier.padding(start = 4.dp))
                                }
                                if (quickDate != null) {
                                    FText(" 全部", 13f, color = colors.primary,
                                        modifier = Modifier.clickable { quickDate = null })
                                }
                            }
                            if (resultList.isNotEmpty()) {
                                AppButton("导出 Excel", { doExport() }, plain = true, round = true,
                                    size = com.live.finance.ui.common.AppButtonSize.Small)
                            }
                        }
                    }
                }
                // 结果
                if (searched) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            FText("共 ${resultList.size} 条", 13f, color = colors.textTertiary)
                            if (totalIncome > 0 || totalExpense > 0) {
                                Row {
                                    FText("收入 +${Money.format(totalIncome)}", 13f, color = MoneyColor.income)
                                    FText(" | ", 13f, color = colors.textTertiary)
                                    FText("支出 -${Money.format(totalExpense)}", 13f, color = MoneyColor.expense)
                                }
                            }
                        }
                    }
                    if (resultList.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                VanEmpty(description = "未找到匹配记录")
                            }
                        }
                    }
                    dayGroups.forEach { group ->
                        item(key = "h_${group.date}") {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                FText(dateHeaderText(group.date), 13f, FontWeight.SemiBold, colors.textSecondary)
                            }
                        }
                        group.nodes.forEachIndexed { ni, node ->
                            item(key = "${group.date}_$ni") {
                                when (node) {
                                    is FilterNode.Flow -> FlowLine(
                                        node.data, bankLabelOf(node.data),
                                        onClick = { nav.navigate(Routes.flowDetail(node.data.id)) },
                                    )
                                    is FilterNode.Pair -> PairLine(
                                        title = when {
                                            node.isReversal -> "冲正"
                                            node.isWithdrawal -> "提现"
                                            else -> "转账"
                                        },
                                        headerColor = when {
                                            node.isReversal -> colors.warning
                                            node.isWithdrawal -> Color(0xFF07C160)
                                            else -> PAIR_BLUE
                                        },
                                        expense = node.expense, income = node.income,
                                        expBank = bankLabelOf(node.expense).ifEmpty { "余额" },
                                        incBank = bankLabelOf(node.income).ifEmpty { "银行卡" },
                                        onExp = { nav.navigate(Routes.flowDetail(node.expense.id)) },
                                        onInc = { nav.navigate(Routes.flowDetail(node.income.id)) },
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }

            if (showBackTop) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp)
                        .size(40.dp).clip(RoundedCornerShape(50))
                        .background(colors.bgCard).clickable {
                            scope.launch { listState.scrollToItem(0) }
                        },
                    contentAlignment = Alignment.Center,
                ) { VanIcon("back-top", size = 20.sp, color = colors.primary) }
            }
        }
    }

    // ===== 弹层 =====
    if (showStartPick) {
        VanPopup(show = true, onDismissRequest = { showStartPick = false }, position = VanPopupPosition.Bottom, round = true) {
            VanDatePicker(type = "date", value = LocalDate.parse(startDate), onValueChange = { },
                title = "开始日期",
                onConfirm = { startDate = it.toString(); showStartPick = false },
                onCancel = { showStartPick = false })
        }
    }
    if (showEndPick) {
        VanPopup(show = true, onDismissRequest = { showEndPick = false }, position = VanPopupPosition.Bottom, round = true) {
            VanDatePicker(type = "date", value = LocalDate.parse(endDate), onValueChange = { },
                title = "结束日期",
                onConfirm = { endDate = it.toString(); showEndPick = false },
                onCancel = { showEndPick = false })
        }
    }
    if (showDirPick) {
        VanPopup(show = true, onDismissRequest = { showDirPick = false }, position = VanPopupPosition.Bottom, round = true) {
            VanPicker(
                columns = listOf(listOf("不限" to "", "支出" to "0", "收入" to "1").map { VanPickerOption(it.first, it.second) }),
                title = "收支",
                onConfirm = { _, v ->
                    direction = (v[0] as? String).orEmpty()
                    selectedCat = null; showDirPick = false
                },
                onCancel = { showDirPick = false },
            )
        }
    }
    if (showCatPick) {
        VanPopup(show = true, onDismissRequest = { showCatPick = false }, position = VanPopupPosition.Bottom, round = true) {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    FText("选择分类", 16f, FontWeight.SemiBold, colors.textPrimary)
                    VanIcon("cross", size = 18.sp, color = colors.textTertiary,
                        modifier = Modifier.clickable { showCatPick = false })
                }
                Column(Modifier.fillMaxWidth().height(360.dp).verticalScroll(rememberScrollState())) {
                    CheckRow("不限", selectedCat == null) { selectedCat = null; showCatPick = false }
                    displayCats.forEach { cat ->
                        CheckRow(cat.name, selectedCat?.first == cat.id) {
                            selectedCat = cat.id to cat.name; showCatPick = false
                        }
                    }
                }
            }
        }
    }
    if (showMethodPick) {
        VanPopup(show = true, onDismissRequest = { showMethodPick = false }, position = VanPopupPosition.Bottom, round = true) {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    FText("选择方式", 16f, FontWeight.SemiBold, colors.textPrimary)
                    VanIcon("cross", size = 18.sp, color = colors.textTertiary,
                        modifier = Modifier.clickable { showMethodPick = false })
                }
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    listOf("", "现金", "余额", "微信支付", "支付宝", "借记卡", "信用卡").forEach { m ->
                        CheckRow(m.ifEmpty { "不限" }, selectedMethod == m) {
                            selectedMethod = m; showMethodPick = false
                        }
                    }
                }
            }
        }
    }
    if (showQuickPick) {
        VanPopup(show = true, onDismissRequest = { showQuickPick = false }, position = VanPopupPosition.Bottom, round = true) {
            VanPicker(
                columns = listOf(availableDates.map { VanPickerOption(it, it) }),
                title = "跳转至",
                onConfirm = { _, v ->
                    quickDate = v[0] as? String
                    showQuickPick = false
                    dateIndexMap[quickDate]?.let { idx ->
                        scope.launch { listState.scrollToItem(idx) }
                    }
                },
                onCancel = { showQuickPick = false },
            )
        }
    }
}
}

@Composable
private fun FilterCell(label: String, value: String, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalAppColors.current
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(colors.bgCard)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FText(label, 11f, color = colors.textTertiary)
            VanIcon("arrow-down", size = 10.sp, color = colors.textTertiary)
        }
        Spacer(Modifier.height(2.dp))
        FText(value, 14f, FontWeight.SemiBold, colors.textPrimary)
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(text, 14f, color = colors.textPrimary)
        if (checked) VanIcon("success", size = 16.sp, color = Color(0xFF07C160))
    }
}

@Composable
private fun FlowLine(item: FlowRow, bank: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val income = item.direction == 1
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp)).background(colors.bgCard)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                FText(item.categoryName.ifEmpty { "未分类" }, 14f, FontWeight.Medium, colors.textPrimary)
                FText(bank, 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                FText(
                    (if (income) "+" else "-") + Money.format(item.amount), 15f, FontWeight.Bold,
                    if (income) MoneyColor.income else MoneyColor.expense,
                )
                FText(item.transDate.take(10), 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun PairLine(
    title: String, headerColor: Color,
    expense: FlowRow, income: FlowRow,
    expBank: String, incBank: String,
    onExp: () -> Unit, onInc: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp)).background(colors.bgCard),
    ) {
        Box(Modifier.fillMaxWidth().background(headerColor.copy(alpha = 0.1f)).padding(horizontal = 14.dp, vertical = 6.dp)) {
            FText(title, 12f, FontWeight.SemiBold, headerColor)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { onExp() }) {
                FText("-${Money.format(expense.amount)}", 15f, FontWeight.Bold, MoneyColor.expense)
                FText(expBank, 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
            }
            FText("→", 16f, FontWeight.Bold, colors.textTertiary)
            Column(Modifier.weight(1f).clickable { onInc() }, horizontalAlignment = Alignment.End) {
                FText("+${Money.format(income.amount)}", 15f, FontWeight.Bold, MoneyColor.income)
                FText(incBank, 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** web formatDateHeader：今天/昨天/yyyy年M月D日。 */
internal fun dateHeaderText(date: String): String {
    val today = LocalDate.now()
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    return when (d) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
    }
}
