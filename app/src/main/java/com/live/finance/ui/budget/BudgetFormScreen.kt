package com.live.finance.ui.budget

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Budget
import com.live.finance.data.model.Dish
import com.live.finance.data.model.EatDetails
import com.live.finance.data.model.RateItem
import com.live.finance.data.model.ShopItem
import com.live.finance.data.model.ShoppingDetails
import com.live.finance.data.model.TravelDay
import com.live.finance.data.model.TravelDetails
import com.live.finance.data.model.TravelItem
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.form.VanDatePicker
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/** 三类型表单（web Shopping.vue / Travel.vue / Eat.vue 三页合一，路由仍是三条）。 */
@Composable
fun BudgetFormScreen(nav: NavHostController, type: String, editId: String?) {
    val colors = LocalAppColors.current
    val toast = LocalVanToastController.current
    val repo = App.of(LocalContext.current).graph.budget
    val scope = rememberCoroutineScope()
    val isEdit = editId != null

    // ===== 基本字段 =====
    var title by remember { mutableStateOf("") }
    var route by remember { mutableStateOf("") }
    var planDate by remember { mutableStateOf(todayStr()) }
    var cycle by remember { mutableStateOf("") }
    var budgetAmount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(isEdit) }
    var saving by remember { mutableStateOf(false) }
    var showSubmit by remember { mutableStateOf(false) }

    // ===== 买：购物清单 =====
    val shopItems = remember { mutableStateListOf<ShopItem>() }
    // ===== 吃：菜单 =====
    val dishes = remember { mutableStateListOf<Dish>() }
    // ===== 行：每日消费 =====
    val days = remember { mutableStateListOf<TravelDay>() }
    val rates = remember {
        mutableStateListOf<RateItem>().apply {
            addAll(listOf(RateItem("HKD", "0.92"), RateItem("USD", "7.25"), RateItem("EUR", "7.80"), RateItem("GBP", "9.00")))
        }
    }

    // ===== 弹层 =====
    var showCycle by remember { mutableStateOf(false) }
    var showPlanDate by remember { mutableStateOf(false) }
    var showPriority by remember { mutableStateOf(-1) }      // 买：item index
    var showShopDate by remember { mutableStateOf(-1) }      // 买：item index
    var showType by remember { mutableStateOf(-1 to -1) }    // 行：(day, item)
    var showCurrency by remember { mutableStateOf(-1 to -1) }
    var showDayDate by remember { mutableStateOf(-1) }       // 行：day index（-2=新增）
    var showAddRate by remember { mutableStateOf(false) }
    var newRateName by remember { mutableStateOf("") }
    var newRateCode by remember { mutableStateOf("") }
    var newRateValue by remember { mutableStateOf("") }
    var showDeleteItem by remember { mutableStateOf(Triple("", -1, -1)) } // (kind: item/dish/day/item2, day, item)

    // ===== 编辑预填 =====
    LaunchedEffect(editId) {
        if (editId == null) return@LaunchedEffect
        val b = (repo.detail(editId) as? ApiResult.Ok)?.data ?: run {
            toast.show("加载失败"); nav.popBackStack(); return@LaunchedEffect
        }
        title = b.title; route = b.route; planDate = b.planDate.ifEmpty { todayStr() }
        cycle = b.cycle; budgetAmount = if (b.budgetAmount == 0.0) "" else trimAmt(b.budgetAmount)
        when (val d = b.details) {
            is ShoppingDetails -> { shopItems.addAll(d.items); notes = d.notes }
            is EatDetails -> { dishes.addAll(d.dishes); notes = d.notes }
            is TravelDetails -> { days.addAll(d.days); rates.clear(); rates.addAll(d.exchangeRates); notes = d.notes }
            else -> {}
        }
        loading = false
    }

    // ===== 汇总（web computed 口径）=====
    val validShops = shopItems.filter { it.name.isNotBlank() }
    val estimatedTotal = validShops.sumOf { it.price * (it.quantity.toDoubleOrNull() ?: 1.0) }
    val shopActual = validShops.sumOf { it.actualPrice }
    val purchasedCount = validShops.count { it.actualPrice > 0 }
    val validDishes = dishes.filter { it.name.isNotBlank() }
    val eatActual = validDishes.sumOf { it.price * (it.quantity.toDoubleOrNull() ?: 1.0) }

    fun rateOf(ccy: String): Double = rates.firstOrNull { it.currency == ccy }?.value?.toDoubleOrNull() ?: 0.0
    fun travelTotal(): Double = days.sumOf { day ->
        day.items.sumOf { if (it.currency == "CNY") it.amount else it.cnyAmount }
    }
    fun refreshCny() {
        for ((di, day) in days.withIndex()) {
            for ((ii, item) in day.items.withIndex()) {
                val cny = if (item.currency == "CNY") item.amount else item.amount * rateOf(item.currency)
                days[di] = day.copy(items = day.items.toMutableList().also { it[ii] = item.copy(cnyAmount = cny) })
            }
        }
    }

    // ===== 提交（web submit 校验原文）=====
    fun doSubmit() {
        showSubmit = false
        if (title.isBlank() || budgetAmount.toDoubleOrNull() == null) { toast.show("请完善基本信息"); return }
        saving = true
        val amount = budgetAmount.toDoubleOrNull() ?: 0.0
        val details = when (type) {
            "买" -> ShoppingDetails(
                items = validShops, estimatedTotal = estimatedTotal, actualTotal = shopActual,
                purchasedCount = purchasedCount, notes = notes,
            )
            "吃" -> EatDetails(dishes = validDishes, actualTotal = eatActual, notes = notes)
            else -> {
                if (days.size > 15) { toast.show("计划天数最多15天"); saving = false; return }
                val filtered = days.map { it.copy(items = it.items.filter { i -> i.amount > 0 && i.description.isNotBlank() }) }
                    .filter { it.items.isNotEmpty() }
                if (filtered.sumOf { it.items.size } == 0) { toast.show("请至少添加一条消费记录"); saving = false; return }
                days.clear(); days.addAll(filtered)
                TravelDetails(days = filtered, exchangeRates = rates.toList(), notes = notes)
            }
        }
        val record = Budget(
            id = editId.orEmpty(), title = title.trim(), budgetType = type,
            budgetAmount = amount, cycle = cycle.ifEmpty { "月" }, planDate = planDate,
            route = route.trim(), details = details,
        )
        scope.launch {
            val r = if (isEdit) repo.update(record) else repo.create(record)
            saving = false
            when (r) {
                is ApiResult.Ok -> { toast.success("保存成功"); nav.popBackStack(Routes.BUDGET, false) }
                is ApiResult.Unauthorized -> nav.navigate(Routes.LOGIN) { popUpTo(Routes.MAIN) { inclusive = true } }
                is ApiResult.RateLimited -> nav.navigate(Routes.ERROR_429)
                is ApiResult.Fail -> toast.show(r.message.ifEmpty { "保存失败" })
                else -> toast.show("保存失败")
            }
        }
    }

    val budgetVal = budgetAmount.toDoubleOrNull() ?: 0.0
    val plannedExpense = when (type) { "行" -> travelTotal(); "吃" -> eatActual; else -> estimatedTotal }

    ScreenScaffold { inner ->
        Column(inner.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            // ===== 基本信息 =====
            SectionHeader("基本信息", null)
            Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)).background(colors.bgCard)) {
                AppField(value = title, onValueChange = { title = it },
                    label = if (type == "行") "行程标题" else "计划标题",
                    placeholder = if (type == "行") "如：香港3天2晚" else if (type == "买") "如：618购物清单" else "如：本月餐饮",
                    border = false)
                if (type == "行") {
                    AppField(value = route, onValueChange = { route = it },
                        label = "路线规划", placeholder = "深圳 → 香港")
                }
                AppField(value = budgetAmount,
                    onValueChange = { budgetAmount = filterAmount(it) },
                    label = "总预算", placeholder = "0.00",
                    keyboardType = KeyboardType.Number,
                    trailing = { FText("元", 14f, color = colors.textTertiary) })
                SelectRow(label = "预算周期", value = BudgetShared.cycleName(cycle).ifEmpty { "" },
                    hasValue = cycle.isNotEmpty(),
                    onClick = { showCycle = true })
                SelectRow(label = if (type == "买") "购买日期" else "计划日期", value = planDate,
                    hasValue = true, onClick = { showPlanDate = true })
            }

            // ===== 行：常用汇率 =====
            if (type == "行") {
                SectionHeader("常用汇率 (1外币 = ? CNY)", null)
                Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)).background(colors.bgCard).padding(12.dp)) {
                    rates.chunked(2).forEach { rowRates ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowRates.forEach { rate ->
                                Row(Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                                    .background(colors.bgThird).padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    FText(rate.currency, 13f, FontWeight.SemiBold, colors.textPrimary)
                                    AppField(value = rate.value,
                                        onValueChange = { v ->
                                            val i = rates.indexOfFirst { it.currency == rate.currency }
                                            if (i >= 0) rates[i] = RateItem(rate.currency, filterRate(v))
                                            refreshCny()
                                        },
                                        placeholder = "0.0000", border = false,
                                        keyboardType = KeyboardType.Number,
                                        modifier = Modifier.weight(1f))
                                    VanIcon("cross", size = 13.sp, color = colors.textTertiary,
                                        modifier = Modifier.clickable {
                                            if (days.any { d -> d.items.any { it.currency == rate.currency && it.amount > 0 } }) {
                                                toast.show("该汇率已被使用，无法删除")
                                            } else {
                                                rates.removeAll { it.currency == rate.currency }; refreshCny()
                                            }
                                        })
                                }
                            }
                            if (rowRates.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(6.dp))
                            .clickable { showAddRate = true },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) { VanIcon("plus", size = 15.sp, color = colors.textSecondary) }
                }
            }

            // ===== 明细区 =====
            when (type) {
                "买" -> ShoppingSection(
                budgetAmount = budgetVal,
                items = shopItems,
                onAdd = { shopItems.add(ShopItem(quantity = "1", purchaseDate = todayStr())) },
                onRemove = { i -> showDeleteItem = Triple("item", -1, i) },
                onPriority = { i -> showPriority = i },
                onDate = { i -> showShopDate = i },
                onChange = { i, s -> shopItems[i] = s },
            )
                "吃" -> EatSection(
                    dishes = dishes,
                    onAdd = { dishes.add(Dish(quantity = "1")) },
                    onRemove = { i -> showDeleteItem = Triple("dish", -1, i) },
                    onChange = { i, d -> dishes[i] = d },
                )
                "行" -> TravelSection(
                    days = days,
                    totalBudget = budgetVal,
                    totalExpense = travelTotal(),
                    currencyTotals = TravelDetails(days = days.toList(), exchangeRates = rates.toList()).currencyTotals(),
                    onAddDay = { showDayDate = -2 },
                    onEditDay = { i -> showDayDate = i },
                    onRemoveDay = { i -> showDeleteItem = Triple("day", i, -1) },
                    onAddItem = { di -> days[di] = days[di].copy(items = days[di].items +
                            TravelItem(type = "吃", currency = "CNY", cnyAmount = 0.0)) },
                    onRemoveItem = { di, ii -> showDeleteItem = Triple("item2", di, ii) },
                    onPickType = { di, ii -> showType = di to ii },
                    onPickCurrency = { di, ii -> showCurrency = di to ii },
                    onChangeItem = { di, ii, item ->
                        val cny = if (item.currency == "CNY") item.amount else item.amount * rateOf(item.currency)
                        days[di] = days[di].copy(items = days[di].items.toMutableList().also { it[ii] = item.copy(cnyAmount = cny) })
                    },
                )
            }

            // ===== 备注（行/吃；买在 summary 内）=====
            if (type != "买") {
                SectionHeader("备忘录", null)
                Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)).background(colors.bgCard)) {
                    AppField(value = notes, onValueChange = { notes = it.take(200) },
                        label = "备忘录", border = false,
                        placeholder = "有什么需要特别注意的？（例如：签证准备、小费习惯等）")
                }
            }

            Spacer(Modifier.height(24.dp))
            AppButton(
                text = when {
                    type == "行" && isEdit -> "保存更新"
                    type == "行" -> "立即创建预算"
                    type == "买" && isEdit -> "保存修改"
                    type == "买" -> "创建购物计划"
                    type == "吃" && isEdit -> "保存修改"
                    else -> "创建餐饮预算"
                },
                onClick = { showSubmit = true },
                type = AppButtonType.Primary, size = AppButtonSize.Large,
                block = true, round = true, loading = saving || loading,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    // ===== 弹层 =====
    if (showCycle) {
        VanPopup(show = true, onDismissRequest = { showCycle = false }, position = VanPopupPosition.Bottom, round = true) {
            VanPicker(
                columns = listOf(listOf("月", "季", "年").map { VanPickerOption(it, it) }),
                title = "选择周期",
                onConfirm = { _, v -> cycle = v[0] as String; showCycle = false },
                onCancel = { showCycle = false },
            )
        }
    }
    if (showPlanDate) {
        VanPopup(show = true, onDismissRequest = { showPlanDate = false }, position = VanPopupPosition.Bottom, round = true) {
            VanDatePicker(
                type = "date", value = parseDate(planDate), onValueChange = { },
                minDate = LocalDate.of(2021, 1, 1), maxDate = LocalDate.of(2031, 12, 31),
                title = "选择日期",
                onConfirm = { planDate = it.toString(); showPlanDate = false },
                onCancel = { showPlanDate = false },
            )
        }
    }
    if (showPriority >= 0) {
        VanPopup(show = true, onDismissRequest = { showPriority = -1 }, position = VanPopupPosition.Bottom, round = true) {
            VanPicker(
                columns = listOf(listOf("必买", "想要", "可选").map { VanPickerOption(it, it) }),
                title = "选择优先级",
                onConfirm = { _, v ->
                    if (showPriority < shopItems.size) shopItems[showPriority] = shopItems[showPriority].copy(priority = v[0] as String)
                    showPriority = -1
                },
                onCancel = { showPriority = -1 },
            )
        }
    }
    if (showShopDate >= 0) {
        VanPopup(show = true, onDismissRequest = { showShopDate = -1 }, position = VanPopupPosition.Bottom, round = true) {
            VanDatePicker(
                type = "date", value = parseDate(shopItems[showShopDate].purchaseDate), onValueChange = { },
                minDate = LocalDate.of(2021, 1, 1), maxDate = LocalDate.of(2031, 12, 31),
                title = "购买日期",
                onConfirm = {
                    if (showShopDate < shopItems.size) shopItems[showShopDate] = shopItems[showShopDate].copy(purchaseDate = it.toString())
                    showShopDate = -1
                },
                onCancel = { showShopDate = -1 },
            )
        }
    }
    val (tDay, tItem) = showType
    if (tDay >= 0) {
        VanPopup(show = true, onDismissRequest = { showType = -1 to -1 }, position = VanPopupPosition.Bottom, round = true) {
            VanPicker(
                columns = listOf(listOf("行", "吃", "喝", "买", "住", "玩", "其他").map { VanPickerOption(it, it) }),
                title = "选择分类",
                onConfirm = { _, v ->
                    days[tDay] = days[tDay].copy(items = days[tDay].items.toMutableList().also { it[tItem] = it[tItem].copy(type = v[0] as String) })
                    showType = -1 to -1
                },
                onCancel = { showType = -1 to -1 },
            )
        }
    }
    val (cDay, cItem) = showCurrency
    if (cDay >= 0) {
        VanPopup(show = true, onDismissRequest = { showCurrency = -1 to -1 }, position = VanPopupPosition.Bottom, round = true) {
            VanPicker(
                columns = listOf(listOf("CNY", "HKD", "USD", "EUR", "GBP").map { VanPickerOption(it, it) }),
                title = "选择币种",
                onConfirm = { _, v ->
                    val item = days[cDay].items[cItem].copy(currency = v[0] as String)
                    val cny = if (item.currency == "CNY") item.amount else item.amount * rateOf(item.currency)
                    days[cDay] = days[cDay].copy(items = days[cDay].items.toMutableList().also { it[cItem] = item.copy(cnyAmount = cny) })
                    showCurrency = -1 to -1
                },
                onCancel = { showCurrency = -1 to -1 },
            )
        }
    }
    if (showDayDate != -1) {
        val isAdd = showDayDate == -2
        VanPopup(show = true, onDismissRequest = { showDayDate = -1 }, position = VanPopupPosition.Bottom, round = true) {
            VanDatePicker(
                type = "date",
                value = if (isAdd) LocalDate.now() else parseDate(days[showDayDate].date),
                onValueChange = { },
                minDate = LocalDate.of(2021, 1, 1), maxDate = LocalDate.of(2031, 12, 31),
                title = "选择日期",
                onConfirm = {
                    if (isAdd) days.add(TravelDay(date = it.toString())) 
                    else days[showDayDate] = days[showDayDate].copy(date = it.toString())
                    showDayDate = -1
                },
                onCancel = { showDayDate = -1 },
            )
        }
    }
    if (showAddRate) {
        VanPopup(show = true, onDismissRequest = { showAddRate = false }, position = VanPopupPosition.Bottom, round = true) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                FText("添加常用汇率", 17f, FontWeight.SemiBold, colors.textPrimary)
                Spacer(Modifier.height(20.dp))
                AppField(value = newRateName, onValueChange = { newRateName = it.take(12) }, label = "币种名称", placeholder = "如：韩元")
                AppField(value = newRateCode, onValueChange = { newRateCode = it.replace(Regex("[^a-zA-Z]"), "").uppercase() }, label = "币种代码", placeholder = "如：KRW")
                AppField(value = newRateValue, onValueChange = { newRateValue = filterRate(it) }, label = "汇率", placeholder = "1外币 = ? CNY", keyboardType = KeyboardType.Number)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton("取消", { showAddRate = false }, block = true, round = true, modifier = Modifier.weight(1f))
                    AppButton("确定", {
                        val code = newRateCode.trim(); val value = newRateValue.trim()
                        if (code.isEmpty() || value.isEmpty()) { toast.show("请输入币种代码和汇率"); return@AppButton }
                        val i = rates.indexOfFirst { it.currency == code }
                        if (i >= 0) rates[i] = RateItem(code, value) else rates.add(RateItem(code, value))
                        refreshCny()
                        newRateName = ""; newRateCode = ""; newRateValue = ""
                        showAddRate = false
                    }, type = AppButtonType.Primary, block = true, round = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
    val (delKind, delDay, delItem) = showDeleteItem
    if (delKind.isNotEmpty()) {
        val message = when (delKind) {
            "day" -> "确定要删除这一天吗？"
            "item2" -> "确定要删除这笔消费吗？"
            else -> "确定要删除这一项吗？"
        }
        VanConfirmDialog(
            show = true, title = "确认删除", message = message,
            onConfirm = {
                when (delKind) {
                    "day" -> days.removeAt(delDay)
                    "item2" -> days[delDay] = days[delDay].copy(items = days[delDay].items.toMutableList().also { it.removeAt(delItem) })
                    "item" -> shopItems.removeAt(delItem)
                    "dish" -> dishes.removeAt(delItem)
                }
                showDeleteItem = Triple("", -1, -1)
            },
            onCancel = { showDeleteItem = Triple("", -1, -1) },
            onClose = { showDeleteItem = Triple("", -1, -1) },
        )
    }
    VanConfirmDialog(
        show = showSubmit,
        title = "确认提交",
        message = if (isEdit) "确定要保存修改吗？" else when (type) {
            "买" -> "确定要创建购物计划吗？"; "吃" -> "确定要创建餐饮预算吗？"; else -> "确定要创建预算吗？"
        },
        onConfirm = { doSubmit() },
        onCancel = { showSubmit = false },
        onClose = { showSubmit = false },
    )
}

// ================= 子组件 =================

@Composable
internal fun SectionHeader(title: String, action: (@Composable () -> Unit)?) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(title, 14f, FontWeight.SemiBold, colors.textPrimary)
        action?.invoke()
    }
}

