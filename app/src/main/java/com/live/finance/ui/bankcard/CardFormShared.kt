package com.live.finance.ui.bankcard

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.core.AppConfig
import com.live.finance.data.model.Category
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppDialogShell
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.rememberBankIconRequest
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanImage
import com.live.vant.feedback.LocalVanToastController
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

/**
 * 卡等级选项（**统一口径**，非 web 原样）。
 *
 * web 三处口径不一致：`card/Add.vue` 以「黑金卡」结尾、`credit/AddFull.vue` 以「黑卡/无限卡」结尾、
 * `card/Edit.vue` 是自由文本输入。本工程取两者**并集**并按等级升序排列，保证任一口径下已存在的
 * 卡等级在原生侧都仍可选；各页默认值仍照抄 web（添加=普卡，新增信用卡=白金卡）。
 */
val CARD_LEVEL_COLUMNS = listOf("普卡", "金卡", "白金卡", "钻石卡", "黑金卡", "黑卡", "无限卡")

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
            // web `.section-title{ font-size:14px→16; color: var(--theme-text-tertiary); padding:12px 16px 8px;
            //   display:flex; align-items:center }` —— **无 font-weight ⇒ 常规体**，且颜色是 text-tertiary
            //   （旧实现用了 text-primary + Medium，偏重偏黑）。
            //   外层 `.form-section{margin-top:12px}` + 标题 `padding-top:12px` ⇒ 标题上方共 **24dp**。
            // ⚠ `.required-hint{ font-size:12px; color:danger; margin-left:8px }` —— 提示**紧跟标题**、间距 8dp，
            //   **不是**右对齐（旧实现给标题加了 weight(1f) 把提示推到了最右）。
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (titleIcon != null) {
                    VanIcon(titleIcon, size = 14.sp, color = titleColor ?: colors.textTertiary)
                    Spacer(Modifier.width(4.dp))
                }
                FText(title, 16f, color = titleColor ?: colors.textTertiary)
                if (requiredHint) {
                    Spacer(Modifier.width(8.dp))
                    FText("*为必填项", 12f, color = colors.danger)
                }
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

/**
 * 只读选择行（label 在上、值 + 箭头在下）。
 *
 * ⚠⚠ **参数顺序是刻意排的，不要"顺手整理"**（2026-09-21 卡死事故的加固）：
 *   - 前 4 位 `label / value / placeholder / onClick` **锁死** —— 大量既有调用点是**位置实参**
 *     （如 `ReadonlyRow("银行", form.bankName, "请选择银行", { showBank = true })`），一动就全炸。
 *   - `leading` / `trailing` 必须排在 `onClick` **之后**，且**最后一个参数必须是非函数类型**
 *     （这里是 `border: Boolean`）。
 *     原因：Kotlin 的**尾随 lambda 绑到「最后一个参数」**。若 `trailing` 在末尾，则
 *     `ReadonlyRow(a, b, c) { ... }` 的 lambda 会绑到 `trailing` 而**不是** `onClick` ⇒
 *     ① 整行点不动；② 那段代码在**组合期被直接调用** ⇒ 状态写入引发**无限重组**（表现为
 *     "自动弹日期选择 + 整个 APP 卡死"）。
 *     现在末尾是 `Boolean`，这种写法会**直接编译报错**，从机制上杜绝复发。
 */
