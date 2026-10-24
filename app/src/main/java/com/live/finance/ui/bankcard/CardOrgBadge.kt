package com.live.finance.ui.bankcard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import com.live.finance.core.AppConfig
import com.live.finance.data.model.Category
import kotlin.math.tan

/**
 * 卡组织 Icon —— 一比一复刻 web `components/BankCard/org/CardOrgIcon.vue`（**纯 CSS 图形，非 SVG**）。
 *
 * - 基准层 80×50，内层按 `min(width/80, height/50)` 等比缩放居中；
 * - `filled=true` 为官方品牌色版（卡片角标用），false 为白色玻璃版；
 * - org 取值：unionpay / mastercard / visa / amex / diners / jcb。
 */
@Composable
fun CardOrgBadge(
    org: String,
    modifier: Modifier = Modifier,
    width: Dp = 80.dp,
    height: Dp = 50.dp,
    filled: Boolean = true,
) {
    val s = minOf(width.value / 80f, height.value / 50f)
    val containerBg = if (filled) when (org) {
        "visa" -> Color(0xFF1A1F71)
        "amex" -> Color(0xFF2E77BC)
        "diners" -> Color(0xFF0067B1)
        else -> null
    } else null

    Box(
        modifier
            .size(width, height)
            .clip(RoundedCornerShape(8.dp))
            .then(if (containerBg != null) Modifier.background(containerBg) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(80.dp, 50.dp)
                .graphicsLayer { scaleX = s; scaleY = s },
            contentAlignment = Alignment.Center,
        ) {
            when (org) {
                "unionpay" -> UnionPay(filled)
                "mastercard" -> Mastercard(filled)
                "visa" -> OrgText("VISA", 17f, FontWeight.Black, italic = true, letterSpacing = 1f, paddingLeft = 3.dp)
                "amex" -> OrgText("AMERICAN\nEXPRESS", 8f, FontWeight.ExtraBold, align = TextAlign.Center, letterSpacing = 1.5f, lineHeightSp = 9.6f)
                "diners" -> OrgText("Diners Club\nINTERNATIONAL", 7f, FontWeight.Bold, align = TextAlign.Center, lineHeightSp = 9.1f)
                "jcb" -> Jcb(filled)
            }
        }
    }
}

/** 1. 银联：三个倒角平行四边形（skewX(-15deg)）+ 居中「银联」。 */
@Composable
private fun UnionPay(filled: Boolean) {
    val red = if (filled) Color(0xFFE21836) else Color.White.copy(alpha = 0.5f)
    val green = if (filled) Color(0xFF007B84) else Color.White.copy(alpha = 0.6f)
    val blue = if (filled) Color(0xFF00447C) else Color.White.copy(alpha = 0.85f)
    Box(contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Row {
            SkewedBar(red)
            SkewedBar(green)
            SkewedBar(blue)
        }
        OrgText("银联", 10f, FontWeight.ExtraBold, italic = true, letterSpacing = 1f)
    }
}

/** 21×38，skewX(-15deg)：x' = x - tan(15°)*y（绕中心）。 */
@Composable
private fun SkewedBar(color: Color) {
    Canvas(Modifier.size(21.dp, 38.dp)) {
        val shift = tan(Math.toRadians(15.0)).toFloat() * size.height / 2f
        val p = Path().apply {
            moveTo(shift, 0f)
            lineTo(size.width + shift, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(p, color)
    }
}

/** 2. 万事达：两圆（left 12 / right 12）+ 底部 mastercard 文字。 */
@Composable
private fun Mastercard(filled: Boolean) {
    val red = if (filled) Color(0xFFEB001B) else Color.White.copy(alpha = 0.7f)
    val yellow = if (filled) Color(0xFFF79E1B) else Color.White.copy(alpha = 0.45f)
    val textColor = if (filled) Color(0xFF231F20) else Color.White
    Box {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = 12.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(50))
                .background(red),
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-12).dp)
                .size(32.dp)
                .clip(RoundedCornerShape(50))
                .background(yellow),
        )
        BasicText(
            "mastercard",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            style = TextStyle(
                // ⚠ 7sp **不参与** 7.9 的移动端字号提档：这是品牌 logo 的图形资产
                // （80×50 画布上按 web CSS 精确排布），10 个 Bold 斜体字符提到 12sp 会撑出容器。
                color = textColor, fontSize = 7.sp, fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic, letterSpacing = 1.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}

/** 6. JCB：三竖块（18×74%，top 13%，left 11/31/51）+ JCB 文字。 */
@Composable
private fun Jcb(filled: Boolean) {
    val c1 = if (filled) Color(0xFF0066B2) else Color.White.copy(alpha = 0.55f)
    val c2 = if (filled) Color(0xFFE60012) else Color.White.copy(alpha = 0.85f)
    val c3 = if (filled) Color(0xFF00A94F) else Color.White.copy(alpha = 0.65f)
    val shape = RoundedCornerShape(topStart = 10.dp, topEnd = 0.dp, bottomEnd = 10.dp, bottomStart = 0.dp)
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.TopStart).offset(x = 11.dp, y = 6.5.dp).size(18.dp, 37.dp).clip(shape).background(c1))
        Box(Modifier.align(Alignment.TopStart).offset(x = 31.dp, y = 6.5.dp).size(18.dp, 37.dp).clip(shape).background(c2))
        Box(Modifier.align(Alignment.TopStart).offset(x = 51.dp, y = 6.5.dp).size(18.dp, 37.dp).clip(shape).background(c3))
        OrgText("JCB", 11f, FontWeight.Black, italic = true, letterSpacing = 1f)
    }
}

