package com.github.guidohu.clockshift.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.container
import com.github.guidohu.clockshift.domain.formatDuration
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

/** Re-arms the alarm after reboot, app update or clock/time zone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val app = context.applicationContext
        async { app.container.rescheduleReminder() }
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

/** Handles the buttons on the reminder notification without opening the app. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action
        val date = LocalDate.ofEpochDay(intent.getLongExtra(Notifications.EXTRA_DATE, LocalDate.now().toEpochDay()))
        async {
            val repository = app.container.repository
            when (action) {
                Notifications.ACTION_LOG_STANDARD -> {
                    val entries = repository.logStandardDay(date)
                    if (entries.isEmpty()) {
                        Notifications.dismiss(app)
                    } else {
                        val worked = entries.filter { !it.isBreak }.sumOf { it.completedMinutes }
                        Notifications.showConfirmation(app, app.getString(R.string.confirm_logged, formatDuration(worked)))
                    }
                }

                Notifications.ACTION_CHECK_OUT -> {
                    val before = repository.checkOut(LocalDateTime.now())
                    if (before == null) {
                        Notifications.dismiss(app)
                    } else {
                        Notifications.showConfirmation(app, app.getString(R.string.confirm_checked_out))
                    }
                }
            }
        }
    }
}
