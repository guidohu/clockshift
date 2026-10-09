package com.github.guidohu.clockshift.data

import android.content.ContentValues
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** A row of the version-1 `entry` table (a work session with an unpositioned break). */
data class LegacyEntry(
    val id: Long,
    val date: Long,
    val startAt: Long,
    val endAt: Long?,
    val breakMinutes: Int,
    val breakStartedAt: Long?,
)

/** One block of the new model. `kind`: 0 = work, 1 = break. [reuseId] keeps the original row. */
data class ConvertedBlock(val kind: Int, val startAt: Long, val endAt: Long?, val reuseId: Boolean)

/**
 * Turns a version-1 session into positioned blocks, or null when nothing has to change:
 *  - finished session with a break of b minutes: work, a break centred in the session, work;
 *  - running session on a break: work up to the break start, then a running break.
 */
fun convertLegacyEntry(entry: LegacyEntry): List<ConvertedBlock>? {
    val end = entry.endAt
    if (end != null) {
        val gross = end - entry.startAt
        val breakLength = entry.breakMinutes.toLong().coerceAtMost(gross - 1)
        if (breakLength <= 0) return null
        val breakStart = entry.startAt + (gross - breakLength) / 2
        return listOf(
            ConvertedBlock(0, entry.startAt, breakStart, reuseId = true),
            ConvertedBlock(1, breakStart, breakStart + breakLength, reuseId = false),
            ConvertedBlock(0, breakStart + breakLength, end, reuseId = false),
        )
    }
    val breakStartedAt = entry.breakStartedAt ?: return null
    return listOf(
        ConvertedBlock(0, entry.startAt, breakStartedAt, reuseId = true),
        ConvertedBlock(1, breakStartedAt, null, reuseId = false),
    )
}

/** Breaks become entries of their own, and the standard day gets a break start time. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE entry ADD COLUMN kind INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE schedule_day ADD COLUMN breakStartMinute INTEGER NOT NULL DEFAULT 720")

        val legacy = mutableListOf<LegacyEntry>()
        db.query("SELECT id, date, startAt, endAt, breakMinutes, breakStartedAt FROM entry").use { cursor ->
            while (cursor.moveToNext()) {
                legacy += LegacyEntry(
                    id = cursor.getLong(0),
                    date = cursor.getLong(1),
                    startAt = cursor.getLong(2),
                    endAt = if (cursor.isNull(3)) null else cursor.getLong(3),
                    breakMinutes = cursor.getInt(4),
                    breakStartedAt = if (cursor.isNull(5)) null else cursor.getLong(5),
                )
            }
        }
        for (entry in legacy) {
            val blocks = convertLegacyEntry(entry) ?: continue
            for (block in blocks) {
                if (block.reuseId) {
                    db.execSQL("UPDATE entry SET endAt = ? WHERE id = ?", arrayOf(block.endAt, entry.id))
                } else {
                    val values = ContentValues().apply {
                        put("date", entry.date)
                        put("startAt", block.startAt)
                        if (block.endAt != null) put("endAt", block.endAt) else putNull("endAt")
                        put("breakMinutes", 0)
                        putNull("breakStartedAt")
                        put("note", "")
                        put("kind", block.kind)
                    }
                    db.insert("entry", 0, values)
                }
            }
        }
        db.execSQL("UPDATE entry SET breakMinutes = 0, breakStartedAt = NULL")
    }
}

/** A day can have several breaks: they move from two columns of `schedule_day` into their own table. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS schedule_break (" +
                "effectiveFrom INTEGER NOT NULL, dayOfWeek INTEGER NOT NULL, position INTEGER NOT NULL, " +
                "startMinute INTEGER NOT NULL, minutes INTEGER NOT NULL, " +
                "PRIMARY KEY(effectiveFrom, dayOfWeek, position))",
        )
        db.execSQL(
            "INSERT INTO schedule_break (effectiveFrom, dayOfWeek, position, startMinute, minutes) " +
                "SELECT effectiveFrom, dayOfWeek, 0, breakStartMinute, breakMinutes FROM schedule_day WHERE breakMinutes > 0",
        )
    }
}

/**
 * The expected hours of a day are stored instead of being derived from the times. Existing days keep
 * what they were worth so far: the time between start and end minus the breaks.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_day ADD COLUMN targetMinutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE schedule_day SET targetMinutes = MAX(0, endMinute - startMinute - COALESCE(" +
                "(SELECT SUM(minutes) FROM schedule_break b " +
                "WHERE b.effectiveFrom = schedule_day.effectiveFrom AND b.dayOfWeek = schedule_day.dayOfWeek), 0))",
        )
    }
}
