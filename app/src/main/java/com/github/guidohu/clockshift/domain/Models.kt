package com.github.guidohu.clockshift.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** A scheduled break: starts at [startMinute] (minutes since midnight) and lasts [minutes]. */
data class ScheduleBreak(val startMinute: Int, val minutes: Int) {
    val endMinute: Int get() = startMinute + minutes
}

/**
 * A single weekday. [target] is how long the user is expected to work on it, as stated by the user;
 * it is independent of the times, so a usual day may well end up above or below it. The times are
 * minutes since midnight and describe the usual day (used to fill in a standard day). The [breaks]
 * are not worked; they lie inside the working hours, in chronological order and without overlaps
 * (see [normalized]).
 */
data class DaySchedule(
    val working: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    val breaks: List<ScheduleBreak>,
    val target: Int,
) {
    /** Total break time of the day. */
    val breakMinutes: Int get() = breaks.sumOf { it.minutes }

    /** Minutes expected on this weekday; 0 when it is not a working day. */
    val targetMinutes: Int get() = if (working) target.coerceAtLeast(0) else 0

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

    /**
     * The same week with [weeklyMinutes] spread evenly over the working days. Minutes that do not
     * divide evenly go to the first working days, one each.
     */
    fun withWeeklyTarget(weeklyMinutes: Int): WeekSchedule {
        val working = DayOfWeek.entries.filter { this[it].working }
        if (working.isEmpty()) return this
        val base = weeklyMinutes / working.size
        val remainder = weeklyMinutes % working.size
        val targets = working.withIndex().associate { (index, day) -> day to base + if (index < remainder) 1 else 0 }
        return WeekSchedule(days.mapValues { (day, schedule) -> targets[day]?.let { schedule.copy(target = it) } ?: schedule })
    }

    /** Latest end of all working days, used to suggest a reminder time. */
    val latestEndMinute: Int? get() = days.values.filter { it.working }.maxOfOrNull { it.endMinute }

    companion object {
        const val DEFAULT_START = 9 * 60
        const val DEFAULT_END = 17 * 60 + 30
        const val DEFAULT_BREAK = 30
        const val DEFAULT_TARGET = 8 * 60
        const val DEFAULT_BREAK_START = 12 * 60
        const val EXTRA_BREAK = 15

        fun standard(): WeekSchedule = WeekSchedule(
            DayOfWeek.entries.associateWith { day ->
                DaySchedule(
                    working = day < DayOfWeek.SATURDAY,
                    startMinute = DEFAULT_START,
                    endMinute = DEFAULT_END,
                    breaks = listOf(ScheduleBreak(DEFAULT_BREAK_START, DEFAULT_BREAK)),
                    target = DEFAULT_TARGET,
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
    val autoTrack: AutoTrackSettings = AutoTrackSettings(),
    val smartReminders: SmartReminderSettings = SmartReminderSettings(),
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

/** How ClockShift notices that the user is at work (Pro). */
enum class AutoTrackMode { Off, Wifi, Location }

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Automatic check-in and check-out (Pro). With [askFirst] the app only suggests the action in a
 * notification; otherwise it acts and offers undo. A departure counts once the user has been away for
 * [leaveDelayMinutes], so a dropped Wi-Fi connection or GPS jitter does not end the day.
 */
data class AutoTrackSettings(
    val mode: AutoTrackMode = AutoTrackMode.Off,
    val wifiNames: List<String> = emptyList(),
    val workplace: GeoPoint? = null,
    val radiusMeters: Int = DEFAULT_RADIUS,
    val askFirst: Boolean = false,
    val workingDaysOnly: Boolean = true,
    val leaveDelayMinutes: Int = DEFAULT_LEAVE_DELAY,
) {
    /** True when the chosen mode has everything it needs to run. */
    val isConfigured: Boolean
        get() = when (mode) {
            AutoTrackMode.Off -> false
            AutoTrackMode.Wifi -> wifiNames.isNotEmpty()
            AutoTrackMode.Location -> workplace != null
        }

    fun matchesWifi(name: String?): Boolean =
        name != null && wifiNames.any { it.trim().equals(name.trim(), ignoreCase = true) }

    companion object {
        const val DEFAULT_RADIUS = 150
        const val MIN_RADIUS = 100
        const val MAX_RADIUS = 1000
        const val DEFAULT_LEAVE_DELAY = 10
        val LEAVE_DELAY_OPTIONS = listOf(5, 10, 15, 30)
    }
}

/**
 * Reminders to check in and out.
 *
 *  - Work start ([checkIn], free): on a working day without any entry, [checkInDelayMinutes] after the
 *    planned start. With Pro the app uses the time the user usually starts instead, and the delay can
 *    be changed.
 *  - Daily hours reached ([checkOut], Pro): while checked in, [checkOutDelayMinutes] after the daily
 *    target was reached.
 *
 * The work end reminder is the evening reminder ([Settings.reminderEnabled]).
 */
data class SmartReminderSettings(
    val checkIn: Boolean = true,
    val checkInDelayMinutes: Int = DEFAULT_CHECK_IN_DELAY,
    val checkOut: Boolean = false,
    val checkOutDelayMinutes: Int = DEFAULT_CHECK_OUT_DELAY,
) {
    companion object {
        const val DEFAULT_CHECK_IN_DELAY = 60
        const val DEFAULT_CHECK_OUT_DELAY = 15
        const val MAX_DELAY = 3 * 60
    }
}
