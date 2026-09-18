package com.live.finance.ui.flow

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppCell
import com.live.finance.ui.common.BankIconView
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.currencySymbol
import com.live.finance.ui.common.flowCardBankIcon
import com.live.finance.ui.common.flowCardBankName
import com.live.finance.ui.common.flowCardText
import com.live.finance.ui.common.flowCategoryName
import com.live.finance.ui.common.flowPayMethod
import com.live.finance.ui.common.isInstallmentFlow
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.icon.VanIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 收支详情 —— 一比一复刻 web `views/Finance/flow/Detail.vue`（路由 `/finance/flow/:id`）。
 *
 * ① **渐变金额卡**（主色 → `--theme-primary-grad`、`padding 40/20`、居中白字）：
 *    `收入/支出` 14px + 币种胶囊（白 25% 底、radius 10）→ `±符号 + 币种符号 + formatMoney 原币金额` 36px 粗体；
 *    外币再加一行 `≈ ¥折算（汇率 x）`（**外币折算 = 金额 × 汇率 ÷ 100**，`formatMoney` 截断 4 位小数）
 * ② **信息组**（`van-cell-group inset` → margin 16 / radius 12）：分类（可点改）、币种、汇率（仅外币）、
 *    时间（`YYYY-MM-DD HH:mm:ss`）、交易方式（分期显示 `信用卡分期` 标签）、关联卡片（银行图标 18 + 银行名·尾号）、交易日期
 * ③ **备注组**：左右各 50%，>30 字折叠 2 行 + 「展开/收起」；空备注显示「暂无备注」并右对齐
 * ④ **操作区**：虚线按钮「冲正」+「修改备注」（**粉底虚线用 `--theme-warning`，主色虚线用 primary**）；
 *    还款记录只提示「本收支不计支出。如需撤销还款，请到还款记录编辑」、旧转账记录提示「暂不支持自动冲正」
 * ⑤ 备注弹窗（textarea 3 行 / maxlength 200 / 字数统计）、分类弹窗（3 列网格、选中绿框 + success 勾、**保存前二次确认**）
 *
 * 冲正判定（web `getReverseType`）：分期 → 无按钮；`pay_type=还款 + CATEGORY_REPAY` → `credit-repay`（只提示）；
 * `pay_type=转账` 有 `transfer_group_id` → `transfer`；无组 → `transfer-legacy`（只提示）；
 * `debit / virtual_balance / virtual_cash` → `debit`；`credit` → `credit-expense`。
 */
