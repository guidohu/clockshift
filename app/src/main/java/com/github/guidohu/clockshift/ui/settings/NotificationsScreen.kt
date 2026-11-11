package com.github.guidohu.clockshift.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.data.AppState
import com.github.guidohu.clockshift.ui.ClockActions
import com.github.guidohu.clockshift.ui.components.rememberNotificationPermission

/** Settings › Notifications: each reminder on its own switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(state: AppState, actions: ClockActions, onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val settings = state.settings
    val permission = rememberNotificationPermission()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_notifications)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
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
            item(key = "work") {
                SettingsCard(title = stringResource(R.string.notifications_reminders)) {
                    WorkRemindersSection(
                        reminders = settings.smartReminders,
                        onRemindersChange = { reminders ->
                            actions.updateSettings { it.copy(smartReminders = reminders) }
                            if (reminders.checkIn && permission.needsRuntimePrompt) permission.request()
                        },
                        endEnabled = settings.reminderEnabled,
                        endMinute = settings.reminderMinute,
                        onEndEnabledChange = { enabled ->
                            actions.updateSettings { it.copy(reminderEnabled = enabled) }
                            if (enabled && permission.needsRuntimePrompt) permission.request()
                        },
                        onEndMinuteChange = { minute -> actions.updateSettings { it.copy(reminderMinute = minute) } },
                        permission = permission,
                    )
                }
            }
            item(key = "pro") {
                SettingsCard(title = stringResource(R.string.notifications_smart)) {
                    ProRemindersSection(
                        reminders = settings.smartReminders,
                        onRemindersChange = { reminders ->
                            actions.updateSettings { it.copy(smartReminders = reminders) }
                            if (reminders.checkOut && permission.needsRuntimePrompt) permission.request()
                        },
                    )
                }
            }
        }
    }
}
