package com.github.guidohu.clockshift.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.domain.formatDuration
import com.github.guidohu.clockshift.domain.formatSigned
import com.github.guidohu.clockshift.domain.minuteOfDayToTime
import com.github.guidohu.clockshift.domain.timeFormatter
import com.github.guidohu.clockshift.ui.theme.overtime
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Digits keep a constant width so a ticking timer does not wobble. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun rememberTimeFormatter(): DateTimeFormatter {
    val context = LocalContext.current
    return remember(context) { timeFormatter(DateFormat.is24HourFormat(context)) }
}

@Composable
fun rememberIs24Hour(): Boolean {
    val context = LocalContext.current
    return remember(context) { DateFormat.is24HourFormat(context) }
}

/** Current date-time, refreshed every [tickMillis] while the screen is visible. */
@Composable
fun rememberNow(tickMillis: Long = 1_000L): State<LocalDateTime> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now = remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(lifecycle, tickMillis) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now.value = LocalDateTime.now()
                delay(tickMillis)
            }
        }
    }
    return now
}

@Composable
fun deltaContentColor(minutes: Int): Color = when {
    minutes > 0 -> MaterialTheme.overtime.positive
    minutes < 0 -> MaterialTheme.overtime.negative
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Small pill with a signed duration, tinted by sign. */
@Composable
fun DeltaChip(minutes: Int, modifier: Modifier = Modifier) {
    val (container, content) = when {
        minutes > 0 -> MaterialTheme.overtime.positiveContainer to MaterialTheme.overtime.onPositiveContainer
        minutes < 0 -> MaterialTheme.overtime.negativeContainer to MaterialTheme.overtime.onNegativeContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(modifier = modifier, shape = CircleShape, color = container, contentColor = content) {
        Text(
            text = formatSigned(minutes),
            style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** A tappable, label-over-value tile that matches [TimeField]; used for any picker-backed value. */
@Composable
fun ValueField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.merge(TabularNumbers),
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** A tappable tile showing a duration; tapping opens a dialog to type hours and minutes. */
@Composable
fun DurationField(
    label: String,
    minutes: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    maxMinutes: Int = 7 * 24 * 60,
) {
    var open by remember { mutableStateOf(false) }
    ValueField(label = label, value = formatDuration(minutes), onClick = { open = true }, modifier = modifier, isError = isError)
    if (open) {
        DurationDialog(
            title = label,
            initialMinutes = minutes,
            maxMinutes = maxMinutes,
            onConfirm = {
                onChange(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun DurationDialog(title: String, initialMinutes: Int, maxMinutes: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var hours by remember { mutableStateOf((initialMinutes / 60).toString()) }
    var minutes by remember { mutableStateOf((initialMinutes % 60).toString()) }
    val minutePart = minutes.toIntOrNull() ?: 0
    val total = (hours.toIntOrNull() ?: 0) * 60 + minutePart
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(enabled = minutePart <= 59 && total <= maxMinutes, onClick = { onConfirm(total) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = hours,
                    onValueChange = { value -> hours = value.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.duration_hours)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { value -> minutes = value.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.duration_minutes)) },
                    singleLine = true,
                    isError = minutePart > 59,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )
}

@Composable
fun TimeField(
    label: String,
    minuteOfDay: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val formatter = rememberTimeFormatter()
    ValueField(
        label = label,
        value = formatter.format(minuteOfDay.minuteOfDayToTime()),
        onClick = { open = true },
        modifier = modifier,
        isError = isError,
    )
    if (open) {
        ClockTimePickerDialog(
            initialMinute = minuteOfDay,
            onConfirm = {
                onChange(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockTimePickerDialog(initialMinute: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = rememberIs24Hour(),
    )
    var useKeyboard by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (useKeyboard) TimeInput(state = state) else TimePicker(state = state)
                IconButton(onClick = { useKeyboard = !useKeyboard }, modifier = Modifier.align(Alignment.Start)) {
                    Icon(
                        imageVector = if (useKeyboard) Icons.Filled.Schedule else Icons.Filled.Keyboard,
                        contentDescription = stringResource(
                            if (useKeyboard) R.string.cd_switch_to_dial else R.string.cd_switch_to_keyboard,
                        ),
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogFor(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    maxDate: LocalDate? = null,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        selectableDates = remember(maxDate) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    maxDate == null || utcTimeMillis <= maxDate.toUtcMillis()
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onConfirm(it.toUtcDate()) } },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Minutes stepper with quick presets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BreakField(
    minutes: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    label: String = stringResource(R.string.label_break),
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { onChange((minutes - BREAK_STEP).coerceAtLeast(MIN_BREAK)) }, enabled = minutes > MIN_BREAK) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_break_less))
                }
                Text(
                    text = formatDuration(minutes),
                    style = MaterialTheme.typography.titleLarge.merge(TabularNumbers),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                FilledTonalIconButton(onClick = { onChange((minutes + BREAK_STEP).coerceAtMost(MAX_BREAK)) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_break_more))
                }
            }
            Spacer(Modifier.size(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BREAK_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = minutes == preset,
                        onClick = { onChange(preset) },
                        label = { Text(formatDuration(preset)) },
                    )
                }
            }
        }
    }
}

private const val BREAK_STEP = 5
private const val MIN_BREAK = 5
private const val MAX_BREAK = 12 * 60
private val BREAK_PRESETS = listOf(15, 30, 45, 60)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 8.dp),
    )
}
