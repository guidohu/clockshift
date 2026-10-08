package com.clockshift.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clockshift.app.R
import com.clockshift.app.data.AppState
import com.clockshift.app.ui.ClockActions
import com.clockshift.app.ui.components.rememberNotificationPermission
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: AppState, actions: ClockActions) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings = state.settings
    val permission = rememberNotificationPermission()
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    // Local mirror so two quick taps in a row both apply even before the database has re-emitted.
    val storedWeek = state.weekFor(LocalDate.now())
    var week by remember(storedWeek) { mutableStateOf(storedWeek) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.nav_settings)) }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
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
            item(key = "reminder") {
                SettingsCard(title = stringResource(R.string.settings_reminder)) {
                    ReminderSection(
                        enabled = settings.reminderEnabled,
                        minuteOfDay = settings.reminderMinute,
                        permission = permission,
                        onEnabledChange = { enabled ->
                            actions.updateSettings { it.copy(reminderEnabled = enabled) }
                            if (enabled && permission.needsRuntimePrompt) permission.request()
                        },
                        onMinuteChange = { minute -> actions.updateSettings { it.copy(reminderMinute = minute) } },
                    )
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
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
