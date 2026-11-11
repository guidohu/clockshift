package com.github.guidohu.clockshift.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.autotrack.LocationAccess
import com.github.guidohu.clockshift.autotrack.LocationProbe
import com.github.guidohu.clockshift.autotrack.WifiProbe
import com.github.guidohu.clockshift.domain.AutoTrackMode
import com.github.guidohu.clockshift.domain.AutoTrackSettings
import com.github.guidohu.clockshift.domain.formatDuration
import com.github.guidohu.clockshift.ui.components.LocationPermission
import com.github.guidohu.clockshift.ui.components.rememberLocationPermission
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** Automatic check-in/out settings (Pro), shared by the wizard and Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoTrackSection(
    settings: AutoTrackSettings,
    onChange: (AutoTrackSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val permission = rememberLocationPermission()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val modes = listOf(
            AutoTrackMode.Off to R.string.auto_mode_off,
            AutoTrackMode.Wifi to R.string.auto_mode_wifi,
            AutoTrackMode.Location to R.string.auto_mode_location,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = settings.mode == mode,
                    // No permission prompt here: Play requires the explanation (the notice below) to come first.
                    onClick = { onChange(settings.copy(mode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                ) { Text(stringResource(label)) }
            }
        }

        when (settings.mode) {
            AutoTrackMode.Off -> Text(
                stringResource(R.string.auto_off_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AutoTrackMode.Wifi -> WifiNames(settings, onChange, permission)
            AutoTrackMode.Location -> Workplace(settings, onChange, permission)
        }

        if (settings.mode != AutoTrackMode.Off) {
            PermissionNotices(settings.mode, permission)
            Behaviour(settings, onChange)
        }
    }
}

@Composable
private fun WifiNames(settings: AutoTrackSettings, onChange: (AutoTrackSettings) -> Unit, permission: LocationPermission) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var notConnected by rememberSaveable { mutableStateOf(false) }
    var dialogOpen by rememberSaveable { mutableStateOf(false) }

    fun add(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && !settings.matchesWifi(trimmed)) onChange(settings.copy(wifiNames = settings.wifiNames + trimmed))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.auto_wifi_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        settings.wifiNames.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                IconButton(onClick = { onChange(settings.copy(wifiNames = settings.wifiNames - name)) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.auto_wifi_remove, name))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                enabled = !reading,
                onClick = {
                    permission.requestForeground {
                        reading = true
                        scope.launch {
                            val name = WifiProbe.currentName(context)
                            reading = false
                            notConnected = name == null
                            if (name != null) add(name)
                        }
                    }
                },
            ) {
                if (reading) {
                    CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                }
                Text(stringResource(R.string.auto_wifi_add_current), modifier = Modifier.padding(start = 8.dp))
            }
            TextButton(onClick = { dialogOpen = true }) { Text(stringResource(R.string.auto_wifi_enter)) }
        }
        if (notConnected) {
            Text(
                stringResource(R.string.auto_wifi_not_connected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (dialogOpen) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(stringResource(R.string.auto_wifi_enter_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_SSID_LENGTH) },
                    label = { Text(stringResource(R.string.auto_wifi_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        add(name)
                        dialogOpen = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { dialogOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Workplace(settings: AutoTrackSettings, onChange: (AutoTrackSettings) -> Unit, permission: LocationPermission) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val workplace = settings.workplace

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.auto_location_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (workplace == null) {
                stringResource(R.string.auto_location_not_set)
            } else {
                stringResource(
                    R.string.auto_location_set,
                    String.format(Locale.US, "%.5f, %.5f", workplace.latitude, workplace.longitude),
                )
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            enabled = !locating,
            onClick = {
                permission.requestForeground {
                    locating = true
                    scope.launch {
                        val point = LocationProbe.current(context)
                        locating = false
                        failed = point == null
                        if (point != null) onChange(settings.copy(workplace = point))
                    }
                }
            },
        ) {
            if (locating) {
                CircularProgressIndicator(
                    Modifier.size(ButtonDefaults.IconSize),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            }
            Text(
                stringResource(if (workplace == null) R.string.auto_location_use_current else R.string.auto_location_update),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (failed) {
            Text(
                stringResource(R.string.auto_location_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(stringResource(R.string.auto_location_radius, settings.radiusMeters), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = settings.radiusMeters.toFloat(),
            onValueChange = { onChange(settings.copy(radiusMeters = (it / RADIUS_STEP).roundToInt() * RADIUS_STEP)) },
            valueRange = AutoTrackSettings.MIN_RADIUS.toFloat()..AutoTrackSettings.MAX_RADIUS.toFloat(),
            steps = (AutoTrackSettings.MAX_RADIUS - AutoTrackSettings.MIN_RADIUS) / RADIUS_STEP - 1,
        )
    }
}

@Composable
private fun PermissionNotices(mode: AutoTrackMode, permission: LocationPermission) {
    val context = LocalContext.current
    when {
        mode == AutoTrackMode.Location && !LocationAccess.hasPlayServices(context) ->
            Notice(stringResource(R.string.auto_no_play_services), action = null, onAction = {})

        !permission.background -> Notice(
            text = stringResource(R.string.auto_permission_needed),
            action = stringResource(R.string.auto_permission_allow),
            onAction = permission::requestAll,
            secondary = stringResource(R.string.auto_permission_settings),
            onSecondary = permission::openAppSettings,
        )

        !permission.locationEnabled -> Notice(
            text = stringResource(R.string.auto_location_off),
            action = stringResource(R.string.auto_location_turn_on),
            onAction = permission::openLocationSettings,
        )
    }
}

@Composable
private fun Notice(
    text: String,
    action: String?,
    onAction: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            if (action != null || secondary != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (action != null) Button(onClick = onAction) { Text(action) }
                    if (secondary != null) TextButton(onClick = onSecondary) { Text(secondary) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Behaviour(settings: AutoTrackSettings, onChange: (AutoTrackSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !settings.askFirst,
                onClick = { onChange(settings.copy(askFirst = false)) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(stringResource(R.string.auto_behaviour_automatic)) }
            SegmentedButton(
                selected = settings.askFirst,
                onClick = { onChange(settings.copy(askFirst = true)) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(stringResource(R.string.auto_behaviour_ask)) }
        }
        Text(
            stringResource(if (settings.askFirst) R.string.auto_behaviour_ask_hint else R.string.auto_behaviour_automatic_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(
            title = stringResource(R.string.auto_workdays_only),
            summary = stringResource(R.string.auto_workdays_only_summary),
            checked = settings.workingDaysOnly,
            onCheckedChange = { onChange(settings.copy(workingDaysOnly = it)) },
        )
        Text(stringResource(R.string.auto_leave_delay), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AutoTrackSettings.LEAVE_DELAY_OPTIONS.forEach { minutes ->
                FilterChip(
                    selected = settings.leaveDelayMinutes == minutes,
                    onClick = { onChange(settings.copy(leaveDelayMinutes = minutes)) },
                    label = { Text(formatDuration(minutes)) },
                )
            }
        }
        Text(
            stringResource(R.string.auto_leave_delay_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val RADIUS_STEP = 50
private const val MAX_SSID_LENGTH = 32
