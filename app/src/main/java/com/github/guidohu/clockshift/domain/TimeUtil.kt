package com.clockshift.app.domain

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/** Date-times are persisted as "local minutes": minutes since 1970-01-01T00:00 wall-clock time. */
fun LocalDateTime.toLocalMinutes(): Long = toEpochSecond(ZoneOffset.UTC) / 60

fun Long.fromLocalMinutes(): LocalDateTime = LocalDateTime.ofEpochSecond(this * 60, 0, ZoneOffset.UTC)

fun Int.minuteOfDayToTime(): LocalTime = LocalTime.of((this / 60).coerceIn(0, 23), this % 60)

fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute
