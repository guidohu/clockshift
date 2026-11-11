package com.github.guidohu.clockshift.export

import com.github.guidohu.clockshift.domain.Timesheet
import com.github.guidohu.clockshift.domain.TimesheetDayKind
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Texts of an export, resolved from resources by the caller so the writers stay plain Kotlin. */
data class TimesheetLabels(
    val title: String,
    val appName: String,
    val date: String,
    val weekday: String,
    val type: String,
    val start: String,
    val end: String,
    val breakTime: String,
    val worked: String,
    val target: String,
    val overtime: String,
    val balance: String,
    val duration: String,
    val work: String,
    val breakEntry: String,
    val total: String,
    val balanceBefore: String,
    val balanceAfter: String,
    val running: String,
    /** "Generated %1$s" */
    val generated: String,
    /** "Page %1$d of %2$d" */
    val pageOf: String,
    val kinds: Map<TimesheetDayKind, String>,
    /** Appended to the type of a day off on which work was logged. */
    val dayOffSuffix: String,
    val weekdayName: (LocalDate) -> String,
    /** Long, localized date for the PDF ("Mon, 5 Oct 2026"). */
    val dateText: (LocalDate) -> String,
    /** Wall-clock time following the 12/24 hour setting, for the PDF. */
    val timeText: (LocalTime) -> String,
)

/**
 * Comma-separated timesheet for spreadsheets. Dates are ISO (2026-10-05), times 24-hour (08:52) and
 * durations decimal hours with a dot (8.25), so any spreadsheet can sum them. A byte order mark makes
 * Excel read accented characters correctly.
 *
 * One row per day, or with [entries] one row per logged block (assumed standard days are left out
 * there, as they have no blocks).
 */
object TimesheetCsv {
    /** Byte order mark, written as a code point so the source file itself stays plain. */
    private val BOM = Char(0xFEFF).toString()
    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE
    private val time24 = DateTimeFormatter.ofPattern("HH:mm")

    fun write(sheet: Timesheet, labels: TimesheetLabels, entries: Boolean): String {
        val rows = if (entries) entryRows(sheet, labels) else dayRows(sheet, labels)
        return BOM + rows.joinToString(separator = "\r\n", postfix = "\r\n") { row -> row.joinToString(",") { escape(it) } }
    }

    private fun dayRows(sheet: Timesheet, labels: TimesheetLabels): List<List<String>> {
        val header = with(labels) {
            listOf(date, weekday, type, start, end, "$breakTime (h)", "$worked (h)", "$target (h)", "$overtime (h)", "$balance (h)")
        }
        return listOf(header) + sheet.days.map { day ->
            val counted = day.balanceMinutes != null
            listOf(
                isoDate.format(day.date),
                labels.weekdayName(day.date),
                typeLabel(day.kind, day.dayOff, labels),
                day.start?.let(time24::format).orEmpty(),
                day.end?.let(time24::format)?.let { if (day.endsNextDay) "$it +1" else it }.orEmpty(),
                if (day.start != null && day.end != null) hours(day.breakMinutes) else "",
                if (day.kind == TimesheetDayKind.Open) "" else hours(day.workedMinutes),
                hours(day.targetMinutes),
                if (counted) hours(day.deltaMinutes) else "",
                day.balanceMinutes?.let(::hours).orEmpty(),
            )
        }
    }

    private fun entryRows(sheet: Timesheet, labels: TimesheetLabels): List<List<String>> {
        val header = with(labels) { listOf(date, weekday, type, start, end, "$duration (h)") }
        val now = sheet.generatedAt
        return listOf(header) + sheet.days.flatMap { day ->
            day.entries.map { entry ->
                listOf(
                    isoDate.format(day.date),
                    labels.weekdayName(day.date),
                    if (entry.isBreak) labels.breakEntry else labels.work,
                    time24.format(entry.start),
                    entry.end?.let { end ->
                        time24.format(end) + if (end.toLocalDate() > day.date) " +1" else ""
                    } ?: labels.running,
                    hours(entry.minutesAt(now)),
                )
            }
        }
    }

    fun typeLabel(kind: TimesheetDayKind, dayOff: Boolean, labels: TimesheetLabels): String {
        val text = labels.kinds.getValue(kind)
        return if (dayOff && kind == TimesheetDayKind.Worked) "$text ${labels.dayOffSuffix}" else text
    }

    /** Decimal hours: 495 → "8.25", −30 → "-0.50". */
    fun hours(minutes: Int): String {
        val sign = if (minutes < 0) "-" else ""
        return sign + String.format(Locale.US, "%.2f", abs(minutes) / 60.0)
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
