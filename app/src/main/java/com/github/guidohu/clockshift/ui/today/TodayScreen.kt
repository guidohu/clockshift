package com.github.guidohu.clockshift.ui.today

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.data.AppState
import com.github.guidohu.clockshift.domain.DayResult
import com.github.guidohu.clockshift.domain.Entry
import com.github.guidohu.clockshift.domain.EntryKind
import com.github.guidohu.clockshift.domain.formatClock
import com.github.guidohu.clockshift.domain.formatDuration
import com.github.guidohu.clockshift.domain.formatSigned
import com.github.guidohu.clockshift.domain.minuteOfDayToTime
import com.github.guidohu.clockshift.ui.ClockActions
import com.github.guidohu.clockshift.ui.components.DeltaChip
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
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** A session counts as forgotten once it has been running this long. */
private const val FORGOTTEN_AFTER_HOURS = 16L

private val ButtonHeight = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: AppState,
    actions: ClockActions,
    onOpenDay: (LocalDate) -> Unit,
    onOpenOverview: () -> Unit,
) {
    val minuteClock by rememberNow(tickMillis = 30_000L)
    val today = minuteClock.toLocalDate()
    val context = LocalContext.current
    val dateLabels = rememberDateLabels()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val timeFormat = rememberTimeFormatter()
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = fabExpanded) { fabExpanded = false }

    val dayResult = remember(state, today) { state.calculator.day(today, today) }
    val stats = remember(state, today) {
        state.calculator.stats(null, today, today, state.settings, WeekFields.of(Locale.getDefault()).firstDayOfWeek)
    }
    val editor = rememberEntryEditor(today, dayResult.entries, state.scheduleFor(today), minuteClock, actions, snackbar)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            dateLabels.dayMonthLong(today),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
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
    ) { padding ->
        FabScrim(visible = fabExpanded, onDismiss = { fabExpanded = false })
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "status") {
                StatusCard(
                    state = state,
                    dayResult = dayResult,
                    timeFormat = timeFormat,
                    onCheckIn = actions::checkIn,
                    onStartBreak = actions::startBreak,
                    onEndBreak = actions::endBreak,
                    onCheckOut = {
                        scope.launch {
                            val running = state.activeEntry
                            val worked = dayResult.workedMinutes +
                                (running?.takeIf { !it.isBreak }?.minutesAt(LocalDateTime.now()) ?: 0)
                            val before = actions.checkOut() ?: return@launch
                            val result = snackbar.showSnackbar(
                                message = context.getString(R.string.snack_checked_out, formatDuration(worked)),
                                actionLabel = context.getString(R.string.snack_undo),
                                withDismissAction = true,
                            )
                            if (result == SnackbarResult.ActionPerformed) actions.saveEntry(before)
                        }
                    },
                    onLogStandard = {
                        scope.launch {
                            val created = actions.logStandardDay(today)
                            if (created.isEmpty()) return@launch
                            val worked = created.filter { !it.isBreak }.sumOf { it.completedMinutes }
                            val result = snackbar.showSnackbar(
                                message = context.getString(R.string.snack_logged, formatDuration(worked)),
                                actionLabel = context.getString(R.string.snack_undo),
                                withDismissAction = true,
                            )
                            if (result == SnackbarResult.ActionPerformed) created.forEach { actions.deleteEntry(it.id) }
                        }
                    },
                    onAddManually = { editor.openAdd(EntryKind.Work) },
                    onLogDayOff = {
                        actions.setDayOff(today, true)
                        scope.launch {
                            val result = snackbar.showSnackbar(
                                message = context.getString(R.string.snack_day_off),
                                actionLabel = context.getString(R.string.snack_undo),
                                withDismissAction = true,
                            )
                            if (result == SnackbarResult.ActionPerformed) actions.setDayOff(today, false)
                        }
                    },
                    onOpenDay = onOpenDay,
                )
            }
            item(key = "balance") {
                BalanceCard(
                    balanceMinutes = stats.balanceMinutes,
                    todayPending = stats.todayPending,
                    onClick = onOpenOverview,
                )
            }
            if (dayResult.entries.isNotEmpty()) {
                item(key = "entries") {
                    EntryListCard(
                        entries = dayResult.entries,
                        onEdit = editor::openEdit,
                        onDelete = editor::delete,
                        title = stringResource(R.string.entries_title),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    editor.Sheet()
}

@Composable
private fun StatusCard(
    state: AppState,
    dayResult: DayResult,
    timeFormat: DateTimeFormatter,
    onCheckIn: () -> Unit,
    onStartBreak: () -> Unit,
    onEndBreak: () -> Unit,
    onCheckOut: () -> Unit,
    onLogStandard: () -> Unit,
    onAddManually: () -> Unit,
    onLogDayOff: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val active = state.activeEntry
    // Only tick every second while a timer is on screen.
    val now by rememberNow(tickMillis = if (active != null) 1_000L else 30_000L)
    val today = dayResult.date

    when {
        active != null && Duration.between(active.start, now).toHours() >= FORGOTTEN_AFTER_HOURS ->
            ForgottenCard(active, timeFormat, onSetEnd = { onOpenDay(active.date) }, onCheckOutNow = onCheckOut)

        active != null -> WorkingCard(
            active = active,
            dayResult = dayResult,
            breakPlannedMinutes = state.scheduleFor(today).breakMinutes,
            now = now,
            timeFormat = timeFormat,
            onStartBreak = onStartBreak,
            onEndBreak = onEndBreak,
            onCheckOut = onCheckOut,
        )

        dayResult.hasEntries -> DoneCard(
            dayResult = dayResult,
            onCheckInAgain = onCheckIn,
            onEditDay = { onOpenDay(today) },
        )

        dayResult.dayOff -> DayOffCard(onCheckIn = onCheckIn, onEditDay = { onOpenDay(today) })

        else -> IdleCard(
            state = state,
            today = today,
            timeFormat = timeFormat,
            onCheckIn = onCheckIn,
            onLogStandard = onLogStandard,
            onAddManually = onAddManually,
            onLogDayOff = onLogDayOff,
        )
    }
}

@Composable
private fun cardColors(container: Color = MaterialTheme.colorScheme.surfaceContainerHigh): CardColors =
    CardDefaults.cardColors(containerColor = container)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleCard(
    state: AppState,
    today: LocalDate,
    timeFormat: DateTimeFormatter,
    onCheckIn: () -> Unit,
    onLogStandard: () -> Unit,
    onAddManually: () -> Unit,
    onLogDayOff: () -> Unit,
) {
    val schedule = state.scheduleFor(today)
    val hasTarget = schedule.working && schedule.targetMinutes > 0
    Card(colors = cardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (hasTarget) R.string.today_ready else R.string.today_no_work_planned),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = if (hasTarget) {
                    stringResource(
                        R.string.today_target_line,
                        formatDuration(schedule.targetMinutes),
                        timeFormat.format(schedule.startMinute.minuteOfDayToTime()),
                        timeFormat.format(schedule.endMinute.minuteOfDayToTime()),
                    )
                } else {
                    stringResource(R.string.today_no_target)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCheckIn, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.action_check_in), style = MaterialTheme.typography.titleMedium)
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (hasTarget) {
                    TextButton(onClick = onLogStandard) { Text(stringResource(R.string.action_log_standard)) }
                }
                TextButton(onClick = onAddManually) { Text(stringResource(R.string.action_add_manually)) }
                TextButton(onClick = onLogDayOff) { Text(stringResource(R.string.action_log_day_off)) }
            }
        }
    }
}

