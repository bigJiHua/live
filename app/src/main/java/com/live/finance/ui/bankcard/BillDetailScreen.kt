package com.live.finance.ui.bankcard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.core.nav.Routes
import com.live.finance.data.model.Bill
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppCell
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagType
import com.live.vant.feedback.VanConfirmDialog
import kotlinx.coroutines.launch

/**
 * 信用卡账单详情 —— 一比一复刻 web `views/BankCard/bill/Detail.vue`。
 *
 * 实时计算（原文）：
 * - `isOverdue` = `need_repay>0 && repay_status !== '已还清' && is_overdue`；
 * - `isPaidOff` = `repay_status === '已还清' || Number(need_repay) <= 0` → 为真时隐藏「添加还款记录 / 删除账单」。
 */
@Composable
fun BillDetailScreen(nav: NavHostController, billId: String = "") {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val graph = App.of(context).graph
    val scope = rememberCoroutineScope()

    var bill by remember { mutableStateOf<Bill?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showDelete by remember { mutableStateOf(false) }

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    fun load() {
        scope.launch {
            loading = true
            when (val r = graph.bill.detail(billId)) {
                is ApiResult.Ok -> bill = r.data
                is ApiResult.Fail -> toast(r.message.ifBlank { "加载失败" })
                else -> Unit
            }
            loading = false
        }
    }

    LaunchedEffect(billId) {
        if (billId.isBlank()) { toast("缺少账单ID"); nav.popBackStack() } else load()
    }

    val b = bill
    val needRepay = b?.needRepay ?: 0.0
    val isOverdue = b != null && needRepay > 0 && b.repayStatus != "已还清" && b.isOverdue
    val displayOverdueDays = if (isOverdue) (b?.overdueDays ?: 0) else 0
    val isPaidOff = b != null && (b.repayStatus == "已还清" || needRepay <= 0)

    Box(Modifier.fillMaxSize().background(colors.bgPage)) {
        ScreenScaffold { inner ->
            if (loading) {
                Box(Modifier.fillMaxSize().then(inner), contentAlignment = Alignment.Center) { VanLoading() }
            } else if (b != null) {
                Column(Modifier.fillMaxSize().then(inner)) {
                    BankTopBar(title = "信用卡账单详情", onBack = { nav.popBackStack() })
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        // 卡片信息
                        DetailSection("卡片信息") {
                            AppCell(title = "卡片别名", value = b.cardAlias.ifBlank { "-" })
                            AppCell(title = "卡号后4位", value = "**** ${b.cardLast4}")
                            AppCell(title = "信用额度", value = "¥${Money.formatMoney(b.creditLimit)}")
                            AppCell(title = "可用额度", value = "¥${Money.formatMoney(b.availLimit)}")
                            AppCell(title = "已用额度", value = "¥${Money.formatMoney(b.usedLimit)}", border = false)
                        }
                        // 账单周期
                        DetailSection("账单周期") {
                            AppCell(title = "账单开始", value = dateOnly(b.billStartDate))
                            AppCell(title = "账单结束", value = dateOnly(b.billEndDate), border = false)
                        }
                        // 账单金额
                        DetailSection("账单金额") {
                            AppCell(title = "本期账单", value = "¥${Money.formatMoney(b.billAmount)}")
                            AppCell(title = "最低还款", value = "¥${Money.formatMoney(b.minRepay)}")
                            AppCell(title = "已还金额", value = "¥${Money.formatMoney(b.repaid)}")
                            AppCell(title = "待还金额", value = "¥${Money.formatMoney(b.needRepay)}", valueColor = colors.danger, border = false)
                        }
                        // 附加信息
                        if (b.tempLimit != 0.0 || b.points != 0.0) {
                            DetailSection("附加信息") {
                                if (b.tempLimit != 0.0) AppCell(title = "临时额度", value = "¥${Money.formatMoney(b.tempLimit)}")
                                if (b.points != 0.0) AppCell(title = "积分", value = if (b.points % 1.0 == 0.0) b.points.toLong().toString() else b.points.toString())
                                if (b.pointsExpire.isNotBlank()) AppCell(title = "积分到期", value = dateOnly(b.pointsExpire), border = false)
                            }
                        }
                        // 还款状态
                        DetailSection("还款状态") {
                            AppCell(title = "还款状态", value = b.repayStatus.ifBlank { "未还" })
                            TagCell(
                                title = "是否逾期",
                                tagText = if (isOverdue) "逾期 $displayOverdueDays 天" else "正常",
                                tagType = if (isOverdue) VanTagType.Danger else VanTagType.Success,
                                border = false,
                            )
                        }
                        // 操作按钮
                        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 0.dp)) {
                            Spacer(Modifier.height(24.dp))
                            AppButton(
                                text = "查看流水明细", onClick = { nav.navigate("${Routes.BILL_LEDGER}?id=${b.id}") },
                                type = AppButtonType.Primary, plain = true, block = true, round = true,
                            )
                            if (!isPaidOff) {
                                Spacer(Modifier.height(12.dp))
                                AppButton(
                                    text = "添加还款记录", onClick = { nav.navigate("${Routes.REPAY_ADD}?billId=${b.id}") },
                                    type = AppButtonType.Primary, block = true, round = true,
                                )
                                Spacer(Modifier.height(12.dp))
                                AppButton(
                                    text = "删除账单", onClick = { showDelete = true },
                                    type = AppButtonType.Danger, plain = true, block = true, round = true,
                                )
                            }
                        }
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }

        VanConfirmDialog(
            show = showDelete,
            title = "删除确认",
            message = "确定要删除这条账单吗？",
            onConfirm = {
                showDelete = false
                scope.launch {
                    when (val r = graph.bill.delete(billId)) {
                        is ApiResult.Ok -> { toast("删除成功"); nav.popBackStack() }
                        is ApiResult.Fail -> toast(r.message.ifBlank { "删除失败" })
                        else -> toast("删除失败")
                    }
                }
            },
            onCancel = { showDelete = false },
            onClose = { showDelete = false },
        )
    }
}

/** 分区：小标题 + 圆角卡片包裹的 cell 组。 */
@Composable
fun DetailSection(title: String, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        FText(title, 14f, color = colors.textTertiary, modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp)).background(colors.bgCard)) {
            content()
        }
    }
}

fun dateOnly(s: String): String = if (s.isBlank()) "-" else s.split(" ").firstOrNull().orEmpty().ifBlank { "-" }

/** `app-cell` + `#value` 槽放 tag（对应 web「是否逾期」行）。 */
@Composable
private fun TagCell(title: String, tagText: String, tagType: VanTagType, border: Boolean = true) {
    val colors = LocalAppColors.current
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(title, 14f, color = colors.textPrimary, modifier = Modifier.weight(1f))
        VanTag(text = tagText, type = tagType)
    }
    if (border) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}
