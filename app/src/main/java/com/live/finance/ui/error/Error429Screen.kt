package com.live.finance.ui.error

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.theme.scaledSp

/**
 * 429 限流警示页 —— 一比一复刻 web `views/error/429.vue`。
 *
 * web 度量（原样照抄，勿改）：
 *  - `.error-page`：铺满、flex 居中，`background: #ff0000`，`animation: bgFlash 0.5s infinite`
 *    （关键帧 0%/100% = #ff0000，50% = #b10e0e → 半周期 0.25s 的红↔暗红循环）
 *  - `.warning-box`：`margin 10`、`border 4px solid #f7ff02`、`border-radius 12px`、
 *    `background rgba(0,0,0,.3)`、`padding 40`
 *  - `.warning-text`：`48px / bold / #fff`、`text-shadow 0 0 20px #ff3b30`、`letter-spacing 4px`
 *  - 文案逐字：`warning！请勿爆破！`（"warning" 为小写半角，"！" 为全角）
 *
 * 注意：web 该页**没有任何按钮**（纯警示页），原生同样不放按钮——系统返回键/手势即可离开。
 * 因此本文件不使用 [NavHostController]，保留形参只为与既有 `composable(Routes.ERROR_429)` 注册签名一致。
 */
@Composable
fun Error429Screen(nav: NavHostController) {
    @Suppress("UNUSED_EXPRESSION")
    nav

    val transition = rememberInfiniteTransition(label = "bgFlash")
    // 0.5s 一轮：0 -> 0.5 红->暗红，0.5 -> 1 暗红->红（对应 CSS 0%/50%/100% 关键帧）
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val flashRed = Color(0xFFFF0000)
    val flashDarkRed = Color(0xFFB10E0E)
    val bg = if (phase < 0.5f) {
        lerp(flashRed, flashDarkRed, phase / 0.5f)
    } else {
        lerp(flashDarkRed, flashRed, (phase - 0.5f) / 0.5f)
    }

    Box(
        Modifier.fillMaxSize().background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(10.dp) // .warning-box margin: 10px
                .background(Color(0x4D000000), RoundedCornerShape(12.dp))
                .border(4.dp, Color(0xFFF7FF02), RoundedCornerShape(12.dp))
                .padding(40.dp), // .warning-box padding: 40px
        ) {
            BasicText(
                "warning！请勿爆破！",
                style = TextStyle(
                    color = Color.White,
                    fontSize = scaledSp(48f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(Color(0xFFFF3B30), Offset.Zero, 20f),
                ),
            )
        }
    }
}