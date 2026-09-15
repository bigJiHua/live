package com.live.vant.form

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime

enum class VanDatePickerType { Date, YearMonth, MonthDay }
enum class VanDatetimePickerType { Datetime, DateHour, YearMonth, MonthDay }

private fun pad2(n: Int) = n.toString().padStart(2, '0')

/**
 * 复刻 van-date-picker（web 项目 17 处使用；props：type / v-model / min-date / max-date / title；
 * 事件 confirm / cancel）。v-model 用 LocalDate。
 */
@Composable
fun VanDatePicker(
    /** Vant type 字符串："date" / "year-month" / "month-day" */
    type: String,
    value: LocalDate,
    onValueChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    minDate: LocalDate = value.minusYears(100),
    maxDate: LocalDate = value.plusYears(100),
    title: String? = null,
    showToolbar: Boolean = true,
    confirmButtonText: String = "确认",
    cancelButtonText: String = "取消",
    onConfirm: ((LocalDate) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    optionHeight: Dp = 44.dp,
) {
    val t = when (type) {
        "year-month" -> VanDatePickerType.YearMonth
        "month-day" -> VanDatePickerType.MonthDay
        else -> VanDatePickerType.Date
    }
    val yearMin = minDate.year
    val months = (1..12).map { VanPickerOption(pad2(it)) }

    val columns: List<List<VanPickerOption>> = when (t) {
        VanDatePickerType.YearMonth -> listOf(
            (yearMin..maxDate.year).map { VanPickerOption(pad2(it)) },
            months,
        )
        VanDatePickerType.MonthDay -> listOf(
            months,
            (1..31).map { VanPickerOption(pad2(it)) },
        )
        VanDatePickerType.Date -> listOf(
            (yearMin..maxDate.year).map { VanPickerOption(pad2(it)) },
            months,
            (1..31).map { VanPickerOption(pad2(it)) },
        )
    }

    val current: List<Int> = when (t) {
        VanDatePickerType.Date -> listOf(value.year - yearMin, value.monthValue - 1, value.dayOfMonth - 1)
        VanDatePickerType.YearMonth -> listOf(value.year - yearMin, value.monthValue - 1)
        VanDatePickerType.MonthDay -> listOf(value.monthValue - 1, value.dayOfMonth - 1)
    }

    fun toDate(idx: List<Int>): LocalDate {
        val y = idx.getOrElse(0) { 0 } + if (t == VanDatePickerType.MonthDay) 2000 else yearMin
        val m = (if (t == VanDatePickerType.MonthDay) idx.getOrElse(0) { 0 } else idx.getOrElse(1) { 0 }) + 1
        val d = (if (t == VanDatePickerType.Date) idx.getOrElse(2) { 0 } else if (t == VanDatePickerType.MonthDay) idx.getOrElse(1) { 0 } else 0) + 1
        val first = LocalDate.of(y, m, 1)
        return first.withDayOfMonth(d.coerceAtMost(first.lengthOfMonth()))
    }

    VanPicker(
        columns = columns,
        value = current,
        onValueChange = { idx -> onValueChange(toDate(idx)) },
        onConfirm = { idx, _ -> onConfirm?.invoke(toDate(idx)) },
        onCancel = onCancel,
        title = title,
        showToolbar = showToolbar,
        confirmButtonText = confirmButtonText,
        cancelButtonText = cancelButtonText,
        modifier = modifier,
        optionHeight = optionHeight,
    )
}

/**
 * 复刻 van-datetime-picker（props：type / v-model / min-date / max-date）。v-model 用 LocalDateTime。
 */
@Composable
fun VanDatetimePicker(
    /** "datetime" / "datehour" / "year-month" / "month-day" */
    type: String,
    value: LocalDateTime,
    onValueChange: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
    minDate: LocalDateTime = value.minusYears(100),
    maxDate: LocalDateTime = value.plusYears(100),
    title: String? = null,
    showToolbar: Boolean = true,
    confirmButtonText: String = "确认",
    cancelButtonText: String = "取消",
    onConfirm: ((LocalDateTime) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    optionHeight: Dp = 44.dp,
) {
    val yearMin = minDate.year
    val columns: List<List<VanPickerOption>> = when (type) {
        "datehour" -> listOf(
            (yearMin..maxDate.year).map { VanPickerOption(pad2(it)) },
            (1..12).map { VanPickerOption(pad2(it)) },
            (1..31).map { VanPickerOption(pad2(it)) },
            (0..23).map { VanPickerOption(pad2(it)) },
        )
        "year-month" -> listOf(
            (yearMin..maxDate.year).map { VanPickerOption(pad2(it)) },
            (1..12).map { VanPickerOption(pad2(it)) },
        )
        "month-day" -> listOf(
            (1..12).map { VanPickerOption(pad2(it)) },
            (1..31).map { VanPickerOption(pad2(it)) },
        )
        else -> listOf( // datetime
            (yearMin..maxDate.year).map { VanPickerOption(pad2(it)) },
            (1..12).map { VanPickerOption(pad2(it)) },
            (1..31).map { VanPickerOption(pad2(it)) },
            (0..23).map { VanPickerOption(pad2(it)) },
            (0..59).map { VanPickerOption(pad2(it)) },
        )
    }

    val current: List<Int> = when (type) {
        "datehour" -> listOf(value.year - yearMin, value.monthValue - 1, value.dayOfMonth - 1, value.hour)
        "year-month" -> listOf(value.year - yearMin, value.monthValue - 1)
        "month-day" -> listOf(value.monthValue - 1, value.dayOfMonth - 1)
        else -> listOf(value.year - yearMin, value.monthValue - 1, value.dayOfMonth - 1, value.hour, value.minute)
    }

    fun toDateTime(idx: List<Int>): LocalDateTime {
        val y = idx.getOrElse(0) { 0 } + if (type == "month-day") 2000 else yearMin
        val m = (if (type == "month-day") idx.getOrElse(0) { 0 } else idx.getOrElse(1) { 0 }) + 1
        val d = (if (type == "datetime" || type == "datehour") idx.getOrElse(2) { 0 } else if (type == "month-day") idx.getOrElse(1) { 0 } else 0) + 1
        val hh = (if (type == "datetime" || type == "datehour") idx.getOrElse(3) { 0 } else 0)
        val mi = (if (type == "datetime") idx.getOrElse(4) { 0 } else 0)
        val first = java.time.LocalDate.of(y, m, 1)
        return java.time.LocalDateTime.of(
            first.year, first.monthValue,
            d.coerceAtMost(first.lengthOfMonth()),
            hh.coerceIn(0, 23), mi.coerceIn(0, 59),
        )
    }

    VanPicker(
        columns = columns,
        value = current,
        onValueChange = { idx -> onValueChange(toDateTime(idx)) },
        onConfirm = { idx, _ -> onConfirm?.invoke(toDateTime(idx)) },
        onCancel = onCancel,
        title = title,
        showToolbar = showToolbar,
        confirmButtonText = confirmButtonText,
        cancelButtonText = cancelButtonText,
        modifier = modifier,
        optionHeight = optionHeight,
    )
}
