package com.github.guidohu.clockshift.ui

import com.github.guidohu.clockshift.domain.Entry
import com.github.guidohu.clockshift.domain.EntryEdit
import com.github.guidohu.clockshift.domain.Settings
import com.github.guidohu.clockshift.domain.WeekSchedule
import java.time.LocalDate

/** Everything the screens can ask the app to do. Implemented by [MainViewModel]; faked in previews. */
interface ClockActions {
    fun checkIn()
    fun startBreak()
    fun endBreak()

    /** Returns the session as it was before checking out, for undo. */
    suspend fun checkOut(): Entry?
    suspend fun saveEntry(entry: Entry): Long
    suspend fun deleteEntry(id: Long)

    /** Applies a merge/split of work blocks; returns the ids of the written entries. */
    suspend fun applyEdit(edit: EntryEdit): List<Long>
    fun setDayOff(date: LocalDate, off: Boolean)

    /** Returns the created entries; empty when [date] has no standard day. */
    suspend fun logStandardDay(date: LocalDate): List<Entry>
    fun saveSchedule(week: WeekSchedule)
    fun updateSettings(transform: (Settings) -> Settings)
    fun completeSetup(week: WeekSchedule, settings: Settings)
}

object NoOpActions : ClockActions {
    override fun checkIn() = Unit
    override fun startBreak() = Unit
    override fun endBreak() = Unit
    override suspend fun checkOut(): Entry? = null
    override suspend fun saveEntry(entry: Entry): Long = entry.id
    override suspend fun deleteEntry(id: Long) = Unit
    override suspend fun applyEdit(edit: EntryEdit): List<Long> = emptyList()
    override fun setDayOff(date: LocalDate, off: Boolean) = Unit
    override suspend fun logStandardDay(date: LocalDate): List<Entry> = emptyList()
    override fun saveSchedule(week: WeekSchedule) = Unit
    override fun updateSettings(transform: (Settings) -> Settings) = Unit
    override fun completeSetup(week: WeekSchedule, settings: Settings) = Unit
}
