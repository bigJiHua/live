package com.live.finance.ui.bankcard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.core.AppConfig
import com.live.finance.data.model.Category
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppField
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanImage
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanPicker
import com.live.vant.form.VanSwitch
import com.live.vant.form.vanPickerColumnsOf
import com.live.vant.icon.VanIcon

// =====================================================================================
// 常量表（原文照抄 web `card/Add.vue` / `card/Edit.vue` / `credit/AddFull.vue`）
// =====================================================================================

/** 三页共用的 12 个颜色选项（原文）。 */
val CARD_COLOR_OPTIONS = listOf(
    "#0052cc" to "蓝色", "#07c160" to "绿色", "#ee0a24" to "红色", "#ff976a" to "橙色",
    "#7232dd" to "紫色", "#1a1a1a" to "黑色", "#c01d24" to "深红", "#1b4f9a" to "深蓝",
    "#f0c987" to "金色", "#ffffff" to "白色", "#9c27b0" to "紫色", "#00bcd4" to "青色",
)

val CARD_ORG_COLUMNS = listOf("银联", "万事达", "Visa", "运通", "大莱", "JCB")
val MAIN_SUB_COLUMNS = listOf("主卡", "副卡")
val STATUS_COLUMNS = listOf("正常", "挂失", "注销")

/** ⚠ 卡等级选项三页**不一致**（Add 用 `黑金卡`，AddFull 用 `黑卡/无限卡`）。 */
val CARD_LEVEL_ADD = listOf("普卡", "金卡", "白金卡", "钻石卡", "黑金卡")
val CARD_LEVEL_FULL = listOf("普卡", "金卡", "白金卡", "钻石卡", "黑卡", "无限卡")

val CURRENCY_COLUMNS = listOf("CNY" to "CNY 人民币", "USD" to "USD 美元", "HKD" to "HKD 港币")
val CARD_LENGTH_COLUMNS = listOf("15" to "15位", "16" to "16位", "19" to "19位")

/** 卡组织 → 有效期年数（Add.vue 原文）。 */
val CARD_ORG_VALIDITY = mapOf("银联" to 10, "万事达" to 8, "Visa" to 5, "运通" to 5, "大莱" to 5, "JCB" to 5)

/** 卡组织 → BIN 前缀 + 需补录位数（Add.vue 原文）。 */
val CARD_ORG_BIN_PREFIX = mapOf(
    "银联" to ("62" to 4), "万事达" to ("53" to 4), "Visa" to ("4" to 5),
    "运通" to ("37" to 4), "大莱" to ("36" to 4), "JCB" to ("35" to 4),
)

/** 卡组织 → 卡号长度（Add/Edit 原文，两页一致）。 */
val CARD_ORG_LENGTH = mapOf(
    "银联" to 19, "万事达" to 16, "Visa" to 16, "运通" to 15, "大莱" to 16, "JCB" to 16,
)

// =====================================================================================
// 行组件（度量复刻 `app-field`：padding 10/16、label 13 text-secondary + 下距 6、值 14、底 1px 通栏线）
// =====================================================================================

/** 分区（web `van-cell-group inset`：左右 16 外边距 + 圆角 8 + 卡底）。 */
@Composable
fun FormSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    requiredHint: Boolean = false,
    titleColor: Color? = null,
    titleIcon: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp)) {
        if (title != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (titleIcon != null) {
                    VanIcon(titleIcon, size = 14.sp, color = titleColor ?: colors.textPrimary)
                    Spacer(Modifier.width(4.dp))
                }
                FText(title, 14f, FontWeight.Medium, titleColor ?: colors.textPrimary, Modifier.weight(1f))
                if (requiredHint) FText("*为必填项", 11f, color = colors.danger)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.bgCard)) { content() }
    }
}

