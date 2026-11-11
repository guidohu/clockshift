package com.github.guidohu.clockshift

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.guidohu.clockshift.data.MIGRATION_2_3
import com.github.guidohu.clockshift.data.MIGRATION_3_4
import com.github.guidohu.clockshift.data.MIGRATION_4_5
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the real migrations against a real SQLite database built from the schemas Room exported for each
 * released version, and checks that the result is exactly the schema Room expects for the newest version.
 * Room refuses to open a database whose tables differ from its expectation, so a mismatch here would crash
 * the app on the first start after an update.
 *
 * (MIGRATION_1_2 reads rows through Android's cursor API and cannot run on the JVM; it is unchanged since
 * it shipped.)
 */
class MigrationTest {
    private lateinit var db: Connection

    @Before
    fun open() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    @After
    fun close() = db.close()

    @Test
    fun `4 to 5 produces the expected schema`() {
        createFromSchema(db, 4)
        migrate(MIGRATION_4_5)
        assertSchemaMatches(5)
    }

    @Test
    fun `2 to 5 and 3 to 5 produce the expected schema`() {
        createFromSchema(db, 2)
        migrate(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        assertSchemaMatches(5)

        db.close()
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        createFromSchema(db, 3)
        migrate(MIGRATION_3_4, MIGRATION_4_5)
        assertSchemaMatches(5)
    }

    @Test
    fun `4 to 5 keeps all data and turns nothing new on`() {
        createFromSchema(db, 4)
        exec("INSERT INTO settings (id, setupDone, startDate, openingBalanceMinutes, reminderEnabled, reminderMinute) VALUES (1, 1, 20000, -45, 0, 1110)")
        exec("INSERT INTO entry (id, date, startAt, endAt, breakMinutes, breakStartedAt, note, kind) VALUES (1, 20000, 28800000, 28800480, 0, NULL, '', 0)")
        exec("INSERT INTO entry (id, date, startAt, endAt, breakMinutes, breakStartedAt, note, kind) VALUES (2, 20001, 28801920, NULL, 0, NULL, '', 0)")
        exec("INSERT INTO day_off (date) VALUES (20002)")
        exec("INSERT INTO schedule_day (effectiveFrom, dayOfWeek, working, startMinute, endMinute, targetMinutes, breakMinutes, breakStartMinute) VALUES (0, 1, 1, 540, 1050, 480, 0, 720)")
        exec("INSERT INTO schedule_break (effectiveFrom, dayOfWeek, position, startMinute, minutes) VALUES (0, 1, 0, 720, 30)")

        migrate(MIGRATION_4_5)

        assertEquals(listOf("1|20000|28800000|28800480|0", "2|20001|28801920|null|0"), rows("SELECT id, date, startAt, endAt, kind FROM entry ORDER BY id"))
        assertEquals(listOf("20002"), rows("SELECT date FROM day_off"))
        assertEquals(listOf("0|1|1|540|1050|480"), rows("SELECT effectiveFrom, dayOfWeek, working, startMinute, endMinute, targetMinutes FROM schedule_day"))
        assertEquals(listOf("0|1|0|720|30"), rows("SELECT * FROM schedule_break"))

        val settings = rows(
            "SELECT setupDone, startDate, openingBalanceMinutes, reminderEnabled, reminderMinute, " +
                "autoTrackMode, autoTrackWifi, autoTrackLatitude, autoTrackLongitude, autoTrackRadius, autoTrackAsk, " +
                "autoTrackWorkdaysOnly, autoTrackLeaveDelay, checkInReminder, checkInReminderDelay, checkOutReminder, " +
                "checkOutReminderDelay FROM settings",
        ).single()
        // Existing values untouched; Pro features off; the work start reminder follows the evening reminder (off here).
        assertEquals("1|20000|-45|0|1110|0||null|null|150|0|1|10|0|60|0|15", settings)
    }

    @Test
    fun `work start reminder is on for users who had the evening reminder on`() {
        createFromSchema(db, 4)
        exec("INSERT INTO settings (id, setupDone, startDate, openingBalanceMinutes, reminderEnabled, reminderMinute) VALUES (1, 1, 20000, 0, 1, 1080)")
        migrate(MIGRATION_4_5)
        assertEquals(listOf("1"), rows("SELECT checkInReminder FROM settings"))
    }

    @Test
    fun `4 to 5 works on a database without a settings row`() {
        // Someone who installed the app but never finished the setup wizard.
        createFromSchema(db, 4)
        migrate(MIGRATION_4_5)
        assertNull(rows("SELECT * FROM settings").firstOrNull())
    }

    // --- helpers -----------------------------------------------------------------------------------

    private fun schema(version: Int): JSONObject {
        val file = File("schemas/com.github.guidohu.clockshift.data.AppDatabase/$version.json")
        return JSONObject(file.readText()).getJSONObject("database")
    }

    /** Creates the tables and indices exactly as Room did for [version]. */
    private fun createFromSchema(connection: Connection, version: Int) {
        val database = schema(version)
        val entities = database.getJSONArray("entities")
        connection.createStatement().use { statement ->
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                statement.execute(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    statement.execute(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
        }
    }

    private fun migrate(vararg migrations: Migration) {
        val support = db.asSupportDatabase()
        migrations.forEach { it.migrate(support) }
    }

    /** Compares every column (type, not null, default, primary key) and every index with a fresh v[version] database. */
    private fun assertSchemaMatches(version: Int) {
        val expected = DriverManager.getConnection("jdbc:sqlite::memory:").use { fresh ->
            createFromSchema(fresh, version)
            describe(fresh)
        }
        assertEquals(expected, describe(db))
    }

    private fun describe(connection: Connection): Map<String, List<String>> {
        val tables = connection.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")
        return tables.associateWith { table ->
            val columns = connection.query("SELECT name, type, \"notnull\", dflt_value, pk FROM pragma_table_info('$table') ORDER BY name")
            val indices = connection.query(
                "SELECT il.name || ':' || ii.name FROM pragma_index_list('$table') il, pragma_index_info(il.name) ii " +
                    "WHERE il.origin = 'c' ORDER BY 1",
            )
            columns + indices.map { "index $it" }
        }
    }

    private fun Connection.query(sql: String): List<String> = createStatement().use { statement ->
        statement.executeQuery(sql).use { result ->
            val count = result.metaData.columnCount
            buildList {
                while (result.next()) add((1..count).joinToString("|") { result.getString(it) ?: "null" })
            }
        }
    }

    private fun rows(sql: String) = db.query(sql)

    private fun exec(sql: String) {
        db.createStatement().use { it.execute(sql) }
    }

    /** The migrations here only call execSQL; anything else would fail the test loudly. */
    private fun Connection.asSupportDatabase(): SupportSQLiteDatabase =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            if (method.name == "execSQL" && args?.size == 1) {
                createStatement().use { it.execute(args[0] as String) }
                null
            } else {
                throw UnsupportedOperationException("Not supported in this test: ${method.name}")
            }
        } as SupportSQLiteDatabase
}
