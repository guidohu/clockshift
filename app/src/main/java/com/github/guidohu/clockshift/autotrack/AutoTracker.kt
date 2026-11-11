package com.github.guidohu.clockshift.autotrack

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.edit
import com.github.guidohu.clockshift.data.AppState
import com.github.guidohu.clockshift.data.ClockRepository
import com.github.guidohu.clockshift.domain.AutoTrackDay
import com.github.guidohu.clockshift.domain.AutoTrackDecision
import com.github.guidohu.clockshift.domain.AutoTrackMode
import com.github.guidohu.clockshift.domain.AutoTrackRules
import com.github.guidohu.clockshift.domain.AutoTrackSettings
import com.github.guidohu.clockshift.domain.Entry
import com.github.guidohu.clockshift.domain.Presence
import com.github.guidohu.clockshift.domain.PresenceSignal
import com.github.guidohu.clockshift.domain.fromLocalMinutes
import com.github.guidohu.clockshift.domain.toLocalMinutes
import com.github.guidohu.clockshift.notify.Notifications
import com.github.guidohu.clockshift.pro.ProManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Automatic check-in and check-out (Pro).
 *
 * Signals come from the system while the app is closed:
 *  - Wi-Fi: Android reports each new Wi-Fi connection (a network callback registered with a
 *    PendingIntent). While the user is at work the connection is checked every few minutes, because
 *    Android does not report losing a network to a closed app.
 *  - Location: a geofence around the workplace reports entering and leaving it.
 *
 * [AutoTrackRules] turns the signals into check-ins and check-outs; this class keeps the presence
 * state, registers with the system, and acts on the decisions. Nothing leaves the device.
 */
