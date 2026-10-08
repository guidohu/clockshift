package com.clockshift.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.clockshift.app.domain.minuteOfDayToTime
import java.time.LocalDateTime
import java.time.ZoneId

/** Arms a single daily alarm. Inexact on purpose: no special permission, and a few minutes of drift are fine. */
class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(reminderMinute: Int, now: LocalDateTime = LocalDateTime.now()) {
        var next = now.toLocalDate().atTime(reminderMinute.minuteOfDayToTime())
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent())
    }

    fun cancel() = alarmManager.cancel(pendingIntent())

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 100
        const val ACTION_REMIND = "com.clockshift.app.action.REMIND"
    }
}
