package com.github.guidohu.clockshift

import com.github.guidohu.clockshift.domain.AddOutcome
import com.github.guidohu.clockshift.domain.Entry
import com.github.guidohu.clockshift.domain.EntryEdit
import com.github.guidohu.clockshift.domain.EntryKind
import com.github.guidohu.clockshift.domain.EntryOps
import com.github.guidohu.clockshift.domain.ScheduleBreak
import com.github.guidohu.clockshift.domain.DaySchedule
import com.github.guidohu.clockshift.domain.standardEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EntryOpsTest {
    private val day = LocalDate.of(2026, 10, 8)
    private val evening = day.atTime(20, 0)

    private fun at(h: Int, m: Int = 0): LocalDateTime = day.atTime(h, m)
    private fun work(id: Long, from: Int, to: Int?) = Entry(id = id, start = at(from), end = to?.let { at(it) })
    private fun pause(id: Long, from: Int, to: Int?) = Entry(id = id, start = at(from), end = to?.let { at(it) }, kind = EntryKind.Break)

    /** Applies an edit to a list the way the database would. */
    private fun apply(list: List<Entry>, edit: EntryEdit): List<Entry> {
        var nextId = 100L
        val kept = list.filter { it.id !in edit.delete && edit.save.none { s -> s.id == it.id } }
        return kept + edit.save.map { if (it.id == 0L) it.copy(id = nextId++) else it }
    }

    private fun result(list: List<Entry>) =
        list.sortedBy { it.start }.map { Triple(if (it.isBreak) "B" else "W", it.start.hour, it.end?.hour) }

    private fun add(list: List<Entry>, kind: EntryKind, from: Int, to: Int?, replacing: Entry? = null): List<Entry> {
        val outcome = EntryOps.add(list, kind, at(from), to?.let { at(it) }, evening, replacing)
        return apply(list, (outcome as AddOutcome.Ok).edit)
    }

    @Test
    fun `break in the middle splits the work block into work break work`() {
        val after = add(listOf(work(1, 7, 18)), EntryKind.Break, 12, 13)
        assertEquals(listOf(Triple("W", 7, 12), Triple("B", 12, 13), Triple("W", 13, 18)), result(after))
        assertEquals(10 * 60, after.filter { !it.isBreak }.sumOf { it.completedMinutes })
    }

    @Test
    fun `break at the edge trims the work and a covering break removes it`() {
        val trimmed = add(listOf(work(1, 7, 18)), EntryKind.Break, 17, 18)
        assertEquals(listOf(Triple("W", 7, 17), Triple("B", 17, 18)), result(trimmed))
        val covered = add(listOf(work(1, 12, 13)), EntryKind.Break, 11, 14)
        assertEquals(listOf(Triple("B", 11, 14)), result(covered))
    }

    @Test
    fun `break without work is rejected`() {
        assertEquals(AddOutcome.NoWork, EntryOps.add(listOf(work(1, 7, 10)), EntryKind.Break, at(12), at(13), evening))
        assertEquals(AddOutcome.NoWork, EntryOps.add(emptyList(), EntryKind.Break, at(12), at(13), evening))
    }

    @Test
    fun `break in a running session keeps the session running`() {
        val running = work(1, 7, null)
        val after = add(listOf(running), EntryKind.Break, 12, 13)
        assertEquals(listOf(Triple("W", 7, 12), Triple("B", 12, 13), Triple("W", 13, null)), result(after))
        assertEquals(AddOutcome.InFuture, EntryOps.add(listOf(running), EntryKind.Break, at(15), at(16), at(15, 30)))
    }

    @Test
    fun `work block over a whole break joins everything into one block`() {
        val base = listOf(work(1, 7, 12), pause(2, 12, 13), work(3, 13, 18))
        assertEquals(listOf(Triple("W", 7, 18)), result(add(base, EntryKind.Work, 12, 13)))
    }

    @Test
    fun `work over half of a break shrinks the break`() {
        val base = listOf(work(1, 7, 12), pause(2, 12, 14), work(3, 14, 18))
        val after = add(base, EntryKind.Work, 11, 13)
        assertEquals(listOf(Triple("W", 7, 13), Triple("B", 13, 14), Triple("W", 14, 18)), result(after))
    }

    @Test
    fun `work blocks merge when they overlap or touch`() {
        val separate = add(listOf(work(1, 7, 10)), EntryKind.Work, 12, 14)
        assertEquals(listOf(Triple("W", 7, 10), Triple("W", 12, 14)), result(separate))
        val touching = add(listOf(work(1, 7, 10)), EntryKind.Work, 10, 12)
        assertEquals(listOf(Triple("W", 7, 12)), result(touching))
        val bridging = add(listOf(work(1, 7, 10), work(2, 13, 17)), EntryKind.Work, 9, 14)
        assertEquals(listOf(Triple("W", 7, 17)), result(bridging))
    }

    @Test
    fun `removing a break fills it with work and removing work leaves a gap`() {
        val base = listOf(work(1, 7, 12), pause(2, 12, 13), work(3, 13, 18))
        assertEquals(listOf(Triple("W", 7, 18)), result(apply(base, EntryOps.remove(base, base[1]))))
        assertEquals(
            listOf(Triple("W", 7, 12), Triple("B", 12, 13)),
            result(apply(base, EntryOps.remove(base, base[2]))),
        )
    }

    @Test
    fun `editing a break moves it and the freed time becomes work again`() {
        val base = listOf(work(1, 7, 12), pause(2, 12, 14), work(3, 14, 18))
        val after = add(base, EntryKind.Break, 12, 13, replacing = base[1])
        assertEquals(listOf(Triple("W", 7, 12), Triple("B", 12, 13), Triple("W", 13, 18)), result(after))
    }

    @Test
    fun `editing the start of a running block works`() {
        val running = work(1, 9, null)
        val after = add(listOf(running), EntryKind.Work, 8, null, replacing = running)
        assertEquals(listOf(Triple("W", 8, null)), result(after))
    }

    @Test
    fun `standard day contains the break at its configured time`() {
        val schedule = DaySchedule(true, 9 * 60, 17 * 60 + 30, listOf(ScheduleBreak(12 * 60, 30)), target = 8 * 60)
        val entries = standardEntries(day, schedule)
        assertEquals(listOf(EntryKind.Work, EntryKind.Break, EntryKind.Work), entries.map { it.kind })
        assertEquals(schedule.targetMinutes, entries.filter { !it.isBreak }.sumOf { it.completedMinutes })
        assertTrue(standardEntries(day, schedule.copy(breaks = emptyList())).size == 1)
    }

    @Test
    fun `standard day with several breaks alternates work and breaks`() {
        val schedule = DaySchedule(
            true, 8 * 60, 17 * 60,
            listOf(ScheduleBreak(15 * 60, 15), ScheduleBreak(12 * 60, 30)),
            target = 8 * 60 + 15,
        )
        val entries = standardEntries(day, schedule)
        assertEquals(
            listOf(EntryKind.Work, EntryKind.Break, EntryKind.Work, EntryKind.Break, EntryKind.Work),
            entries.map { it.kind },
        )
        assertEquals(schedule.targetMinutes, entries.filter { !it.isBreak }.sumOf { it.completedMinutes })
        assertEquals(8 * 60 + 15, schedule.targetMinutes)
    }

    @Test
    fun `schedule normalisation sorts clamps and merges breaks`() {
        val messy = DaySchedule(
            true, 9 * 60, 17 * 60,
            listOf(ScheduleBreak(16 * 60 + 50, 60), ScheduleBreak(12 * 60, 30), ScheduleBreak(12 * 60 + 15, 30), ScheduleBreak(13 * 60 + 15, 15)),
            target = 8 * 60,
        ).normalized()
        // 12:00-12:30 and the overlapping one pushed to 12:30-13:00 merge; 13:15 stays; the last is clamped to the end.
        assertEquals(
            listOf(ScheduleBreak(12 * 60, 60), ScheduleBreak(13 * 60 + 15, 15), ScheduleBreak(16 * 60 + 50, 10)),
            messy.breaks,
        )
    }
}