@Composable
private fun WorkingCard(
    active: Entry,
    dayResult: DayResult,
    breakPlannedMinutes: Int,
    now: LocalDateTime,
    timeFormat: DateTimeFormatter,
    onStartBreak: () -> Unit,
    onEndBreak: () -> Unit,
    onCheckOut: () -> Unit,
) {
    val onBreak = active.isBreak
    val container = if (onBreak) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (onBreak) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    val firstStart = dayResult.entries.firstOrNull { !it.isBreak }?.start ?: active.start
    // The timer shows everything worked today: finished blocks plus the running one (frozen on a break).
    val workedSeconds = dayResult.workedMinutes * 60L + if (onBreak) 0L else active.secondsAt(now)
    val workedMinutes = (workedSeconds / 60).toInt()
    val target = dayResult.targetMinutes
    val breakTaken = dayResult.entries.filter { it.isBreak }.sumOf { it.minutesAt(now) }
    val remaining = target - workedMinutes

    Card(colors = cardColors(container), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (onBreak) {
                    stringResource(R.string.today_on_break_since, timeFormat.format(active.start))
                } else {
                    stringResource(R.string.today_working_since, timeFormat.format(firstStart))
                },
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
            Text(
                text = formatClock(workedSeconds),
                style = MaterialTheme.typography.displayMedium.merge(TabularNumbers),
                color = content,
            )
            if (onBreak) {
                Text(
                    text = stringResource(R.string.today_break_running, formatClock(active.secondsAt(now))),
                    style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                    color = content,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (target > 0) {
                LinearProgressIndicator(
                    progress = { (workedMinutes / target.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    trackColor = content.copy(alpha = 0.18f),
                    color = content,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.today_progress, formatDuration(workedMinutes), formatDuration(target)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = content,
                    )
                    Text(
                        text = when {
                            remaining <= 0 -> stringResource(R.string.today_overtime_so_far, formatDuration(-remaining))
                            onBreak -> stringResource(R.string.today_left, formatDuration(remaining))
                            else -> {
                                val plannedBreakLeft = (breakPlannedMinutes - breakTaken).coerceAtLeast(0)
                                stringResource(
                                    R.string.today_done_around,
                                    timeFormat.format(now.plusMinutes((remaining + plannedBreakLeft).toLong())),
                                )
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = content,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.today_no_target_running),
                    style = MaterialTheme.typography.bodyMedium,
                    color = content,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onBreak) {
                    Button(onClick = onEndBreak, modifier = Modifier.weight(1f).height(ButtonHeight)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.action_end_break))
                    }
                    OutlinedButton(onClick = onCheckOut, modifier = Modifier.weight(1f).height(ButtonHeight)) {
                        Text(stringResource(R.string.action_check_out), color = content)
                    }
                } else {
                    OutlinedButton(onClick = onStartBreak, modifier = Modifier.weight(1f).height(ButtonHeight)) {
                        Icon(Icons.Filled.FreeBreakfast, contentDescription = null, tint = content)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.action_start_break), color = content)
                    }
                    Button(onClick = onCheckOut, modifier = Modifier.weight(1f).height(ButtonHeight)) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.action_check_out))
                    }
                }
            }
        }
    }
}

