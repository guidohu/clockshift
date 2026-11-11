package com.github.guidohu.clockshift.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.guidohu.clockshift.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Preset periods offered by the Overview and the export. */
enum class RangeKind(val labelRes: Int) {
    All(R.string.range_all),
    ThisWeek(R.string.range_this_week),
    ThisMonth(R.string.range_this_month),
    LastMonth(R.string.range_last_month),
    ThisYear(R.string.range_this_year),
    Custom(R.string.range_custom),
}

/** The dates of [kind]; a null start means "since tracking started". */
fun resolveRange(
    kind: RangeKind,
    today: LocalDate,
    customFrom: LocalDate,
    customTo: LocalDate,
    firstDayOfWeek: DayOfWeek,
): Pair<LocalDate?, LocalDate> = when (kind) {
    RangeKind.All -> null to today
    RangeKind.ThisWeek -> today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).let { it to it.plusDays(6) }
    RangeKind.ThisMonth -> today.withDayOfMonth(1) to today.with(TemporalAdjusters.lastDayOfMonth())
    RangeKind.LastMonth -> today.minusMonths(1).withDayOfMonth(1).let { it to it.with(TemporalAdjusters.lastDayOfMonth()) }
    RangeKind.ThisYear -> today.withDayOfYear(1) to today.with(TemporalAdjusters.lastDayOfYear())
    RangeKind.Custom -> customFrom to customTo
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(
    initialFrom: LocalDate,
    initialTo: LocalDate,
    maxDate: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFrom.toUtcMillis(),
        initialSelectedEndDateMillis = initialTo.toUtcMillis(),
        initialDisplayMode = DisplayMode.Picker,
        selectableDates = remember(maxDate) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= maxDate.toUtcMillis()
            }
        },
    )
    val from = state.selectedStartDateMillis
    val to = state.selectedEndDateMillis
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                    Text(
                        stringResource(R.string.range_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = from != null && to != null,
                        onClick = { if (from != null && to != null) onConfirm(from.toUtcDate(), to.toUtcDate()) },
                    ) { Text(stringResource(R.string.action_save)) }
                }
                DateRangePicker(state = state, modifier = Modifier.weight(1f))
            }
        }
    }
}
