package com.github.guidohu.clockshift.data

import androidx.room.withTransaction
import com.github.guidohu.clockshift.domain.DaySchedule
import com.github.guidohu.clockshift.domain.Entry
import com.github.guidohu.clockshift.domain.EntryEdit
import com.github.guidohu.clockshift.domain.EntryKind
import com.github.guidohu.clockshift.domain.OvertimeCalculator
import com.github.guidohu.clockshift.domain.ScheduleBreak
import com.github.guidohu.clockshift.domain.ScheduleVersion
import com.github.guidohu.clockshift.domain.Settings
import com.github.guidohu.clockshift.domain.WeekSchedule
import com.github.guidohu.clockshift.domain.fromLocalMinutes
import com.github.guidohu.clockshift.domain.standardEntries
import com.github.guidohu.clockshift.domain.toLocalMinutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Everything the UI needs, emitted whenever any underlying table or setting changes. */
class AppState(
    val settings: Settings,
    val schedules: List<ScheduleVersion>,
    val entries: List<Entry>,
    val daysOff: Set<LocalDate>,
) {
    val calculator: OvertimeCalculator by lazy { OvertimeCalculator(schedules, entries, daysOff) }
    val activeEntry: Entry? get() = entries.firstOrNull { it.isActive }
    val onBreak: Boolean get() = activeEntry?.isBreak == true

    fun weekFor(date: LocalDate): WeekSchedule =
        schedules.lastOrNull { it.effectiveFrom <= date }?.week
            ?: schedules.firstOrNull()?.week
            ?: WeekSchedule.standard()

    fun scheduleFor(date: LocalDate): DaySchedule = weekFor(date)[date.dayOfWeek]

    fun entriesOn(date: LocalDate): List<Entry> = entries.filter { it.date == date }
}

class ClockRepository(
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
) {
    private val entryDao get() = database.entryDao()
    private val dayOffDao get() = database.dayOffDao()
    private val scheduleDao get() = database.scheduleDao()

    val state: Flow<AppState> = combine(
        settingsStore.settings,
        scheduleDao.observeDays(),
        scheduleDao.observeBreaks(),
        entryDao.observeAll(),
        dayOffDao.observeAll(),
    ) { settings, scheduleRows, breakRows, entryRows, dayOffRows ->
        AppState(
            settings = settings,
            schedules = toVersions(scheduleRows, breakRows),
            entries = entryRows.map { it.toDomain() },
            daysOff = dayOffRows.mapTo(HashSet()) { LocalDate.ofEpochDay(it.date) },
        )
    }

    suspend fun settings(): Settings = settingsStore.current()

    suspend fun updateSettings(transform: (Settings) -> Settings) = settingsStore.update(transform)

    // --- live session ---------------------------------------------------------------------

    suspend fun checkIn(now: LocalDateTime) = database.withTransaction {
        if (entryDao.findActive() == null) {
            entryDao.upsert(Entry(start = now.truncatedTo(ChronoUnit.MINUTES), end = null).toEntity())
        }
    }

    /** Ends the running work block and starts a running break. */
    suspend fun startBreak(now: LocalDateTime) = database.withTransaction {
        val active = entryDao.findActive()?.toDomain() ?: return@withTransaction
        if (active.isBreak) return@withTransaction
        val at = now.truncatedTo(ChronoUnit.MINUTES)
        if (at > active.start) entryDao.update(active.copy(end = at).toEntity()) else entryDao.delete(active.id)
        entryDao.upsert(Entry(start = at, end = null, kind = EntryKind.Break).toEntity())
    }

    /** Ends the running break and resumes work. */
    suspend fun endBreak(now: LocalDateTime) = database.withTransaction {
        val active = entryDao.findActive()?.toDomain() ?: return@withTransaction
        if (!active.isBreak) return@withTransaction
        val at = now.truncatedTo(ChronoUnit.MINUTES)
        if (at > active.start) entryDao.update(active.copy(end = at).toEntity()) else entryDao.delete(active.id)
        entryDao.upsert(Entry(start = at, end = null).toEntity())
    }

    /**
     * Ends the running work block. A running break is simply dropped (a break at the end of the day is
     * not a break). Returns the block as it was before, so the caller can offer undo.
     */
    suspend fun checkOut(now: LocalDateTime): Entry? = database.withTransaction {
        val active = entryDao.findActive()?.toDomain() ?: return@withTransaction null
        if (active.isBreak) {
            entryDao.delete(active.id)
        } else {
            val end = maxOf(now.truncatedTo(ChronoUnit.MINUTES), active.start)
            entryDao.update(active.copy(end = end).toEntity())
        }
        active
    }

    // --- entries --------------------------------------------------------------------------

    suspend fun saveEntry(entry: Entry): Long = entryDao.upsert(entry.toEntity())

    suspend fun deleteEntry(id: Long) = entryDao.delete(id)

    /** Applies a merge/split atomically. Returns the ids of the written entries, in order. */
    suspend fun applyEdit(edit: EntryEdit): List<Long> = database.withTransaction {
        edit.delete.forEach { entryDao.delete(it) }
        edit.save.map { entryDao.upsert(it.toEntity()).let { rowId -> if (it.id != 0L) it.id else rowId } }
    }

    suspend fun setDayOff(date: LocalDate, off: Boolean) {
        if (off) dayOffDao.insert(DayOffEntity(date.toEpochDay())) else dayOffDao.delete(date.toEpochDay())
    }

    /** Adds the configured standard day for [date]. Returns the created entries (empty when it has no standard day). */
    suspend fun logStandardDay(date: LocalDate): List<Entry> {
        val schedule = currentSchedule(date)[date.dayOfWeek]
        if (!schedule.working || schedule.targetMinutes <= 0) return emptyList()
        return database.withTransaction {
            standardEntries(date, schedule).map { it.copy(id = entryDao.upsert(it.toEntity())) }
        }
    }

    // --- schedule -------------------------------------------------------------------------

    suspend fun currentSchedule(date: LocalDate): WeekSchedule {
        val versions = toVersions(scheduleDao.getDays(), scheduleDao.getBreaks())
        return versions.lastOrNull { it.effectiveFrom <= date }?.week
            ?: versions.firstOrNull()?.week
            ?: WeekSchedule.standard()
    }

    /** Stores [week] as a new version effective from [from]; earlier days keep their targets. */
    suspend fun saveSchedule(week: WeekSchedule, from: LocalDate) {
        val versions = toVersions(scheduleDao.getDays(), scheduleDao.getBreaks())
        val effective = versions.lastOrNull { it.effectiveFrom <= from }?.week
        if (effective == week) return
        scheduleDao.replaceVersion(from.toEpochDay(), week.toDayRows(from), week.toBreakRows(from))
    }

    /** Completes the setup wizard: the first schedule version applies to all dates. */
    suspend fun completeSetup(week: WeekSchedule, settings: Settings) {
        database.withTransaction {
            scheduleDao.replaceVersion(EPOCH.toEpochDay(), week.toDayRows(EPOCH), week.toBreakRows(EPOCH))
            settingsStore.update { settings.copy(setupDone = true) }
        }
    }

    companion object {
        val EPOCH: LocalDate = LocalDate.ofEpochDay(0)
    }
}