@Composable
internal fun SelectRow(label: String, value: String, hasValue: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            FText(label, 13f, color = colors.textSecondary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FText(value.ifEmpty { "请选择" }, 14f,
                    color = if (hasValue) colors.textPrimary else colors.textTertiary,
                    modifier = Modifier.weight(1f))
                VanIcon("arrow-down", size = 13.sp, color = colors.textTertiary)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

/** 买：购物清单编辑区。 */
@Composable
private fun ShoppingSection(
    budgetAmount: Double,
    items: List<ShopItem>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onPriority: (Int) -> Unit,
    onDate: (Int) -> Unit,
    onChange: (Int, ShopItem) -> Unit,
) {
    val colors = LocalAppColors.current
    SectionHeader("购物清单") {
        AppButton("添加商品", onAdd, type = AppButtonType.Primary, plain = true, size = AppButtonSize.Small)
    }
    if (items.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            FText("点击上方添加商品", 13f, color = colors.textTertiary)
        }
    }
    items.forEachIndexed { i, item ->
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)).background(colors.bgCard).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IndexBadge(i + 1, colors.primary)
                Spacer(Modifier.weight(1f))
                VanIcon("cross", size = 16.sp, color = colors.danger, modifier = Modifier.clickable { onRemove(i) })
            }
            AppField(value = item.name, onValueChange = { onChange(i, item.copy(name = it)) },
                label = "商品名称", placeholder = "如：iPhone 16")
            AppField(value = item.category, onValueChange = { onChange(i, item.copy(category = it)) },
                label = "类别", placeholder = "电子产品")
            AppField(value = item.quantity, onValueChange = { onChange(i, item.copy(quantity = it.filter { c -> c.isDigit() }.take(4))) },
                label = "数量", placeholder = "1", keyboardType = KeyboardType.Number)
            SelectRow(label = "优先级", value = item.priority, hasValue = item.priority.isNotEmpty(), onClick = { onPriority(i) })
            AppField(value = if (item.price == 0.0) "" else trimAmt(item.price),
                onValueChange = { onChange(i, item.copy(price = it.toDoubleOrNull() ?: 0.0)) },
                label = "预计价格", placeholder = "0.00", keyboardType = KeyboardType.Number,
                trailing = { FText("元", 13f, color = colors.textTertiary) })
            AppField(value = if (item.actualPrice == 0.0) "" else trimAmt(item.actualPrice),
                onValueChange = { onChange(i, item.copy(actualPrice = it.toDoubleOrNull() ?: 0.0)) },
                label = "实际价格", placeholder = "0.00", keyboardType = KeyboardType.Number,
                trailing = { FText("元", 13f, color = colors.textTertiary) })
            SelectRow(label = "购买日期", value = item.purchaseDate, hasValue = true, onClick = { onDate(i) })
            AppField(value = item.shop, onValueChange = { onChange(i, item.copy(shop = it)) },
                label = "店铺", placeholder = "京东自营")
            AppField(value = item.notes, onValueChange = { onChange(i, item.copy(notes = it.take(50))) },
                label = "备注", placeholder = "颜色、规格等")
        }
    }
    // 预算汇总（web summary-group）
    val est = items.filter { it.name.isNotBlank() }.sumOf { it.price * (it.quantity.toDoubleOrNull() ?: 1.0) }
    val act = items.filter { it.name.isNotBlank() }.sumOf { it.actualPrice }
    val bought = items.filter { it.name.isNotBlank() && it.actualPrice > 0 }.size
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
        .clip(RoundedCornerShape(8.dp)).background(colors.bgCard)) {
        SummaryRow("预算总额", "¥${Money.format(budgetAmount)}", colors.textPrimary)
        SummaryRow("预计总花费", "¥${Money.format(est)}", colors.primary)
        SummaryRow("实际总花费", "¥${Money.format(act)}", colors.danger)
        SummaryRow("已购/总数", "$bought / ${items.filter { it.name.isNotBlank() }.size}", Color(0xFF07C160))
    }
}

