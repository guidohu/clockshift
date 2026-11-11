package com.github.guidohu.clockshift.data

import androidx.room.withTransaction
import com.github.guidohu.clockshift.domain.AutoTrackMode
import com.github.guidohu.clockshift.domain.AutoTrackSettings
import com.github.guidohu.clockshift.domain.GeoPoint
import com.github.guidohu.clockshift.domain.Settings
import com.github.guidohu.clockshift.domain.SmartReminderSettings
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
    autoTrack = AutoTrackSettings(
        mode = AutoTrackMode.entries.getOrElse(autoTrackMode) { AutoTrackMode.Off },
        wifiNames = autoTrackWifi.lines().filter { it.isNotBlank() },
        workplace = if (autoTrackLatitude != null && autoTrackLongitude != null) {
            GeoPoint(autoTrackLatitude, autoTrackLongitude)
        } else {
            null
        },
        radiusMeters = autoTrackRadius,
        askFirst = autoTrackAsk,
        workingDaysOnly = autoTrackWorkdaysOnly,
        leaveDelayMinutes = autoTrackLeaveDelay,
    ),
    smartReminders = SmartReminderSettings(
        checkIn = checkInReminder,
        checkInDelayMinutes = checkInReminderDelay,
        checkOut = checkOutReminder,
        checkOutDelayMinutes = checkOutReminderDelay,
    ),
)

private fun Settings.toEntity() = SettingsEntity(
    setupDone = setupDone,
    startDate = startDate.toEpochDay(),
    openingBalanceMinutes = openingBalanceMinutes,
    reminderEnabled = reminderEnabled,
    reminderMinute = reminderMinute,
    autoTrackMode = autoTrack.mode.ordinal,
    autoTrackWifi = autoTrack.wifiNames.joinToString("\n"),
    autoTrackLatitude = autoTrack.workplace?.latitude,
    autoTrackLongitude = autoTrack.workplace?.longitude,
    autoTrackRadius = autoTrack.radiusMeters,
    autoTrackAsk = autoTrack.askFirst,
    autoTrackWorkdaysOnly = autoTrack.workingDaysOnly,
    autoTrackLeaveDelay = autoTrack.leaveDelayMinutes,
    checkInReminder = smartReminders.checkIn,
    checkInReminderDelay = smartReminders.checkInDelayMinutes,
    checkOutReminder = smartReminders.checkOut,
    checkOutReminderDelay = smartReminders.checkOutDelayMinutes,
)
