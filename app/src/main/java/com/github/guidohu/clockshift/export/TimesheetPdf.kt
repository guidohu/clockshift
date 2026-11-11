package com.github.guidohu.clockshift.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.github.guidohu.clockshift.domain.Timesheet
import com.github.guidohu.clockshift.domain.TimesheetDay
import com.github.guidohu.clockshift.domain.TimesheetDayKind
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import kotlin.math.abs

/**
 * Printable A4 timesheet: a summary, then one row per day with start, end, break, worked, target and
 * overtime, weeks separated by a line, and a total. With [entries] each logged block is listed under its
 * day. Drawn with the platform's PdfDocument, so no extra library is needed.
 */
class TimesheetPdf(
    private val labels: TimesheetLabels,
    private val firstDayOfWeek: DayOfWeek,
) {
    private val ink = Color.rgb(0x16, 0x1D, 0x1D)
    private val muted = Color.rgb(0x4A, 0x5F, 0x61)
    private val rule = Color.rgb(0xD5, 0xDC, 0xDC)
    private val accent = Color.rgb(0x00, 0x6A, 0x6A)
    private val panel = Color.rgb(0xEE, 0xF4, 0xF4)

    private fun paint(size: Float, bold: Boolean = false, color: Int = ink, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.color = color
            textAlign = align
        }

    private val titlePaint = paint(20f, bold = true)
    private val subtitlePaint = paint(11f, color = muted)
    private val smallPaint = paint(8f, color = muted)
    private val headerPaint = paint(8.5f, bold = true, color = muted)
    private val cellPaint = paint(9f)
    private val detailPaint = paint(7.5f, color = muted)
    private val linePaint = Paint().apply {
        color = rule
        strokeWidth = 0.6f
    }

    /** Column x positions; numeric columns are right-aligned at their right edge. */
    private data class Column(val label: String, val left: Float, val width: Float, val right: Boolean) {
        val anchor: Float get() = if (right) left + width else left
    }

    private val columns: List<Column> = run {
        val widths = listOf(118f, 97f, 42f, 50f, 44f, 52f, 52f, 60f)
        val names = with(labels) { listOf(date, type, start, end, breakTime, worked, target, overtime) }
        var x = MARGIN
        names.zip(widths).mapIndexed { index, (name, width) ->
            Column(name, x, width, right = index >= 2).also { x += width }
        }
    }

    private sealed interface Row {
        val height: Float

        data class Day(val day: TimesheetDay, val details: List<String>, val weekStart: Boolean) : Row {
            override val height: Float get() = ROW_HEIGHT + details.size * DETAIL_HEIGHT
        }

        data object Total : Row {
            override val height: Float get() = ROW_HEIGHT + 6f
        }
    }

    fun render(sheet: Timesheet, entries: Boolean): ByteArray {
        val rows = sheet.days.mapIndexed { index, day ->
            Row.Day(day, if (entries) details(day) else emptyList(), weekStart = index > 0 && day.date.dayOfWeek == firstDayOfWeek)
        } + Row.Total
        val pages = paginate(rows)
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, pageRows ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
                val canvas = page.canvas
                var y = MARGIN
                if (index == 0) y = drawHeader(canvas, sheet)
                y = drawTableHeader(canvas, y)
                pageRows.forEach { row ->
                    when (row) {
                        is Row.Day -> drawDay(canvas, row, y)
                        Row.Total -> drawTotal(canvas, sheet, y)
                    }
                    y += row.height
                }
                drawFooter(canvas, index + 1, pages.size)
                document.finishPage(page)
            }
            return ByteArrayOutputStream().use { out ->
                document.writeTo(out)
                out.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    private fun paginate(rows: List<Row>): List<List<Row>> {
        val pages = mutableListOf<MutableList<Row>>(mutableListOf())
        var available = PAGE_HEIGHT - MARGIN - FOOTER_SPACE - HEADER_HEIGHT - TABLE_HEADER_HEIGHT - MARGIN
        for (row in rows) {
            if (row.height > available && pages.last().isNotEmpty()) {
                pages += mutableListOf<Row>()
                available = PAGE_HEIGHT - MARGIN - FOOTER_SPACE - TABLE_HEADER_HEIGHT - MARGIN
            }
            pages.last() += row
            available -= row.height
        }
        return pages
    }

    private fun drawHeader(canvas: Canvas, sheet: Timesheet): Float {
        var y = MARGIN + 18f
        canvas.drawText(labels.title, MARGIN, y, titlePaint)
        y += 18f
        canvas.drawText("${labels.dateText(sheet.from)} – ${labels.dateText(sheet.to)}", MARGIN, y, subtitlePaint)
        y += 14f
        val generated = labels.generated.format("${labels.dateText(sheet.generatedAt.toLocalDate())} ${labels.timeText(sheet.generatedAt.toLocalTime())}")
        canvas.drawText(generated, MARGIN, y, smallPaint)

        // Summary panels: worked, target, overtime in the period, balance at its end.
        y += 14f
        val panels = listOf(
            labels.worked to hm(sheet.workedMinutes),
            labels.target to hm(sheet.targetMinutes),
            labels.overtime to signedHm(sheet.deltaMinutes),
            labels.balanceAfter to signedHm(sheet.balanceAfterMinutes),
        )
        val gap = 8f
        val width = (CONTENT_WIDTH - gap * (panels.size - 1)) / panels.size
        val fill = Paint().apply { color = panel }
        val valuePaint = paint(15f, bold = true, color = accent)
        panels.forEachIndexed { index, (label, value) ->
            val left = MARGIN + index * (width + gap)
            canvas.drawRoundRect(left, y, left + width, y + PANEL_HEIGHT, 6f, 6f, fill)
            canvas.drawText(label, left + 10f, y + 16f, smallPaint)
            canvas.drawText(value, left + 10f, y + 36f, valuePaint)
        }
        y += PANEL_HEIGHT + 12f
        canvas.drawText("${labels.balanceBefore}: ${signedHm(sheet.balanceBeforeMinutes)}", MARGIN, y, smallPaint)
        return MARGIN + HEADER_HEIGHT
    }

    private fun drawTableHeader(canvas: Canvas, top: Float): Float {
        val baseline = top + 12f
        columns.forEach { column ->
            headerPaint.textAlign = if (column.right) Paint.Align.RIGHT else Paint.Align.LEFT
            canvas.drawText(column.label, column.anchor, baseline, headerPaint)
        }
        val bottom = top + TABLE_HEADER_HEIGHT - 4f
        canvas.drawLine(MARGIN, bottom, MARGIN + CONTENT_WIDTH, bottom, linePaint.apply { strokeWidth = 1f })
        linePaint.strokeWidth = 0.6f
        return top + TABLE_HEADER_HEIGHT
    }

    private fun drawDay(canvas: Canvas, row: Row.Day, top: Float) {
        val day = row.day
        if (row.weekStart) canvas.drawLine(MARGIN, top, MARGIN + CONTENT_WIDTH, top, linePaint)
        val quiet = day.kind == TimesheetDayKind.Free || day.kind == TimesheetDayKind.DayOff || day.kind == TimesheetDayKind.Open
        val color = if (quiet) muted else ink
        val counted = day.balanceMinutes != null
        val values = listOf(
            labels.dateText(day.date),
            TimesheetCsv.typeLabel(day.kind, day.dayOff, labels),
            day.start?.let(labels.timeText).orEmpty(),
            day.end?.let(labels.timeText)?.let { if (day.endsNextDay) "$it +1" else it }.orEmpty(),
            if (day.start != null && day.end != null && day.breakMinutes > 0) hm(day.breakMinutes) else "",
            if (day.kind == TimesheetDayKind.Open || day.kind == TimesheetDayKind.Free && day.workedMinutes == 0) "" else hm(day.workedMinutes),
            if (day.targetMinutes > 0) hm(day.targetMinutes) else "",
            if (counted && (day.deltaMinutes != 0 || day.kind == TimesheetDayKind.Worked)) signedHm(day.deltaMinutes) else "",
        )
        val baseline = top + 12f
        columns.zip(values).forEach { (column, value) ->
            cellPaint.color = color
            cellPaint.textAlign = if (column.right) Paint.Align.RIGHT else Paint.Align.LEFT
            canvas.drawText(ellipsize(value, column.width - 4f, cellPaint), column.anchor, baseline, cellPaint)
        }
        row.details.forEachIndexed { index, line ->
            canvas.drawText(line, columns[1].left, baseline + (index + 1) * DETAIL_HEIGHT, detailPaint)
        }
    }

    private fun drawTotal(canvas: Canvas, sheet: Timesheet, top: Float) {
        canvas.drawLine(MARGIN, top + 2f, MARGIN + CONTENT_WIDTH, top + 2f, linePaint.apply { strokeWidth = 1f })
        linePaint.strokeWidth = 0.6f
        val bold = paint(9f, bold = true)
        val baseline = top + 16f
        canvas.drawText(labels.total, MARGIN, baseline, bold)
        bold.textAlign = Paint.Align.RIGHT
        canvas.drawText(hm(sheet.workedMinutes), columns[5].anchor, baseline, bold)
        canvas.drawText(hm(sheet.targetMinutes), columns[6].anchor, baseline, bold)
        canvas.drawText(signedHm(sheet.deltaMinutes), columns[7].anchor, baseline, bold)
    }

    private fun drawFooter(canvas: Canvas, page: Int, pages: Int) {
        val y = PAGE_HEIGHT - MARGIN + 10f
        canvas.drawText(labels.appName, MARGIN, y, smallPaint)
        val right = paint(8f, color = muted, align = Paint.Align.RIGHT)
        canvas.drawText(labels.pageOf.format(page, pages), MARGIN + CONTENT_WIDTH, y, right)
    }

    /** The day's blocks, e.g. "08:52–12:00 · Break 12:00–12:45 · 12:45–17:31", wrapped to the table width. */
    private fun details(day: TimesheetDay): List<String> {
        if (day.entries.isEmpty()) return emptyList()
        val parts = day.entries.map { entry ->
            val range = labels.timeText(entry.start.toLocalTime()) + "–" + (entry.end?.let { labels.timeText(it.toLocalTime()) } ?: labels.running)
            if (entry.isBreak) "${labels.breakEntry} $range" else range
        }
        val maxWidth = CONTENT_WIDTH - (columns[1].left - MARGIN)
        val lines = mutableListOf<String>()
        var current = ""
        for (part in parts) {
            val candidate = if (current.isEmpty()) part else "$current · $part"
            if (detailPaint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines += current
                current = part
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun ellipsize(text: String, width: Float, paint: Paint): String {
        if (paint.measureText(text) <= width) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > width) end--
        return text.substring(0, end) + "…"
    }

    private companion object {
        const val PAGE_WIDTH = 595 // A4 in points
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val HEADER_HEIGHT = 142f
        const val PANEL_HEIGHT = 46f
        const val TABLE_HEADER_HEIGHT = 20f
        const val ROW_HEIGHT = 16f
        const val DETAIL_HEIGHT = 10f
        const val FOOTER_SPACE = 16f
    }
}

/** "8:15" */
internal fun hm(minutes: Int): String {
    val total = abs(minutes)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** "+0:15", "−0:30", "0:00" */
internal fun signedHm(minutes: Int): String = when {
    minutes > 0 -> "+${hm(minutes)}"
    minutes < 0 -> "−${hm(minutes)}"
    else -> hm(0)
}
