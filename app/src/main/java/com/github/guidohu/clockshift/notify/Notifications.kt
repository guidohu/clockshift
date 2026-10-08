package com.clockshift.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.clockshift.app.MainActivity
import com.clockshift.app.R
import com.clockshift.app.data.AppState
import com.clockshift.app.domain.formatDuration
import com.clockshift.app.domain.timeFormatter
import java.time.LocalDate
import java.time.LocalDateTime

/** What, if anything, the evening reminder should say. */
sealed interface ReminderContent {
    data object Nothing : ReminderContent
    data class LogDay(val date: LocalDate, val targetMinutes: Int) : ReminderContent
    data class StillWorking(val since: LocalDateTime) : ReminderContent
}

fun decideReminder(state: AppState, now: LocalDateTime): ReminderContent {
    val active = state.activeEntry
    if (active != null) return ReminderContent.StillWorking(active.start)

    val today = now.toLocalDate()
    val schedule = state.scheduleFor(today)
    val hasLoggedTime = state.entriesOn(today).any { !it.isBreak }
    return if (today in state.daysOff || !schedule.working || hasLoggedTime) {
        ReminderContent.Nothing
    } else {
        ReminderContent.LogDay(today, schedule.targetMinutes)
    }
}

object Notifications {
    private const val CHANNEL_ID = "daily_reminder"
    private const val NOTIFICATION_ID = 1

    const val ACTION_LOG_STANDARD = "com.clockshift.app.action.LOG_STANDARD"
    const val ACTION_CHECK_OUT = "com.clockshift.app.action.CHECK_OUT"
    const val EXTRA_DATE = "date"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showReminder(context: Context, content: ReminderContent, is24Hour: Boolean) {
        when (content) {
            ReminderContent.Nothing -> return
            is ReminderContent.LogDay -> post(
                context,
                text = context.getString(R.string.reminder_text_log, formatDuration(content.targetMinutes)),
                primaryAction = action(
                    context, R.string.action_standard_day, Notifications.ACTION_LOG_STANDARD, content.date,
                ),
                secondaryAction = customiseAction(context, content.date),
            )

            is ReminderContent.StillWorking -> post(
                context,
                text = context.getString(
                    R.string.reminder_text_working,
                    content.since.format(timeFormatter(is24Hour)),
                ),
                primaryAction = action(
                    context, R.string.action_check_out_now, Notifications.ACTION_CHECK_OUT, content.since.toLocalDate(),
                ),
                secondaryAction = customiseAction(context, content.since.toLocalDate()),
            )
        }
    }

    /** Replaces the reminder with a short confirmation that disappears on its own. */
    fun showConfirmation(context: Context, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp(context, null))
            .setTimeoutAfter(CONFIRMATION_TIMEOUT_MS)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun post(
        context: Context,
        text: String,
        primaryAction: NotificationCompat.Action,
        secondaryAction: NotificationCompat.Action,
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(context, null))
            .setAutoCancel(true)
            .addAction(primaryAction)
            .addAction(secondaryAction)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun action(context: Context, label: Int, action: String, date: LocalDate): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_DATE, date.toEpochDay())
        val pending = PendingIntent.getBroadcast(
            context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(0, context.getString(label), pending).build()
    }

    private fun customiseAction(context: Context, date: LocalDate): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            0,
            context.getString(R.string.action_customise),
            openApp(context, date),
        ).build()

    private fun openApp(context: Context, date: LocalDate?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (date != null) intent.putExtra(MainActivity.EXTRA_OPEN_DAY, date.toEpochDay())
        return PendingIntent.getActivity(
            context,
            date?.toEpochDay()?.toInt() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val CONFIRMATION_TIMEOUT_MS = 8_000L
}