/** 吃：菜单编辑区。 */
@Composable
private fun EatSection(
    dishes: List<Dish>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onChange: (Int, Dish) -> Unit,
) {
    val colors = LocalAppColors.current
    SectionHeader("菜单明细") {
        AppButton("添加菜品", onAdd, type = AppButtonType.Primary, plain = true, size = AppButtonSize.Small)
    }
    if (dishes.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            FText("点击上方添加菜品", 13f, color = colors.textTertiary)
        }
    }
    dishes.forEachIndexed { i, dish ->
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)).background(colors.bgCard).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IndexBadge(i + 1, colors.danger)
                Spacer(Modifier.weight(1f))
                VanIcon("cross", size = 16.sp, color = colors.danger, modifier = Modifier.clickable { onRemove(i) })
            }
            AppField(value = dish.name, onValueChange = { onChange(i, dish.copy(name = it)) },
                label = "菜品名称", placeholder = "如：火锅")
            AppField(value = if (dish.price == 0.0) "" else trimAmt(dish.price),
                onValueChange = { onChange(i, dish.copy(price = it.toDoubleOrNull() ?: 0.0)) },
                label = "价格", placeholder = "0.00", keyboardType = KeyboardType.Number,
                trailing = { FText("元", 13f, color = colors.textTertiary) })
            AppField(value = dish.quantity, onValueChange = { onChange(i, dish.copy(quantity = it.filter { c -> c.isDigit() }.take(4))) },
                label = "数量", placeholder = "1", keyboardType = KeyboardType.Number)
            AppField(value = dish.notes, onValueChange = { onChange(i, dish.copy(notes = it.take(50))) },
                label = "备注", placeholder = "口味、日期等")
        }
    }
    val valid = dishes.filter { it.name.isNotBlank() }
    val actual = valid.sumOf { it.price * (it.quantity.toDoubleOrNull() ?: 1.0) }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
        .clip(RoundedCornerShape(8.dp)).background(Color(0x1AEE0A24)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            FText("实际消费合计", 14f, FontWeight.SemiBold, colors.danger)
            FText("¥${Money.format(actual)}", 18f, FontWeight.Bold, colors.danger)
        }
    }
}

