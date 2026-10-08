package com.github.guidohu.clockshift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entry ORDER BY startAt")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entry WHERE endAt IS NULL LIMIT 1")
    suspend fun findActive(): EntryEntity?

    @Query("SELECT * FROM entry WHERE date = :date ORDER BY startAt")
    suspend fun findByDate(date: Long): List<EntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: EntryEntity): Long

    @Update
    suspend fun update(entry: EntryEntity)

    @Query("DELETE FROM entry WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface DayOffDao {
    @Query("SELECT * FROM day_off")
    fun observeAll(): Flow<List<DayOffEntity>>

    @Query("SELECT COUNT(*) FROM day_off WHERE date = :date")
    suspend fun count(date: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dayOff: DayOffEntity)

    @Query("DELETE FROM day_off WHERE date = :date")
    suspend fun delete(date: Long)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_day ORDER BY effectiveFrom, dayOfWeek")
    fun observeDays(): Flow<List<ScheduleDayEntity>>

    @Query("SELECT * FROM schedule_break ORDER BY effectiveFrom, dayOfWeek, position")
    fun observeBreaks(): Flow<List<ScheduleBreakEntity>>

    @Query("SELECT * FROM schedule_day ORDER BY effectiveFrom, dayOfWeek")
    suspend fun getDays(): List<ScheduleDayEntity>

    @Query("SELECT * FROM schedule_break ORDER BY effectiveFrom, dayOfWeek, position")
    suspend fun getBreaks(): List<ScheduleBreakEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDays(days: List<ScheduleDayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreaks(breaks: List<ScheduleBreakEntity>)

    @Query("DELETE FROM schedule_day WHERE effectiveFrom = :effectiveFrom")
    suspend fun deleteDays(effectiveFrom: Long)

    @Query("DELETE FROM schedule_break WHERE effectiveFrom = :effectiveFrom")
    suspend fun deleteBreaks(effectiveFrom: Long)

    @Transaction
    suspend fun replaceVersion(effectiveFrom: Long, days: List<ScheduleDayEntity>, breaks: List<ScheduleBreakEntity>) {
        deleteDays(effectiveFrom)
        deleteBreaks(effectiveFrom)
        insertDays(days)
        insertBreaks(breaks)
    }
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)
}
