package com.live.finance.ui.bankcard

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiGetCache
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.core.net.failToastType
import com.live.finance.core.nav.Routes
import com.live.finance.data.model.Card
import com.live.finance.data.model.Repay
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagType
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.rememberVanPullRefreshState
import com.live.vant.form.VanPicker
import com.live.vant.form.vanPickerColumnsOf
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanToastType

/**
 * 还款记录列表 —— 一比一复刻 web `views/BankCard/repay/List.vue`。
 * 撤销按**来源流水 account_id**（`POST /account/credit/{accountId}/reverse/repay`），非记录 id。
 */
/**
 * keep-alive 式数据缓存（与 `BillListKeepAlive` 同一套：进子页返回时先落旧数据再静默刷新）。
 * key = 「选中卡片 + 账单月份」；进程内缓存，不做磁盘持久化。
 */
private object RepayListKeepAlive {
    data class Key(val cardId: String?, val billMonth: String)
    data class Snapshot(val rows: List<Repay>, val cards: List<Card>)

    private val map = LinkedHashMap<Key, Snapshot>()

    @Synchronized
    fun get(key: Key): Snapshot? = map[key]

    @Synchronized
    fun put(key: Key, snap: Snapshot) {
        map[key] = snap
        while (map.size > 12) map.remove(map.keys.first())
    }
}

