package com.github.guidohu.clockshift.autotrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.guidohu.clockshift.container
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Receives Wi-Fi, geofence and periodic-check broadcasts for [AutoTracker]. */
class AutoTrackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val tracker = app.container.autoTracker
        val geofence = if (intent.action == ACTION_GEOFENCE) GeofencingEvent.fromIntent(intent) else null
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_WIFI -> tracker.onWifiAvailable()
                    ACTION_CHECK -> tracker.onCheck()
                    ACTION_GEOFENCE -> if (geofence != null && !geofence.hasError()) {
                        val entered = when (geofence.geofenceTransition) {
                            Geofence.GEOFENCE_TRANSITION_ENTER -> true
                            Geofence.GEOFENCE_TRANSITION_EXIT -> false
                            else -> null
                        }
                        // The fix that triggered the event dates it better than its (possibly delayed) delivery.
                        val at = geofence.triggeringLocation?.time?.takeIf { it > 0 }
                            ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
                            ?.coerceAtMost(LocalDateTime.now())
                            ?: LocalDateTime.now()
                        if (entered != null) tracker.onGeofence(entered, at)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_WIFI = "com.github.guidohu.clockshift.action.AUTO_WIFI"
        const val ACTION_GEOFENCE = "com.github.guidohu.clockshift.action.AUTO_GEOFENCE"
        const val ACTION_CHECK = "com.github.guidohu.clockshift.action.AUTO_CHECK"
    }
}
