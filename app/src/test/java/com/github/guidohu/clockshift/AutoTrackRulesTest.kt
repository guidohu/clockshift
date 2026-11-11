package com.github.guidohu.clockshift

import com.github.guidohu.clockshift.domain.AutoTrackDay
import com.github.guidohu.clockshift.domain.AutoTrackDecision
import com.github.guidohu.clockshift.domain.AutoTrackMode
import com.github.guidohu.clockshift.domain.AutoTrackRules
import com.github.guidohu.clockshift.domain.AutoTrackSettings
import com.github.guidohu.clockshift.domain.Entry
import com.github.guidohu.clockshift.domain.GeoPoint
import com.github.guidohu.clockshift.domain.Presence
import com.github.guidohu.clockshift.domain.PresenceSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class AutoTrackRulesTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val saturday = monday.plusDays(5)

    private val wifi = AutoTrackSettings(mode = AutoTrackMode.Wifi, wifiNames = listOf("Office"), leaveDelayMinutes = 10)
    private val geo = AutoTrackSettings(mode = AutoTrackMode.Location, workplace = GeoPoint(47.0, 8.0), leaveDelayMinutes = 10)

    private fun at(date: LocalDate, time: String): LocalDateTime =
        date.atTime(time.substringBefore(":").toInt(), time.substringAfter(":").toInt())

    private fun day(active: Entry? = null, logged: Set<LocalDate> = emptySet()) = object : AutoTrackDay {
        override val active = active
        override fun hasEntriesOn(date: LocalDate) = date in logged
        override fun isWorkingDay(date: LocalDate) = date.dayOfWeek < DayOfWeek.SATURDAY
    }

    @Test
    fun `arriving on a working day checks in at the arrival time`() {
        val step = AutoTrackRules.step(Presence(), PresenceSignal.Arrived(at(monday, "08:52")), geo, day(), at(monday, "08:53"))
        assertEquals(AutoTrackDecision.CheckIn(at(monday, "08:52")), step.decision)
        assertTrue(step.presence.atWork)
        assertNull("a geofence needs no polling", step.nextCheckAt)
    }

    @Test
    fun `no check-in on a weekend unless allowed`() {
        val arrive = PresenceSignal.Arrived(at(saturday, "10:00"))
        assertEquals(AutoTrackDecision.None, AutoTrackRules.step(Presence(), arrive, geo, day(), at(saturday, "10:00")).decision)
        val anyDay = geo.copy(workingDaysOnly = false)
        assertEquals(
            AutoTrackDecision.CheckIn(at(saturday, "10:00")),
            AutoTrackRules.step(Presence(), arrive, anyDay, day(), at(saturday, "10:00")).decision,
        )
    }

    @Test
    fun `no check-in while already checked in`() {
        val running = Entry(start = at(monday, "07:30"), end = null)
        val step = AutoTrackRules.step(Presence(), PresenceSignal.Arrived(at(monday, "08:30")), geo, day(running), at(monday, "08:30"))
        assertEquals(AutoTrackDecision.None, step.decision)
        assertTrue(step.presence.atWork)
    }

    @Test
    fun `a state report does not reopen a day that already has entries`() {
        val report = PresenceSignal.Arrived(at(monday, "17:40"), initial = true)
        val logged = day(logged = setOf(monday))
        assertEquals(AutoTrackDecision.None, AutoTrackRules.step(Presence(), report, geo, logged, at(monday, "17:40")).decision)
        // The first report of an empty day still checks in, e.g. after setting up at the desk in the morning.
        assertEquals(
            AutoTrackDecision.CheckIn(at(monday, "08:10")),
            AutoTrackRules.step(Presence(), report.copy(at = at(monday, "08:10")), geo, day(), at(monday, "08:10")).decision,
        )
    }

    @Test
    fun `leaving the geofence checks out at the exit time once away long enough`() {
        val running = Entry(start = at(monday, "08:52"), end = null)
        val present = Presence(since = at(monday, "08:52"), lastSeen = at(monday, "08:52"))

        val left = AutoTrackRules.step(present, PresenceSignal.Left(at(monday, "17:31")), geo, day(running), at(monday, "17:33"))
        assertEquals(AutoTrackDecision.None, left.decision)
        assertEquals(at(monday, "17:41"), left.nextCheckAt)

        val confirmed = AutoTrackRules.step(left.presence, PresenceSignal.Check(at(monday, "17:42"), null), geo, day(running), at(monday, "17:42"))
        assertEquals(AutoTrackDecision.CheckOut(at(monday, "17:31")), confirmed.decision)
        assertFalse(confirmed.presence.atWork)
        assertNull(confirmed.nextCheckAt)
    }

    @Test
    fun `coming back before the delay keeps the day going`() {
        val running = Entry(start = at(monday, "08:52"), end = null)
        val present = Presence(since = at(monday, "08:52"), lastSeen = at(monday, "08:52"))
        val left = AutoTrackRules.step(present, PresenceSignal.Left(at(monday, "12:00")), geo, day(running), at(monday, "12:00"))
        val back = AutoTrackRules.step(left.presence, PresenceSignal.Arrived(at(monday, "12:06")), geo, day(running), at(monday, "12:06"))
        assertEquals(AutoTrackDecision.None, back.decision)
        assertNull(back.presence.leftAt)

        val check = AutoTrackRules.step(back.presence, PresenceSignal.Check(at(monday, "12:11"), null), geo, day(running), at(monday, "12:11"))
        assertEquals(AutoTrackDecision.None, check.decision)
        assertTrue(check.presence.atWork)
    }

    @Test
    fun `a session started after leaving is not ended`() {
        val present = Presence(since = at(monday, "08:00"), lastSeen = at(monday, "08:00"), leftAt = at(monday, "17:00"))
        val homeSession = Entry(start = at(monday, "17:05"), end = null)
        val step = AutoTrackRules.step(present, PresenceSignal.Check(at(monday, "17:11"), null), geo, day(homeSession), at(monday, "17:11"))
        assertEquals(AutoTrackDecision.None, step.decision)
        assertFalse(step.presence.atWork)
    }

    @Test
    fun `wifi polls while at work and checks out at the last time it was seen`() {
        val running = Entry(start = at(monday, "08:30"), end = null)
        val arrived = AutoTrackRules.step(Presence(), PresenceSignal.Arrived(at(monday, "08:30")), wifi, day(), at(monday, "08:30"))
        assertEquals(at(monday, "08:35"), arrived.nextCheckAt)

        val seen = AutoTrackRules.step(arrived.presence, PresenceSignal.Check(at(monday, "17:25"), true), wifi, day(running), at(monday, "17:25"))
        val gone = AutoTrackRules.step(seen.presence, PresenceSignal.Check(at(monday, "17:30"), false), wifi, day(running), at(monday, "17:30"))
        assertEquals(AutoTrackDecision.None, gone.decision)
        assertEquals(at(monday, "17:25"), gone.presence.leftAt)

        val confirmed = AutoTrackRules.step(gone.presence, PresenceSignal.Check(at(monday, "17:35"), false), wifi, day(running), at(monday, "17:35"))
        assertEquals(AutoTrackDecision.CheckOut(at(monday, "17:25")), confirmed.decision)
    }

    @Test
    fun `a wifi reconnect while at work changes nothing`() {
        val present = Presence(since = at(monday, "08:30"), lastSeen = at(monday, "10:00"))
        val step = AutoTrackRules.step(present, PresenceSignal.Arrived(at(monday, "10:02")), wifi, day(), at(monday, "10:02"))
        assertEquals(AutoTrackDecision.None, step.decision)
        assertEquals(at(monday, "08:30"), step.presence.since)
    }

    @Test
    fun `a geofence entry while thought present means the exit was missed`() {
        val stale = Presence(since = at(monday, "08:00"), lastSeen = at(monday, "08:00"))
        val step = AutoTrackRules.step(stale, PresenceSignal.Arrived(at(monday, "13:00")), geo, day(), at(monday, "13:00"))
        assertEquals(AutoTrackDecision.CheckIn(at(monday, "13:00")), step.decision)
    }

    @Test
    fun `presence from the day before is forgotten`() {
        val yesterday = Presence(since = at(monday, "08:00"), lastSeen = at(monday, "17:00"))
        val tuesday = monday.plusDays(1)
        val step = AutoTrackRules.step(yesterday, PresenceSignal.Arrived(at(tuesday, "09:30"), initial = true), wifi, day(), at(tuesday, "09:30"))
        assertEquals(AutoTrackDecision.CheckIn(at(tuesday, "09:30")), step.decision)
    }
}
