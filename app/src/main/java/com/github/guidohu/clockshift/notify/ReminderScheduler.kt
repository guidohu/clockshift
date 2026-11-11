package com.github.guidohu.clockshift.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.github.guidohu.clockshift.domain.minuteOfDayToTime
import java.time.LocalDate
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
        const val ACTION_REMIND = "com.github.guidohu.clockshift.action.REMIND"
    }
}

/**
 * Arms the next work start / daily-hours-reached reminder (see [SmartReminderPlanner]) and remembers
 * which reminders were shown, so each fires at most once a day. Inexact like the evening reminder.
 */
class SmartReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var log: SmartReminderLog
        get() = SmartReminderLog(
            checkInDay = prefs.getLong(KEY_CHECK_IN, NONE).takeIf { it != NONE }?.let(LocalDate::ofEpochDay),
            checkOutDay = prefs.getLong(KEY_CHECK_OUT, NONE).takeIf { it != NONE }?.let(LocalDate::ofEpochDay),
        )
        set(value) = prefs.edit {
            putLong(KEY_CHECK_IN, value.checkInDay?.toEpochDay() ?: NONE)
            putLong(KEY_CHECK_OUT, value.checkOutDay?.toEpochDay() ?: NONE)
        }

    fun schedule(reminder: SmartReminder?) {
        if (reminder == null) {
            alarmManager.cancel(pendingIntent())
            return
        }
        val triggerAt = reminder.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, SmartReminderReceiver::class.java).setAction(ACTION_REMIND_WORK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 101
        const val ACTION_REMIND_WORK = "com.github.guidohu.clockshift.action.REMIND_WORK"
        const val PREFS = "work_reminders"
        const val KEY_CHECK_IN = "check_in_day"
        const val KEY_CHECK_OUT = "check_out_day"
        const val NONE = Long.MIN_VALUE
    }
}
