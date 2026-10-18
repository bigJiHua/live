package com.live.finance.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.scaledSp

/**
 * 复刻 web `src/components/base/AppTag.vue`（全仓 75 处 / 27 文件在用），**不是** `van-tag`。
 *
 * 与 `VanTag` 的区别就是这几点（web CSS `AppTag.vue:48-61`）：
 *  - `padding: 2px 6px`（van-tag 是 `2px 4px`）
 *  - `border-radius: 4px`（van-tag 是 2px）
 *  - `border: 1px solid transparent`（van-tag 无描边 ⇒ 同一行里比 app-tag 矮 ~2px、窄 ~4px）
 *  - `font-size: 12px; line-height: 1.4`（≈17sp，不是 van-tag 的固定 16sp 行高）
 *  - `default`（无 type）= **灰底深灰字**：`background: var(--theme-bg-tertiary)` + `color: var(--theme-text-secondary)`；
 *    `primary/success/warning/danger` 才是**实底色 + 白字**。
 *
 * @param plain 只有 `default` 之外的 type 才有意义：描边改同色、底透明（web `.app-tag--plain`）。
 *              账单页用到的都是非 plain，留这个参数是给其它 app-tag 调用点复用。
 */
enum class AppTagType { Default, Primary, Success, Warning, Danger }

@Composable
fun AppTag(
    text: String,
    modifier: Modifier = Modifier,
    type: AppTagType = AppTagType.Default,
    plain: Boolean = false,
) {
    val colors = LocalAppColors.current
    val solid = when (type) {
        AppTagType.Primary -> colors.primary
        AppTagType.Success -> colors.success
        AppTagType.Warning -> colors.warning
        AppTagType.Danger -> colors.danger
        AppTagType.Default -> colors.textSecondary   // default 的「色」只用于 plain 描边
    }
    val bg = if (type == AppTagType.Default) colors.bgThird else if (plain) Color.Transparent else solid
    val fg = when {
        type == AppTagType.Default -> colors.textSecondary
        plain -> solid
        else -> Color.White
    }
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, if (plain) solid else Color.Transparent, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text,
            style = TextStyle(
                color = fg,
                fontSize = scaledSp(12f),
                fontWeight = FontWeight.Normal,
                lineHeight = scaledSp(17f),          // web `line-height: 1.4` × 12px
            ),
            maxLines = 1,                   // web 无换行（inline-block + nowrap 语义）
        )
    }
}