@Composable
fun ReadonlyRow(
    label: String,
    value: String,
    placeholder: String = "请选择",
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    suffix: String? = null,
    arrow: Boolean = true,
    enabled: Boolean = true,
    border: Boolean = true,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable { onClick() } else Modifier),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (label.isNotEmpty()) {
                FText(label, 14f, color = colors.textSecondary)
                Spacer(Modifier.height(6.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
                FText(
                    value.ifBlank { placeholder },
                    16f,
                    color = if (value.isBlank()) colors.textTertiary else colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (!suffix.isNullOrEmpty()) { Spacer(Modifier.width(6.dp)); FText(suffix, 16f, color = colors.textSecondary) }
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
    // ⚠⚠ 同 `ReadonlyRow`：`trailing` **不能排在末尾**，末尾必须是非函数类型
    //   （这里是 `showWordLimit: Boolean`），否则尾随 lambda 会误绑到 `trailing`。
    trailing: (@Composable () -> Unit)? = null,
    border: Boolean = true,
    maxlength: Int? = null,
    showWordLimit: Boolean = false,
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
        FText(label, 14f, color = colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        VanSwitch(checked = checked, onCheckedChange = onCheckedChange, size = 20)
    }
    RowDivider(border)
}

/**
 * 颜色选择行（12 个圆形色块 + 「自定义」入口）。
 *
 * web 三处都有原生取色器：`card/Add.vue:133` 的 `.color-picker-btn`（edit 图标触发隐藏 input）、
 * `card/Edit.vue:258` 的「自定义」+ `<input type="color">`、`credit/AddFull.vue` 两处 `title="自定义颜色"`。
 * 安卓没有系统取色器，这里以「色块预览 + 十六进制输入」等价实现（弹窗标题取 web 的 `title` 原文「自定义颜色」）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerRow(
    /** web `card/Add.vue` 的颜色区**没有 label**（分区标题「外观」即其标题）→ 传 null 即可。 */
    label: String? = null,
    value: String,
    onSelect: (String) -> Unit,
    border: Boolean = true,
) {
    val colors = LocalAppColors.current
    val toast = LocalVanToastController.current
    var showCustom by remember { mutableStateOf(false) }
    var customHex by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (!label.isNullOrEmpty()) {
            FText(label, 14f, color = colors.textSecondary)
            Spacer(Modifier.height(8.dp))
        }
        // web `.color-picker-row{ display:flex; flex-wrap:wrap; gap:12px; align-items:center }`
        //   `.color-option{ width:32px; height:32px; border-radius:50%; border:1px solid var(--theme-border) }`
        //   `.color-option.active{ box-shadow: 0 0 0 2px #fff, 0 0 0 4px currentColor }`
        // —— 旧实现是 `chunked(6)` 固定 6 列 + 28dp + 选中描边用**主题色**，三处都不符。
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CARD_COLOR_OPTIONS.forEach { (hex, _) ->
                val selected = hex.equals(value, ignoreCase = true)
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(50))
                        .background(parseColor(hex, Color.Gray))
                        // web 选中态是「2px 白环 + 4px 本色环」（box-shadow），Compose 用 2dp 白描边近似
                        .then(
                            if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(50))
                            else Modifier.border(1.dp, colors.border, RoundedCornerShape(50)),
                        )
                        .clickable { onSelect(hex) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) VanIcon("success", size = 14.sp, color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clickable {
                customHex = value.ifBlank { "#0052CC" }
                showCustom = true
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FText("自定义", 14f, color = colors.textSecondary)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(50))
                    .background(parseColor(value, Color.Gray))
                    .border(1.dp, colors.border, RoundedCornerShape(50)),
            )
            Spacer(Modifier.width(8.dp))
            FText(value.ifBlank { "—" }, 14f, color = colors.textTertiary)
        }
    }
    RowDivider(border)
    AppDialogShell(
        show = showCustom,
        title = "自定义颜色",
        onOverlay = { showCustom = false },
        onCancel = { showCustom = false },
        onConfirm = {
            val hex = normalizeHexColor(customHex)
            if (hex == null) toast.show("请输入 #RRGGBB 格式的颜色值") else { onSelect(hex); showCustom = false }
        },
    ) {
        AppField(value = customHex, onValueChange = { customHex = it }, label = "颜色值", placeholder = "#RRGGBB")
    }
}

/** 归一化十六进制色值：接受 `#RGB` / `#RRGGBB`（`#` 可省略、大小写不敏感）；非法返回 null。 */
private fun normalizeHexColor(raw: String): String? {
    val s = raw.trim().removePrefix("#")
    val hex = when {
        s.length == 3 && s.all { isHexDigit(it) } -> s.map { "$it$it" }.joinToString("")
        s.length == 6 && s.all { isHexDigit(it) } -> s
        else -> return null
    }
    return "#" + hex.uppercase()
}

private fun isHexDigit(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

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

/**
 * 卡组织选择弹层 —— web `card/Add.vue` / `card/Edit.vue` / `credit/AddFull.vue` 三页用的是**自定义列表**（`.org-picker`），**不是** `van-picker`：
 * 每项 = `CardOrgIcon small filled` + 名称 + （选中时）主题色 `success` 勾。
 * `.org-picker{ background: var(--theme-bg-primary); padding:8px 0 16px; max-height:70vh; overflow-y:auto }`
 * —— 旧实现直接套了通用单列 `PickerSheet`（滚轮），既没有卡组织图标也没有勾选标记，结构不符。
 */
@Composable
fun OrgPickerSheet(show: Boolean, selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    if (!show) return
    val colors = LocalAppColors.current
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.7f).dp // max-height: 70vh
    VanPopup(show = true, onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(colors.bgPage)
                .heightIn(max = maxH).verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            // .org-picker-title{ text-align:center; font-size:15px→16; font-weight:600; text-primary; padding:12px 0 8px }
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                FText("选择卡组织", 16f, FontWeight.SemiBold, colors.textPrimary)
            }
            CARD_ORG_COLUMNS.forEach { org ->
                // .org-picker-item{ display:flex; align-items:center; gap:14px; padding:12px 20px }
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(org) }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CardOrgMini(org)
                    Spacer(Modifier.width(14.dp))
                    FText(org, 16f, color = colors.textPrimary, modifier = Modifier.weight(1f)) // .org-picker-label{flex:1; 15px→16}
                    if (selected == org) VanIcon("success", size = 18.sp, color = colors.primary) // .org-picker-check
                }
            }
        }
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
                    FText("取消", 16f, color = colors.textSecondary)
                }
                FText("选择银行", 18f, FontWeight.Medium, colors.textPrimary)
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
                        FText(bank.name, 16f, color = colors.textPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun BankRowIcon(bank: Category) {
    if (bank.iconUrl.isNotBlank()) {
        val url = AppConfig.fullFileUrl(bank.iconUrl)
        VanImage(
            src = url,
            // 走共用 bank logo 请求 ⇒ 与启动预热同一把缓存键，选银行弹层打开即命中（见 BankIconCache）
            imageRequest = rememberBankIconRequest(url),
            modifier = Modifier.size(24.dp),
            fit = androidx.compose.ui.layout.ContentScale.Fit,
            showLoading = false,
            errorSlot = { BankInitialBadge(bank.name, 24.dp) },
        )
    } else {
        BankInitialBadge(bank.name, 24.dp)
    }
}