class AutoTracker(
    context: Context,
    private val repository: ClockRepository,
    private val pro: ProManager,
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val alarmManager = app.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    /** The settings automatic tracking runs with, or null while it is off, unconfigured or not unlocked. */
    private suspend fun activeConfig(): AutoTrackSettings? {
        val settings = repository.settings()
        return settings.autoTrack.takeIf { pro.isPro.value && settings.setupDone && it.isConfigured }
    }

    /**
     * Registers with the system for the configured mode and unregisters the other. Called on app start,
     * when the settings or the Pro status change, after a reboot and when the app comes to the
     * foreground (permissions or location services may have changed). Changing the configuration
     * forgets where the user was.
     */
    suspend fun sync() = mutex.withLock {
        val config = activeConfig()
        val key = config?.registrationKey()
        if (key != prefs.getString(KEY_CONFIG, null)) {
            savePresence(Presence())
            scheduleCheck(null)
            prefs.edit { putString(KEY_CONFIG, key) }
        }
        // Only undo what was registered before: users who never turned this on never touch these system services.
        val previous = prefs.getString(KEY_REGISTERED_MODE, null)
        if (config?.mode != AutoTrackMode.Wifi && previous == AutoTrackMode.Wifi.name) unregisterWifi()
        if (config?.mode != AutoTrackMode.Location && previous == AutoTrackMode.Location.name) unregisterGeofence()
        val registered = when (config?.mode) {
            AutoTrackMode.Wifi -> registerWifi()
            AutoTrackMode.Location -> registerGeofence(config)
            AutoTrackMode.Off, null -> false
        }
        prefs.edit {
            if (registered) {
                putLong(KEY_REGISTERED_AT, LocalDateTime.now().toLocalMinutes())
                putString(KEY_REGISTERED_MODE, config?.mode?.name)
            } else if (config?.mode?.name != previous) {
                remove(KEY_REGISTERED_MODE)
            }
        }
    }

    /** A Wi-Fi network became available. */
    suspend fun onWifiAvailable() = handle(AutoTrackMode.Wifi) { config, now ->
        if (config.matchesWifi(WifiProbe.currentName(app))) {
            PresenceSignal.Arrived(now, initial = recentlyRegistered(now))
        } else {
            PresenceSignal.Check(now, atWork = false)
        }
    }

    /** The geofence reported entering ([entered]) or leaving the workplace at [at]. */
    suspend fun onGeofence(entered: Boolean, at: LocalDateTime) = handle(AutoTrackMode.Location) { _, now ->
        if (entered) PresenceSignal.Arrived(at, initial = recentlyRegistered(now)) else PresenceSignal.Left(at)
    }

    /** The periodic check is due. */
    suspend fun onCheck() {
        val config = activeConfig() ?: return
        when (config.mode) {
            AutoTrackMode.Wifi -> handle(AutoTrackMode.Wifi) { c, now -> PresenceSignal.Check(now, c.matchesWifi(WifiProbe.currentName(app))) }
            AutoTrackMode.Location -> handle(AutoTrackMode.Location) { _, now -> PresenceSignal.Check(now, atWork = null) }
            AutoTrackMode.Off -> Unit
        }
    }

    private suspend fun handle(
        mode: AutoTrackMode,
        signal: suspend (AutoTrackSettings, LocalDateTime) -> PresenceSignal,
    ) = mutex.withLock {
        val config = activeConfig()?.takeIf { it.mode == mode } ?: return@withLock
        val now = LocalDateTime.now()
        val received = signal(config, now)
        val state = repository.state.first()
        val step = AutoTrackRules.step(loadPresence(), received, config, state.toAutoTrackDay(), now)
        savePresence(step.presence)
        scheduleCheck(step.nextCheckAt)
        act(step.decision, config, now)
    }

    private suspend fun act(decision: AutoTrackDecision, config: AutoTrackSettings, now: LocalDateTime) {
        when (decision) {
            AutoTrackDecision.None -> Unit
            is AutoTrackDecision.CheckIn ->
                if (config.askFirst) {
                    Notifications.askCheckIn(app, decision.at)
                } else {
                    repository.checkInAt(decision.at, now)?.let { Notifications.showAutoCheckedIn(app, it) }
                }

            is AutoTrackDecision.CheckOut ->
                if (config.askFirst) {
                    Notifications.askCheckOut(app, decision.at)
                } else {
                    repository.checkOut(decision.at)?.let { before -> Notifications.showAutoCheckedOut(app, before, decision.at) }
                }
        }
    }

    // --- presence ---------------------------------------------------------------------------

    private fun loadPresence() = Presence(
        since = prefs.time(KEY_SINCE),
        lastSeen = prefs.time(KEY_LAST_SEEN),
        leftAt = prefs.time(KEY_LEFT_AT),
    )

    private fun savePresence(presence: Presence) = prefs.edit {
        putTime(KEY_SINCE, presence.since)
        putTime(KEY_LAST_SEEN, presence.lastSeen)
        putTime(KEY_LEFT_AT, presence.leftAt)
    }

    private fun recentlyRegistered(now: LocalDateTime): Boolean {
        val registeredAt = prefs.time(KEY_REGISTERED_AT) ?: return false
        return Duration.between(registeredAt, now) < INITIAL_REPORT_WINDOW
    }

    // --- periodic check -----------------------------------------------------------------------

    private fun scheduleCheck(at: LocalDateTime?) {
        val pending = broadcast(AutoTrackReceiver.ACTION_CHECK, mutable = false)
        if (at == null) {
            alarmManager.cancel(pending)
            return
        }
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    // --- system registration --------------------------------------------------------------------

    private fun registerWifi(): Boolean {
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        return runCatching {
            app.getSystemService(ConnectivityManager::class.java)
                .registerNetworkCallback(request, broadcast(AutoTrackReceiver.ACTION_WIFI, mutable = true))
        }.isSuccess
    }

    private fun unregisterWifi() {
        runCatching {
            app.getSystemService(ConnectivityManager::class.java)
                .unregisterNetworkCallback(broadcast(AutoTrackReceiver.ACTION_WIFI, mutable = true))
        }
    }

    @SuppressLint("MissingPermission") // Checked first.
    private fun registerGeofence(config: AutoTrackSettings): Boolean {
        val workplace = config.workplace ?: return false
        if (!LocationAccess.hasBackground(app) || !LocationAccess.hasPlayServices(app)) return false
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(workplace.latitude, workplace.longitude, config.radiusMeters.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setNotificationResponsiveness(GEOFENCE_RESPONSIVENESS_MS)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()
        return runCatching {
            LocationServices.getGeofencingClient(app).addGeofences(request, broadcast(AutoTrackReceiver.ACTION_GEOFENCE, mutable = true))
        }.isSuccess
    }

    private fun unregisterGeofence() {
        if (!LocationAccess.hasPlayServices(app)) return
        runCatching {
            LocationServices.getGeofencingClient(app).removeGeofences(broadcast(AutoTrackReceiver.ACTION_GEOFENCE, mutable = true))
        }
    }

    /** Geofencing and network callbacks fill in extras, so their intents must be mutable (they are explicit). */
    private fun broadcast(action: String, mutable: Boolean): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        val intent = Intent(app, AutoTrackReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(app, action.hashCode(), intent, flags)
    }

    private companion object {
        const val PREFS = "auto_track"
        const val KEY_CONFIG = "config"
        const val KEY_REGISTERED_AT = "registered_at"
        const val KEY_REGISTERED_MODE = "registered_mode"
        const val KEY_SINCE = "since"
        const val KEY_LAST_SEEN = "last_seen"
        const val KEY_LEFT_AT = "left_at"
        const val GEOFENCE_ID = "workplace"
        const val GEOFENCE_RESPONSIVENESS_MS = 60_000

        /** Reports this soon after registering describe the current state, not an arrival. */
        val INITIAL_REPORT_WINDOW: Duration = Duration.ofMinutes(2)
    }
}

/** Identifies what the system registration depends on; a change resets the presence state. */
private fun AutoTrackSettings.registrationKey(): String = when (mode) {
    AutoTrackMode.Off -> "off"
    AutoTrackMode.Wifi -> "wifi:" + wifiNames.map { it.trim().lowercase() }.sorted().joinToString("|")
    AutoTrackMode.Location -> "geo:${workplace?.latitude},${workplace?.longitude},$radiusMeters"
}

private fun AppState.toAutoTrackDay(): AutoTrackDay = object : AutoTrackDay {
    override val active: Entry? = activeEntry
    override fun hasEntriesOn(date: LocalDate): Boolean = entriesOn(date).isNotEmpty()
    override fun isWorkingDay(date: LocalDate): Boolean = date !in daysOff && scheduleFor(date).targetMinutes > 0
}

private fun android.content.SharedPreferences.time(key: String): LocalDateTime? =
    if (contains(key)) getLong(key, 0L).fromLocalMinutes() else null

private fun android.content.SharedPreferences.Editor.putTime(key: String, value: LocalDateTime?) {
    if (value == null) remove(key) else putLong(key, value.toLocalMinutes())
}
