package com.github.guidohu.clockshift.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.github.guidohu.clockshift.autotrack.LocationAccess

/** Whether the app may post notifications, plus a way to ask for it. */
@Stable
class NotificationPermission internal constructor(private val context: Context) {
    var granted by mutableStateOf(areNotificationsEnabled())
        private set

    internal var launcher: ((String) -> Unit)? = null

    /** True when the system can still show its permission dialog (Android 13+, not granted yet). */
    val needsRuntimePrompt: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted

    internal fun refresh() {
        granted = areNotificationsEnabled()
    }

    /** Shows the system dialog on Android 13+, otherwise opens the app's notification settings. */
    fun request() {
        val launch = launcher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && launch != null) {
            launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openSettings()
        }
    }

    fun openSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun areNotificationsEnabled() = NotificationManagerCompat.from(context).areNotificationsEnabled()
}

@Composable
fun rememberNotificationPermission(onResult: (Boolean) -> Unit = {}): NotificationPermission {
    val context = LocalContext.current
    val permission = remember(context) { NotificationPermission(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        permission.refresh()
        onResult(isGranted)
    }
    SideEffect { permission.launcher = { launcher.launch(it) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permission.refresh() }
    return permission
}

/**
 * Location access for automatic check-in: precise location while in use (needed to read the Wi-Fi name
 * and to locate the workplace) and, on Android 10+, "Allow all the time" so arrivals and departures are
 * noticed while the app is closed. Android only lets apps ask for the latter after the former.
 */
@Stable
class LocationPermission internal constructor(private val context: Context) {
    var foreground by mutableStateOf(LocationAccess.hasForeground(context))
        private set
    var background by mutableStateOf(LocationAccess.hasBackground(context))
        private set
    var locationEnabled by mutableStateOf(LocationAccess.isLocationEnabled(context))
        private set

    internal var foregroundLauncher: (() -> Unit)? = null
    internal var backgroundLauncher: (() -> Unit)? = null
    internal var afterForeground: (() -> Unit)? = null

    internal fun refresh() {
        foreground = LocationAccess.hasForeground(context)
        background = LocationAccess.hasBackground(context)
        locationEnabled = LocationAccess.isLocationEnabled(context)
    }

    /** Asks for location while in use, then runs [then] if it was granted. */
    fun requestForeground(then: () -> Unit = {}) {
        if (foreground) return then()
        afterForeground = then
        foregroundLauncher?.invoke() ?: openAppSettings()
    }

    /** Asks for everything automatic check-in needs, one system dialog after the other. */
    fun requestAll() = requestForeground {
        if (!background) backgroundLauncher?.invoke() ?: openAppSettings()
    }

    fun openAppSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openLocationSettings() {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun rememberLocationPermission(): LocationPermission {
    val context = LocalContext.current
    val permission = remember(context) { LocationPermission(context) }
    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permission.refresh()
        val then = permission.afterForeground
        permission.afterForeground = null
        if (permission.foreground) then?.invoke()
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission.refresh()
    }
    SideEffect {
        permission.foregroundLauncher = {
            foregroundLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        permission.backgroundLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            { backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
        } else {
            null
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permission.refresh() }
    return permission
}
