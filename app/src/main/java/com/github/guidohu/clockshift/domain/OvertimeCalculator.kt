package com.github.guidohu.clockshift.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Outcome of one calendar day. [counted] is false while the day is still open (today / running session). */
data class DayResult(
    val date: LocalDate,
    val targetMinutes: Int,
    val workedMinutes: Int,
    val entries: List<Entry>,
    val dayOff: Boolean,
    val assumed: Boolean,
    val inProgress: Boolean,
    val counted: Boolean,
) {
    val deltaMinutes: Int get() = if (counted) workedMinutes - targetMinutes else 0
    /** True when any work was logged (breaks alone do not make a logged day). */
    val hasEntries: Boolean get() = entries.any { !it.isBreak }
}

enum class Granularity { DAY, WEEK, MONTH }

data class PeriodStat(
    val start: LocalDate,
    val end: LocalDate,
    val workedMinutes: Int,
    val targetMinutes: Int,
    val deltaMinutes: Int,
)

data class RangeStats(
    val from: LocalDate,
    val to: LocalDate,
    val workedMinutes: Int,
    val targetMinutes: Int,
    val deltaMinutes: Int,
    val openingBalanceMinutes: Int,
    val loggedDays: Int,
    val assumedDays: Int,
    val dayOffs: Int,
    val todayPending: Boolean,
    val granularity: Granularity,
    val periods: List<PeriodStat>,
) {
    val balanceMinutes: Int get() = deltaMinutes + openingBalanceMinutes
    val isEmpty: Boolean get() = loggedDays + assumedDays + dayOffs == 0 && openingBalanceMinutes == 0
}

/**
 * Pure overtime maths. See PLAN.md §2 for the rules.
 *
 * @param schedules schedule versions in any order; the latest one with `effectiveFrom <= date` applies
 */
class OvertimeCalculator(
    schedules: List<ScheduleVersion>,
    entries: List<Entry>,
    private val daysOff: Set<LocalDate>,
) {
    private val schedules = schedules.sortedBy { it.effectiveFrom }
    private val entriesByDate: Map<LocalDate, List<Entry>> =
        entries.groupBy { it.date }.mapValues { (_, list) -> list.sortedBy { it.start } }

    /** First date for which anything is known: the configured start or an earlier entry/day off. */
    fun trackingStart(configuredStart: LocalDate): LocalDate {
        val earliest = (entriesByDate.keys + daysOff).minOrNull()
        return if (earliest != null && earliest < configuredStart) earliest else configuredStart
    }

    fun scheduleFor(date: LocalDate): DaySchedule {
        val week = schedules.lastOrNull { it.effectiveFrom <= date }?.week
            ?: schedules.firstOrNull()?.week
            ?: WeekSchedule.standard()
        return week[date.dayOfWeek]
    }

    fun day(date: LocalDate, today: LocalDate): DayResult {
        val dayOff = date in daysOff
        val schedule = scheduleFor(date)
        val target = if (dayOff) 0 else schedule.targetMinutes
        val dayEntries = entriesByDate[date].orEmpty()
        val completed = dayEntries.filter { !it.isActive && !it.isBreak }
        val active = dayEntries.any { it.isActive }
        val workedLogged = completed.sumOf { it.completedMinutes }

        return when {
            date > today -> result(date, target, 0, dayEntries, dayOff, counted = false)
            active -> result(date, target, workedLogged, dayEntries, dayOff, inProgress = true, counted = false)
            completed.isNotEmpty() -> result(date, target, workedLogged, dayEntries, dayOff, counted = true)
            dayOff -> result(date, 0, 0, dayEntries, dayOff, counted = true)
            date < today -> result(date, target, target, dayEntries, dayOff, assumed = target > 0, counted = true)
            else -> result(date, target, 0, dayEntries, dayOff, counted = false)
        }
    }

    private fun result(
        date: LocalDate,
        target: Int,
        worked: Int,
        entries: List<Entry>,
        dayOff: Boolean,
        assumed: Boolean = false,
        inProgress: Boolean = false,
        counted: Boolean,
    ) = DayResult(date, target, worked, entries, dayOff, assumed, inProgress, counted)

    fun days(from: LocalDate, to: LocalDate, today: LocalDate): List<DayResult> {
        if (to < from) return emptyList()
        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { it <= to }
            .map { day(it, today) }
            .toList()
    }

    /**
     * Statistics for the requested range, clipped to `[trackingStart, today]`.
     * The opening balance is only part of ranges that reach back to the tracking start.
     */
    fun stats(
        requestedFrom: LocalDate?,
        requestedTo: LocalDate,
        today: LocalDate,
        settings: Settings,
        firstDayOfWeek: DayOfWeek,
    ): RangeStats {
        val start = trackingStart(settings.startDate)
        val from = maxOf(requestedFrom ?: start, start)
        val to = minOf(requestedTo, today)
        val results = days(from, to, today)
        val counted = results.filter { it.counted }
        val includesOpening = requestedFrom == null || requestedFrom <= start
        val granularity = granularityFor(from, to)

        val periods = results
            .groupBy { periodKey(it.date, granularity, firstDayOfWeek) }
            .map { (_, group) ->
                val groupCounted = group.filter { it.counted }
                PeriodStat(
                    start = group.first().date,
                    end = group.last().date,
                    workedMinutes = groupCounted.sumOf { it.workedMinutes },
                    targetMinutes = groupCounted.sumOf { it.targetMinutes },
                    deltaMinutes = groupCounted.sumOf { it.deltaMinutes },
                )
            }
            .filter { granularity == Granularity.DAY || it.workedMinutes != 0 || it.targetMinutes != 0 }

        return RangeStats(
            from = from,
            to = to,
            workedMinutes = counted.sumOf { it.workedMinutes },
            targetMinutes = counted.sumOf { it.targetMinutes },
            deltaMinutes = counted.sumOf { it.deltaMinutes },
            openingBalanceMinutes = if (includesOpening) settings.openingBalanceMinutes else 0,
            loggedDays = counted.count { it.hasEntries },
            assumedDays = counted.count { it.assumed },
            dayOffs = counted.count { it.dayOff && !it.hasEntries },
            todayPending = results.any { it.date == today && !it.counted },
            granularity = granularity,
            periods = periods,
        )
    }

    private fun granularityFor(from: LocalDate, to: LocalDate): Granularity {
        val length = to.toEpochDay() - from.toEpochDay() + 1
        return when {
            length <= 14 -> Granularity.DAY
            length <= 130 -> Granularity.WEEK
            else -> Granularity.MONTH
        }
    }

    private fun periodKey(date: LocalDate, granularity: Granularity, firstDayOfWeek: DayOfWeek): Any =
        when (granularity) {
            Granularity.DAY -> date
            Granularity.WEEK -> date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            Granularity.MONTH -> YearMonth.from(date)
        }
}
