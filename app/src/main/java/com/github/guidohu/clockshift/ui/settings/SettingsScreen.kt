package com.github.guidohu.clockshift.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.data.AppState
import com.github.guidohu.clockshift.ui.ClockActions
import com.github.guidohu.clockshift.domain.minuteOfDayToTime
import com.github.guidohu.clockshift.ui.components.rememberTimeFormatter
import com.github.guidohu.clockshift.ui.backup.BackupSection
import com.github.guidohu.clockshift.ui.export.ExportSheet
import com.github.guidohu.clockshift.ui.pro.LocalPro
import com.github.guidohu.clockshift.ui.pro.ProActiveRow
import com.github.guidohu.clockshift.ui.pro.ProBadge
import com.github.guidohu.clockshift.ui.pro.ProUpsellCard
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: AppState, actions: ClockActions, onOpenNotifications: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val settings = state.settings
    val pro = LocalPro.current
    var exportOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    // Local mirror so two quick taps in a row both apply even before the database has re-emitted.
    val storedWeek = state.weekFor(LocalDate.now())
    var week by remember(storedWeek) { mutableStateOf(storedWeek) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "schedule") {
                SettingsCard(title = stringResource(R.string.settings_schedule)) {
                    ScheduleEditor(
                        week = week,
                        onChange = {
                            week = it
                            actions.saveSchedule(it)
                        },
                    )
                    Text(
                        stringResource(R.string.settings_schedule_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "tracking") {
                SettingsCard(title = stringResource(R.string.settings_tracking)) {
                    TrackingFields(
                        startDate = settings.startDate,
                        openingBalanceMinutes = settings.openingBalanceMinutes,
                        onStartDateChange = { date -> actions.updateSettings { it.copy(startDate = date) } },
                        onOpeningBalanceChange = { minutes -> actions.updateSettings { it.copy(openingBalanceMinutes = minutes) } },
                    )
                }
            }
            item(key = "notifications") {
                NotificationsRow(state, onClick = onOpenNotifications)
            }
            item(key = "export") {
                NavigationCard(
                    icon = Icons.Outlined.IosShare,
                    title = stringResource(R.string.settings_export),
                    summary = stringResource(R.string.settings_export_summary),
                    badge = !pro.isPro,
                    onClick = { exportOpen = true },
                )
            }
            item(key = "backup") {
                SettingsCard(title = stringResource(R.string.settings_backup)) { BackupSection() }
            }
            item(key = "pro") {
                if (pro.isPro) {
                    SettingsCard(title = stringResource(R.string.pro_title)) { ProActiveRow() }
                } else {
                    ProUpsellCard()
                }
            }
            item(key = "auto") {
                SettingsCard(title = stringResource(R.string.settings_auto), badge = !pro.isPro) {
                    if (pro.isPro) {
                        AutoTrackSection(
                            settings = settings.autoTrack,
                            onChange = { auto -> actions.updateSettings { it.copy(autoTrack = auto) } },
                        )
                    } else {
                        Text(
                            stringResource(R.string.auto_locked),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "about") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.settings_about_version, stringResource(R.string.app_name), version),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        stringResource(R.string.settings_about_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (exportOpen) ExportSheet(state = state, onDismiss = { exportOpen = false })
}

@Composable
internal fun SettingsCard(title: String, badge: Boolean = false, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (badge) ProBadge()
            }
            content()
        }
    }
}

/** Opens Settings › Notifications; the summary says which reminders are on. */
@Composable
private fun NotificationsRow(state: AppState, onClick: () -> Unit) {
    val settings = state.settings
    val timeFormat = rememberTimeFormatter()
    val on = buildList {
        if (settings.smartReminders.checkIn) add(stringResource(R.string.notifications_summary_start))
        if (settings.reminderEnabled) {
            add(stringResource(R.string.notifications_summary_end, timeFormat.format(settings.reminderMinute.minuteOfDayToTime())))
        }
        if (settings.smartReminders.checkOut && LocalPro.current.isPro) add(stringResource(R.string.notifications_summary_done))
    }
    NavigationCard(
        icon = Icons.Outlined.Notifications,
        title = stringResource(R.string.settings_notifications),
        summary = if (on.isEmpty()) stringResource(R.string.notifications_summary_none) else on.joinToString(" · "),
        onClick = onClick,
    )
}

/** A card that opens another screen or sheet. */
@Composable
private fun NavigationCard(icon: ImageVector, title: String, summary: String, onClick: () -> Unit, badge: Boolean = false) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (badge) ProBadge()
                }
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