/** 行：每日消费明细编辑区（摘要 + 天折叠）。 */
@Composable
private fun TravelSection(
    days: List<TravelDay>,
    totalBudget: Double,
    totalExpense: Double,
    currencyTotals: List<Pair<String, Double>>,
    onAddDay: () -> Unit,
    onEditDay: (Int) -> Unit,
    onRemoveDay: (Int) -> Unit,
    onAddItem: (Int) -> Unit,
    onRemoveItem: (Int, Int) -> Unit,
    onPickType: (Int, Int) -> Unit,
    onPickCurrency: (Int, Int) -> Unit,
    onChangeItem: (Int, Int, TravelItem) -> Unit,
) {
    val colors = LocalAppColors.current
    val balance = totalBudget - totalExpense
    val progress = if (totalBudget <= 0) 0 else ((totalExpense / totalBudget * 100).roundToInt()).coerceIn(0, 100)
    // 摘要卡
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
        .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TSummary("总预算", "¥${Money.format(totalBudget)}", colors.textPrimary, Modifier.weight(1f))
            TSummary("已规划支出", "¥${Money.format(totalExpense)}", colors.danger, Modifier.weight(1f))
            TSummary("剩余结余", "¥${Money.format(balance)}",
                if (balance >= 0) Color(0xFF07C160) else colors.danger, Modifier.weight(1f))
        }
        if (currencyTotals.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                currencyTotals.forEach { (ccy, amt) ->
                    FText("${trimAmt(amt)} $ccy", 11f, FontWeight.Medium, colors.textSecondary,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(colors.bgThird).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.bgThird)) {
            Box(Modifier.fillMaxWidth(progress / 100f).height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(if (balance >= 0) Color(0xFF07C160) else colors.danger))
        }
        Spacer(Modifier.height(4.dp))
        FText("预算进度 $progress%", 11f, FontWeight.Medium,
            if (balance >= 0) Color(0xFF07C160) else colors.danger)
    }
    SectionHeader("消费明细清单") {
        AppButton("增加天数", onAddDay, type = AppButtonType.Primary, size = AppButtonSize.Small, round = true, icon = "plus")
    }
    if (days.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            FText("暂无行程规划，点击「增加天数」开始", 13f, color = colors.textTertiary)
        }
    }
    days.forEachIndexed { di, day ->
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)).background(colors.bgCard)) {
            // 日头：日期编辑 + 删除
            Row(Modifier.fillMaxWidth().background(colors.bgThird)
                .clickable { onEditDay(di) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                VanIcon("calendar-o", size = 14.sp, color = colors.primary)
                FText(day.date.ifEmpty { "未设置日期" }, 14f, FontWeight.SemiBold, colors.primary,
                    modifier = Modifier.weight(1f).padding(start = 6.dp))
                VanIcon("arrow", size = 12.sp, color = colors.textTertiary)
                Spacer(Modifier.width(8.dp))
                VanIcon("delete-o", size = 16.sp, color = colors.danger, modifier = Modifier.clickable { onRemoveDay(di) })
            }
            // 消费条目
            day.items.forEachIndexed { ii, item ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 类型 tag（点击弹 picker）
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp))
                                .background(expenseTypeColor(item.type).copy(alpha = 0.12f))
                                .clickable { onPickType(di, ii) }
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FText(item.type, 12f, FontWeight.Medium, expenseTypeColor(item.type))
                                VanIcon("arrow-down", size = 10.sp, color = expenseTypeColor(item.type))
                            }
                        }
                        AppField(value = item.description,
                            onValueChange = { onChangeItem(di, ii, item.copy(description = it.take(30))) },
                            placeholder = "备注(如：晚餐)", border = false,
                            modifier = Modifier.weight(1f).padding(start = 6.dp))
                        VanIcon("clear", size = 16.sp, color = colors.textTertiary,
                            modifier = Modifier.clickable { onRemoveItem(di, ii) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppField(value = if (item.amount == 0.0) "" else trimAmt(item.amount),
                            onValueChange = { v -> onChangeItem(di, ii, item.copy(amount = v.toDoubleOrNull() ?: 0.0)) },
                            placeholder = "0.00", border = false, keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f))
                        // 币种（点击弹 picker）
                        Row(
                            Modifier.clip(RoundedCornerShape(6.dp)).background(colors.bgThird)
                                .clickable { onPickCurrency(di, ii) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FText(item.currency, 13f, FontWeight.Medium, colors.textPrimary)
                            VanIcon("arrow-down", size = 10.sp, color = colors.textTertiary)
                        }
                    }
                    if (item.currency != "CNY") {
                        FText("≈ ¥${Money.format(item.cnyAmount)}", 11f, color = colors.textTertiary,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(colors.border))
            }
            // 记一笔 + 当日小计
            Row(Modifier.fillMaxWidth().clickable { onAddItem(di) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                VanIcon("add-o", size = 14.sp, color = colors.primary)
                FText(" 记一笔", 13f, color = colors.primary)
            }
            Row(Modifier.fillMaxWidth().background(colors.bgThird).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                FText("当日小计:", 12f, color = colors.textSecondary)
                FText("¥${Money.format(day.items.sumOf { it.cnyAmount })}", 13f, FontWeight.SemiBold, colors.primary)
            }
        }
    }
}

