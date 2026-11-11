package com.github.guidohu.clockshift.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.domain.SmartReminderSettings
import com.github.guidohu.clockshift.domain.formatDuration
import com.github.guidohu.clockshift.domain.formatSigned
import com.github.guidohu.clockshift.ui.components.DurationField
import com.github.guidohu.clockshift.ui.components.DatePickerDialogFor
import com.github.guidohu.clockshift.ui.components.NotificationPermission
import com.github.guidohu.clockshift.ui.components.TimeField
import com.github.guidohu.clockshift.ui.components.ValueField
import com.github.guidohu.clockshift.ui.pro.LocalPro
import com.github.guidohu.clockshift.ui.pro.ProBadge
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

/** "Counting from" date and "starting balance" pickers, shared by the wizard and Settings. */
@Composable
fun TrackingFields(
    startDate: LocalDate,
    openingBalanceMinutes: Int,
    onStartDateChange: (LocalDate) -> Unit,
    onOpeningBalanceChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    var balanceOpen by rememberSaveable { mutableStateOf(false) }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ValueField(
            label = stringResource(R.string.tracking_start_label),
            value = dateFormat.format(startDate),
            onClick = { datePickerOpen = true },
            modifier = Modifier.fillMaxWidth(),
        )
        ValueField(
            label = stringResource(R.string.tracking_balance_label),
            value = if (openingBalanceMinutes == 0) stringResource(R.string.tracking_balance_none) else formatSigned(openingBalanceMinutes),
            onClick = { balanceOpen = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (datePickerOpen) {
        DatePickerDialogFor(
            initial = startDate,
            maxDate = LocalDate.now(),
            onConfirm = {
                onStartDateChange(it)
                datePickerOpen = false
            },
            onDismiss = { datePickerOpen = false },
        )
    }
    if (balanceOpen) {
        BalanceDialog(
            initialMinutes = openingBalanceMinutes,
            onConfirm = {
                onOpeningBalanceChange(it)
                balanceOpen = false
            },
            onDismiss = { balanceOpen = false },
        )
    }
}

/** Enter an overtime balance as ahead (+) or behind (−) plus hours and minutes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceDialog(initialMinutes: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var ahead by rememberSaveable { mutableStateOf(initialMinutes >= 0) }
    var hours by rememberSaveable { mutableStateOf((abs(initialMinutes) / 60).toString()) }
    var minutes by rememberSaveable { mutableStateOf((abs(initialMinutes) % 60).toString()) }

    val hoursValue = hours.toIntOrNull()
    val minutesValue = minutes.toIntOrNull()
    val hoursValid = hoursValue != null && hoursValue in 0..999
    val minutesValid = minutesValue != null && minutesValue in 0..59
    val total = if (hoursValid && minutesValid) hoursValue!! * 60 + minutesValue!! else 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.balance_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(R.string.balance_dialog_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = ahead,
                        onClick = { ahead = true },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(stringResource(R.string.balance_ahead)) }
                    SegmentedButton(
                        selected = !ahead,
                        onClick = { ahead = false },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(stringResource(R.string.balance_behind)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.balance_hours)) },
                        isError = !hoursValid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.balance_minutes)) },
                        isError = !minutesValid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = hoursValid && minutesValid,
                onClick = { onConfirm(if (ahead) total else -total) },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A title and summary with a switch; the whole row toggles. */
@Composable
fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                trailing()
            }
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(start = 16.dp))
    }
}

/**
 * Work start and work end (evening) reminders with their times, plus a notice when the system blocks
 * notifications. Shared by the wizard and the Notifications screen.
 */
@Composable
fun WorkRemindersSection(
    reminders: SmartReminderSettings,
    onRemindersChange: (SmartReminderSettings) -> Unit,
    endEnabled: Boolean,
    endMinute: Int,
    onEndEnabledChange: (Boolean) -> Unit,
    onEndMinuteChange: (Int) -> Unit,
    permission: NotificationPermission,
    modifier: Modifier = Modifier,
    showPermissionNotice: Boolean = true,
) {
    val isPro = LocalPro.current.isPro
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwitchRow(
            title = stringResource(R.string.work_start_switch),
            summary = if (isPro) {
                stringResource(R.string.work_start_summary_pro, formatDuration(reminders.checkInDelayMinutes))
            } else {
                stringResource(R.string.work_start_summary)
            },
            checked = reminders.checkIn,
            onCheckedChange = { onRemindersChange(reminders.copy(checkIn = it)) },
        )
        if (reminders.checkIn && isPro) {
            DurationField(
                label = stringResource(R.string.work_start_delay),
                minutes = reminders.checkInDelayMinutes,
                onChange = { onRemindersChange(reminders.copy(checkInDelayMinutes = it)) },
                maxMinutes = SmartReminderSettings.MAX_DELAY,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SwitchRow(
            title = stringResource(R.string.reminder_switch_title),
            summary = stringResource(R.string.reminder_switch_summary),
            checked = endEnabled,
            onCheckedChange = onEndEnabledChange,
        )
        if (endEnabled) {
            TimeField(
                label = stringResource(R.string.reminder_time_label),
                minuteOfDay = endMinute,
                onChange = onEndMinuteChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showPermissionNotice && (endEnabled || reminders.checkIn) && !permission.granted) {
            NotificationsBlockedCard(permission)
        }
    }
}

/** Pro reminders: the "daily hours reached" check-out reminder. Shown locked without Pro. */
@Composable
fun ProRemindersSection(
    reminders: SmartReminderSettings,
    onRemindersChange: (SmartReminderSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPro = LocalPro.current.isPro
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwitchRow(
            title = stringResource(R.string.work_done_switch),
            summary = stringResource(R.string.work_done_summary),
            checked = isPro && reminders.checkOut,
            enabled = isPro,
            onCheckedChange = { onRemindersChange(reminders.copy(checkOut = it)) },
            trailing = { if (!isPro) ProBadge() },
        )
        if (isPro && reminders.checkOut) {
            DurationField(
                label = stringResource(R.string.work_done_delay),
                minutes = reminders.checkOutDelayMinutes,
                onChange = { onRemindersChange(reminders.copy(checkOutDelayMinutes = it)) },
                maxMinutes = SmartReminderSettings.MAX_DELAY,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!isPro) {
            Text(
                stringResource(R.string.work_reminders_pro_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun NotificationsBlockedCard(permission: NotificationPermission) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.reminder_blocked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(onClick = permission::request) { Text(stringResource(R.string.reminder_allow)) }
        }
    }
}
