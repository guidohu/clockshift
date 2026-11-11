package com.github.guidohu.clockshift

import com.github.guidohu.clockshift.backup.BackupCodec
import com.github.guidohu.clockshift.backup.BackupData
import com.github.guidohu.clockshift.backup.BackupFormatException
import com.github.guidohu.clockshift.data.DayOffEntity
import com.github.guidohu.clockshift.data.EntryEntity
import com.github.guidohu.clockshift.data.ScheduleBreakEntity
import com.github.guidohu.clockshift.data.ScheduleDayEntity
import com.github.guidohu.clockshift.data.SettingsEntity
import com.github.guidohu.clockshift.domain.Entry
import com.github.guidohu.clockshift.domain.EntryKind
import com.github.guidohu.clockshift.domain.ScheduleVersion
import com.github.guidohu.clockshift.domain.Settings
import com.github.guidohu.clockshift.domain.TimesheetBuilder
import com.github.guidohu.clockshift.domain.TimesheetDayKind
import com.github.guidohu.clockshift.domain.WeekSchedule
import com.github.guidohu.clockshift.domain.toLocalMinutes
import com.github.guidohu.clockshift.export.TimesheetCsv
import com.github.guidohu.clockshift.export.TimesheetLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ExportAndBackupTest {
    private val monday = LocalDate.of(2026, 10, 5)

    private fun at(date: LocalDate, time: String): LocalDateTime =
        date.atTime(time.substringBefore(":").toInt(), time.substringAfter(":").toInt())

    private val schedules = listOf(ScheduleVersion(LocalDate.ofEpochDay(0), WeekSchedule.standard()))
    private val settings = Settings.initial(monday).copy(setupDone = true, openingBalanceMinutes = 60)

    /** Monday worked 8:30 with a lunch break, Tuesday a day off, Wednesday assumed, Saturday free. */
    private val entries = listOf(
        Entry(start = at(monday, "08:00"), end = at(monday, "12:00")),
        Entry(start = at(monday, "12:00"), end = at(monday, "12:30"), kind = EntryKind.Break),
        Entry(start = at(monday, "12:30"), end = at(monday, "17:00")),
    )
    private val daysOff = setOf(monday.plusDays(1))

    private fun sheet(to: LocalDate = monday.plusDays(6), now: LocalDateTime = at(monday.plusDays(7), "09:00")) =
        TimesheetBuilder.build(schedules, entries, daysOff, settings, monday, to, now)!!

    private val labels = TimesheetLabels(
        title = "Timesheet", appName = "ClockShift", date = "Date", weekday = "Weekday", type = "Type",
        start = "Start", end = "End", breakTime = "Break", worked = "Worked", target = "Target",
        overtime = "Overtime", balance = "Balance", duration = "Duration", work = "Work", breakEntry = "Break",
        total = "Total", balanceBefore = "Before", balanceAfter = "After", running = "Running",
        generated = "Generated %1\$s", pageOf = "Page %1\$d of %2\$d",
        kinds = TimesheetDayKind.entries.associateWith { it.name },
        dayOffSuffix = "(day off)",
        weekdayName = { it.dayOfWeek.name.lowercase() },
        dateText = { it.toString() },
        timeText = { it.toString() },
    )

    @Test
    fun `timesheet classifies days and carries the balance`() {
        val sheet = sheet()
        assertEquals(7, sheet.days.size)
        val (mon, tue, wed) = sheet.days
        assertEquals(TimesheetDayKind.Worked, mon.kind)
        assertEquals(LocalTime.of(8, 0), mon.start)
        assertEquals(LocalTime.of(17, 0), mon.end)
        assertEquals(30, mon.breakMinutes)
        assertEquals(8 * 60 + 30, mon.workedMinutes)
        assertEquals(30, mon.deltaMinutes)
        assertEquals(TimesheetDayKind.DayOff, tue.kind)
        assertEquals(TimesheetDayKind.Standard, wed.kind)
        assertEquals(LocalTime.of(9, 0), wed.start)
        assertEquals(TimesheetDayKind.Free, sheet.days[5].kind)

        // Opening balance 1 h, +30 min on Monday; the day off and the assumed days are neutral.
        assertEquals(60, sheet.balanceBeforeMinutes)
        assertEquals(90, mon.balanceMinutes)
        assertEquals(90, sheet.balanceAfterMinutes)
        assertEquals(30, sheet.deltaMinutes)
    }

    @Test
    fun `timesheet stops at today and leaves today open`() {
        val sheet = sheet(now = at(monday.plusDays(2), "10:00"))
        assertEquals(3, sheet.days.size)
        assertEquals(TimesheetDayKind.Open, sheet.days.last().kind)
        assertNull(sheet.days.last().balanceMinutes)
    }

    @Test
    fun `nothing to export before tracking started`() {
        assertNull(TimesheetBuilder.build(schedules, entries, daysOff, settings, monday.minusDays(10), monday.minusDays(1), at(monday, "09:00")))
    }

    @Test
    fun `csv has one row per day with decimal hours`() {
        val lines = TimesheetCsv.write(sheet(), labels, entries = false).removePrefix("﻿").trimEnd().split("\r\n")
        assertEquals("Date,Weekday,Type,Start,End,Break (h),Worked (h),Target (h),Overtime (h),Balance (h)", lines[0])
        assertEquals("2026-10-05,monday,Worked,08:00,17:00,0.50,8.50,8.00,0.50,1.50", lines[1])
        assertEquals("2026-10-06,tuesday,DayOff,,,,0.00,0.00,0.00,1.50", lines[2])
        assertEquals(8, lines.size)
    }

    @Test
    fun `csv entries mode lists each block`() {
        val lines = TimesheetCsv.write(sheet(), labels, entries = true).removePrefix("﻿").trimEnd().split("\r\n")
        assertEquals("Date,Weekday,Type,Start,End,Duration (h)", lines[0])
        assertEquals("2026-10-05,monday,Break,12:00,12:30,0.50", lines[2])
        assertEquals(4, lines.size)
    }

    @Test
    fun `negative hours and quoting`() {
        assertEquals("-0.50", TimesheetCsv.hours(-30))
        val quoted = labels.copy(weekdayName = { "Mon, \"short\"" })
        val line = TimesheetCsv.write(sheet(), quoted, entries = false).split("\r\n")[1]
        assertTrue(line, line.contains("\"Mon, \"\"short\"\"\""))
    }

    private val backup = BackupData(
        createdAt = at(monday, "18:00"),
        appVersion = "1.5",
        settings = SettingsEntity(
            setupDone = true, startDate = monday.toEpochDay(), openingBalanceMinutes = -45, reminderEnabled = false,
            reminderMinute = 1080, autoTrackMode = 1, autoTrackWifi = "Office\nOffice 5G", autoTrackLatitude = 47.37,
            autoTrackLongitude = 8.54, checkOutReminder = true,
        ),
        scheduleDays = listOf(ScheduleDayEntity(0, 1, true, 540, 1050, 480)),
        scheduleBreaks = listOf(ScheduleBreakEntity(0, 1, 0, 720, 30)),
        entries = listOf(
            EntryEntity(id = 7, date = monday.toEpochDay(), startAt = at(monday, "08:00").toLocalMinutes(), endAt = at(monday, "12:00").toLocalMinutes()),
            EntryEntity(id = 8, date = monday.toEpochDay(), startAt = at(monday, "12:30").toLocalMinutes(), endAt = null, kind = 1),
        ),
        daysOff = listOf(DayOffEntity(monday.plusDays(1).toEpochDay())),
    )

    @Test
    fun `backup survives a round trip`() {
        val json = BackupCodec.encode(backup)
        assertTrue(json.contains("\"start\": \"2026-10-05T08:00\""))
        assertEquals(backup, BackupCodec.decode(json))
    }

    @Test
    fun `other files and newer formats are refused`() {
        listOf("not json", "{}", """{"app":"Other","format":1}""", """{"app":"ClockShift","format":99,"createdAt":"2026-10-05T18:00"}""")
            .forEach { text ->
                try {
                    BackupCodec.decode(text)
                    throw AssertionError("accepted: $text")
                } catch (expected: BackupFormatException) {
                    // refused as it should be
                }
            }
    }

    @Test
    fun `settings missing from an older backup fall back to defaults`() {
        val minimal = """{"app":"ClockShift","format":1,"createdAt":"2026-10-05T18:00","settings":{"startDate":"2026-10-05"}}"""
        val settings = BackupCodec.decode(minimal).settings!!
        assertEquals(monday.toEpochDay(), settings.startDate)
        assertTrue(settings.checkInReminder)
        assertEquals(150, settings.autoTrackRadius)
    }
}
