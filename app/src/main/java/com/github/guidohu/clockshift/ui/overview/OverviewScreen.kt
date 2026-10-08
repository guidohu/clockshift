package com.github.guidohu.clockshift.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.data.AppState
import com.github.guidohu.clockshift.domain.Granularity
import com.github.guidohu.clockshift.domain.PeriodStat
import com.github.guidohu.clockshift.domain.RangeStats
import com.github.guidohu.clockshift.domain.formatDuration
import com.github.guidohu.clockshift.domain.formatSigned
import com.github.guidohu.clockshift.ui.components.DateLabels
import com.github.guidohu.clockshift.ui.components.DeltaChip
import com.github.guidohu.clockshift.ui.components.TabularNumbers
import com.github.guidohu.clockshift.ui.components.rememberDateLabels
import com.github.guidohu.clockshift.ui.components.rememberNow
import com.github.guidohu.clockshift.ui.components.toUtcDate
import com.github.guidohu.clockshift.ui.components.toUtcMillis
import com.github.guidohu.clockshift.ui.theme.overtime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

private enum class RangeKind(val labelRes: Int) {
    All(R.string.range_all),
    ThisWeek(R.string.range_this_week),
    ThisMonth(R.string.range_this_month),
    LastMonth(R.string.range_last_month),
    ThisYear(R.string.range_this_year),
    Custom(R.string.range_custom),
}

private const val MAX_LIST_ROWS = 120

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(state: AppState) {
    val now by rememberNow(tickMillis = 30_000L)
    val today = now.toLocalDate()
    val dateLabels = rememberDateLabels()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val firstDayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }

    var kind by rememberSaveable { mutableStateOf(RangeKind.All) }
    var customFrom by rememberSaveable { mutableLongStateOf(today.minusDays(29).toEpochDay()) }
    var customTo by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var selectedBar by rememberSaveable { mutableStateOf<Int?>(null) }

    val range = resolveRange(kind, today, LocalDate.ofEpochDay(customFrom), LocalDate.ofEpochDay(customTo), firstDayOfWeek)
    val stats = remember(state, range, today) {
        state.calculator.stats(range.first, range.second, today, state.settings, firstDayOfWeek)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.nav_overview)) }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "ranges") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(RangeKind.entries.toList(), key = { it.name }) { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = {
                                selectedBar = null
                                if (option == RangeKind.Custom) pickerOpen = true else kind = option
                            },
                            label = {
                                Text(
                                    if (option == RangeKind.Custom && kind == RangeKind.Custom) {
                                        "${dateLabels.dayMonth(LocalDate.ofEpochDay(customFrom))} – ${dateLabels.dayMonth(LocalDate.ofEpochDay(customTo))}"
                                    } else {
                                        stringResource(option.labelRes)
                                    },
                                )
                            },
                        )
                    }
                }
            }
            item(key = "hero") {
                HeroCard(stats, kind, dateLabels, Modifier.padding(horizontal = 16.dp))
            }
            item(key = "tiles") {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.stat_worked), formatDuration(stats.workedMinutes), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stat_target), formatDuration(stats.targetMinutes), Modifier.weight(1f))
                }
            }
            if (stats.isEmpty) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.overview_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            } else {
                item(key = "days") {
                    Text(
                        text = stringResource(R.string.overview_days, stats.loggedDays, stats.assumedDays, stats.dayOffs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                if (stats.periods.isNotEmpty()) {
                    item(key = "chart") {
                        ChartCard(stats, selectedBar, { selectedBar = it }, dateLabels, Modifier.padding(horizontal = 16.dp))
                    }
                    item(key = "breakdown") {
                        BreakdownCard(stats, dateLabels, Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        DateRangeDialog(
            initialFrom = LocalDate.ofEpochDay(customFrom),
            initialTo = LocalDate.ofEpochDay(customTo),
            maxDate = today,
            onConfirm = { from, to ->
                customFrom = from.toEpochDay()
                customTo = to.toEpochDay()
                kind = RangeKind.Custom
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

private fun resolveRange(
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

@Composable
private fun HeroCard(stats: RangeStats, kind: RangeKind, dateLabels: DateLabels, modifier: Modifier = Modifier) {
    val balance = stats.balanceMinutes
    val container = when {
        balance > 0 -> MaterialTheme.overtime.positiveContainer
        balance < 0 -> MaterialTheme.overtime.negativeContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        balance > 0 -> MaterialTheme.overtime.onPositiveContainer
        balance < 0 -> MaterialTheme.overtime.onNegativeContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(colors = CardDefaults.cardColors(containerColor = container, contentColor = content), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (kind == RangeKind.All) R.string.overview_balance_all else R.string.overview_balance_range),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                formatSigned(balance),
                style = MaterialTheme.typography.displayLarge.merge(TabularNumbers),
            )
            Text(
                stringResource(
                    when {
                        balance > 0 -> R.string.overview_ahead
                        balance < 0 -> R.string.overview_behind
                        else -> R.string.overview_on_target
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${dateLabels.dayMonth(stats.from)} – ${dateLabels.dayMonth(stats.to)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (stats.openingBalanceMinutes != 0) {
                Text(
                    stringResource(R.string.overview_includes_opening, formatSigned(stats.openingBalanceMinutes)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (stats.todayPending) {
                Text(stringResource(R.string.balance_pending), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers))
        }
    }
}

private fun periodLabel(period: PeriodStat, granularity: Granularity, labels: DateLabels): String = when (granularity) {
    Granularity.DAY -> labels.short(period.start)
    Granularity.WEEK -> "${labels.dayMonth(period.start)} – ${labels.dayMonth(period.end)}"
    Granularity.MONTH -> labels.monthYear(period.start)
}

private fun axisLabel(period: PeriodStat, granularity: Granularity, labels: DateLabels): String = when (granularity) {
    Granularity.DAY -> period.start.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
    Granularity.WEEK -> labels.dayMonth(period.start)
    Granularity.MONTH -> labels.monthShort(period.start)
}

@Composable
private fun ChartCard(
    stats: RangeStats,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    dateLabels: DateLabels,
    modifier: Modifier = Modifier,
) {
    val periods = stats.periods
    val granularity = stats.granularity
    val titleRes = when (granularity) {
        Granularity.DAY -> R.string.chart_title_day
        Granularity.WEEK -> R.string.chart_title_week
        Granularity.MONTH -> R.string.chart_title_month
    }
    val selectedPeriod = selected?.let(periods::getOrNull)
    val description = stringResource(
        R.string.chart_description,
        stringResource(titleRes),
        formatSigned(periods.maxOf { it.deltaMinutes }),
        formatSigned(periods.minOf { it.deltaMinutes }),
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (selectedPeriod != null) {
                    "${periodLabel(selectedPeriod, granularity, dateLabels)} · ${formatSigned(selectedPeriod.deltaMinutes)}"
                } else {
                    stringResource(R.string.chart_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DeltaBarChart(
                values = periods.map { it.deltaMinutes },
                labels = periods.map { axisLabel(it, granularity, dateLabels) },
                selected = selected,
                onSelect = onSelect,
                description = description,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun BreakdownCard(stats: RangeStats, dateLabels: DateLabels, modifier: Modifier = Modifier) {
    val rows = stats.periods.asReversed().take(MAX_LIST_ROWS)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.breakdown_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            rows.forEachIndexed { index, period ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            periodLabel(period, stats.granularity, dateLabels),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(
                                R.string.breakdown_detail,
                                formatDuration(period.workedMinutes),
                                formatDuration(period.targetMinutes),
                            ),
                            style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DeltaChip(period.deltaMinutes)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
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
