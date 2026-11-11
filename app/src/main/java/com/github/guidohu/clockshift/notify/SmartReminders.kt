package com.github.guidohu.clockshift.notify

import com.github.guidohu.clockshift.data.AppState
import com.github.guidohu.clockshift.domain.minuteOfDayToTime
import com.github.guidohu.clockshift.domain.toMinuteOfDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class SmartReminderKind { CheckIn, CheckOut }

/**
 * A check-in or check-out reminder due at [at] for [date].
 * [suggested] is the time offered in the notification: the usual start for a check-in, the moment the
 * daily target was reached for a check-out.
 */
data class SmartReminder(
    val kind: SmartReminderKind,
    val at: LocalDateTime,
    val date: LocalDate,
    val suggested: LocalDateTime,
)

/** The last day each kind of reminder was shown, so each fires at most once a day. */
data class SmartReminderLog(val checkInDay: LocalDate? = null, val checkOutDay: LocalDate? = null) {
    fun shown(reminder: SmartReminder): SmartReminderLog = when (reminder.kind) {
        SmartReminderKind.CheckIn -> copy(checkInDay = reminder.date)
        SmartReminderKind.CheckOut -> copy(checkOutDay = reminder.date)
    }
}

/**
 * Decides when to remind the user to check in or out.
 *
 *  - Work start: on a working day with nothing logged yet, an hour after the planned start. With Pro,
 *    the start is the time the user usually starts (learned from the last few same weekdays, falling
 *    back to the schedule) and the delay is the user's choice. No reminder once the usual end of the
 *    day has passed.
 *  - Daily hours reached (Pro): while checked in and working (not on a break), some minutes after the
 *    daily target was reached.
 */
object SmartReminderPlanner {
    /** Same weekdays looked at to learn the usual start, and how many are needed to trust them. */
    private const val LEARN_DAYS = 8
    private const val MIN_SAMPLES = 3

    /** Days ahead searched for the next check-in reminder. */
    private const val LOOKAHEAD_DAYS = 7L

    /** Without Pro the work start reminder comes a fixed hour after the planned start. */
    private const val FREE_CHECK_IN_DELAY = 60L

    fun next(state: AppState, log: SmartReminderLog, now: LocalDateTime, pro: Boolean): SmartReminder? {
        val settings = state.settings.smartReminders
        val time = now.truncatedTo(ChronoUnit.MINUTES)
        val candidates = listOfNotNull(
            if (settings.checkIn) nextCheckIn(state, log, time, pro) else null,
            if (settings.checkOut && pro) nextCheckOut(state, log, time) else null,
        )
        return candidates.minByOrNull { it.at }
    }

    /** Median start of the first work block on the last few days that share [date]'s weekday. */
    fun usualStartMinute(state: AppState, date: LocalDate): Int {
        val starts = state.entries
            .asSequence()
            .filter { !it.isBreak && it.date < date && it.date.dayOfWeek == date.dayOfWeek }
            .groupBy { it.date }
            .entries
            .sortedByDescending { it.key }
            .take(LEARN_DAYS)
            .map { (_, blocks) -> blocks.minOf { it.start }.toLocalTime().toMinuteOfDay() }
            .sorted()
        if (starts.size < MIN_SAMPLES) return state.scheduleFor(date).startMinute
        val middle = starts.size / 2
        return if (starts.size % 2 == 1) starts[middle] else (starts[middle - 1] + starts[middle]) / 2
    }

    private fun nextCheckIn(state: AppState, log: SmartReminderLog, now: LocalDateTime, pro: Boolean): SmartReminder? {
        if (state.activeEntry != null) return null
        val delay = if (pro) state.settings.smartReminders.checkInDelayMinutes.toLong() else FREE_CHECK_IN_DELAY
        val today = now.toLocalDate()
        for (offset in 0..LOOKAHEAD_DAYS) {
            val date = today.plusDays(offset)
            val schedule = state.scheduleFor(date)
            if (!schedule.working || schedule.targetMinutes <= 0) continue
            if (date in state.daysOff || date < state.settings.startDate) continue
            if (date == today && (log.checkInDay == today || state.entriesOn(today).any { !it.isBreak })) continue

            val startMinute = if (pro) usualStartMinute(state, date) else schedule.startMinute
            val usual = date.atTime(startMinute.minuteOfDayToTime())
            val due = usual.plusMinutes(delay)
            val windowEnd = maxOf(date.atTime(schedule.endMinute.minuteOfDayToTime()), due.plusHours(1))
            return when {
                due > now -> SmartReminder(SmartReminderKind.CheckIn, due, date, usual)
                now < windowEnd -> SmartReminder(SmartReminderKind.CheckIn, now, date, usual)
                else -> continue
            }
        }
        return null
    }

    private fun nextCheckOut(state: AppState, log: SmartReminderLog, now: LocalDateTime): SmartReminder? {
        val active = state.activeEntry ?: return null
        if (active.isBreak) return null
        val date = active.date
        // A session forgotten for days is the "still checked in?" card's business, not a reminder's.
        if (date < now.toLocalDate().minusDays(1) || log.checkOutDay == date) return null
        val target = if (date in state.daysOff) 0 else state.scheduleFor(date).targetMinutes
        if (target <= 0) return null

        val worked = state.entriesOn(date).filter { !it.isBreak }.sumOf { it.minutesAt(now) }
        val reached = now.plusMinutes((target - worked).toLong())
        val due = reached.plusMinutes(state.settings.smartReminders.checkOutDelayMinutes.toLong())
        return SmartReminder(SmartReminderKind.CheckOut, maxOf(due, now), date, reached)
    }
}
