package com.github.guidohu.clockshift.ui.components

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.util.Formatter
import java.util.Locale

/** Locale-aware date strings (field order, month names, year omitted when current). */
class DateLabels(private val context: Context) {
    private fun format(date: LocalDate, flags: Int): String {
        val millis = date.toUtcMillis()
        return DateUtils.formatDateRange(
            context,
            Formatter(StringBuilder(), Locale.getDefault()),
            millis,
            millis,
            flags,
            "UTC",
        ).toString()
    }

    /** "Thursday, 8 October" */
    fun full(date: LocalDate) = format(date, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY)

    /** "Thu, 8 Oct" */
    fun short(date: LocalDate) = format(
        date,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL,
    )

    /** "8 Oct" (year added when it is not the current year) */
    fun dayMonth(date: LocalDate) = format(date, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)

    /** "8 October" */
    fun dayMonthLong(date: LocalDate) = format(date, DateUtils.FORMAT_SHOW_DATE)

    /** "October 2026" */
    fun monthYear(date: LocalDate) = format(
        date,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_MONTH_DAY or DateUtils.FORMAT_SHOW_YEAR,
    )

    /** "Oct" */
    fun monthShort(date: LocalDate) = format(
        date,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_MONTH_DAY or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_MONTH,
    )
}

@Composable
fun rememberDateLabels(): DateLabels {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { DateLabels(context) }
}
