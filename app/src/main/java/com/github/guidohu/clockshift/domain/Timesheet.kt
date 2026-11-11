package com.github.guidohu.clockshift.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** How a day appears on the timesheet. */
enum class TimesheetDayKind {
    /** Work was logged. */
    Worked,

    /** Nothing logged on a past working day: the standard day is assumed. */
    Standard,

    /** Marked as a day off, nothing logged. */
    DayOff,

    /** Not a working day, nothing logged. */
    Free,

    /** Checked in right now; not counted yet. */
    InProgress,

    /** Today, nothing logged yet; not counted yet. */
    Open,
}

/**
 * One day of the timesheet. [start] and [end] span the day's work; [breakMinutes] is the time between
 * them that was not worked (breaks and gaps). [balanceMinutes] is the overtime balance at the end of the
 * day, or null while the day is not counted yet.
 */
data class TimesheetDay(
    val date: LocalDate,
    val kind: TimesheetDayKind,
    val dayOff: Boolean,
    val start: LocalTime?,
    val end: LocalTime?,
    val endsNextDay: Boolean,
    val breakMinutes: Int,
    val workedMinutes: Int,
    val targetMinutes: Int,
    val deltaMinutes: Int,
    val balanceMinutes: Int?,
    /** Logged blocks (work and breaks), in order; empty for assumed days. */
    val entries: List<Entry>,
)

/** Everything an export shows for [from]..[to]. Balances include the starting balance. */
data class Timesheet(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<TimesheetDay>,
    val workedMinutes: Int,
    val targetMinutes: Int,
    val deltaMinutes: Int,
    val balanceBeforeMinutes: Int,
    val balanceAfterMinutes: Int,
    val generatedAt: LocalDateTime,
)

object TimesheetBuilder {
    /**
     * Builds the timesheet for [requestedFrom] (null: since tracking started) to [requestedTo], clipped to
     * the tracked period up to today. Returns null when nothing of the range has been tracked yet.
     */
    fun build(
        schedules: List<ScheduleVersion>,
        entries: List<Entry>,
        daysOff: Set<LocalDate>,
        settings: Settings,
        requestedFrom: LocalDate?,
        requestedTo: LocalDate,
        now: LocalDateTime,
    ): Timesheet? {
        val today = now.toLocalDate()
        val calculator = OvertimeCalculator(schedules, entries, daysOff)
        val trackingStart = calculator.trackingStart(settings.startDate)
        val from = maxOf(requestedFrom ?: trackingStart, trackingStart)
        val to = minOf(requestedTo, today)
        if (to < from) return null

        val before = calculator.stats(null, from.minusDays(1), today, settings, DayOfWeek.MONDAY).balanceMinutes
        var balance = before
        val days = calculator.days(from, to, today).map { result ->
            if (result.counted) balance += result.deltaMinutes
            toDay(result, calculator.scheduleFor(result.date), if (result.counted) balance else null)
        }
        val counted = days.filter { it.balanceMinutes != null }
        return Timesheet(
            from = from,
            to = to,
            days = days,
            workedMinutes = counted.sumOf { it.workedMinutes },
            targetMinutes = counted.sumOf { it.targetMinutes },
            deltaMinutes = counted.sumOf { it.deltaMinutes },
            balanceBeforeMinutes = before,
            balanceAfterMinutes = balance,
            generatedAt = now,
        )
    }

    private fun toDay(result: DayResult, schedule: DaySchedule, balance: Int?): TimesheetDay {
        val work = result.entries.filter { !it.isBreak }
        val kind = when {
            result.inProgress -> TimesheetDayKind.InProgress
            result.hasEntries -> TimesheetDayKind.Worked
            result.assumed -> TimesheetDayKind.Standard
            result.dayOff -> TimesheetDayKind.DayOff
            !result.counted -> TimesheetDayKind.Open
            else -> TimesheetDayKind.Free
        }
        val base = TimesheetDay(
            date = result.date,
            kind = kind,
            dayOff = result.dayOff,
            start = null,
            end = null,
            endsNextDay = false,
            breakMinutes = 0,
            workedMinutes = result.workedMinutes,
            targetMinutes = result.targetMinutes,
            deltaMinutes = result.deltaMinutes,
            balanceMinutes = balance,
            entries = result.entries,
        )
        return when (kind) {
            TimesheetDayKind.Worked, TimesheetDayKind.InProgress -> {
                val first = work.minOfOrNull { it.start } ?: result.entries.first().start
                val last = work.mapNotNull { it.end }.maxOrNull()
                val span = if (kind == TimesheetDayKind.Worked && last != null) minutesBetween(first, last) else 0
                base.copy(
                    start = first.toLocalTime(),
                    end = if (kind == TimesheetDayKind.Worked) last?.toLocalTime() else null,
                    endsNextDay = kind == TimesheetDayKind.Worked && last != null && last.toLocalDate() > result.date,
                    breakMinutes = (span - result.workedMinutes).coerceAtLeast(0),
                )
            }

            TimesheetDayKind.Standard -> base.copy(
                start = schedule.startMinute.minuteOfDayToTime(),
                end = schedule.endMinute.minuteOfDayToTime(),
                breakMinutes = schedule.breakMinutes,
            )

            TimesheetDayKind.DayOff, TimesheetDayKind.Free, TimesheetDayKind.Open -> base
        }
    }

    private fun minutesBetween(from: LocalDateTime, to: LocalDateTime): Int =
        java.time.Duration.between(from, to).toMinutes().toInt().coerceAtLeast(0)
}
