package com.clockshift.app.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** A scheduled break: starts at [startMinute] (minutes since midnight) and lasts [minutes]. */
data class ScheduleBreak(val startMinute: Int, val minutes: Int) {
    val endMinute: Int get() = startMinute + minutes
}

/**
 * Working hours of a single weekday. Times are minutes since midnight. The [breaks] are not worked;
 * they lie inside the working hours, in chronological order and without overlaps (see [normalized]).
 */
data class DaySchedule(
    val working: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    val breaks: List<ScheduleBreak>,
) {
    /** Total break time of the day. */
    val breakMinutes: Int get() = breaks.sumOf { it.minutes }

    /** Minutes expected on this weekday; 0 when it is not a working day. */
    val targetMinutes: Int
        get() = if (working) (endMinute - startMinute - breakMinutes).coerceAtLeast(0) else 0

    /**
     * Sorts the breaks, keeps them inside the working hours, pushes overlapping ones back and merges
     * breaks that touch. Breaks that no longer fit are dropped.
     */
    fun normalized(): DaySchedule {
        val result = mutableListOf<ScheduleBreak>()
        var earliest = startMinute
        for (candidate in breaks.filter { it.minutes > 0 }.sortedBy { it.startMinute }) {
            val from = candidate.startMinute.coerceAtLeast(earliest)
            val to = (from + candidate.minutes).coerceAtMost(endMinute)
            if (to <= from) continue
            val last = result.lastOrNull()
            if (last != null && last.endMinute == from) {
                result[result.lastIndex] = ScheduleBreak(last.startMinute, to - last.startMinute)
            } else {
                result += ScheduleBreak(from, to - from)
            }
            earliest = to
        }
        return copy(breaks = result)
    }
}

/** The configured standard week, Monday to Sunday. */
data class WeekSchedule(val days: Map<DayOfWeek, DaySchedule>) {
    operator fun get(day: DayOfWeek): DaySchedule = days.getValue(day)

    val weeklyTargetMinutes: Int get() = days.values.sumOf { it.targetMinutes }

    /** Latest end of all working days, used to suggest a reminder time. */
    val latestEndMinute: Int? get() = days.values.filter { it.working }.maxOfOrNull { it.endMinute }

    companion object {
        const val DEFAULT_START = 9 * 60
        const val DEFAULT_END = 17 * 60 + 30
        const val DEFAULT_BREAK = 30
        const val DEFAULT_BREAK_START = 12 * 60
        const val EXTRA_BREAK = 15

        fun standard(): WeekSchedule = WeekSchedule(
            DayOfWeek.entries.associateWith { day ->
                DaySchedule(
                    working = day < DayOfWeek.SATURDAY,
                    startMinute = DEFAULT_START,
                    endMinute = DEFAULT_END,
                    breaks = listOf(ScheduleBreak(DEFAULT_BREAK_START, DEFAULT_BREAK)),
                )
            },
        )
    }
}

/** A [WeekSchedule] that applies to all dates from [effectiveFrom] until the next version. */
data class ScheduleVersion(val effectiveFrom: LocalDate, val week: WeekSchedule)

enum class EntryKind { Work, Break }

/**
 * A block of time on a day: either work or a break. [end] is null while the block is still running
 * (the user is checked in, or on a break). Only work counts towards the hours worked.
 */
data class Entry(
    val id: Long = 0,
    val start: LocalDateTime,
    val end: LocalDateTime?,
    val kind: EntryKind = EntryKind.Work,
) {
    val date: LocalDate get() = start.toLocalDate()
    val isActive: Boolean get() = end == null
    val isBreak: Boolean get() = kind == EntryKind.Break

    /** Minutes of a finished block (0 while it is still running). */
    val completedMinutes: Int get() = if (end == null) 0 else minutesBetween(start, end)

    /** Minutes so far, whether the block is finished or running. */
    fun minutesAt(now: LocalDateTime): Int = minutesBetween(start, end ?: now)

    fun secondsAt(now: LocalDateTime): Long = Duration.between(start, end ?: now).seconds.coerceAtLeast(0)

    private fun minutesBetween(from: LocalDateTime, to: LocalDateTime): Int =
        Duration.between(from, to).toMinutes().toInt().coerceAtLeast(0)
}

/** User preferences that are not part of the schedule or the entries. */
data class Settings(
    val setupDone: Boolean,
    val startDate: LocalDate,
    val openingBalanceMinutes: Int,
    val reminderEnabled: Boolean,
    val reminderMinute: Int,
) {
    companion object {
        const val DEFAULT_REMINDER_MINUTE = 18 * 60

        fun initial(today: LocalDate) = Settings(
            setupDone = false,
            startDate = today,
            openingBalanceMinutes = 0,
            reminderEnabled = true,
            reminderMinute = DEFAULT_REMINDER_MINUTE,
        )
    }
}