private fun Entry.toEntity() = EntryEntity(
    id = id,
    date = date.toEpochDay(),
    startAt = start.toLocalMinutes(),
    endAt = end?.toLocalMinutes(),
    kind = kind.ordinal,
)

private fun EntryEntity.toDomain() = Entry(
    id = id,
    start = startAt.fromLocalMinutes(),
    end = endAt?.fromLocalMinutes(),
    kind = EntryKind.entries.getOrElse(kind) { EntryKind.Work },
)

private fun WeekSchedule.toDayRows(from: LocalDate): List<ScheduleDayEntity> =
    days.map { (day, schedule) ->
        ScheduleDayEntity(
            effectiveFrom = from.toEpochDay(),
            dayOfWeek = day.value,
            working = schedule.working,
            startMinute = schedule.startMinute,
            endMinute = schedule.endMinute,
            targetMinutes = schedule.target,
        )
    }

private fun WeekSchedule.toBreakRows(from: LocalDate): List<ScheduleBreakEntity> =
    days.flatMap { (day, schedule) ->
        schedule.breaks.mapIndexed { position, scheduled ->
            ScheduleBreakEntity(from.toEpochDay(), day.value, position, scheduled.startMinute, scheduled.minutes)
        }
    }

private fun toVersions(dayRows: List<ScheduleDayEntity>, breakRows: List<ScheduleBreakEntity>): List<ScheduleVersion> {
    val breaksByDay = breakRows.groupBy { it.effectiveFrom to it.dayOfWeek }
    return dayRows.groupBy { it.effectiveFrom }
        .toSortedMap()
        .map { (epochDay, rows) ->
            ScheduleVersion(
                effectiveFrom = LocalDate.ofEpochDay(epochDay),
                week = WeekSchedule(
                    rows.associate { row ->
                        val breaks = breaksByDay[epochDay to row.dayOfWeek].orEmpty()
                            .sortedBy { it.position }
                            .map { ScheduleBreak(it.startMinute, it.minutes) }
                        DayOfWeek.of(row.dayOfWeek) to
                            DaySchedule(row.working, row.startMinute, row.endMinute, breaks, row.targetMinutes).normalized()
                    },
                ),
            )
        }
}
