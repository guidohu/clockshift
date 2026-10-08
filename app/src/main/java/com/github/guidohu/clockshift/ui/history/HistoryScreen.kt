package com.clockshift.app.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clockshift.app.R
import com.clockshift.app.data.AppState
import com.clockshift.app.domain.DayResult
import com.clockshift.app.domain.formatDuration
import com.clockshift.app.domain.formatSigned
import com.clockshift.app.ui.components.DatePickerDialogFor
import com.clockshift.app.ui.components.DateLabels
import com.clockshift.app.ui.components.TabularNumbers
import com.clockshift.app.ui.components.deltaContentColor
import com.clockshift.app.ui.components.rememberDateLabels
import com.clockshift.app.ui.components.rememberNow
import com.clockshift.app.ui.components.rememberTimeFormatter
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    state: AppState,
    onOpenDay: (date: LocalDate, addEntry: Boolean) -> Unit,
) {
    val now by rememberNow(tickMillis = 30_000L)
    val today = now.toLocalDate()
    val dateLabels = rememberDateLabels()
    val timeFormat = rememberTimeFormatter()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }

    val trackingStart = remember(state) { state.calculator.trackingStart(state.settings.startDate) }
    val groups = remember(state, today) {
        state.calculator.days(trackingStart, today, today)
            .asReversed()
            .groupBy { YearMonth.from(it.date) }
            .toList()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.nav_history)) }, scrollBehavior = scrollBehavior) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { datePickerOpen = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_add_entry)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            groups.forEach { (month, days) ->
                stickyHeader(key = "month-$month") {
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = dateLabels.monthYear(month.atDay(1)),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(days, key = { it.date.toEpochDay() }) { day ->
                    DayListItem(
                        day = day,
                        isToday = day.date == today,
                        dateLabels = dateLabels,
                        timeFormat = timeFormat,
                        onClick = { onOpenDay(day.date, false) },
                    )
                }
            }
            item(key = "footer") {
                Text(
                    text = stringResource(R.string.history_footer, dateLabels.short(trackingStart)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                )
            }
        }
    }

    if (datePickerOpen) {
        DatePickerDialogFor(
            initial = today,
            maxDate = today,
            onConfirm = {
                datePickerOpen = false
                onOpenDay(it, true)
            },
            onDismiss = { datePickerOpen = false },
        )
    }
}

@Composable
private fun DayListItem(
    day: DayResult,
    isToday: Boolean,
    dateLabels: DateLabels,
    timeFormat: DateTimeFormatter,
    onClick: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val work = day.entries.filter { !it.isBreak }
    val first = work.firstOrNull()
    val last = work.lastOrNull { it.end != null }

    val title: String
    val subtitle: String?
    var titleMuted = false
    when {
        day.inProgress -> {
            title = stringResource(R.string.history_checked_in)
            subtitle = first?.let { stringResource(R.string.history_since, timeFormat.format(it.start)) }
        }

        day.hasEntries -> {
            val end = last?.end
            title = if (end != null && first != null) {
                "${timeFormat.format(first.start)} – ${timeFormat.format(end)}"
            } else {
                first?.let { timeFormat.format(it.start) }.orEmpty()
            }
            val breakMinutes = day.entries.filter { it.isBreak }.sumOf { it.completedMinutes }
            subtitle = buildString {
                append(
                    if (breakMinutes > 0) stringResource(R.string.entry_break, formatDuration(breakMinutes))
                    else stringResource(R.string.entry_no_break),
                )
            }
        }

        day.dayOff -> {
            title = stringResource(R.string.history_day_off)
            subtitle = null
        }

        day.assumed -> {
            title = stringResource(R.string.history_standard_day)
            subtitle = stringResource(R.string.history_assumed, formatDuration(day.targetMinutes))
            titleMuted = true
        }

        day.targetMinutes == 0 -> {
            title = stringResource(R.string.history_no_work)
            subtitle = null
            titleMuted = true
        }

        else -> {
            title = stringResource(R.string.history_not_logged)
            subtitle = null
            titleMuted = true
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(width = 52.dp, height = 52.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium.merge(TabularNumbers))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                color = if (titleMuted) muted else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = muted)
            }
        }
        if (day.hasEntries) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (day.inProgress) stringResource(R.string.entry_running) else formatDuration(day.workedMinutes),
                    style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                )
                if (day.counted) {
                    Text(
                        text = formatSigned(day.deltaMinutes),
                        style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                        color = deltaContentColor(day.deltaMinutes),
                    )
                }
            }
        }
    }
}
