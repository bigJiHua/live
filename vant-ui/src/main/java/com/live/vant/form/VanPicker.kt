package com.live.vant.form

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

data class VanPickerOption(
    val text: String,
    val value: Any? = null,
    val disabled: Boolean = false,
)

fun vanPickerColumnsOf(vararg cols: List<String>): List<List<VanPickerOption>> =
    cols.map { c -> c.map { VanPickerOption(it) } }

/**
 * 复刻 van-picker（web 项目 54 处使用，核心表单组件）。
 *
 * props：columns / title / show-toolbar / confirm-button-text / cancel-button-text；
 * events：confirm / cancel / change；v-model → value(List<Int> 各列下标) + onValueChange。
 * 滚轮 6 × 44px，选项 16px，上下蒙层 + 选中区两条 1px 线，与 Vant CSS 一致。
 */
@Composable
fun VanPicker(
    /** 各列选项（支持 String 简写 → 自动转 text） */
    columns: List<List<VanPickerOption>>,
    modifier: Modifier = Modifier,
    /** 各列选中下标（v-model） */
    value: List<Int> = columns.map { 0 },
    onValueChange: (List<Int>) -> Unit = {},
    /** confirm 回调：各列下标 + 各列选中值 */
    onConfirm: (List<Int>, List<Any?>) -> Unit = { _, _ -> },
    onCancel: (() -> Unit)? = null,
    title: String? = null,
    showToolbar: Boolean = true,
    confirmButtonText: String = "确认",
    cancelButtonText: String = "取消",
    visibleOptionCount: Int = 6,
    optionHeight: Dp = 44.dp,
) {
    val tokens = LocalVantDimens.current

    // 每列一个索引流
    val indexFlows = remember(columns) {
        columns.mapIndexed { i, _ -> MutableStateFlow(value.getOrNull(i)?.coerceIn(0, (columns[i].size - 1).coerceAtLeast(0)) ?: 0) }
    }
    val indices = indexFlows.map { it.collectAsStateValue() }
    val indexSnapshot = remember(indices) { indices.toList() }

    // 滚动结果回传（对应 v-model / change）
    LaunchedEffect(indexSnapshot) {
        val current = indexFlows.map { it.value }
        if (current != value) onValueChange(current)
    }

    Column(modifier.fillMaxWidth()) {
        if (showToolbar) {
            VanPickerToolbar(
                title = title,
                confirmButtonText = confirmButtonText,
                cancelButtonText = cancelButtonText,
                onConfirm = {
                    val idx = indexFlows.map { it.value }
                    val vals = columns.mapIndexed { i, col -> col.getOrNull(idx.getOrElse(i) { 0 })?.let { it.value ?: it.text } }
                    onConfirm(idx, vals)
                },
                onCancel = { onCancel?.invoke() },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(optionHeight * visibleOptionCount),
        ) {
            columns.forEachIndexed { colIdx, col ->
                Box(Modifier.weight(1f).fillMaxSize()) {
                    PickerWheel(
                        indexFlow = indexFlows[colIdx],
                        options = col,
                        optionHeight = optionHeight,
                        visibleOptionCount = visibleOptionCount,
                    )
                }
            }
        }
    }
}

/** 轻量 collectAsState（避免重复样板） */
@Composable
private fun StateFlow<Int>.collectAsStateValue(): Int =
    collectAsState().value

@Composable
private fun PickerWheel(
    indexFlow: MutableStateFlow<Int>,
    options: List<VanPickerOption>,
    optionHeight: Dp,
    visibleOptionCount: Int,
) {
    val c = LocalVantColors.current
    val tokens = LocalVantDimens.current
    val total = optionHeight * visibleOptionCount
    val centerIndex = (visibleOptionCount - 1) / 2f

    val wheelPos = remember { Animatable(indexFlow.value.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(indexFlow.value.toFloat()) }
    val shown: Float = if (dragging) dragPos else wheelPos.value
    val dragScope = androidx.compose.runtime.rememberCoroutineScope()
    // 外部索引变化（联动列） → 滚轮对齐
    LaunchedEffect(indexFlow.value) {
        if (!dragging && !wheelPos.isRunning && abs(wheelPos.value - indexFlow.value) > 0.01f) {
            wheelPos.snapTo(indexFlow.value.toFloat())
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(total)
            .clipToBounds()
            .pointerInput(options) {
                detectVerticalDragGestures(
                    onDragStart = { start ->
                        dragging = true
                        dragPos = wheelPos.value
                    },
                    onDragEnd = {
                        val snapped = nearestEnabled(options, dragPos.roundToInt())
                        dragScope.launch {
                            wheelPos.animateTo(snapped.toFloat(), tween(120))
                            dragging = false
                            if (indexFlow.value != snapped) indexFlow.value = snapped
                        }
                    },
                    onDragCancel = {
                        dragging = false
                    },
                ) { change, dragY ->
                    if (dragging) {
                        dragPos = (dragPos - dragY / optionHeight.value).coerceIn(-0.4f, options.size - 0.6f)
                        change.consume()
                    }
                }
            },
    ) {
        val half = (visibleOptionCount - 1) / 2
        for (i in options.indices) {
            val dist = i - shown
            if (abs(dist) > half + 1f) continue
            val top = optionHeight * (centerIndex + dist)
            val opt = options[i]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(optionHeight)
                    .offset(y = top),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = opt.text,
                    style = TextStyle(
                        color = if (opt.disabled) c.textPlaceholder else c.textPrimary,
                        fontSize = tokens.fontSizeLg,
                        textAlign = TextAlign.Center,
                        lineHeight = optionHeight.value.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        // 上下蒙层（--van-picker-mask-color 渐变）
        val maskH = optionHeight * (((visibleOptionCount - 2) / 2f).coerceAtLeast(0f))
        if (maskH > 0.dp) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(maskH)
                    .background(Brush.verticalGradient(0f to c.pickerMaskColor.copy(alpha = 0.95f), 1f to Color.Transparent)),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(maskH)
                    .background(Brush.verticalGradient(0f to Color.Transparent, 1f to c.pickerMaskColor.copy(alpha = 0.95f))),
            )
        }
        // 选中区上下各一条 1px hairline
        Box(
            Modifier
                .padding(top = optionHeight * centerIndex)
                .fillMaxWidth()
                .height(tokens.borderWidth)
                .background(c.border),
        )
        Box(
            Modifier
                .padding(top = optionHeight * (centerIndex + 1f) - tokens.borderWidth)
                .fillMaxWidth()
                .height(tokens.borderWidth)
                .background(c.border),
        )
    }
}

private fun nearestEnabled(options: List<VanPickerOption>, index: Int): Int {
    if (options.isEmpty()) return 0
    val i = index.coerceIn(0, options.size - 1)
    if (!options[i].disabled) return i
    for (d in 1 until options.size) {
        if (i - d >= 0 && !options[i - d].disabled) return i - d
        if (i + d < options.size && !options[i + d].disabled) return i + d
    }
    return i
}

/** picker 顶栏（title + 取消/确认），van-picker / van-date-picker 共用 */
@Composable
fun VanPickerToolbar(
    title: String?,
    confirmButtonText: String,
    cancelButtonText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalVantColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(c.bgCard),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .clickable { onCancel() },
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                cancelButtonText,
                style = TextStyle(color = c.textSecondary, fontSize = 14.sp),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Box(Modifier.weight(1.4f), contentAlignment = Alignment.Center) {
            if (!title.isNullOrEmpty()) {
                BasicText(
                    title,
                    style = TextStyle(color = c.textPrimary, fontSize = 16.sp, lineHeight = 20.sp),
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .clickable { onConfirm() },
            contentAlignment = Alignment.CenterEnd,
        ) {
            BasicText(
                confirmButtonText,
                style = TextStyle(color = c.primary, fontSize = 14.sp),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
