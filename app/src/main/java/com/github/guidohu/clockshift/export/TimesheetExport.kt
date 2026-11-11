package com.github.guidohu.clockshift.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.core.content.FileProvider
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.domain.Timesheet
import com.github.guidohu.clockshift.domain.TimesheetDayKind
import com.github.guidohu.clockshift.domain.timeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

enum class ExportFormat(val mimeType: String, val extension: String) {
    Pdf("application/pdf", "pdf"),
    Csv("text/csv", "csv"),
}

/** Renders a [Timesheet] as PDF or CSV and hands it to the share sheet or a file the user picked. */
object TimesheetExport {
    private const val EXPORT_DIR = "exports"

    fun fileName(sheet: Timesheet, format: ExportFormat): String =
        "ClockShift_${sheet.from}_${sheet.to}.${format.extension}"

    suspend fun render(context: Context, sheet: Timesheet, format: ExportFormat, entries: Boolean): ByteArray =
        withContext(Dispatchers.Default) {
            val labels = labels(context)
            when (format) {
                ExportFormat.Csv -> TimesheetCsv.write(sheet, labels, entries).toByteArray(Charsets.UTF_8)
                ExportFormat.Pdf -> TimesheetPdf(labels, WeekFields.of(Locale.getDefault()).firstDayOfWeek).render(sheet, entries)
            }
        }

    /** Writes the file to the app's cache and opens the share sheet for it. */
    suspend fun share(context: Context, bytes: ByteArray, fileName: String, format: ExportFormat) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // only the latest export is kept
            File(dir, fileName).apply { writeBytes(bytes) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(format.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, fileName)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(fileName, uri)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.export_share_title)))
    }

    /** Writes to a document the user created with the system file picker. */
    suspend fun save(context: Context, uri: Uri, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("No stream")
        }.isSuccess
    }

    fun labels(context: Context): TimesheetLabels {
        val locale = Locale.getDefault()
        val medium = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        val time = timeFormatter(DateFormat.is24HourFormat(context))
        fun s(id: Int) = context.getString(id)
        return TimesheetLabels(
            title = s(R.string.ts_title),
            appName = s(R.string.app_name),
            date = s(R.string.ts_date),
            weekday = s(R.string.ts_weekday),
            type = s(R.string.ts_type),
            start = s(R.string.label_start),
            end = s(R.string.label_end),
            breakTime = s(R.string.ts_break),
            worked = s(R.string.stat_worked),
            target = s(R.string.stat_target),
            overtime = s(R.string.ts_overtime),
            balance = s(R.string.stat_balance),
            duration = s(R.string.ts_duration),
            work = s(R.string.entry_kind_work),
            breakEntry = s(R.string.entry_kind_break),
            total = s(R.string.ts_total),
            balanceBefore = s(R.string.ts_balance_before),
            balanceAfter = s(R.string.ts_balance_after),
            running = s(R.string.entry_running),
            generated = s(R.string.ts_generated),
            pageOf = s(R.string.ts_page),
            kinds = mapOf(
                TimesheetDayKind.Worked to s(R.string.ts_kind_worked),
                TimesheetDayKind.Standard to s(R.string.ts_kind_standard),
                TimesheetDayKind.DayOff to s(R.string.ts_kind_day_off),
                TimesheetDayKind.Free to s(R.string.ts_kind_free),
                TimesheetDayKind.InProgress to s(R.string.ts_kind_in_progress),
                TimesheetDayKind.Open to s(R.string.ts_kind_open),
            ),
            dayOffSuffix = s(R.string.ts_day_off_suffix),
            weekdayName = { it.dayOfWeek.getDisplayName(TextStyle.FULL, locale) },
            dateText = { "${it.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)}, ${medium.format(it)}" },
            timeText = { time.format(it) },
        )
    }
}
