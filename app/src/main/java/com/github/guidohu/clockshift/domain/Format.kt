package com.github.guidohu.clockshift.domain

import kotlin.math.abs

private const val MINUS = '−'

/** "8h 30m", "45m", "2h", "0m". */
fun formatDuration(minutes: Int): String {
    val total = abs(minutes)
    val h = total / 60
    val m = total % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

/** "+1h 15m", "−45m", "0m". */
fun formatSigned(minutes: Int): String = when {
    minutes > 0 -> "+${formatDuration(minutes)}"
    minutes < 0 -> "$MINUS${formatDuration(minutes)}"
    else -> "0m"
}

/** "01:05:09" style clock for the live timer. */
fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val minutes = (s % 3600 / 60).toString().padStart(2, '0')
    val seconds = (s % 60).toString().padStart(2, '0')
    return "${s / 3600}:$minutes:$seconds"
}

/** Wall-clock formatter that follows the system 12/24 hour setting. */
fun timeFormatter(is24Hour: Boolean): java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a")
