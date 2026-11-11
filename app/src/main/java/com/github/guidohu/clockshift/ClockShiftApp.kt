package com.github.guidohu.clockshift

import android.app.Application
import com.github.guidohu.clockshift.autotrack.AutoTracker
import com.github.guidohu.clockshift.backup.BackupManager
import com.github.guidohu.clockshift.data.AppDatabase
import com.github.guidohu.clockshift.data.ClockRepository
import com.github.guidohu.clockshift.data.SettingsStore
import com.github.guidohu.clockshift.notify.Notifications
import com.github.guidohu.clockshift.notify.ReminderScheduler
import com.github.guidohu.clockshift.notify.SmartReminderPlanner
import com.github.guidohu.clockshift.notify.SmartReminderScheduler
import com.github.guidohu.clockshift.pro.ProManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime

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
    val backup by lazy { BackupManager(app, database) }
    val settingsStore by lazy { SettingsStore(database) }
    val repository by lazy { ClockRepository(database, settingsStore) }
    val reminders = ReminderScheduler(app)
    val pro = ProManager(app, scope)
    val autoTracker by lazy { AutoTracker(app, repository, pro) }
    private val workReminders = SmartReminderScheduler(app)
    private val workReminderLock = Mutex()

    fun start() {
        Notifications.createChannels(app)
        // Keep the alarm in sync with the settings; also re-arms it after a reboot or app update.
        scope.launch {
            settingsStore.settings
                .map { if (it.setupDone && it.reminderEnabled) it.reminderMinute else null }
                .distinctUntilChanged()
                .collect { minute -> if (minute != null) reminders.schedule(minute) else reminders.cancel() }
        }
        // Work start / daily-hours-reached reminders follow every change to the entries, schedule or settings.
        scope.launch {
            combine(repository.state, pro.isPro) { _, _ -> }.collect { rescheduleWorkReminder() }
        }
        // The daily backup job follows its settings and the Pro status.
        scope.launch {
            combine(backup.daily, pro.isPro) { daily, isPro -> Triple(daily.enabled, daily.targetUri, isPro) }
                .distinctUntilChanged()
                .collect { (_, _, isPro) -> backup.sync(isPro) }
        }
        // Automatic tracking registers with the system when its settings or the Pro status change.
        scope.launch {
            combine(settingsStore.settings, pro.isPro) { settings, isPro -> Triple(settings.setupDone, settings.autoTrack, isPro) }
                .distinctUntilChanged()
                .collect { autoTracker.sync() }
        }
    }

    /** Re-arms the alarm, e.g. after a reboot or a time zone change. */
    suspend fun rescheduleReminder() {
        val settings = settingsStore.current()
        if (settings.setupDone && settings.reminderEnabled) reminders.schedule(settings.reminderMinute)
    }

    suspend fun rescheduleWorkReminder() = workReminderLock.withLock {
        val state = repository.state.first()
        val next = if (state.settings.setupDone) {
            SmartReminderPlanner.next(state, workReminders.log, LocalDateTime.now(), pro.isPro.value)
        } else {
            null
        }
        workReminders.schedule(next)
    }

    /** Shows the work reminder that is due now, if it still applies, and arms the next one. */
    suspend fun deliverWorkReminder() {
        workReminderLock.withLock {
            val state = repository.state.first()
            if (!state.settings.setupDone) return@withLock
            val now = LocalDateTime.now()
            val due = SmartReminderPlanner.next(state, workReminders.log, now, pro.isPro.value)
            if (due != null && !due.at.isAfter(now.plusMinutes(DUE_TOLERANCE_MINUTES))) {
                Notifications.showWorkReminder(app, due)
                workReminders.log = workReminders.log.shown(due)
            }
        }
        rescheduleWorkReminder()
    }

    private companion object {
        /** Inexact alarms may fire a little early. */
        const val DUE_TOLERANCE_MINUTES = 2L
    }
}

val android.content.Context.container: AppContainer
    get() = (applicationContext as ClockShiftApp).container