@Composable
fun RepayListScreen(nav: NavHostController) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val graph = App.of(context).graph
    val scope = rememberCoroutineScope()
    // keep-alive：滚动位置保留（从「还款详情」返回不跳回顶部）
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val refreshState = rememberVanPullRefreshState()

    var rows by remember { mutableStateOf<List<Repay>>(emptyList()) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // ⚠ 年月 / 选中卡片必须 `rememberSaveable`：本页会跳「还款详情 / 再次还款」子页，
    //   返回时普通 `remember` 会随组合销毁丢失 ⇒ 跳回当月（与账单列表页同一个坑，2026-09-20 修）。
    var selectedCardId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCardName by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf(LocalDate.now().year) }
    var month by rememberSaveable { mutableStateOf(LocalDate.now().monthValue) }
    var showCardPicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var revokeTarget by remember { mutableStateOf<Repay?>(null) }

    val vanToast = LocalVanToastController.current
    fun toast(m: String, type: VanToastType = VanToastType.Text) = vanToast.show(m, type)
    /** keep-alive 缓存 key（选中卡片 + 账单月份）。⚠ 局部函数必须先声明后使用 ⇒ 放在 `load` 之前。 */
    fun currentKey() = RepayListKeepAlive.Key(selectedCardId, "%04d-%02d".format(year, month))

    /** @param silent true = 不弹全屏 loading 遮罩（keep-alive 返回后的静默刷新 / 下拉刷新）。 */
    /** @return 本次请求的 Job —— 下拉刷新要 join() 完再收刷新头。 */
    fun load(silent: Boolean = false): Job {
        val billMonth = "%04d-%02d".format(year, month)
        return scope.launch {
            if (!silent) loading = true
            when (val r = graph.repay.list(selectedCardId, billMonth)) {
                is ApiResult.Ok -> { rows = r.data.orEmpty(); RepayListKeepAlive.put(currentKey(), RepayListKeepAlive.Snapshot(rows, cards)) }
                // 刷新失败保留旧记录：清空会让整页变空
                else -> toast(r.failMessage("加载失败"), r.failToastType())
            }
            if (!silent) loading = false
        }
    }

    LaunchedEffect(Unit) {
        when (val r = graph.card.list("credit")) { is ApiResult.Ok -> cards = r.data.orEmpty(); else -> Unit }
        // keep-alive：命中缓存 ⇒ 先落旧数据（不闪遮罩）再静默刷新；未命中 ⇒ 正常带 loading 加载。
        val snap = RepayListKeepAlive.get(currentKey())
        if (snap != null) {
            rows = snap.rows
            if (snap.cards.isNotEmpty()) cards = snap.cards
            loading = false
            load(silent = true)
        } else {
            load()
        }
    }

    val cardColumns = remember(cards) {
        listOf("全部卡片") + cards.map { c ->
            "${c.alias.ifBlank { c.bankName.ifBlank { "卡片" } }} **** ${c.last4.ifBlank { "****" }}"
        }
    }
    val years = remember { ((LocalDate.now().year - 10)..(LocalDate.now().year + 10)).map { "${it}年" } }
    val months = remember { (1..12).map { "${it}月" } }

    VanPullRefresh(
        state = refreshState,
        isChildAtTop = { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 },
        // 下拉刷新不该弹全屏遮罩（与账单列表页同一处理）⇒ silent
        onRefresh = { ApiGetCache.clear(); scope.launch { load(silent = true).join(); refreshState.finishRefresh() } },
    ) {
        ScreenScaffold { inner ->
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().then(inner)) {
                item {
                    // web `.filter-section{ padding: 12px 0 }` + `van-cell-group inset`：
                    //   **一张白卡里上下叠两个 `app-field`**（label 在上、值在下、中间一条底边线），
                    //   不是并排两列 + 竖分隔线（旧实现结构不符）。
                    FormSection(title = null) {
                        ReadonlyRow("选择卡片", selectedCardName, "全部卡片", onClick = { showCardPicker = true })
                        ReadonlyRow("账单月份", "${year}年${month}月", "请选择月份", onClick = { showMonthPicker = true }, border = false)
                    }
                    Spacer(Modifier.height(12.dp)) // .filter-section 的下内边距
                }
                if (rows.isNotEmpty()) {
                    items(rows.size, key = { rows[it].id }) { i ->
                        RepayCard(
                            item = rows[i],
                            onDetail = { nav.navigate("${Routes.REPAY_DETAIL}?id=${rows[i].id}") },
                            onAgain = { nav.navigate("${Routes.REPAY_ADD}?billId=${rows[i].billId}") },
                            onRevoke = { revokeTarget = rows[i] },
                        )
                    }
                } else if (!loading) {
                    // web 直接 `<van-empty description="暂无还款记录" />`（Vant 自带内边距），不要再套间距
                    item { VanEmpty(description = "暂无还款记录") }
                }
                item { Spacer(Modifier.height(20.dp)) } // .page-repay-list{ padding-bottom: 20px }
            }

            if (loading) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { VanLoading() }
            }
        }
    }

    if (showCardPicker) {
        VanPopup(show = true, onDismissRequest = { showCardPicker = false }) {
            VanPicker(
                columns = vanPickerColumnsOf(cardColumns),
                value = listOf(if (selectedCardId == null) 0 else cards.indexOfFirst { it.id == selectedCardId } + 1),
                onConfirm = { sel, _ ->
                    val idx = sel.firstOrNull() ?: 0
                    if (idx <= 0) { selectedCardId = null; selectedCardName = "" }
                    else {
                        selectedCardId = cards.getOrNull(idx - 1)?.id
                        selectedCardName = cardColumns.getOrNull(idx) ?: ""
                    }
                    showCardPicker = false
                    load()
                },
                onCancel = { showCardPicker = false },
            )
        }
    }
    if (showMonthPicker) {
        VanPopup(show = true, onDismissRequest = { showMonthPicker = false }) {
            VanPicker(
                columns = vanPickerColumnsOf(years, months),
                value = listOf((year - (LocalDate.now().year - 10)).coerceIn(0, years.size - 1), month - 1),
                title = "选择月份",
                onConfirm = { sel, _ ->
                    year = LocalDate.now().year - 10 + (sel.getOrNull(0) ?: 0)
                    month = (sel.getOrNull(1) ?: 0) + 1
                    showMonthPicker = false
                    load()
                },
                onCancel = { showMonthPicker = false },
            )
        }
    }

    val target = revokeTarget
    if (target != null) {
        VanConfirmDialog(
            show = true,
            title = "确认撤销",
            message = "确定要撤销这笔 ¥${Money.formatMoney(target.amount)} 的还款记录吗？",
            onConfirm = {
                revokeTarget = null
                scope.launch {
                    if (target.accountId.isBlank()) { toast("该记录无法撤销", VanToastType.Fail); return@launch }
                    when (val r = graph.repay.reverse(target.accountId)) {
                        is ApiResult.Ok -> { toast("撤销成功", VanToastType.Success); load() }
                        is ApiResult.Fail -> toast(r.message.ifBlank { "撤销失败" }, VanToastType.Fail)
                        else -> toast(r.failMessage("撤销失败"), r.failToastType())
                    }
                }
            },
            onCancel = { revokeTarget = null },
            onClose = { revokeTarget = null },
        )
    }
}

