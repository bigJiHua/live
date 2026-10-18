package com.live.finance.ui.bankcard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.core.net.failToastType
import com.live.finance.data.model.Card
import com.live.finance.data.model.Pool
import com.live.finance.data.repo.NewCard
import com.live.finance.theme.LocalAppColors
import com.live.vant.form.VanCheckbox
import com.live.vant.form.VanCheckboxShape
import com.live.vant.icon.VanIcon
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanPicker
import com.live.vant.form.vanPickerColumnsOf
import kotlinx.coroutines.launch
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanToastType

private data class SharedGroup(val poolId: String, val poolName: String, val cards: List<Card>)

/**
 * 额度与共享池管理 —— 一比一复刻 web `views/BankCard/credit/LimitManage.vue`。
 * 「临时额度取消」= 把临额改 0 后随固额一并 `updateCard` 统一提交（web 无独立取消接口）；
 * 池删除**无二次确认**（web 原样）；批量归池串行逐卡 `POST /card/pool/assign`。
 */
@Composable
fun LimitManageScreen(nav: NavHostController) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val graph = App.of(context).graph
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var tab by remember { mutableStateOf(0) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var pools by remember { mutableStateOf<List<Pool>>(emptyList()) }
    var activeGroups by remember { mutableStateOf<List<String>>(emptyList()) }

    // 卡编辑弹层
    var editCard by remember { mutableStateOf<Card?>(null) }
    var fCredit by remember { mutableStateOf("0") }
    var fTemp by remember { mutableStateOf("0") }
    var fRate by remember { mutableStateOf("1") }
    var fPoolId by remember { mutableStateOf("") }
    var prevPoolId by remember { mutableStateOf("") }
    var showPoolPicker by remember { mutableStateOf(false) }

    // 池编辑弹层
    var poolEditShow by remember { mutableStateOf(false) }
    var pId by remember { mutableStateOf("") }
    var pBankId by remember { mutableStateOf("") }
    var pBankName by remember { mutableStateOf("") }
    var pCredit by remember { mutableStateOf("0") }
    var pTemp by remember { mutableStateOf("0") }
    var pMerged by remember { mutableStateOf(false) }
    var showBankPicker by remember { mutableStateOf(false) }

    // 批量归池
    var batchPool by remember { mutableStateOf<Pool?>(null) }
    var batchSelected by remember { mutableStateOf<List<String>>(emptyList()) }
    // 提交互斥：web 这些操作都套了 showLoadingToast({forbidClick:true})，原生等价物是按钮 loading + 早退
    var busy by remember { mutableStateOf(false) }

    val vanToast = LocalVanToastController.current
    fun toast(m: String, type: VanToastType = VanToastType.Text) = vanToast.show(m, type)
    fun load() {
        scope.launch {
            when (val r = graph.card.list("credit")) { is ApiResult.Ok -> cards = r.data.orEmpty(); else -> Unit }
            when (val r = graph.pool.list()) { is ApiResult.Ok -> pools = r.data.orEmpty(); else -> Unit }
        }
    }

    LaunchedEffect(Unit) { load() }

    val sharedGroups = remember(cards, pools) {
        val map = LinkedHashMap<String, MutableList<Card>>()
        cards.forEach { c -> if (c.sharePoolId.isNotBlank()) map.getOrPut(c.sharePoolId) { mutableListOf() }.add(c) }
        map.map { (pid, list) -> SharedGroup(pid, pools.firstOrNull { it.id == pid }?.bankName.orEmpty(), list) }
    }
    val standalone = remember(cards) { cards.filter { it.sharePoolId.isBlank() } }
    LaunchedEffect(sharedGroups) { activeGroups = sharedGroups.map { it.poolId } }

    fun poolCardCount(pid: String) = cards.count { it.sharePoolId == pid }

    fun openCardEdit(card: Card) {
        editCard = card
        fCredit = Money.formatMoney(card.creditLimit)
        fTemp = Money.formatMoney(card.tempLimit)
        fRate = if (card.pointsRate == 0.0) "1" else card.pointsRate.toString()
        fPoolId = card.sharePoolId
        prevPoolId = card.sharePoolId
    }

    fun saveCard() {
        val card = editCard ?: return
        if (busy) return
        busy = true
        scope.launch {
            val body = NewCard(
                cardType = card.cardType, bankId = card.bankId, last4No = card.last4,
                cardBin = card.cardBin, openDate = card.openDate, expireDate = card.expireDate,
                alias = card.alias, cardLevel = card.cardLevel, mainSub = card.mainSub, cardOrg = card.cardOrg,
                cardLength = card.cardLength, cardImg = card.cardImg, currency = card.currency,
                tag = card.tag, remark = card.remark, color = card.color,
                creditLimit = fCredit.toDoubleOrNull() ?: 0.0,
                tempLimit = fTemp.toDoubleOrNull() ?: 0.0,
                pointsRate = fRate.toDoubleOrNull() ?: 1.0,
                billDay = card.billDay, annualFee = card.annualFee, feeFreeRule = card.feeFreeRule,
            )
            when (val r = graph.card.update(card.id, body)) {
                is ApiResult.Ok -> Unit
                is ApiResult.Fail -> { toast(r.message.ifBlank { "保存失败" }, VanToastType.Fail); return@launch }
                else -> { toast(r.failMessage("保存失败"), r.failToastType()); return@launch }
            }
            if (fPoolId != prevPoolId) {
                when (val r = graph.pool.assign(card.id, fPoolId.ifBlank { null })) {
                    is ApiResult.Fail -> { toast(r.message.ifBlank { "归池失败" }, VanToastType.Fail); return@launch }
                    else -> Unit
                }
            }
            toast("已保存并同步账单", VanToastType.Success)
            editCard = null
            load()
        }.invokeOnCompletion { busy = false }
    }

    fun savePool() {
        if (pId.isBlank() && pBankId.isBlank()) { toast("请选择银行"); return }
        if (busy) return
        busy = true
        scope.launch {
            val r = if (pId.isBlank()) graph.pool.create(
                pBankId, pBankName, pCredit.toDoubleOrNull() ?: 0.0, pTemp.toDoubleOrNull() ?: 0.0, pMerged,
            ) else graph.pool.update(
                pId, pBankId, pBankName, pCredit.toDoubleOrNull() ?: 0.0, pTemp.toDoubleOrNull() ?: 0.0, pMerged,
            )
            when (r) {
                is ApiResult.Ok -> { toast("已保存", VanToastType.Success); poolEditShow = false; load() }
                is ApiResult.Fail -> toast(r.message.ifBlank { "保存失败" }, VanToastType.Fail)
                else -> toast(r.failMessage("保存失败"), r.failToastType())
            }
        }.invokeOnCompletion { busy = false }
    }

    fun removePool(pool: Pool) {
        if (busy) return
        busy = true
        scope.launch {
            when (val r = graph.pool.delete(pool.id)) {
                is ApiResult.Ok -> { toast("已删除", VanToastType.Success); load() }
                else -> toast(r.failMessage("删除失败"), r.failToastType())
            }
        }.invokeOnCompletion { busy = false }
    }

    fun saveBatch() {
        val pool = batchPool ?: return
        if (busy) return
        busy = true
        scope.launch {
            var ok = 0
            for (cardId in batchSelected) {
                when (val r = graph.pool.assign(cardId, pool.id)) {
                    is ApiResult.Ok -> ok++
                    else -> { toast(r.failMessage("归池失败"), r.failToastType()); return@launch }
                }
            }
            toast("已批量归池", VanToastType.Success)
            batchPool = null
            load()
        }.invokeOnCompletion { busy = false }
    }

    val poolPickerOptions = remember(editCard, pools) {
        val cardBank = editCard?.bankId
        listOf("独立（不共享）" to "") + pools
            .filter { cardBank.isNullOrBlank() || it.bankId == cardBank }
            .map { "${it.bankName}（共 ${Money.formatMoney(it.totalCreditLimit)}）" to it.id }
    }
    val bankOptions = remember(cards) {
        cards.filter { it.bankId.isNotBlank() }.distinctBy { it.bankId }.map { it.bankName.ifBlank { "未命名银行" } to it.bankId }
    }
    val batchCards = remember(batchPool, cards) { if (batchPool?.bankId.isNullOrBlank()) emptyList() else cards.filter { it.bankId == batchPool?.bankId } }

    ScreenScaffold { inner ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().then(inner)) {
            item {
                // 两个页签
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("卡片额度", "共享额度池").forEachIndexed { i, t ->
                        val active = tab == i
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (active) colors.primary else colors.bgCard)
                                .clickable { tab = i }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { FText(t, 16f, FontWeight.Medium, if (active) Color.White else colors.textPrimary) }
                    }
                }
            }
            if (tab == 0) {
                if (cards.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { VanEmpty(description = "暂无信用卡") } }
                }
                items(sharedGroups.size, key = { sharedGroups[it].poolId }) { gi ->
                    val g = sharedGroups[gi]
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(8.dp)).background(colors.bgCard)) {
                        Row(Modifier.fillMaxWidth().clickable {
                            activeGroups = if (activeGroups.contains(g.poolId)) activeGroups - g.poolId else activeGroups + g.poolId
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            FText("${g.poolName.ifBlank { "共享池" }} · ${g.cards.size} 张卡", 16f, FontWeight.SemiBold, colors.textPrimary, modifier = Modifier.weight(1f))
                            FText(if (activeGroups.contains(g.poolId)) "收起" else "展开", 12f, color = colors.textTertiary)
                        }
                        if (activeGroups.contains(g.poolId)) {
                            g.cards.forEach { c -> CardLimitCell(c, "尾号 ${c.last4.ifBlank { "--" }}") { openCardEdit(c) } }
                        }
                    }
                }
                if (standalone.isNotEmpty()) {
                    item {
                        // web `.standalone-divider{ text-align:center; color:text-secondary; font-size:12px; padding: 8px 0 4px }`
                        // ⚠ 旧实现只给了 fillMaxWidth()，FText/BasicText **不会自动居中** → 文字贴在左边。
                        Box(
                            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) { FText("——— 独立额度 ———", 12f, color = colors.textSecondary) }
                    }
                    items(standalone.size, key = { "s_" + standalone[it].id }) { si ->
                        val c = standalone[si]
                        CardLimitCell(c, "尾号 ${c.last4.ifBlank { "--" }} · 独立额度", inset = true) { openCardEdit(c) }
                    }
                }
            } else {
                item {
                    Column(Modifier.padding(12.dp)) {
                        AppButton(text = "＋ 新建同银行共享额度池", onClick = {
                            pId = ""; pBankId = ""; pBankName = ""; pCredit = "0"; pTemp = "0"; pMerged = false
                            poolEditShow = true
                        }, type = AppButtonType.Primary, block = true, round = true)
                    }
                }
                if (pools.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { VanEmpty(description = "暂无共享池（同银行多卡可共享一个额度）") } }
                }
                items(pools.size, key = { pools[it].id }) { pi ->
                    val pool = pools[pi]
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(8.dp)).background(colors.bgCard).padding(12.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                FText(pool.bankName.ifBlank { "共享池" }, 16f, FontWeight.SemiBold, colors.textPrimary)
                                Spacer(Modifier.height(4.dp))
                                FText(
                                    "${poolCardCount(pool.id)} 张卡共享${if (pool.creditReportMerged) " · 信报合一" else ""}",
                                    12f, color = colors.textTertiary,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                FText("共享固额 ${Money.formatMoney(pool.totalCreditLimit)}", 12f, color = colors.textSecondary)
                                Spacer(Modifier.height(2.dp))
                                FText("共享临额 ${Money.formatMoney(pool.totalTempLimit)}", 12f, color = colors.textSecondary)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppButton(text = "编辑额度", onClick = {
                                pId = pool.id; pBankId = pool.bankId; pBankName = pool.bankName
                                pCredit = Money.formatMoney(pool.totalCreditLimit); pTemp = Money.formatMoney(pool.totalTempLimit)
                                pMerged = pool.creditReportMerged
                                poolEditShow = true
                            }, type = AppButtonType.Primary, size = AppButtonSize.Small, plain = true)
                            AppButton(text = "批量归池", onClick = {
                                batchPool = pool; batchSelected = batchCards.map { it.id }
                            }, type = AppButtonType.Success, size = AppButtonSize.Small, plain = true)
                            AppButton(text = "删除", onClick = { removePool(pool) }, type = AppButtonType.Danger, size = AppButtonSize.Small, plain = true, loading = busy)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) } // web `.section{ padding: 12px 0 }`（页面本身无 padding-bottom）
        }
    }

    // ===== 卡编辑弹层 =====
    if (editCard != null) {
        VanPopup(show = true, onDismissRequest = { editCard = null }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(colors.bgCard).padding(16.dp)) {
                // web `credit/LimitManage.vue:89`：`{{ cardNameOf(editingCard) }} · 额度设置`
                FText(
                    "${editCard?.let { cardNameOf(it) }.orEmpty()} · 额度设置",
                    18f, FontWeight.SemiBold, colors.textPrimary, modifier = Modifier.padding(bottom = 12.dp),
                )
                AppField(value = fCredit, onValueChange = { fCredit = it }, label = "固定额度", placeholder = "0.00")
                AppField(value = fTemp, onValueChange = { fTemp = it }, label = "临时额度", placeholder = "0.00")
                AppField(value = fRate, onValueChange = { fRate = it }, label = "积分倍率", placeholder = "1")
                Row(Modifier.fillMaxWidth().clickable { showPoolPicker = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FText("归入共享池", 16f, color = colors.textPrimary)
                    Spacer(Modifier.weight(1f))
                    FText(
                        if (fPoolId.isBlank()) "独立（不共享）" else (pools.firstOrNull { it.id == fPoolId }?.bankName ?: fPoolId),
                        16f, color = colors.textSecondary,
                    )
                    Spacer(Modifier.width(6.dp))
                    VanIcon("arrow", size = 14.sp, color = colors.textTertiary) // web `is-link` 的右箭头（旧用「›」字符）
                }
                Spacer(Modifier.height(8.dp))
                AppButton(text = "保存并同步账单", onClick = { saveCard() }, type = AppButtonType.Primary, block = true, round = true, loading = busy)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (showPoolPicker) {
        PickerSheet(
            show = true,
            columns = poolPickerOptions.map { it.first },
            selectedIndex = poolPickerOptions.indexOfFirst { it.second == fPoolId }.coerceAtLeast(0),
            title = "选择共享额度池",
            onConfirm = { idx -> fPoolId = poolPickerOptions.getOrNull(idx)?.second ?: ""; showPoolPicker = false },
            onDismiss = { showPoolPicker = false },
        )
    }
    if (showBankPicker) {
        PickerSheet(
            show = true,
            columns = bankOptions.map { it.first },
            selectedIndex = bankOptions.indexOfFirst { it.second == pBankId }.coerceAtLeast(0),
            title = "选择银行",
            onConfirm = { idx ->
                val sel = bankOptions.getOrNull(idx) ?: return@PickerSheet
                pBankId = sel.second; pBankName = sel.first
                // 新池默认额度 = 该银行所有卡固额/临额最大值（web maxLimitsOfBank 原文）
                val same = cards.filter { it.bankId == sel.second }
                pCredit = Money.formatMoney(same.maxOfOrNull { it.creditLimit } ?: 0.0)
                pTemp = Money.formatMoney(same.maxOfOrNull { it.tempLimit } ?: 0.0)
                showBankPicker = false
            },
            onDismiss = { showBankPicker = false },
        )
    }
    // ===== 池编辑弹层 =====
    if (poolEditShow) {
        VanPopup(show = true, onDismissRequest = { poolEditShow = false }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(colors.bgCard).padding(16.dp)) {
                FText(if (pId.isBlank()) "新建共享池" else "编辑共享池", 18f, FontWeight.SemiBold, colors.textPrimary, modifier = Modifier.padding(bottom = 12.dp))
                if (pId.isBlank()) {
                    Row(Modifier.fillMaxWidth().clickable { showBankPicker = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FText("银行名称", 16f, color = colors.textPrimary)
                        Spacer(Modifier.weight(1f))
                        FText(pBankName.ifBlank { "请选择" }, 16f, color = colors.textSecondary)
                        Spacer(Modifier.width(6.dp))
                        VanIcon("arrow", size = 14.sp, color = colors.textTertiary) // web `is-link` 的右箭头（旧用「›」字符）
                    }
                } else {
                    AppField(value = pBankName, onValueChange = { pBankName = it }, label = "银行名称", placeholder = "如 农业银行")
                }
                AppField(value = pCredit, onValueChange = { pCredit = it }, label = "共享固定额度", placeholder = "0.00")
                AppField(value = pTemp, onValueChange = { pTemp = it }, label = "共享临时额度", placeholder = "0.00")
                Row(Modifier.fillMaxWidth().clickable { pMerged = !pMerged }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        FText("信报合一", 16f, color = colors.textPrimary)
                        Spacer(Modifier.height(2.dp))
                        FText("同一银行多卡共享一个账户，可一次性结清共享额度", 12f, color = colors.textTertiary)
                    }
                    ToggleBox(pMerged) { pMerged = !pMerged }
                }
                Spacer(Modifier.height(8.dp))
                AppButton(text = "保存", onClick = { savePool() }, type = AppButtonType.Primary, block = true, round = true, loading = busy)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    // ===== 批量归池弹层 =====
    if (batchPool != null) {
        VanPopup(show = true, onDismissRequest = { batchPool = null }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(colors.bgCard).padding(16.dp)) {
                FText("批量归池", 18f, FontWeight.SemiBold, colors.textPrimary, modifier = Modifier.padding(bottom = 4.dp))
                FText(
                    "仅限同银行（${batchPool?.bankName.orEmpty()}）的信用卡，不同银行不能交叉归池。已勾选 ${batchSelected.size}/${batchCards.size}",
                    12f, color = colors.textTertiary,
                )
                Spacer(Modifier.height(10.dp))
                if (batchCards.isEmpty()) {
                    // web `<van-empty description="该银行暂无信用卡" />`（Vant 自带内边距，不要自绘间距）
                    VanEmpty(description = "该银行暂无信用卡")
                } else {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        batchCards.forEach { c ->
                            val checked = batchSelected.contains(c.id)
                            Row(Modifier.fillMaxWidth().clickable {
                                batchSelected = if (checked) batchSelected - c.id else batchSelected + c.id
                            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                // web 是 `<van-checkbox :model-value="…" shape="round" />`（圆形、20px）
                                // —— 旧实现是手绘圆 + 「✓」字符，视觉与底座不一致。
                                VanCheckbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        batchSelected = if (checked) batchSelected - c.id else batchSelected + c.id
                                    },
                                    shape = VanCheckboxShape.Round,
                                    iconSize = 20,
                                )
                                Spacer(Modifier.width(10.dp))
                                FText(cardNameOf(c), 14f, color = colors.textPrimary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                AppButton(
                    text = "归入该共享池（${batchSelected.size}）",
                    onClick = { saveBatch() },
                    type = AppButtonType.Primary, block = true, disabled = batchSelected.isEmpty(), loading = busy,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CardLimitCell(card: Card, label: String, inset: Boolean = false, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = if (inset) 16.dp else 0.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp)).background(colors.bgCard)
            .clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        FText(cardNameOf(card), 16f, FontWeight.Medium, colors.textPrimary)
        Spacer(Modifier.height(2.dp))
        FText(label, 12f, color = colors.textTertiary)
        Spacer(Modifier.height(4.dp))
        FText("固额 ${Money.formatMoney(card.creditLimit)}   临额 ${Money.formatMoney(card.tempLimit)}", 12f, color = colors.textSecondary)
    }
}

private fun cardNameOf(card: Card): String {
    val base = card.alias.ifBlank { card.bankName.ifBlank { "卡片" } }
    return if (card.last4.isNotBlank()) "$base(${card.last4})" else base
}

@Composable
private fun ToggleBox(checked: Boolean, onToggle: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier.width(44.dp).height(24.dp).clip(RoundedCornerShape(12.dp))
            .background(if (checked) colors.primary else colors.bgThird)
            .clickable { onToggle() },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(horizontal = 2.dp).size(20.dp).clip(CircleShape).background(Color.White))
    }
}