@Composable
fun FlowDetailScreen(nav: NavHostController, id: String) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val toast = LocalVanToastController.current
    val scope = rememberCoroutineScope()
    val graph = App.of(LocalContext.current).graph

    var detail by remember { mutableStateOf<FlowRow?>(null) }
    var loading by remember { mutableStateOf(true) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }

    // 备注
    var showRemarkPopup by remember { mutableStateOf(false) }
    var editRemark by remember { mutableStateOf("") }
    var remarkLoading by remember { mutableStateOf(false) }
    var remarkExpanded by remember { mutableStateOf(false) }

    // 分类
    var showCategoryPopup by remember { mutableStateOf(false) }
    var categoryList by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selectedCategoryId by remember { mutableStateOf("") }
    var categoryLoading by remember { mutableStateOf(false) }

    // 二次确认（冲正 / 改分类）
    var confirmReverse by remember { mutableStateOf(false) }
    var confirmCategory by remember { mutableStateOf(false) }

    // 拉卡片 + 银行（web loadCardList：Promise.all）
    LaunchedEffect(Unit) {
        cards = (graph.card.list() as? ApiResult.Ok)?.data.orEmpty()
        banks = (graph.category.list("bank") as? ApiResult.Ok)?.data.orEmpty()
    }

    LaunchedEffect(id) {
        loading = true
        val r = graph.flow.detail(id)
        val data = (r as? ApiResult.Ok)?.data
        if (data == null || data.id.isEmpty()) {
            // web：404 或空 → toast 后 replace 回列表页
            toast.show("记录不存在或已被删除")
            nav.popBackStack()
        } else {
            detail = data
        }
        loading = false
    }

    val row = detail
    val reverseType = row?.let { reverseTypeOf(it) }

    ScreenScaffold { inner ->
        Box(inner.background(colors.bgPage)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    VanLoading(size = 32.sp, vertical = true, text = "加载中...")
                }
                row == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    VanEmpty(description = "未找到相关记录")
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    // ① 金额卡
                    AmountCard(row, cards, banks)

                    // ② 信息组
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.bgCard),
                    ) {
                        AppCell(
                            title = "分类",
                            value = flowCategoryName(row),
                            isLink = true,
                            onClick = {
                                selectedCategoryId = row.categoryId
                                scope.launch {
                                    val type = if (row.isIncome) "income" else "expense"
                                    categoryList = (graph.category.list(type) as? ApiResult.Ok)?.data.orEmpty()
                                }
                                showCategoryPopup = true
                            },
                        )
                        AppCell(title = "币种", value = row.currency.ifEmpty { "CNY" })
                        if (row.currency.isNotEmpty() && row.currency != "CNY") {
                            AppCell(
                                title = "汇率",
                                value = if (row.exchangeRate > 0) fmtRate(row.exchangeRate) else "-",
                            )
                        }
                        AppCell(title = "时间", value = formatDateTime(row.createTime))
                        AppCell(
                            title = "交易方式",
                            value = if (isInstallmentFlow(row)) null else flowPayMethod(row),
                            rightIcon = if (isInstallmentFlow(row)) {
                                {
                                    Box(
                                        Modifier
                                            .background(Color(0x24FF976A), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 1.dp),
                                    ) { FText("信用卡分期", 12f, FontWeight.Medium, colors.warning) }
                                }
                            } else null,
                        )
                        AppCell(
                            title = "关联卡片",
                            rightIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BankIconView(
                                        src = flowCardBankIcon(row, cards, banks),
                                        name = flowCardBankName(row, cards, banks),
                                        size = 18.dp,
                                        modifier = Modifier.padding(end = 5.dp),
                                    )
                                    FText(flowCardText(row, cards, banks), 14f, FontWeight.Normal, colors.textSecondary)
                                }
                            },
                        )
                        AppCell(title = "交易日期", value = row.transDate.ifEmpty { "-" }, border = false)
                    }

                    // ③ 备注组
                    RemarkRow(
                        remark = row.remark,
                        expanded = remarkExpanded,
                        onToggle = { remarkExpanded = !remarkExpanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )

                    // ④ 操作按钮
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (reverseType != null && reverseType != "credit-repay" && reverseType != "transfer-legacy") {
                            DashedActionButton(
                                text = reverseBtnText(reverseType, row),
                                color = colors.warning,
                                modifier = Modifier.weight(1f),
                            ) { confirmReverse = true }
                        }
                        if (reverseType != "credit-repay") {
                            DashedActionButton(
                                text = "修改备注",
                                color = tokens.primary,
                                modifier = Modifier.weight(1f),
                            ) {
                                editRemark = row.remark
                                showRemarkPopup = true
                            }
                        }
                    }

                    // ⑤ 提示条（无冲正按钮时）
                    if (reverseType == "credit-repay") {
                        RepayHint("本收支不计支出。如需撤销还款，请到还款记录编辑", colors.warning)
                    }
                    if (reverseType == "transfer-legacy") {
                        RepayHint("此为旧版转账记录，暂不支持自动冲正。如需撤销请手动创建相反方向的收支记录", colors.warning)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    // 备注编辑弹窗
    if (showRemarkPopup && row != null) {
        VanPopup(show = true, onDismissRequest = { showRemarkPopup = false }, position = VanPopupPosition.Bottom, round = true) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                PopupHeader("修改备注") { showRemarkPopup = false }
                FText("请输入新的备注", 14f, FontWeight.Normal, colors.textTertiary, Modifier.padding(bottom = 12.dp))
                RemarkTextField(
                    value = editRemark,
                    onValueChange = { editRemark = it },
                    maxlength = 200,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    AppButton(text = "取消", size = AppButtonSize.Small, onClick = { showRemarkPopup = false })
                    AppButton(
                        text = "保存",
                        type = AppButtonType.Primary,
                        size = AppButtonSize.Small,
                        loading = remarkLoading,
                        onClick = {
                            scope.launch {
                                remarkLoading = true
                                val r = graph.flow.updateRemark(id, editRemark)
                                remarkLoading = false
                                if (r is ApiResult.Ok) {
                                    detail = row.copy(remark = editRemark)
                                    showRemarkPopup = false
                                    toast.success("保存成功")
                                } else {
                                    toast.show((r as? ApiResult.Fail)?.message?.ifBlank { "保存失败" } ?: "保存失败")
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    // 分类编辑弹窗
    if (showCategoryPopup && row != null) {
        val dirText = if (row.isIncome) "收入分类" else "支出分类"
        VanPopup(show = true, onDismissRequest = { showCategoryPopup = false }, position = VanPopupPosition.Bottom, round = true) {
            Column(Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 620.dp)) {
                PopupHeader("选择$dirText") { showCategoryPopup = false }
                FText("请选择新的$dirText", 14f, FontWeight.Normal, colors.textTertiary, Modifier.padding(bottom = 4.dp))
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    categoryList.chunked(3).forEach { rowCats ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowCats.forEach { cat ->
                                CategoryTile(
                                    name = cat.name,
                                    active = selectedCategoryId == cat.id,
                                    modifier = Modifier.weight(1f),
                                ) { selectedCategoryId = cat.id }
                            }
                            repeat(3 - rowCats.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    AppButton(text = "取消", size = AppButtonSize.Small, onClick = { showCategoryPopup = false })
                    AppButton(
                        text = "保存",
                        type = AppButtonType.Primary,
                        size = AppButtonSize.Small,
                        loading = categoryLoading,
                        onClick = {
                            when {
                                selectedCategoryId.isEmpty() -> toast.show("请选择分类")
                                selectedCategoryId == row.categoryId -> showCategoryPopup = false
                                else -> confirmCategory = true
                            }
                        },
                    )
                }
            }
        }
    }

    // 冲正确认
    VanConfirmDialog(
        show = confirmReverse && row != null,
        title = "确认冲正",
        message = row?.let { "确定要执行${reverseBtnText(reverseType ?: "", it)}吗？" } ?: "",
        onConfirm = {
            confirmReverse = false
            val r = row ?: return@VanConfirmDialog
            val type = reverseType ?: return@VanConfirmDialog
            scope.launch {
                val res = when (type) {
                    "debit" -> graph.flow.reverseDebit(r.id)
                    "credit-expense" -> graph.flow.reverseCreditExpense(r.id)
                    "transfer" -> graph.flow.reverseTransfer(r.id)
                    else -> ApiResult.Ok("", "")
                }
                when (res) {
                    is ApiResult.Ok -> {
                        toast.show(res.data?.takeIf { it.isNotBlank() } ?: "冲正成功")
                        nav.popBackStack()          // web：toast 后 router.push('/finance/flow')
                    }
                    is ApiResult.Fail -> toast.show(res.message.ifBlank { "冲正失败" })
                    else -> toast.show("冲正失败")
                }
            }
        },
        onClose = { confirmReverse = false },
    )

    // 改分类二次确认
    val targetCat = categoryList.firstOrNull { it.id == selectedCategoryId }
    VanConfirmDialog(
        show = confirmCategory && row != null,
        title = "确认修改",
        message = row?.let { "确定将分类从「${flowCategoryName(it)}」改为「${targetCat?.name ?: ""}」吗？" } ?: "",
        onConfirm = {
            confirmCategory = false
            val r = row ?: return@VanConfirmDialog
            scope.launch {
                categoryLoading = true
                val res = graph.flow.updateCategory(r.id, selectedCategoryId, r.accountType == "credit")
                categoryLoading = false
                if (res is ApiResult.Ok) {
                    detail = r.copy(categoryId = selectedCategoryId, categoryName = targetCat?.name ?: "未知分类")
                    showCategoryPopup = false
                    toast.success("分类已更新")
                } else {
                    toast.show((res as? ApiResult.Fail)?.message?.ifBlank { "修改失败" } ?: "修改失败")
                }
            }
        },
        onClose = { confirmCategory = false },
    )
}

// ───────────────────────── 页内部件 ─────────────────────────

/** web `.amount-card`：主色渐变、`padding 40/20`、居中白字。 */
@Composable
private fun AmountCard(row: FlowRow, cards: List<Card>, banks: List<Category>) {
    val tokens = LocalAppTokens.current
    val income = row.isIncome
    val currency = row.currency.ifEmpty { "CNY" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(tokens.primary, tokens.grad)))   // web 135deg
            .padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            FText(if (income) "收入" else "支出", 14f, FontWeight.Normal, Color.White.copy(alpha = 0.9f))
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) { FText(currency, 12f, FontWeight.Normal, Color.White) }
        }
        FText(
            (if (income) "+" else "-") + currencySymbol(currency) + Money.formatMoney(row.amount),
            36f, FontWeight.Bold, Color.White,
        )
        // 外币折算行：≈ ¥金额（汇率 x）
        if (currency != "CNY") {
            val cny = if (row.exchangeRate > 0) row.amount * row.exchangeRate / 100 else row.amount
            FText(
                "≈ ¥" + Money.formatMoney(cny) + "（汇率 " + fmtRate(row.exchangeRate) + "）",
                12f, FontWeight.Normal, Color.White.copy(alpha = 0.85f),
                Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** web `.remark-row`：标签/内容各 50%，内容 >30 字折叠 2 行并有「展开/收起」。 */
@Composable
private fun RemarkRow(remark: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val canTruncate = remark.length > 30
    Column(modifier.background(colors.bgCard)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) { FText("备注", 14f, FontWeight.Normal, colors.textPrimary) }
            Column(Modifier.weight(1f)) {
                if (remark.isEmpty()) {
                    // web `.remark-value.is-empty { text-align: right }`
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        FText("暂无备注", 14f, FontWeight.Normal, colors.textSecondary)
                    }
                } else {
                    BasicText(
                        remark,
                        style = TextStyle(color = colors.textSecondary, fontSize = 14.sp),
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (canTruncate) {
                        FText(
                            if (expanded) "收起" else "展开", 12f, FontWeight.Normal, tokens.primary,
                            Modifier.padding(top = 4.dp).clickable(onClick = onToggle),
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

/** 备注输入框（web `app-field type=textarea rows=3 maxlength=200 show-word-limit`）。 */
@Composable
private fun RemarkTextField(value: String, onValueChange: (String) -> Unit, maxlength: Int) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    Column(Modifier.fillMaxWidth().background(colors.bgCard)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 60.dp)) {          // rows=3
                if (value.isEmpty()) {
                    FText("请输入备注", 14f, FontWeight.Normal, colors.textTertiary)
                }
                BasicTextField(
                    value = value,
                    onValueChange = { onValueChange(if (it.length > maxlength) it.take(maxlength) else it) },
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(tokens.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                FText("${value.length}/$maxlength", 12f, FontWeight.Normal, colors.textTertiary)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))   // app-field 通栏底边线
    }
}

/** 弹窗头（web `.popup-header`：16/600 左 + cross 20px 三级色右）。 */
@Composable
private fun PopupHeader(title: String, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(title, 16f, FontWeight.SemiBold, colors.textPrimary)
        VanIcon(name = "cross", size = 20.sp, color = colors.textTertiary, onClick = onClose)
    }
}

/** web `.category-item`：3 列网格项（选中 = 绿框 + `#f0fff5` 底 + success 勾）。 */
@Composable
private fun CategoryTile(name: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val green = Color(0xFF07C160)
    Box(
        modifier = modifier
            .heightIn(min = 64.dp)
            .background(if (active) Color(0xFFF0FFF5) else Color.Transparent, RoundedCornerShape(10.dp))
            .border(1.dp, if (active) green else colors.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        FText(name, 12f, FontWeight.Normal, colors.textPrimary)
        if (active) {
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                VanIcon(name = "success", size = 14.sp, color = colors.success)
            }
        }
    }
}

/** web `.repay-hint`（`--van-orange-bg` 未定义 → 实际透明底）。 */
@Composable
private fun RepayHint(text: String, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VanIcon(name = "info-o", size = 16.sp, color = color)
        Spacer(Modifier.size(6.dp))
        FText(text, 13f, FontWeight.Normal, color)
    }
}

/** web：`app-button round` + 内联 `2px dashed` 描边 + 透明底 + 同色字（app-button normal = 44 高 / 15px / 500）。 */
@Composable
private fun DashedActionButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Transparent)
            .flowDashedBorder(color, 5.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FText(text, 15f, FontWeight.Medium, color)
    }
}

// ───────────────────────── 纯函数 ─────────────────────────

/** web `formatDateTime`：毫秒时间戳 → `YYYY-MM-DD HH:mm:ss`。 */
private fun formatDateTime(ts: String): String {
    if (ts.isEmpty()) return "-"
    val ms = ts.toLongOrNull() ?: return ts.take(16)
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))
}

/** 汇率展示（web `{{ detail.exchange_rate || "-" }}`）。 */
private fun fmtRate(rate: Double): String =
    if (rate == 0.0) "-" else rate.toString().removeSuffix(".0")

/** web `getReverseType`。 */
private fun reverseTypeOf(item: FlowRow): String? {
    if (item.categoryId == "installment") return null                              // 分期入账不可前端冲正
    if (item.payType == "还款" && item.categoryId == "CATEGORY_REPAY") return "credit-repay"
    if (item.payType == "转账" && item.transferGroupId.isNotEmpty()) return "transfer"
    if (item.payType == "转账") return "transfer-legacy"
    return when (item.accountType) {
        "debit", "virtual_balance", "virtual_cash" -> "debit"
        "credit" -> "credit-expense"
        else -> null
    }
}

/** web `getReverseBtnText`。 */
private fun reverseBtnText(type: String, item: FlowRow): String = when {
    type == "debit" -> when (item.accountType) {
        "virtual_balance" -> "余额冲正"
        "virtual_cash" -> "现金冲正"
        else -> "借记卡冲正"
    }
    type == "credit-expense" -> "消费冲正"
    type == "credit-repay" -> "还款撤销"
    type == "transfer" -> "转账冲正"
    else -> ""
}
