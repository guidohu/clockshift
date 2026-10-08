package com.github.guidohu.clockshift.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "entry", indices = [Index("date")])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch day of the start. */
    val date: Long,
    /** Local minutes, see [com.github.guidohu.clockshift.domain.toLocalMinutes]. */
    val startAt: Long,
    val endAt: Long?,
    /** Legacy column from before breaks were separate entries; always 0 for new rows. */
    val breakMinutes: Int = 0,
    /** Legacy column, always null for new rows. */
    val breakStartedAt: Long? = null,
    /** Legacy column, no longer used. */
    val note: String = "",
    /** [com.github.guidohu.clockshift.domain.EntryKind] ordinal: 0 = work, 1 = break. */
    @ColumnInfo(defaultValue = "0") val kind: Int = 0,
)

@Entity(tableName = "day_off")
data class DayOffEntity(@PrimaryKey val date: Long)

@Entity(tableName = "schedule_day", primaryKeys = ["effectiveFrom", "dayOfWeek"])
data class ScheduleDayEntity(
    val effectiveFrom: Long,
    /** [java.time.DayOfWeek.getValue]: 1 = Monday … 7 = Sunday. */
    val dayOfWeek: Int,
    val working: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    /** Legacy column from before a day could have several breaks; see [ScheduleBreakEntity]. */
    val breakMinutes: Int = 0,
    /** Legacy column, see [breakMinutes]. */
    @ColumnInfo(defaultValue = "720") val breakStartMinute: Int = 720,
)

@Entity(tableName = "schedule_break", primaryKeys = ["effectiveFrom", "dayOfWeek", "position"])
data class ScheduleBreakEntity(
    val effectiveFrom: Long,
    val dayOfWeek: Int,
    /** Order of the break within its day. */
    val position: Int,
    val startMinute: Int,
    val minutes: Int,
)

/** Single-row table (id is always 1). */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val setupDone: Boolean,
    /** Epoch day of the first tracked day. */
    val startDate: Long,
    val openingBalanceMinutes: Int,
    val reminderEnabled: Boolean,
    val reminderMinute: Int,
)
