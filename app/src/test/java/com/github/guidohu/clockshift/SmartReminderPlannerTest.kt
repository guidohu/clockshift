package com.github.guidohu.clockshift

import com.github.guidohu.clockshift.data.AppState
import com.github.guidohu.clockshift.domain.Entry
import com.github.guidohu.clockshift.domain.EntryKind
import com.github.guidohu.clockshift.domain.ScheduleVersion
import com.github.guidohu.clockshift.domain.Settings
import com.github.guidohu.clockshift.domain.SmartReminderSettings
import com.github.guidohu.clockshift.domain.WeekSchedule
import com.github.guidohu.clockshift.notify.SmartReminder
import com.github.guidohu.clockshift.notify.SmartReminderKind
import com.github.guidohu.clockshift.notify.SmartReminderLog
import com.github.guidohu.clockshift.notify.SmartReminderPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SmartReminderPlannerTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val friday = monday.plusDays(4)
    private val saturday = monday.plusDays(5)

    private fun at(date: LocalDate, time: String): LocalDateTime =
        date.atTime(time.substringBefore(":").toInt(), time.substringAfter(":").toInt())

    /** Mon–Fri 09:00–17:30, 8 h target; the work start reminder on by default, as for new users. */
    private fun state(
        entries: List<Entry> = emptyList(),
        daysOff: Set<LocalDate> = emptySet(),
        reminders: SmartReminderSettings = SmartReminderSettings(),
    ) = AppState(
        settings = Settings.initial(monday.minusYears(1)).copy(setupDone = true, smartReminders = reminders),
        schedules = listOf(ScheduleVersion(LocalDate.ofEpochDay(0), WeekSchedule.standard())),
        entries = entries,
        daysOff = daysOff,
    )

    private fun next(state: AppState, now: LocalDateTime, pro: Boolean = false, log: SmartReminderLog = SmartReminderLog()) =
        SmartReminderPlanner.next(state, log, now, pro)

    @Test
    fun `work start reminder comes an hour after the planned start`() {
        assertEquals(
            SmartReminder(SmartReminderKind.CheckIn, at(monday, "10:00"), monday, at(monday, "09:00")),
            next(state(), at(monday, "07:00")),
        )
    }

    @Test
    fun `no work start reminder once checked in or logged`() {
        val running = Entry(start = at(monday, "08:40"), end = null)
        assertNull(next(state(listOf(running)), at(monday, "09:30")))
        val logged = Entry(start = at(monday, "07:00"), end = at(monday, "09:00"))
        // Today is done, so the next reminder is tomorrow's.
        assertEquals(monday.plusDays(1), next(state(listOf(logged)), at(monday, "09:30"))?.date)
    }

    @Test
    fun `days off and weekends are skipped`() {
        assertEquals(monday.plusDays(7), next(state(daysOff = setOf(monday)), at(saturday, "08:00"))?.date)
        assertEquals(friday.plusDays(3), next(state(), at(friday, "18:00"))?.date)
    }

    @Test
    fun `an overdue reminder fires now, but not after the usual end of the day`() {
        assertEquals(at(monday, "11:15"), next(state(), at(monday, "11:15"))?.at)
        assertEquals(monday.plusDays(1), next(state(), at(monday, "18:00"))?.date)
    }

    @Test
    fun `a reminder already shown today is not repeated`() {
        val log = SmartReminderLog(checkInDay = monday)
        assertEquals(monday.plusDays(1), next(state(), at(monday, "10:01"), log = log)?.date)
    }

    @Test
    fun `switched off means no reminder`() {
        assertNull(next(state(reminders = SmartReminderSettings(checkIn = false)), at(monday, "07:00")))
    }

    @Test
    fun `with Pro the reminder follows the learned start and the chosen delay`() {
        // The last three Mondays started at 07:30, 07:45 and 08:00: median 07:45.
        val history = listOf("07:30", "07:45", "08:00").mapIndexed { index, start ->
            val date = monday.minusWeeks(index + 1L)
            Entry(start = at(date, start), end = at(date, "16:00"))
        }
        val reminders = SmartReminderSettings(checkInDelayMinutes = 20)
        assertEquals(
            SmartReminder(SmartReminderKind.CheckIn, at(monday, "08:05"), monday, at(monday, "07:45")),
            next(state(history, reminders = reminders), at(monday, "06:00"), pro = true),
        )
        // Without Pro the planned start and the fixed hour apply.
        assertEquals(at(monday, "10:00"), next(state(history, reminders = reminders), at(monday, "06:00"))?.at)
    }

    @Test
    fun `daily hours reached reminder needs Pro and counts breaks out`() {
        val reminders = SmartReminderSettings(checkIn = false, checkOut = true, checkOutDelayMinutes = 15)
        val entries = listOf(
            Entry(start = at(monday, "08:00"), end = at(monday, "12:00")),
            Entry(start = at(monday, "12:00"), end = at(monday, "12:45"), kind = EntryKind.Break),
            Entry(start = at(monday, "12:45"), end = null),
        )
        val now = at(monday, "14:00")
        // 4 h before lunch + 1:15 since: 8 h are reached at 16:45, the reminder comes at 17:00.
        assertEquals(
            SmartReminder(SmartReminderKind.CheckOut, at(monday, "17:00"), monday, at(monday, "16:45")),
            next(state(entries, reminders = reminders), now, pro = true),
        )
        assertNull(next(state(entries, reminders = reminders), now, pro = false))
    }

    @Test
    fun `no check-out reminder during a break`() {
        val reminders = SmartReminderSettings(checkIn = false, checkOut = true)
        val entries = listOf(
            Entry(start = at(monday, "08:00"), end = at(monday, "12:00")),
            Entry(start = at(monday, "12:00"), end = null, kind = EntryKind.Break),
        )
        assertNull(next(state(entries, reminders = reminders), at(monday, "12:10"), pro = true))
    }
}