/** 行尾 1px 通栏分隔线。 */
@Composable
fun RowDivider(show: Boolean) {
    if (show) {
        val colors = LocalAppColors.current
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

/** 只读选择行（label 在上、值 + 箭头在下）。 */
@Composable
fun ReadonlyRow(
    label: String,
    value: String,
    placeholder: String = "请选择",
    onClick: (() -> Unit)? = null,
    suffix: String? = null,
    arrow: Boolean = true,
    enabled: Boolean = true,
    border: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable { onClick() } else Modifier),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (label.isNotEmpty()) {
                FText(label, 13f, color = colors.textSecondary)
                Spacer(Modifier.height(6.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
                FText(
                    value.ifBlank { placeholder },
                    14f,
                    color = if (value.isBlank()) colors.textTertiary else colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (!suffix.isNullOrEmpty()) { Spacer(Modifier.width(6.dp)); FText(suffix, 14f, color = colors.textSecondary) }
                if (trailing != null) { Spacer(Modifier.width(6.dp)); trailing() }
                if (arrow) { Spacer(Modifier.width(6.dp)); VanIcon("arrow", size = 14.sp, color = colors.textTertiary) }
            }
        }
        RowDivider(border)
    }
}

/** 文本输入行（直接复用共享 `AppField`）。 */
@Composable
fun InputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "请输入",
    border: Boolean = true,
    maxlength: Int? = null,
    showWordLimit: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    AppField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        border = border,
        maxlength = maxlength,
        showWordLimit = showWordLimit,
        trailing = trailing,
    )
}

/** 开关行。 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    border: Boolean = true,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        FText(label, 13f, color = colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        VanSwitch(checked = checked, onCheckedChange = onCheckedChange, size = 20)
    }
    RowDivider(border)
}

/** 颜色选择行（12 个圆形色块）；`custom` 为 web 的「自定义颜色」入口（原生暂以提示代替）。 */
@Composable
fun ColorPickerRow(
    label: String,
    value: String,
    onSelect: (String) -> Unit,
    border: Boolean = true,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        FText(label, 13f, color = colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        CARD_COLOR_OPTIONS.chunked(6).forEach { rowColors ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowColors.forEach { (hex, _) ->
                    val selected = hex.equals(value, ignoreCase = true)
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(50))
                            .background(parseColor(hex, Color.Gray))
                            .then(if (selected) Modifier.border(2.dp, colors.primary, RoundedCornerShape(50)) else Modifier)
                            .clickable { onSelect(hex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) VanIcon("success", size = 14.sp, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    RowDivider(border)
}

// =====================================================================================
// 弹层：picker / 银行选择 / 日期选择
// =====================================================================================

/** 通用单列 picker（bottom popup）。 */
@Composable
fun PickerSheet(
    show: Boolean,
    columns: List<String>,
    selectedIndex: Int,
    title: String? = null,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    VanPopup(show = true, onDismissRequest = onDismiss) {
        VanPicker(
            columns = vanPickerColumnsOf(columns),
            value = listOf(selectedIndex.coerceIn(0, (columns.size - 1).coerceAtLeast(0))),
            onConfirm = { sel, _ -> onConfirm(sel.firstOrNull() ?: 0) },
            onCancel = onDismiss,
            title = title,
        )
    }
}

/** 键值对 picker（如币种：value=CNY / text=CNY 人民币）。 */
@Composable
fun IndexedPickerSheet(
    show: Boolean,
    options: List<Pair<String, String>>,
    selectedValue: String,
    title: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    val texts = options.map { it.second }
    val idx = options.indexOfFirst { it.first == selectedValue }.coerceAtLeast(0)
    VanPopup(show = true, onDismissRequest = onDismiss) {
        VanPicker(
            columns = vanPickerColumnsOf(texts),
            value = listOf(idx),
            onConfirm = { sel, _ -> onConfirm(options.getOrNull(sel.firstOrNull() ?: 0)?.first ?: selectedValue) },
            onCancel = onDismiss,
            title = title,
        )
    }
}

/** 银行选择（带图标的列表弹层）。 */
@Composable
fun BankPickerSheet(
    show: Boolean,
    banks: List<Category>,
    onSelect: (Category) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    val colors = LocalAppColors.current
    VanPopup(show = true, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(colors.bgCard)) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clickable { onDismiss() }.padding(start = 16.dp)) {
                    FText("取消", 14f, color = colors.textSecondary)
                }
                FText("选择银行", 16f, FontWeight.Medium, colors.textPrimary)
                Box(Modifier.weight(1f))
            }
            LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                items(banks) { bank ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(bank) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BankRowIcon(bank)
                        Spacer(Modifier.width(10.dp))
                        FText(bank.name, 15f, color = colors.textPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun BankRowIcon(bank: Category) {
    if (bank.iconUrl.isNotBlank()) {
        VanImage(
            src = AppConfig.fullFileUrl(bank.iconUrl),
            modifier = Modifier.size(24.dp),
            fit = androidx.compose.ui.layout.ContentScale.Fit,
            showLoading = false,
            errorSlot = { BankInitialBadge(bank.name, 24.dp) },
        )
    } else {
        BankInitialBadge(bank.name, 24.dp)
    }
}
