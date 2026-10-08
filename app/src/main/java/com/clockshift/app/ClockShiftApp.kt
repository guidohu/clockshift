package com.clockshift.app

import android.app.Application
import com.clockshift.app.data.AppDatabase
import com.clockshift.app.data.ClockRepository
import com.clockshift.app.data.SettingsStore
import com.clockshift.app.notify.Notifications
import com.clockshift.app.notify.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ClockShiftApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}

/** Hand-wired dependencies; small enough that a DI framework would only add noise. */
class AppContainer(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { AppDatabase.create(app) }
    val settingsStore by lazy { SettingsStore(database) }
    val repository by lazy { ClockRepository(database, settingsStore) }
    val reminders = ReminderScheduler(app)

    fun start() {
        Notifications.createChannel(app)
        // Keep the alarm in sync with the settings; also re-arms it after a reboot or app update.
        scope.launch {
            settingsStore.settings
                .map { if (it.setupDone && it.reminderEnabled) it.reminderMinute else null }
                .distinctUntilChanged()
                .collect { minute -> if (minute != null) reminders.schedule(minute) else reminders.cancel() }
        }
    }

    /** Re-arms the alarm, e.g. after a reboot or a time zone change. */
    suspend fun rescheduleReminder() {
        val settings = settingsStore.current()
        if (settings.setupDone && settings.reminderEnabled) reminders.schedule(settings.reminderMinute)
    }
}

val android.content.Context.container: AppContainer
    get() = (applicationContext as ClockShiftApp).container