@Composable
private fun FilterField(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(modifier.clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        FText(label, 14f, color = colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FText(value, 16f, color = colors.textPrimary, modifier = Modifier.weight(1f))
            VanIcon("arrow", size = 14.sp, color = colors.textTertiary)
        }
    }
}

/** 单条还款记录卡（web `.repay-card`）。 */
@Composable
private fun RepayCard(item: Repay, onDetail: () -> Unit, onAgain: () -> Unit, onRevoke: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        // web `.repay-card{ border-radius:12px; padding:16px; margin-bottom:12px;
        //   box-shadow: 0 2px 8px rgba(0,0,0,.06) }`（旧实现缺投影、纵向 margin 只有 6）
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)
            .shadow(4.dp, RoundedCornerShape(12.dp), clip = false,
                ambientColor = Color(0x0F000000), spotColor = Color(0x0F000000))
            .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).clickable { onDetail() }.padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FText(item.cardLabel, 18f, FontWeight.SemiBold, colors.textPrimary)
                Spacer(Modifier.height(2.dp))
                FText("本次记录还款日期 ${monthDayLabel(item)}", 14f, FontWeight.Medium, colors.primary)
            }
            VanTag(text = "已还款", type = VanTagType.Success)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("账单金额", "¥${Money.formatMoney(item.billAmount)}", colors.textPrimary)
                InfoRow("已还金额", "¥${Money.formatMoney(item.amount)}", colors.textPrimary)
                InfoRow("待还金额", "¥${Money.formatMoney(item.billNeedRepay)}", colors.danger)
            }
            Column(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(colors.primary, colors.primaryGrad)))
                    .padding(horizontal = 16.dp, vertical = 8.dp).width(100.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FText("本次还款", 12f, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(4.dp))
                FText("¥${Money.formatMoney(item.amount)}", 18f, FontWeight.Bold, Color.White)
            }
        }
        // web `.repay-footer{ padding-top:12px; border: 1px solid var(--theme-border) }` —— 源码写的是**四边框**
        //   （`.repay-footer` 是卡片最后一行，四边框会画成一个小方框，语义上更像是想写 `border-top`）。
        //   2026-09-19 负责人拍板：**只改安卓、web 不同步** ⇒ 原生按「上分隔线」渲染，属**有意偏离 web**，
        //   已记入构造书 §5.6 差异表；不要再「照抄回去」，也不要顺手去改 web。
        //   顺序也照 web：线在**上**、`padding-top:12px` 在线**下面**（原生原来是 12dp 留白跑到线上方了）。
        //   （用 1dp Box 而不是 `Modifier.border(..., topStart = …)`：本工程这版 Compose Foundation 的
        //    `border` 只有 `(Dp, Color, Shape)` / `(BorderStroke, Shape)` 两个重载，没有四角布尔参数。）
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                VanIcon("card", size = 14.sp, color = colors.textTertiary)
                Spacer(Modifier.width(4.dp))
                FText(repayMethodText(item.method), 12f, color = colors.textTertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(text = "还款撤销", onClick = onRevoke, type = AppButtonType.Warning, size = AppButtonSize.Small, round = true)
                AppButton(text = "再次还款", onClick = onAgain, type = AppButtonType.Default, size = AppButtonSize.Small, round = true, plain = true)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color) {
    val colors = LocalAppColors.current
    Row {
        FText(label, 14f, color = colors.textTertiary)
        Spacer(Modifier.weight(1f))
        FText(value, 16f, FontWeight.Medium, valueColor)
    }
}

/** web `formatRepayMethod`。 */
internal fun repayMethodText(method: String): String = when (method) {
    "cash" -> "现金还款"
    "balance" -> "余额还款"
    "bank_card" -> "借记卡还款"
    "card" -> "卡还款"
    else -> method.ifBlank { "还款" }
}

private fun monthDayLabel(item: Repay): String {
    val d = item.time.take(10)
    if (d.length < 10) return item.dateLabel
    val m = d.substring(5, 7).toIntOrNull() ?: return item.dateLabel
    val day = d.substring(8, 10).toIntOrNull() ?: return item.dateLabel
    return "${m}月${day}日"
}
