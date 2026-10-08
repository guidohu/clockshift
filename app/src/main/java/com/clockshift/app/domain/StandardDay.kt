package com.clockshift.app.domain

import java.time.LocalDate

/**
 * The entries that make up a standard [schedule] day on [date]: work blocks separated by the
 * scheduled breaks. A break at the very start or end of the day splits nothing and is left out.
 */
fun standardEntries(date: LocalDate, schedule: DaySchedule): List<Entry> {
    fun at(minute: Int) = date.atStartOfDay().plusMinutes(minute.toLong())
    val result = mutableListOf<Entry>()
    var cursor = schedule.startMinute
    for (scheduled in schedule.normalized().breaks) {
        if (scheduled.startMinute > cursor) result += Entry(start = at(cursor), end = at(scheduled.startMinute))
        val hasWorkBefore = result.isNotEmpty()
        val hasWorkAfter = scheduled.endMinute < schedule.endMinute
        if (hasWorkBefore && hasWorkAfter) {
            result += Entry(start = at(scheduled.startMinute), end = at(scheduled.endMinute), kind = EntryKind.Break)
        }
        cursor = scheduled.endMinute
    }
    if (cursor < schedule.endMinute) result += Entry(start = at(cursor), end = at(schedule.endMinute))
    return result
}
