package com.clockshift.app.ui.settings

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
import com.clockshift.app.R
import com.clockshift.app.domain.formatSigned
import com.clockshift.app.ui.components.DatePickerDialogFor
import com.clockshift.app.ui.components.NotificationPermission
import com.clockshift.app.ui.components.TimeField
import com.clockshift.app.ui.components.ValueField
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

/** Reminder switch, time and a notice when the system blocks notifications. */
@Composable
fun ReminderSection(
    enabled: Boolean,
    minuteOfDay: Int,
    permission: NotificationPermission,
    onEnabledChange: (Boolean) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showPermissionNotice: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.reminder_switch_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.reminder_switch_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
        }
        if (enabled) {
            TimeField(
                label = stringResource(R.string.reminder_time_label),
                minuteOfDay = minuteOfDay,
                onChange = onMinuteChange,
                modifier = Modifier.fillMaxWidth(),
            )
            if (showPermissionNotice && !permission.granted) {
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
        }
    }
}