@Composable
private fun IndexBadge(n: Int, color: Color) {
    Box(
        Modifier.size(20.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) { FText("$n", 11f, FontWeight.SemiBold, Color.White) }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color) {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        FText(label, 14f, color = colors.textSecondary)
        FText(value, 14f, FontWeight.SemiBold, valueColor)
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(colors.border))
}

@Composable
private fun TSummary(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FText(label, 12f, color = LocalAppColors.current.textTertiary)
        FText(value, 15f, FontWeight.Bold, valueColor)
    }
}

// ================= 工具 =================

internal fun todayStr(): String = LocalDate.now().toString()
internal fun parseDate(s: String): LocalDate =
    runCatching { LocalDate.parse(s) }.getOrDefault(LocalDate.now())
internal fun trimAmt(v: Double): String {
    val r = Math.round(v * 100.0) / 100.0
    return if (r == r.toLong().toDouble()) r.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", r).trimEnd('0').trimEnd('.')
}
internal fun filterAmount(raw: String): String {
    var s = raw.replace(Regex("[^\\d.]"), "").replace(Regex("\\.{2,}"), ".")
    s = s.replace(".", "#").replace(".", "").replace("#", ".")
    val head = s.substringBefore('.').take(9)
    val tail = s.substringAfter('.', "").take(2)
    return if (tail.isEmpty()) head else "$head.$tail"
}
internal fun filterRate(raw: String): String {
    var s = raw.replace(Regex("[^\\d.]"), "").replace(Regex("\\.{2,}"), ".")
    s = s.replace(".", "#").replace(".", "").replace("#", ".")
    val head = s.substringBefore('.').take(3)
    val tail = s.substringAfter('.', "").take(4)
    val v = if (tail.isEmpty()) head else "$head.$tail"
    return if ((v.toDoubleOrNull() ?: 0.0) > 999.9999) "999.9999" else v
}
