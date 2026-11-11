package com.github.guidohu.clockshift.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import com.github.guidohu.clockshift.MainActivity
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.backup.BackupError
import com.github.guidohu.clockshift.data.AppState
import com.github.guidohu.clockshift.domain.Entry
import com.github.guidohu.clockshift.domain.formatDuration
import com.github.guidohu.clockshift.domain.timeFormatter
import com.github.guidohu.clockshift.domain.toLocalMinutes
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
    private const val CHANNEL_DAILY = "daily_reminder"
    private const val CHANNEL_WORK = "work_reminders"
    private const val CHANNEL_AUTO = "auto_tracking"
    private const val CHANNEL_BACKUP = "backups"

    /** One notification slot per purpose, so a confirmation replaces the notification it answers. */
    const val ID_DAILY = 1
    const val ID_WORK = 2
    const val ID_AUTO = 3
    const val ID_BACKUP = 4

    const val ACTION_LOG_STANDARD = "com.github.guidohu.clockshift.action.LOG_STANDARD"
    const val ACTION_CHECK_OUT = "com.github.guidohu.clockshift.action.CHECK_OUT"
    const val ACTION_CHECK_IN = "com.github.guidohu.clockshift.action.CHECK_IN"
    const val ACTION_UNDO_CHECK_IN = "com.github.guidohu.clockshift.action.UNDO_CHECK_IN"
    const val ACTION_UNDO_CHECK_OUT = "com.github.guidohu.clockshift.action.UNDO_CHECK_OUT"
    const val ACTION_DISMISS = "com.github.guidohu.clockshift.action.DISMISS"
    const val EXTRA_DATE = "date"

    /** Local minutes ([toLocalMinutes]) of the time to check in or out at; absent means "now". */
    const val EXTRA_TIME = "time"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_ENTRY_ID = "entry_id"
    const val EXTRA_ENTRY_START = "entry_start"
    const val EXTRA_ENTRY_KIND = "entry_kind"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_DAILY, context.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_description) },
                NotificationChannel(CHANNEL_WORK, context.getString(R.string.channel_work_name), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_work_description) },
                NotificationChannel(CHANNEL_AUTO, context.getString(R.string.channel_auto_name), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = context.getString(R.string.channel_auto_description) },
                NotificationChannel(CHANNEL_BACKUP, context.getString(R.string.channel_backup_name), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_backup_description) },
            ),
        )
    }

    fun showReminder(context: Context, content: ReminderContent, is24Hour: Boolean) {
        when (content) {
            ReminderContent.Nothing -> return
            is ReminderContent.LogDay -> post(
                context,
                id = ID_DAILY,
                channel = CHANNEL_DAILY,
                title = context.getString(R.string.reminder_title),
                text = context.getString(R.string.reminder_text_log, formatDuration(content.targetMinutes)),
                actions = listOf(
                    action(context, R.string.action_standard_day, ACTION_LOG_STANDARD, ID_DAILY, content.date),
                    customiseAction(context, content.date),
                ),
            )

            is ReminderContent.StillWorking -> post(
                context,
                id = ID_DAILY,
                channel = CHANNEL_DAILY,
                title = context.getString(R.string.reminder_title),
                text = context.getString(R.string.reminder_text_working, content.since.format(timeFormatter(is24Hour))),
                actions = listOf(
                    action(context, R.string.action_check_out_now, ACTION_CHECK_OUT, ID_DAILY, content.since.toLocalDate()),
                    customiseAction(context, content.since.toLocalDate()),
                ),
            )
        }
    }

    /** Work start or "daily hours reached" reminder. */
    fun showWorkReminder(context: Context, reminder: SmartReminder) {
        val time = reminder.suggested.format(timeFormatter(DateFormat.is24HourFormat(context)))
        when (reminder.kind) {
            SmartReminderKind.CheckIn -> post(
                context,
                id = ID_WORK,
                channel = CHANNEL_WORK,
                title = context.getString(R.string.work_start_title),
                text = context.getString(R.string.work_start_text, time),
                actions = listOf(
                    action(context, R.string.action_check_in_now, ACTION_CHECK_IN, ID_WORK, reminder.date),
                    action(context, context.getString(R.string.action_started_at, time), ACTION_CHECK_IN, ID_WORK, reminder.date, reminder.suggested),
                ),
                date = reminder.date,
            )

            SmartReminderKind.CheckOut -> post(
                context,
                id = ID_WORK,
                channel = CHANNEL_WORK,
                title = context.getString(R.string.work_done_title),
                text = context.getString(R.string.work_done_text, time),
                actions = listOf(
                    action(context, R.string.action_check_out_now, ACTION_CHECK_OUT, ID_WORK, reminder.date),
                    action(context, context.getString(R.string.action_check_out_at, time), ACTION_CHECK_OUT, ID_WORK, reminder.date, reminder.suggested),
                ),
                date = reminder.date,
            )
        }
    }

    fun showAutoCheckedIn(context: Context, entry: Entry) {
        val time = entry.start.format(timeFormatter(DateFormat.is24HourFormat(context)))
        val undo = actionIntent(context, ACTION_UNDO_CHECK_IN, ID_AUTO, entry.date).putExtra(EXTRA_ENTRY_ID, entry.id)
        post(
            context,
            id = ID_AUTO,
            channel = CHANNEL_AUTO,
            title = context.getString(R.string.auto_checked_in_title, time),
            text = context.getString(R.string.auto_checked_in_text),
            actions = listOf(action(context, context.getString(R.string.snack_undo), undo, ACTION_UNDO_CHECK_IN)),
            date = entry.date,
        )
    }

    /** [before] is the running block as it was, so undo can put it back. */
    fun showAutoCheckedOut(context: Context, before: Entry, at: LocalDateTime) {
        val time = at.format(timeFormatter(DateFormat.is24HourFormat(context)))
        val undo = actionIntent(context, ACTION_UNDO_CHECK_OUT, ID_AUTO, before.date)
            .putExtra(EXTRA_ENTRY_ID, before.id)
            .putExtra(EXTRA_ENTRY_START, before.start.toLocalMinutes())
            .putExtra(EXTRA_ENTRY_KIND, before.kind.ordinal)
        post(
            context,
            id = ID_AUTO,
            channel = CHANNEL_AUTO,
            title = context.getString(R.string.auto_checked_out_title, time),
            text = context.getString(R.string.auto_checked_out_text),
            actions = listOf(action(context, context.getString(R.string.snack_undo), undo, ACTION_UNDO_CHECK_OUT)),
            date = before.date,
        )
    }

    fun askCheckIn(context: Context, at: LocalDateTime) {
        val time = at.format(timeFormatter(DateFormat.is24HourFormat(context)))
        post(
            context,
            id = ID_AUTO,
            channel = CHANNEL_WORK,
            title = context.getString(R.string.auto_ask_in_title),
            text = context.getString(R.string.auto_ask_in_text, time),
            actions = listOf(
                action(context, context.getString(R.string.action_check_in_at, time), ACTION_CHECK_IN, ID_AUTO, at.toLocalDate(), at),
                action(context, R.string.action_not_now, ACTION_DISMISS, ID_AUTO, at.toLocalDate()),
            ),
            date = at.toLocalDate(),
        )
    }

    fun askCheckOut(context: Context, at: LocalDateTime) {
        val time = at.format(timeFormatter(DateFormat.is24HourFormat(context)))
        post(
            context,
            id = ID_AUTO,
            channel = CHANNEL_WORK,
            title = context.getString(R.string.auto_ask_out_title),
            text = context.getString(R.string.auto_ask_out_text, time),
            actions = listOf(
                action(context, context.getString(R.string.action_check_out_at, time), ACTION_CHECK_OUT, ID_AUTO, at.toLocalDate(), at),
                action(context, R.string.action_still_working, ACTION_DISMISS, ID_AUTO, at.toLocalDate()),
            ),
            date = at.toLocalDate(),
        )
    }

    /** The daily backup could not be written; tapping opens the app to fix the target. */
    fun showBackupFailed(context: Context, error: BackupError) {
        post(
            context,
            id = ID_BACKUP,
            channel = CHANNEL_BACKUP,
            title = context.getString(R.string.backup_failed_title),
            text = context.getString(if (error == BackupError.NoAccess) R.string.backup_error_no_access else R.string.backup_error_write),
            actions = emptyList(),
        )
    }

    /** Replaces notification [id] with a short confirmation that disappears on its own. */
    fun showConfirmation(context: Context, text: String, id: Int = ID_DAILY) {
        val notification = NotificationCompat.Builder(context, channelFor(id))
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp(context, null))
            .setTimeoutAfter(CONFIRMATION_TIMEOUT_MS)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    fun dismiss(context: Context, id: Int = ID_DAILY) {
        context.getSystemService(NotificationManager::class.java).cancel(id)
    }

    private fun channelFor(id: Int) = when (id) {
        ID_WORK -> CHANNEL_WORK
        ID_AUTO -> CHANNEL_AUTO
        else -> CHANNEL_DAILY
    }

    private fun post(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        actions: List<NotificationCompat.Action>,
        date: LocalDate? = null,
    ) {
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(context, date))
            .setAutoCancel(true)
        actions.forEach { builder.addAction(it) }
        context.getSystemService(NotificationManager::class.java).notify(id, builder.build())
    }

    private fun actionIntent(context: Context, action: String, notificationId: Int, date: LocalDate): Intent =
        Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_DATE, date.toEpochDay())
            .putExtra(EXTRA_NOTIFICATION_ID, notificationId)

    private fun action(
        context: Context,
        label: Int,
        action: String,
        notificationId: Int,
        date: LocalDate,
    ): NotificationCompat.Action = action(context, context.getString(label), action, notificationId, date)

    private fun action(
        context: Context,
        label: String,
        action: String,
        notificationId: Int,
        date: LocalDate,
        time: LocalDateTime? = null,
    ): NotificationCompat.Action {
        val intent = actionIntent(context, action, notificationId, date)
        if (time != null) intent.putExtra(EXTRA_TIME, time.toLocalMinutes())
        return action(context, label, intent, action + notificationId + (time != null))
    }

    /** [key] tells pending intents apart; extras alone do not. */
    private fun action(context: Context, label: String, intent: Intent, key: String): NotificationCompat.Action {
        val pending = PendingIntent.getBroadcast(
            context, key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(0, label, pending).build()
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
