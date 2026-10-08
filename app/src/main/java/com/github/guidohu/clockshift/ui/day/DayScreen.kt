package com.github.guidohu.clockshift.ui.day

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.data.AppState
import com.github.guidohu.clockshift.domain.DayResult
import com.github.guidohu.clockshift.domain.DaySchedule
import com.github.guidohu.clockshift.domain.EntryKind
import com.github.guidohu.clockshift.domain.formatDuration
import com.github.guidohu.clockshift.domain.formatSigned
import com.github.guidohu.clockshift.domain.minuteOfDayToTime
import com.github.guidohu.clockshift.ui.ClockActions
import com.github.guidohu.clockshift.ui.components.TabularNumbers
import com.github.guidohu.clockshift.ui.components.deltaContentColor
import com.github.guidohu.clockshift.ui.components.rememberDateLabels
import com.github.guidohu.clockshift.ui.components.rememberNow
import com.github.guidohu.clockshift.ui.components.rememberTimeFormatter
import com.github.guidohu.clockshift.ui.entries.AddBlockFab
import com.github.guidohu.clockshift.ui.entries.EntryListCard
import com.github.guidohu.clockshift.ui.entries.FabScrim
import com.github.guidohu.clockshift.ui.entries.rememberEntryEditor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    state: AppState,
    actions: ClockActions,
    date: LocalDate,
    openNewEntry: Boolean,
    onBack: () -> Unit,
) {
    val now by rememberNow(tickMillis = 30_000L)
    val today = now.toLocalDate()
    val context = LocalContext.current
    val dateLabels = rememberDateLabels()
    val timeFormat = rememberTimeFormatter()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val result = remember(state, date, today) { state.calculator.day(date, today) }
    val schedule = state.scheduleFor(date)
    val editor = rememberEntryEditor(date, result.entries, schedule, now, actions, snackbar)
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = fabExpanded) { fabExpanded = false }

    // Coming from "Add entry" in the history: open the sheet once.
    var addHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openNewEntry) {
        if (openNewEntry && !addHandled) {
            addHandled = true
            editor.openAdd(EntryKind.Work)
        }
    }

    fun showUndo(message: String, onUndo: suspend () -> Unit) {
        scope.launch {
            val outcome = snackbar.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.snack_undo),
                withDismissAction = true,
            )
            if (outcome == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dateLabels.full(date)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (result.entries.isNotEmpty()) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_clear_day)) },
                                onClick = {
                                    menuOpen = false
                                    val removed = result.entries
                                    scope.launch {
                                        removed.forEach { actions.deleteEntry(it.id) }
                                        showUndo(context.getString(R.string.snack_day_cleared)) {
                                            removed.forEach { actions.saveEntry(it) }
                                        }
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            AddBlockFab(
                expanded = fabExpanded,
                onToggle = { fabExpanded = !fabExpanded },
                onAddWork = {
                    fabExpanded = false
                    editor.openAdd(EntryKind.Work)
                },
                onAddBreak = {
                    fabExpanded = false
                    editor.openAdd(EntryKind.Break)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        FabScrim(visible = fabExpanded, onDismiss = { fabExpanded = false })
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + FAB_CLEARANCE,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "summary") { SummaryCard(result) }
            item(key = "dayoff") {
                DayOffRow(checked = result.dayOff, onChange = { off -> actions.setDayOff(date, off) })
            }
            if (!result.hasEntries && !result.dayOff && schedule.working && schedule.targetMinutes > 0) {
                item(key = "standard") {
                    StandardDayCard(
                        schedule = schedule,
                        isPast = date < today,
                        timeFormat = timeFormat,
                        onUseStandard = {
                            scope.launch {
                                val created = actions.logStandardDay(date)
                                if (created.isEmpty()) return@launch
                                val worked = created.filter { !it.isBreak }.sumOf { it.completedMinutes }
                                showUndo(context.getString(R.string.snack_logged_day, formatDuration(worked))) {
                                    created.forEach { actions.deleteEntry(it.id) }
                                }
                            }
                        },
                        onCustom = { editor.openAdd(EntryKind.Work) },
                    )
                }
            }
            if (result.entries.isNotEmpty()) {
                item(key = "entries") {
                    EntryListCard(
                        entries = result.entries,
                        onEdit = editor::openEdit,
                        onDelete = editor::delete,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    editor.Sheet()
}

@Composable
private fun SummaryCard(result: DayResult) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(stringResource(R.string.stat_target), formatDuration(result.targetMinutes))
                Stat(
                    stringResource(R.string.stat_worked),
                    if (result.counted || result.hasEntries) formatDuration(result.workedMinutes) else "–",
                )
                Stat(
                    label = stringResource(R.string.stat_balance),
                    value = if (result.counted) formatSigned(result.deltaMinutes) else "–",
                    valueColor = deltaContentColor(result.deltaMinutes),
                )
            }
            val note = when {
                result.inProgress -> R.string.day_note_running
                result.assumed -> R.string.day_note_assumed
                result.dayOff -> R.string.day_note_off
                !result.counted -> R.string.day_note_pending
                else -> null
            }
            if (note != null) {
                Text(
                    stringResource(note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers), color = valueColor)
    }
}

@Composable
private fun DayOffRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.day_off_title)) },
            supportingContent = { Text(stringResource(R.string.day_off_summary)) },
            trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun StandardDayCard(
    schedule: DaySchedule,
    isPast: Boolean,
    timeFormat: DateTimeFormatter,
    onUseStandard: () -> Unit,
    onCustom: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (isPast) R.string.day_standard_assumed else R.string.day_nothing_logged),
                style = MaterialTheme.typography.titleMedium,
            )
            val breakRanges = schedule.breaks.joinToString(", ") {
                "${timeFormat.format(it.startMinute.minuteOfDayToTime())} \u2013 ${timeFormat.format(it.endMinute.minuteOfDayToTime())}"
            }
            val breakText = when (schedule.breaks.size) {
                0 -> stringResource(R.string.day_breaks_none)
                1 -> stringResource(R.string.day_breaks_one, breakRanges)
                else -> stringResource(R.string.day_breaks_many, breakRanges)
            }
            Text(
                text = stringResource(
                    R.string.day_standard_details,
                    timeFormat.format(schedule.startMinute.minuteOfDayToTime()),
                    timeFormat.format(schedule.endMinute.minuteOfDayToTime()),
                    breakText,
                    formatDuration(schedule.targetMinutes),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onUseStandard, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(stringResource(R.string.action_use_standard))
                }
                OutlinedButton(onClick = onCustom, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(stringResource(R.string.action_custom_entry))
                }
            }
        }
    }
}

private val FAB_CLEARANCE = 96.dp
