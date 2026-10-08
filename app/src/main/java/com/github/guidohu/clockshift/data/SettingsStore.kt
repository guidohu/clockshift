package com.github.guidohu.clockshift.data

import androidx.room.withTransaction
import com.github.guidohu.clockshift.domain.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Reads and writes the single [Settings] row. */
class SettingsStore(private val database: AppDatabase) {
    private val dao get() = database.settingsDao()

    val settings: Flow<Settings> = dao.observe().map { it?.toDomain() ?: Settings.initial(LocalDate.now()) }

    suspend fun current(): Settings = dao.get()?.toDomain() ?: Settings.initial(LocalDate.now())

    suspend fun update(transform: (Settings) -> Settings) {
        database.withTransaction { dao.upsert(transform(current()).toEntity()) }
    }
}

private fun SettingsEntity.toDomain() = Settings(
    setupDone = setupDone,
    startDate = LocalDate.ofEpochDay(startDate),
    openingBalanceMinutes = openingBalanceMinutes,
    reminderEnabled = reminderEnabled,
    reminderMinute = reminderMinute,
)

private fun Settings.toEntity() = SettingsEntity(
    setupDone = setupDone,
    startDate = startDate.toEpochDay(),
    openingBalanceMinutes = openingBalanceMinutes,
    reminderEnabled = reminderEnabled,
    reminderMinute = reminderMinute,
)