@Composable
private fun DoneCard(dayResult: DayResult, onCheckInAgain: () -> Unit, onEditDay: () -> Unit) {
    val delta = dayResult.deltaMinutes
    Card(colors = cardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.today_done_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatDuration(dayResult.workedMinutes),
                style = MaterialTheme.typography.displayMedium.merge(TabularNumbers),
            )
            if (dayResult.targetMinutes > 0 || delta != 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeltaChip(delta)
                    Text(
                        stringResource(R.string.today_vs_target, formatDuration(dayResult.targetMinutes)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onCheckInAgain, modifier = Modifier.weight(1f).height(ButtonHeight)) {
                    Text(stringResource(R.string.action_check_in_again))
                }
                OutlinedButton(onClick = onEditDay, modifier = Modifier.weight(1f).height(ButtonHeight)) {
                    Text(stringResource(R.string.action_edit_day))
                }
            }
        }
    }
}

@Composable
private fun DayOffCard(onCheckIn: () -> Unit, onEditDay: () -> Unit) {
    Card(colors = cardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.today_day_off), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.today_day_off_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCheckIn, modifier = Modifier.weight(1f).height(ButtonHeight)) {
                    Text(stringResource(R.string.action_check_in_anyway))
                }
                TextButton(onClick = onEditDay, modifier = Modifier.weight(1f).height(ButtonHeight)) {
                    Text(stringResource(R.string.action_edit_day))
                }
            }
        }
    }
}

@Composable
private fun ForgottenCard(active: Entry, timeFormat: DateTimeFormatter, onSetEnd: () -> Unit, onCheckOutNow: () -> Unit) {
    val dateLabels = rememberDateLabels()
    Card(colors = cardColors(MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    stringResource(R.string.today_forgot_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                stringResource(R.string.today_forgot_text, dateLabels.short(active.date), timeFormat.format(active.start)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSetEnd, modifier = Modifier.weight(1f).height(ButtonHeight)) {
                    Text(stringResource(R.string.action_set_end_time))
                }
                TextButton(onClick = onCheckOutNow, modifier = Modifier.weight(1f).height(ButtonHeight)) {
                    Text(stringResource(R.string.action_check_out_now), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(balanceMinutes: Int, todayPending: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, colors = cardColors(MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.balance_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatSigned(balanceMinutes),
                    style = MaterialTheme.typography.headlineLarge.merge(TabularNumbers),
                    color = deltaContentColor(balanceMinutes),
                )
                if (todayPending) {
                    Text(
                        stringResource(R.string.balance_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