/** 统一的白色文字块（复刻 `.org-icon { color:#fff; font-weight:800; text-shadow }`）。 */
@Composable
private fun OrgText(
    text: String,
    sizeSp: Float,
    weight: FontWeight,
    italic: Boolean = false,
    align: TextAlign? = null,
    letterSpacing: Float = 0f,
    lineHeightSp: Float = 0f,
    paddingLeft: Dp = 0.dp,
) {
    BasicText(
        text,
        modifier = Modifier.padding(start = paddingLeft),
        style = TextStyle(
            color = Color.White,
            fontSize = sizeSp.sp,
            fontWeight = weight,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            letterSpacing = letterSpacing.sp,
            lineHeight = if (lineHeightSp > 0f) lineHeightSp.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
            textAlign = align ?: TextAlign.Start,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}

/** 中文卡组织名 → org key（对齐 web `orgMap.js` + 线上 `getCardOrgInfo` 的英文忽略大小写语义：
 *  ORG_NAME_MAP 只列了 Visa/visa 两种写法，后端数据 `VISA` 大写会漏匹配 ⇒ 卡组织整块不显示
 *  ——2026-10-20 真机「卡组织匹配问题」的根因）。 */
fun cardOrgKey(name: String): String {
    val hit = ORG_KEYS.firstOrNull { it.equals(name, ignoreCase = true) } ?: return ""
    return ORG_MAP[hit] ?: ""
}

/**
 * 卡组织 logo 资源 URL —— 对齐 web `debit.vue/credit.vue` 的 `getCardOrgInfo()`：
 * 在 `category.list("bank")` 里按名称找同名的卡组织分类（中文全等、英文忽略大小写），
 * 命中且该分类有 `icon_url` 就用它；否则返回空串、由 [CardOrgBadge] 组件兜底。
 *
 * ⚠ web 不是「有卡组织名就直接画组件」：先匹配资源图，图缺失或加载失败才回落组件。
 */
fun cardOrgIconUrlOf(cardOrg: String, banks: List<Category>): String {
    val name = cardOrg.trim()
    if (name.isEmpty()) return ""
    val isChinese = name.any { it.code in 0x4E00..0x9FA5 }
    val hit = banks.firstOrNull {
        if (isChinese) it.name.trim() == name else it.name.trim().equals(name, ignoreCase = true)
    } ?: return ""
    return hit.iconUrl.takeIf { it.isNotBlank() }
        ?.let { AppConfig.fullFileUrl(it) }
        .orEmpty()
}

private val ORG_KEYS = arrayOf("银联", "万事达", "Visa", "运通", "AmericanExpress", "amex", "大莱", "Diners", "diners", "JCB")
private val ORG_MAP = mapOf(
    "银联" to "unionpay",
    "万事达" to "mastercard",
    "Visa" to "visa",
    "运通" to "amex",
    "AmericanExpress" to "amex",
    "amex" to "amex",
    "大莱" to "diners",
    "Diners" to "diners",
    "diners" to "diners",
    "JCB" to "jcb",
)
