package com.github.guidohu.clockshift

import com.github.guidohu.clockshift.domain.DaySchedule
import com.github.guidohu.clockshift.data.LegacyEntry
import com.github.guidohu.clockshift.data.convertLegacyEntry
import com.github.guidohu.clockshift.domain.Entry
import com.github.guidohu.clockshift.domain.EntryKind
import com.github.guidohu.clockshift.domain.OvertimeCalculator
import com.github.guidohu.clockshift.domain.ScheduleVersion
import com.github.guidohu.clockshift.domain.Settings
import com.github.guidohu.clockshift.domain.WeekSchedule
import com.github.guidohu.clockshift.domain.formatDuration
import com.github.guidohu.clockshift.domain.formatSigned
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class OvertimeCalculatorTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val tuesday = monday.plusDays(1)
    private val wednesday = monday.plusDays(2)
    private val thursday = monday.plusDays(3)
    private val saturday = monday.plusDays(5)

    /** Mon–Fri 09:00–17:30 with 30 min break = 8 h. */
    private val standard = ScheduleVersion(LocalDate.ofEpochDay(0), WeekSchedule.standard())

    private fun at(date: LocalDate, time: String) =
        date.atTime(time.substringBefore(":").toInt(), time.substringAfter(":").toInt())

    /** One work entry, or work-break-work with the break centred when [breakMinutes] > 0. */
    private fun entry(date: LocalDate, from: String, to: String?, breakMinutes: Int = 30): List<Entry> {
        val start = at(date, from)
        val end = to?.let { at(date, it) }
        if (end == null || breakMinutes == 0) return listOf(Entry(start = start, end = end))
        val gross = java.time.Duration.between(start, end).toMinutes()
        val breakStart = start.plusMinutes((gross - breakMinutes) / 2)
        val breakEnd = breakStart.plusMinutes(breakMinutes.toLong())
        return listOf(
            Entry(start = start, end = breakStart),
            Entry(start = breakStart, end = breakEnd, kind = EntryKind.Break),
            Entry(start = breakEnd, end = end),
        )
    }

    private fun calculator(
        entries: List<Entry> = emptyList(),
        daysOff: Set<LocalDate> = emptySet(),
        schedules: List<ScheduleVersion> = listOf(standard),
    ) = OvertimeCalculator(schedules, entries, daysOff)

    private fun settings(start: LocalDate = monday, opening: Int = 0) =
        Settings.initial(start).copy(startDate = start, openingBalanceMinutes = opening)

    @Test
    fun `unlogged past working days are assumed standard and add no overtime`() {
        val day = calculator().day(monday, thursday)
        assertTrue(day.assumed)
        assertEquals(8 * 60, day.workedMinutes)
        assertEquals(0, day.deltaMinutes)
    }

    @Test
    fun `longer and shorter days produce signed overtime`() {
        val calc = calculator(
            entry(monday, "08:30", "18:00") + entry(tuesday, "09:00", "16:00"),
        )
        assertEquals(60, calc.day(monday, thursday).deltaMinutes)
        assertEquals(-90, calc.day(tuesday, thursday).deltaMinutes)
        val stats = calc.stats(null, thursday, thursday, settings(), DayOfWeek.MONDAY)
        assertEquals(-30, stats.balanceMinutes)
    }

    @Test
    fun `weekend work is pure overtime`() {
        val calc = calculator(entry(saturday, "10:00", "12:00", breakMinutes = 0))
        val day = calc.day(saturday, saturday.plusDays(2))
        assertEquals(0, day.targetMinutes)
        assertEquals(120, day.deltaMinutes)
    }

    @Test
    fun `day off removes the target`() {
        val calc = calculator(daysOff = setOf(tuesday))
        val day = calc.day(tuesday, thursday)
        assertTrue(day.dayOff)
        assertEquals(0, day.targetMinutes)
        assertEquals(0, day.deltaMinutes)
    }

    @Test
    fun `today is not counted while checked in or before anything is logged`() {
        val running = calculator(entry(thursday, "09:00", null))
        assertFalse(running.day(thursday, thursday).counted)
        assertFalse(calculator().day(thursday, thursday).counted)
        val finished = calculator(entry(thursday, "09:00", "18:30"))
        assertEquals(60, finished.day(thursday, thursday).deltaMinutes)
    }

    @Test
    fun `schedule changes only affect days from the effective date`() {
        val shorter = WeekSchedule(
            DayOfWeek.entries.associateWith { DaySchedule(it < DayOfWeek.SATURDAY, 9 * 60, 15 * 60, emptyList()) },
        )
        val calc = calculator(schedules = listOf(standard, ScheduleVersion(wednesday, shorter)))
        assertEquals(8 * 60, calc.scheduleFor(tuesday).targetMinutes)
        assertEquals(6 * 60, calc.scheduleFor(wednesday).targetMinutes)
    }

    @Test
    fun `opening balance only belongs to ranges reaching the tracking start`() {
        val calc = calculator(entry(tuesday, "09:00", "18:30"))
        val all = calc.stats(null, thursday, thursday, settings(opening = 120), DayOfWeek.MONDAY)
        assertEquals(60 + 120, all.balanceMinutes)
        val later = calc.stats(wednesday, thursday, thursday, settings(opening = 120), DayOfWeek.MONDAY)
        assertEquals(0, later.balanceMinutes)
    }

    @Test
    fun `tracking start moves back to earlier entries`() {
        val calc = calculator(entry(monday.minusDays(7), "09:00", "18:30"))
        assertEquals(monday.minusDays(7), calc.trackingStart(monday))
    }

    @Test
    fun `short ranges are grouped by day and long ones by week`() {
        val calc = calculator()
        val week = calc.stats(monday, monday.plusDays(6), monday.plusDays(6), settings(), DayOfWeek.MONDAY)
        assertEquals(7, week.periods.size)
        val quarter = calc.stats(monday, monday.plusDays(60), monday.plusDays(60), settings(), DayOfWeek.MONDAY)
        assertTrue(quarter.periods.size in 8..10)
    }

    @Test
    fun `breaks do not count as work and a running break freezes the day`() {
        val entries = entry(thursday, "09:00", "18:00", breakMinutes = 60)
        val finished = calculator(entries).day(thursday, thursday)
        assertEquals(8 * 60, finished.workedMinutes)
        val onBreak = calculator(listOf(Entry(start = at(thursday, "09:00"), end = at(thursday, "12:00")), Entry(start = at(thursday, "12:00"), end = null, kind = EntryKind.Break)))
        val result = onBreak.day(thursday, thursday)
        assertTrue(result.inProgress)
        assertFalse(result.counted)
        assertEquals(180, result.workedMinutes)
    }

    @Test
    fun `entry minutes are live while running`() {
        val running = Entry(start = at(thursday, "09:00"), end = null)
        assertEquals(90, running.minutesAt(at(thursday, "10:30")))
        assertEquals(5400L, running.secondsAt(at(thursday, "10:30")))
    }

    @Test
    fun `legacy sessions with a break are split into work break work`() {
        val gross = 9 * 60L
        val legacy = LegacyEntry(1, 0, 1000, 1000 + gross, breakMinutes = 60, breakStartedAt = null)
        val blocks = convertLegacyEntry(legacy)!!
        assertEquals(listOf(0, 1, 0), blocks.map { it.kind })
        assertEquals(1000L, blocks.first().startAt)
        assertEquals(1000 + gross, blocks.last().endAt)
        assertEquals(60L, blocks[1].endAt!! - blocks[1].startAt)
        assertEquals(null, convertLegacyEntry(legacy.copy(breakMinutes = 0)))
        val onBreak = convertLegacyEntry(LegacyEntry(2, 0, 1000, null, 0, 1200))!!
        assertEquals(listOf(0, 1), onBreak.map { it.kind })
        assertEquals(null, onBreak[1].endAt)
    }

    @Test
    fun `durations are formatted compactly`() {
        assertEquals("8h 30m", formatDuration(510))
        assertEquals("45m", formatDuration(45))
        assertEquals("2h", formatDuration(120))
        assertEquals("+1h 15m", formatSigned(75))
        assertEquals("−45m", formatSigned(-45))
        assertEquals("0m", formatSigned(0))
    }
}
