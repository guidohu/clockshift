package com.github.guidohu.clockshift.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
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
