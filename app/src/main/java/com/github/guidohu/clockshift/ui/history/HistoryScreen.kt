package com.github.guidohu.clockshift.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.pluralStringResource
import com.github.guidohu.clockshift.ui.entries.SwipeActionRow
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
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.github.guidohu.clockshift.ui.ClockActions
import kotlinx.coroutines.launch
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
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.data.AppState
import com.github.guidohu.clockshift.domain.DayResult
import com.github.guidohu.clockshift.domain.formatDuration
import com.github.guidohu.clockshift.domain.formatSigned
import com.github.guidohu.clockshift.ui.components.DatePickerDialogFor
import com.github.guidohu.clockshift.ui.components.DateLabels
import com.github.guidohu.clockshift.ui.components.TabularNumbers
import com.github.guidohu.clockshift.ui.components.deltaContentColor
import com.github.guidohu.clockshift.ui.components.rememberDateLabels
import com.github.guidohu.clockshift.ui.components.rememberNow
import com.github.guidohu.clockshift.ui.components.rememberTimeFormatter
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    state: AppState,
    actions: ClockActions,
    onOpenDay: (date: LocalDate, addEntry: Boolean) -> Unit,
) {
    val now by rememberNow(tickMillis = 30_000L)
    val today = now.toLocalDate()
    val dateLabels = rememberDateLabels()
    val timeFormat = rememberTimeFormatter()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    var addForEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var revealedDay by remember { mutableStateOf<Long?>(null) }
    var selectedDays by remember { mutableStateOf(emptySet<Long>()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    val selecting = selectedDays.isNotEmpty()
    BackHandler(enabled = selecting) { selectedDays = emptySet() }

    fun showUndo(message: String, onUndo: suspend () -> Unit) {
        scope.launch {
            val outcome = snackbar.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.snack_undo),
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            if (outcome == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    // Deleting a day removes its entries and its day-off mark; undo puts both back.
    fun deleteDays(days: List<DayResult>) {
        if (days.isEmpty()) return
        scope.launch {
            days.forEach { day ->
                day.entries.forEach { actions.deleteEntry(it.id) }
                if (day.dayOff) actions.setDayOff(day.date, false)
            }
            val message = if (days.size == 1) {
                context.getString(R.string.snack_day_deleted, dateLabels.dayMonth(days.first().date))
            } else {
                context.resources.getQuantityString(R.plurals.snack_days_deleted, days.size, days.size)
            }
            showUndo(message) {
                days.forEach { day ->
                    day.entries.forEach { actions.saveEntry(it) }
                    if (day.dayOff) actions.setDayOff(day.date, true)
                }
            }
        }
    }

    val trackingStart = remember(state) { state.calculator.trackingStart(state.settings.startDate) }
    val groups = remember(state, today) {
        state.calculator.days(trackingStart, today, today)
            .asReversed()
            .groupBy { YearMonth.from(it.date) }
            .toList()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        // One bar for both modes, so the list below keeps its place when the selection starts.
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        if (selecting) stringResource(R.string.history_selected_count, selectedDays.size)
                        else stringResource(R.string.nav_history),
                    )
                },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = { selectedDays = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_cancel_selection))
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(onClick = { confirmBulkDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete_selected))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = { datePickerOpen = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_add_entry)) },
                )
            }
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
                    val key = day.date.toEpochDay()
                    val deletable = day.dayOff || day.entries.isNotEmpty()
                    SwipeActionRow(
                        isRevealed = revealedDay == key,
                        onRevealedChange = { revealed -> revealedDay = if (revealed) key else null },
                        onTap = { onOpenDay(day.date, false) },
                        onDelete = {
                            revealedDay = null
                            deleteDays(listOf(day))
                        },
                        isSelected = key in selectedDays,
                        selectionModeActive = selecting,
                        // Days without anything logged have nothing to delete, so they cannot be selected.
                        onToggleSelect = if (deletable) {
                            { selectedDays = if (key in selectedDays) selectedDays - key else selectedDays + key }
                        } else {
                            null
                        },
                    ) {
                        DayListItem(
                            day = day,
                            isToday = day.date == today,
                            dateLabels = dateLabels,
                            timeFormat = timeFormat,
                        )
                    }
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
                addForEpochDay = it.toEpochDay()
            },
            onDismiss = { datePickerOpen = false },
        )
    }

    if (confirmBulkDelete) {
        val toDelete = groups.flatMap { it.second }.filter { it.date.toEpochDay() in selectedDays }
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text(pluralStringResource(R.plurals.history_delete_title, toDelete.size, toDelete.size)) },
            text = { Text(stringResource(R.string.history_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmBulkDelete = false
                    selectedDays = emptySet()
                    deleteDays(toDelete)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmBulkDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    addForEpochDay?.let { epochDay ->
        val date = LocalDate.ofEpochDay(epochDay)
        val result = state.calculator.day(date, today)
        val schedule = state.scheduleFor(date)
        AddDayDialog(
            title = dateLabels.full(date),
            standardDetail = formatDuration(schedule.targetMinutes),
            standardUnavailable = when {
                result.hasEntries -> R.string.history_add_unavailable_logged
                !schedule.working || schedule.targetMinutes <= 0 -> R.string.history_add_unavailable_no_work
                else -> null
            },
            dayOffUnavailable = if (result.dayOff) R.string.history_add_already_off else null,
            onStandard = {
                addForEpochDay = null
                scope.launch {
                    val created = actions.logStandardDay(date)
                    if (created.isEmpty()) return@launch
                    val worked = created.filter { !it.isBreak }.sumOf { it.completedMinutes }
                    showUndo(context.getString(R.string.snack_logged_day, formatDuration(worked))) {
                        created.forEach { actions.deleteEntry(it.id) }
                    }
                }
            },
            onDayOff = {
                addForEpochDay = null
                actions.setDayOff(date, true)
                showUndo(context.getString(R.string.snack_day_off_on, dateLabels.dayMonth(date))) { actions.setDayOff(date, false) }
            },
            onCustom = {
                addForEpochDay = null
                onOpenDay(date, true)
            },
            onDismiss = { addForEpochDay = null },
        )
    }
}

@Composable
private fun AddDayDialog(
    title: String,
    standardDetail: String,
    standardUnavailable: Int?,
    dayOffUnavailable: Int?,
    onStandard: () -> Unit,
    onDayOff: () -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = {
            Column {
                AddDayOption(
                    icon = Icons.Filled.EventAvailable,
                    title = stringResource(R.string.history_add_standard),
                    detail = standardUnavailable?.let { stringResource(it) } ?: stringResource(R.string.history_add_standard_hint, standardDetail),
                    enabled = standardUnavailable == null,
                    onClick = onStandard,
                )
                AddDayOption(
                    icon = Icons.Filled.BeachAccess,
                    title = stringResource(R.string.history_add_day_off),
                    detail = dayOffUnavailable?.let { stringResource(it) } ?: stringResource(R.string.history_add_day_off_hint),
                    enabled = dayOffUnavailable == null,
                    onClick = onDayOff,
                )
                AddDayOption(
                    icon = Icons.Filled.Edit,
                    title = stringResource(R.string.history_add_custom),
                    detail = stringResource(R.string.history_add_custom_hint),
                    enabled = true,
                    onClick = onCustom,
                )
            }
        },
    )
}

@Composable
private fun AddDayOption(icon: ImageVector, title: String, detail: String, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).alpha(if (enabled) 1f else 0.5f),
    )
}

@Composable
private fun DayListItem(
    day: DayResult,
    isToday: Boolean,
    dateLabels: DateLabels,
    timeFormat: DateTimeFormatter,
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
