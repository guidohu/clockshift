package com.github.guidohu.clockshift.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.container
import com.github.guidohu.clockshift.domain.Entry
import com.github.guidohu.clockshift.domain.EntryKind
import com.github.guidohu.clockshift.domain.formatDuration
import com.github.guidohu.clockshift.domain.fromLocalMinutes
import com.github.guidohu.clockshift.domain.timeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Runs [block] off the main thread while keeping the broadcast alive. */
private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Fires at the configured time each day. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        async {
            val container = app.container
            val state = container.repository.state.first()
            val content = decideReminder(state, LocalDateTime.now())
            Notifications.showReminder(app, content, DateFormat.is24HourFormat(app))
            container.rescheduleReminder()
        }
    }
}

/** Fires when a work start or daily-hours-reached reminder is due. */
class SmartReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        async { app.container.deliverWorkReminder() }
    }
}

/** Re-arms the alarms and automatic tracking after reboot, app update or clock/time zone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val app = context.applicationContext
        async {
            app.container.rescheduleReminder()
            app.container.rescheduleWorkReminder()
            app.container.autoTracker.sync()
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}

/** Handles the buttons on ClockShift's notifications without opening the app. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action
        val date = LocalDate.ofEpochDay(intent.getLongExtra(Notifications.EXTRA_DATE, LocalDate.now().toEpochDay()))
        val id = intent.getIntExtra(Notifications.EXTRA_NOTIFICATION_ID, Notifications.ID_DAILY)
        val time = if (intent.hasExtra(Notifications.EXTRA_TIME)) intent.getLongExtra(Notifications.EXTRA_TIME, 0L).fromLocalMinutes() else null
        async {
            val repository = app.container.repository
            fun confirm(text: Int, vararg args: Any) = Notifications.showConfirmation(app, app.getString(text, *args), id)
            when (action) {
                Notifications.ACTION_LOG_STANDARD -> {
                    val entries = repository.logStandardDay(date)
                    if (entries.isEmpty()) {
                        Notifications.dismiss(app, id)
                    } else {
                        confirm(R.string.confirm_logged, formatDuration(entries.filter { !it.isBreak }.sumOf { it.completedMinutes }))
                    }
                }

                Notifications.ACTION_CHECK_OUT -> {
                    val before = repository.checkOut(time ?: LocalDateTime.now())
                    if (before == null) Notifications.dismiss(app, id) else confirm(R.string.confirm_checked_out)
                }

                Notifications.ACTION_CHECK_IN -> {
                    val now = LocalDateTime.now()
                    val entry = repository.checkInAt(time ?: now, now)
                    if (entry == null) {
                        Notifications.dismiss(app, id)
                    } else {
                        confirm(R.string.confirm_checked_in, entry.start.format(timeFormatter(DateFormat.is24HourFormat(app))))
                    }
                }

                Notifications.ACTION_UNDO_CHECK_IN -> {
                    val undone = repository.undoCheckIn(intent.getLongExtra(Notifications.EXTRA_ENTRY_ID, 0L))
                    if (undone) confirm(R.string.confirm_check_in_undone) else Notifications.dismiss(app, id)
                }

                Notifications.ACTION_UNDO_CHECK_OUT -> {
                    if (!intent.hasExtra(Notifications.EXTRA_ENTRY_START)) return@async Notifications.dismiss(app, id)
                    val before = Entry(
                        id = intent.getLongExtra(Notifications.EXTRA_ENTRY_ID, 0L),
                        start = intent.getLongExtra(Notifications.EXTRA_ENTRY_START, 0L).fromLocalMinutes(),
                        end = null,
                        kind = EntryKind.entries.getOrElse(intent.getIntExtra(Notifications.EXTRA_ENTRY_KIND, 0)) { EntryKind.Work },
                    )
                    if (repository.restoreSession(before)) confirm(R.string.confirm_check_out_undone) else Notifications.dismiss(app, id)
                }

                Notifications.ACTION_DISMISS -> Notifications.dismiss(app, id)
            }
        }
    }
}
