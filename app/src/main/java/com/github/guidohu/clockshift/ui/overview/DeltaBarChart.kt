package com.github.guidohu.clockshift.ui.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.github.guidohu.clockshift.domain.formatSigned
import com.github.guidohu.clockshift.ui.theme.overtime
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Bars above the axis for overtime, below it for time owed. Tapping a bar selects it.
 * [labels] are the x-axis captions; only as many as fit are drawn.
 */
@Composable
fun DeltaBarChart(
    values: List<Int>,
    labels: List<String>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) return
    val positive = MaterialTheme.overtime.positive
    val negative = MaterialTheme.overtime.negative
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .semantics { contentDescription = description }
            .pointerInput(values.size, selected) {
                detectTapGestures { offset ->
                    val slot = size.width / values.size.toFloat()
                    val index = (offset.x / slot).toInt().coerceIn(0, values.lastIndex)
                    onSelect(if (index == selected) null else index)
                }
            },
    ) {
        val labelArea = 22.dp.toPx()
        val topPad = 20.dp.toPx()
        val plotHeight = size.height - labelArea - topPad
        val maxValue = max(values.max(), 0)
        val minValue = min(values.min(), 0)
        val span = max(maxValue - minValue, MIN_SPAN_MINUTES).toFloat()
        val zeroY = topPad + plotHeight * (maxValue / span)
        val slot = size.width / values.size
        val barWidth = min(slot * 0.6f, 28.dp.toPx())
        val corner = CornerRadius(4.dp.toPx())
        val minBar = 2.dp.toPx()

        drawLine(axisColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())

        values.forEachIndexed { index, value ->
            val centerX = slot * index + slot / 2
            val dim = if (selected != null && selected != index) 0.35f else 1f
            if (value == 0) {
                drawRoundRect(axisColor, Offset(centerX - barWidth / 2, zeroY - minBar / 2), Size(barWidth, minBar), corner)
            } else {
                val height = max(kotlin.math.abs(value) / span * plotHeight, minBar)
                val color: Color = (if (value > 0) positive else negative).copy(alpha = dim)
                val top = if (value > 0) zeroY - height else zeroY
                drawRoundRect(color, Offset(centerX - barWidth / 2, top), Size(barWidth, height), corner)
            }
        }

        // Extremes, so the scale is readable without gridlines.
        if (maxValue > 0) {
            drawText(measurer, formatSigned(maxValue), Offset(0f, 0f), labelStyle)
        }
        if (minValue < 0) {
            val layout = measurer.measure(formatSigned(minValue), labelStyle)
            drawText(layout, topLeft = Offset(0f, topPad + plotHeight - layout.size.height))
        }

        val maxLabels = max((size.width / 52.dp.toPx()).toInt(), 1)
        val step = ceil(values.size / maxLabels.toFloat()).toInt().coerceAtLeast(1)
        labels.forEachIndexed { index, label ->
            if (index % step != 0) return@forEachIndexed
            val layout = measurer.measure(label, labelStyle)
            val x = (slot * index + slot / 2 - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, size.height - labelArea + 4.dp.toPx()))
        }
    }
}

private val ChartHeight = 200.dp
private const val MIN_SPAN_MINUTES = 30
