package com.github.guidohu.clockshift.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EntryEntity::class, DayOffEntity::class, ScheduleDayEntity::class, ScheduleBreakEntity::class, SettingsEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun dayOffDao(): DayOffDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "clockshift.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
