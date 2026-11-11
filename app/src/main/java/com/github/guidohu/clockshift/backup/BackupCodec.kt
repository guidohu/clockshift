package com.github.guidohu.clockshift.backup

import com.github.guidohu.clockshift.data.DayOffEntity
import com.github.guidohu.clockshift.data.EntryEntity
import com.github.guidohu.clockshift.data.ScheduleBreakEntity
import com.github.guidohu.clockshift.data.ScheduleDayEntity
import com.github.guidohu.clockshift.data.SettingsEntity
import com.github.guidohu.clockshift.domain.fromLocalMinutes
import com.github.guidohu.clockshift.domain.toLocalMinutes
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/** Everything ClockShift stores, as one snapshot. Pro status and device-specific state are not part of it. */
data class BackupData(
    val createdAt: LocalDateTime,
    val appVersion: String,
    val settings: SettingsEntity?,
    val scheduleDays: List<ScheduleDayEntity>,
    val scheduleBreaks: List<ScheduleBreakEntity>,
    val entries: List<EntryEntity>,
    val daysOff: List<DayOffEntity>,
)

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads and writes backups as JSON. Dates and times are ISO strings ("2026-10-05", "2026-10-05T08:52"), so a
 * backup is readable and stays valid whatever the internal storage format. [FORMAT] grows when fields are
 * added; older backups keep loading, newer ones are refused rather than half-read.
 */
object BackupCodec {
    const val FORMAT = 1
    private const val APP = "ClockShift"

    fun encode(data: BackupData): String {
        val root = JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("createdAt", data.createdAt.toString())
            .put("appVersion", data.appVersion)
        data.settings?.let { root.put("settings", it.toJson()) }
        root.put("schedule", JSONArray(data.scheduleDays.map { it.toJson() }))
        root.put("breaks", JSONArray(data.scheduleBreaks.map { it.toJson() }))
        root.put("entries", JSONArray(data.entries.map { it.toJson() }))
        root.put("daysOff", JSONArray(data.daysOff.map { LocalDate.ofEpochDay(it.date).toString() }))
        return root.toString(2)
    }

    fun decode(text: String): BackupData {
        try {
            val root = JSONObject(text)
            if (root.optString("app") != APP) throw BackupFormatException("Not a ClockShift backup")
            val format = root.optInt("format", 0)
            if (format < 1 || format > FORMAT) throw BackupFormatException("Unsupported backup format $format")
            return BackupData(
                createdAt = LocalDateTime.parse(root.getString("createdAt")),
                appVersion = root.optString("appVersion"),
                settings = root.optJSONObject("settings")?.toSettings(),
                scheduleDays = root.optJSONArray("schedule").objects().map { it.toScheduleDay() },
                scheduleBreaks = root.optJSONArray("breaks").objects().map { it.toScheduleBreak() },
                entries = root.optJSONArray("entries").objects().map { it.toEntry() },
                daysOff = root.optJSONArray("daysOff").strings().map { DayOffEntity(LocalDate.parse(it).toEpochDay()) },
            )
        } catch (e: JSONException) {
            throw BackupFormatException("Damaged backup", e)
        } catch (e: DateTimeParseException) {
            throw BackupFormatException("Damaged backup", e)
        }
    }

    // --- settings ------------------------------------------------------------------------------

    private fun SettingsEntity.toJson() = JSONObject()
        .put("setupDone", setupDone)
        .put("startDate", LocalDate.ofEpochDay(startDate).toString())
        .put("openingBalanceMinutes", openingBalanceMinutes)
        .put("reminderEnabled", reminderEnabled)
        .put("reminderMinute", reminderMinute)
        .put("autoTrackMode", autoTrackMode)
        .put("autoTrackWifi", JSONArray(autoTrackWifi.lines().filter { it.isNotBlank() }))
        .putOpt("autoTrackLatitude", autoTrackLatitude)
        .putOpt("autoTrackLongitude", autoTrackLongitude)
        .put("autoTrackRadius", autoTrackRadius)
        .put("autoTrackAsk", autoTrackAsk)
        .put("autoTrackWorkdaysOnly", autoTrackWorkdaysOnly)
        .put("autoTrackLeaveDelay", autoTrackLeaveDelay)
        .put("checkInReminder", checkInReminder)
        .put("checkInReminderDelay", checkInReminderDelay)
        .put("checkOutReminder", checkOutReminder)
        .put("checkOutReminderDelay", checkOutReminderDelay)

    /** Fields added after format 1 must fall back to the entity defaults. */
    private fun JSONObject.toSettings(): SettingsEntity {
        val defaults = SettingsEntity(setupDone = true, startDate = 0, openingBalanceMinutes = 0, reminderEnabled = true, reminderMinute = 18 * 60)
        return SettingsEntity(
            setupDone = optBoolean("setupDone", true),
            startDate = LocalDate.parse(getString("startDate")).toEpochDay(),
            openingBalanceMinutes = optInt("openingBalanceMinutes", 0),
            reminderEnabled = optBoolean("reminderEnabled", defaults.reminderEnabled),
            reminderMinute = optInt("reminderMinute", defaults.reminderMinute),
            autoTrackMode = optInt("autoTrackMode", defaults.autoTrackMode),
            autoTrackWifi = optJSONArray("autoTrackWifi").strings().joinToString("\n"),
            autoTrackLatitude = optDoubleOrNull("autoTrackLatitude"),
            autoTrackLongitude = optDoubleOrNull("autoTrackLongitude"),
            autoTrackRadius = optInt("autoTrackRadius", defaults.autoTrackRadius),
            autoTrackAsk = optBoolean("autoTrackAsk", defaults.autoTrackAsk),
            autoTrackWorkdaysOnly = optBoolean("autoTrackWorkdaysOnly", defaults.autoTrackWorkdaysOnly),
            autoTrackLeaveDelay = optInt("autoTrackLeaveDelay", defaults.autoTrackLeaveDelay),
            checkInReminder = optBoolean("checkInReminder", defaults.checkInReminder),
            checkInReminderDelay = optInt("checkInReminderDelay", defaults.checkInReminderDelay),
            checkOutReminder = optBoolean("checkOutReminder", defaults.checkOutReminder),
            checkOutReminderDelay = optInt("checkOutReminderDelay", defaults.checkOutReminderDelay),
        )
    }

    // --- schedule ------------------------------------------------------------------------------

    private fun ScheduleDayEntity.toJson() = JSONObject()
        .put("effectiveFrom", LocalDate.ofEpochDay(effectiveFrom).toString())
        .put("dayOfWeek", dayOfWeek)
        .put("working", working)
        .put("startMinute", startMinute)
        .put("endMinute", endMinute)
        .put("targetMinutes", targetMinutes)

    private fun JSONObject.toScheduleDay() = ScheduleDayEntity(
        effectiveFrom = LocalDate.parse(getString("effectiveFrom")).toEpochDay(),
        dayOfWeek = getInt("dayOfWeek").also { if (it !in 1..7) throw BackupFormatException("Bad weekday $it") },
        working = getBoolean("working"),
        startMinute = getInt("startMinute"),
        endMinute = getInt("endMinute"),
        targetMinutes = getInt("targetMinutes"),
    )

    private fun ScheduleBreakEntity.toJson() = JSONObject()
        .put("effectiveFrom", LocalDate.ofEpochDay(effectiveFrom).toString())
        .put("dayOfWeek", dayOfWeek)
        .put("position", position)
        .put("startMinute", startMinute)
        .put("minutes", minutes)

    private fun JSONObject.toScheduleBreak() = ScheduleBreakEntity(
        effectiveFrom = LocalDate.parse(getString("effectiveFrom")).toEpochDay(),
        dayOfWeek = getInt("dayOfWeek"),
        position = getInt("position"),
        startMinute = getInt("startMinute"),
        minutes = getInt("minutes"),
    )

    // --- entries -------------------------------------------------------------------------------

    private fun EntryEntity.toJson() = JSONObject()
        .put("id", id)
        .put("start", startAt.fromLocalMinutes().toString())
        .putOpt("end", endAt?.fromLocalMinutes()?.toString())
        .put("kind", if (kind == 1) "break" else "work")

    private fun JSONObject.toEntry(): EntryEntity {
        val start = LocalDateTime.parse(getString("start"))
        val end = if (has("end") && !isNull("end")) LocalDateTime.parse(getString("end")) else null
        if (end != null && end < start) throw BackupFormatException("Entry ends before it starts")
        return EntryEntity(
            id = optLong("id", 0L),
            date = start.toLocalDate().toEpochDay(),
            startAt = start.toLocalMinutes(),
            endAt = end?.toLocalMinutes(),
            kind = if (optString("kind") == "break") 1 else 0,
        )
    }

    // --- helpers -------------------------------------------------------------------------------

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }

    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) getDouble(key) else null
}
