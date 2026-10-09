package com.github.guidohu.clockshift.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.domain.DaySchedule
import com.github.guidohu.clockshift.domain.ScheduleBreak
import com.github.guidohu.clockshift.domain.WeekSchedule
import com.github.guidohu.clockshift.domain.formatDuration
import com.github.guidohu.clockshift.domain.minuteOfDayToTime
import com.github.guidohu.clockshift.ui.components.BreakField
import com.github.guidohu.clockshift.ui.components.DurationField
import com.github.guidohu.clockshift.ui.components.SectionLabel
import com.github.guidohu.clockshift.ui.components.TimeField
import com.github.guidohu.clockshift.ui.components.rememberTimeFormatter
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Edits the standard week: which days are working days, how many hours the user has to work (asked
 * explicitly, never worked out from the times), the usual times that apply to all of them, and
 * optional per-day exceptions. Used by the setup wizard and by Settings.
 */
@Composable
fun ScheduleEditor(week: WeekSchedule, onChange: (WeekSchedule) -> Unit, modifier: Modifier = Modifier) {
    val reference = week.days.values.firstOrNull { it.working } ?: week[DayOfWeek.MONDAY]
    var customiseOpen by rememberSaveable { mutableStateOf(false) }
    var expandedDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var targetPerWeek by rememberSaveable { mutableStateOf(false) }

    fun applyUsual(
        start: Int = reference.startMinute,
        end: Int = reference.endMinute,
        breaks: List<ScheduleBreak> = reference.breaks,
    ) {
        onChange(
            WeekSchedule(
                week.days.mapValues { (_, d) -> d.copy(startMinute = start, endMinute = end, breaks = breaks).normalized() },
            ),
        )
    }

    fun applyTarget(minutes: Int) {
        onChange(
            if (targetPerWeek) {
                week.withWeeklyTarget(minutes)
            } else {
                WeekSchedule(week.days.mapValues { (_, d) -> d.copy(target = minutes) })
            },
        )
    }

    fun toggleDay(day: DayOfWeek) {
        val current = week[day]
        val updated = if (current.working) {
            current.copy(working = false)
        } else {
            current.copy(
                working = true,
                startMinute = reference.startMinute,
                endMinute = reference.endMinute,
                breaks = reference.breaks,
                target = reference.target,
            )
        }
        val changed = WeekSchedule(week.days + (day to updated))
        // Per week, the weekly total stays what the user entered and is shared among the working days.
        onChange(if (targetPerWeek) changed.withWeeklyTarget(week.weeklyTargetMinutes) else changed)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(stringResource(R.string.schedule_working_days))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DayOfWeek.entries.forEach { day ->
                DayToggle(day = day, selected = week[day].working, onToggle = { toggleDay(day) })
            }
        }

        SectionLabel(stringResource(R.string.schedule_target_hours))
        val targetInvalid = week.days.values.any { it.working } && week.weeklyTargetMinutes <= 0
        val targetOptions = listOf(false to R.string.schedule_per_day, true to R.string.schedule_per_week)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            targetOptions.forEachIndexed { index, (perWeek, label) ->
                SegmentedButton(
                    selected = targetPerWeek == perWeek,
                    onClick = { targetPerWeek = perWeek },
                    shape = SegmentedButtonDefaults.itemShape(index, targetOptions.size),
                ) { Text(stringResource(label)) }
            }
        }
        DurationField(
            label = stringResource(if (targetPerWeek) R.string.schedule_target_per_week else R.string.schedule_target_per_day),
            minutes = if (targetPerWeek) week.weeklyTargetMinutes else reference.target,
            onChange = ::applyTarget,
            modifier = Modifier.fillMaxWidth(),
            isError = targetInvalid,
        )
        Text(
            text = stringResource(R.string.schedule_target_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel(stringResource(R.string.schedule_usual_hours))
        val usualInvalid = reference.endMinute <= reference.startMinute
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField(
                label = stringResource(R.string.label_start),
                minuteOfDay = reference.startMinute,
                onChange = { applyUsual(start = it) },
                modifier = Modifier.weight(1f),
                isError = usualInvalid,
            )
            TimeField(
                label = stringResource(R.string.label_end),
                minuteOfDay = reference.endMinute,
                onChange = { applyUsual(end = it) },
                modifier = Modifier.weight(1f),
                isError = usualInvalid,
            )
        }
        BreaksEditor(
            breaks = reference.breaks,
            onChange = { applyUsual(breaks = it) },
            workStart = reference.startMinute,
            workEnd = reference.endMinute,
            isError = usualInvalid,
        )
        if (usualInvalid) {
            Text(
                text = stringResource(R.string.schedule_times_invalid),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        ScheduleSummary(week = week, invalid = targetInvalid)

        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { customiseOpen = !customiseOpen }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.schedule_customise),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (customiseOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        AnimatedVisibility(customiseOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { day ->
                    DayRow(
                        day = day,
                        schedule = week[day],
                        expanded = expandedDay == day.value,
                        onToggleExpanded = { expandedDay = if (expandedDay == day.value) null else day.value },
                        onChange = { onChange(WeekSchedule(week.days + (day to it.normalized()))) },
                    )
                }
            }
        }
    }
}

/**
 * The breaks of a day: a start time and a length for each, a "Remove" link per break and an
 * "Add break" link that appends one more.
 */
@Composable
private fun BreaksEditor(
    breaks: List<ScheduleBreak>,
    onChange: (List<ScheduleBreak>) -> Unit,
    workStart: Int,
    workEnd: Int,
    isError: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        breaks.forEachIndexed { index, scheduled ->
            val number = index + 1
            fun replace(updated: ScheduleBreak) = onChange(breaks.toMutableList().also { it[index] = updated })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (breaks.size == 1) stringResource(R.string.schedule_break) else stringResource(R.string.schedule_break_n, number),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onChange(breaks.filterIndexed { i, _ -> i != index }) }) {
                    Text(stringResource(R.string.schedule_remove_break))
                }
            }
            TimeField(
                label = stringResource(R.string.label_break_starts),
                minuteOfDay = scheduled.startMinute,
                onChange = { replace(scheduled.copy(startMinute = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
            BreakField(
                minutes = scheduled.minutes,
                onChange = { replace(scheduled.copy(minutes = it)) },
                isError = isError,
            )
        }
        TextButton(onClick = { onChange(breaks + suggestBreak(breaks, workStart, workEnd)) }) {
            Text(stringResource(R.string.schedule_add_break))
        }
    }
}

/** Puts a new break in the middle of the longest stretch of work that is left. */
private fun suggestBreak(breaks: List<ScheduleBreak>, workStart: Int, workEnd: Int): ScheduleBreak {
    val edges = listOf(workStart) + breaks.sortedBy { it.startMinute }.flatMap { listOf(it.startMinute, it.endMinute) } + workEnd
    val gaps = edges.chunked(2).filter { it.size == 2 && it[1] > it[0] }
    val longest = gaps.maxByOrNull { it[1] - it[0] } ?: return ScheduleBreak(WeekSchedule.DEFAULT_BREAK_START, WeekSchedule.EXTRA_BREAK)
    val length = WeekSchedule.EXTRA_BREAK.coerceAtMost((longest[1] - longest[0]) / 2).coerceAtLeast(5)
    return ScheduleBreak((longest[0] + longest[1] - length) / 2, length)
}

@Composable
private fun ScheduleSummary(week: WeekSchedule, invalid: Boolean) {
    val working = week.days.values.filter { it.working }
    val text = when {
        working.isEmpty() -> stringResource(R.string.schedule_no_days)
        invalid -> stringResource(R.string.schedule_invalid)
        working.map { it.targetMinutes }.distinct().size == 1 -> stringResource(
            R.string.schedule_summary_same,
            formatDuration(working.first().targetMinutes),
            formatDuration(week.weeklyTargetMinutes),
        )

        else -> stringResource(R.string.schedule_summary_week, formatDuration(week.weeklyTargetMinutes))
    }
    val isProblem = invalid || working.isEmpty()
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DayToggle(day: DayOfWeek, selected: Boolean, onToggle: () -> Unit) {
    val locale = Locale.getDefault()
    val fullName = day.getDisplayName(TextStyle.FULL, locale)
    val shortLabel = day.getDisplayName(TextStyle.NARROW, locale)
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .widthIn(max = 44.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(container)
            .semantics { contentDescription = fullName }
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = shortLabel,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
private fun DayRow(
    day: DayOfWeek,
    schedule: DaySchedule,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onChange: (DaySchedule) -> Unit,
) {
    val formatter = rememberTimeFormatter()
    val name = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val summary = if (schedule.working) {
        stringResource(
            R.string.schedule_day_summary,
            formatter.format(schedule.startMinute.minuteOfDayToTime()),
            formatter.format(schedule.endMinute.minuteOfDayToTime()),
            formatDuration(schedule.targetMinutes),
        )
    } else {
        stringResource(R.string.schedule_not_working)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .then(if (schedule.working) Modifier.clickable(onClick = onToggleExpanded) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(expanded && schedule.working) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val invalid = schedule.endMinute <= schedule.startMinute
                    DurationField(
                        label = stringResource(R.string.schedule_target_per_day),
                        minutes = schedule.target,
                        onChange = { onChange(schedule.copy(target = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        isError = schedule.target <= 0,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeField(
                            label = stringResource(R.string.label_start),
                            minuteOfDay = schedule.startMinute,
                            onChange = { onChange(schedule.copy(startMinute = it)) },
                            modifier = Modifier.weight(1f),
                            isError = invalid,
                        )
                        TimeField(
                            label = stringResource(R.string.label_end),
                            minuteOfDay = schedule.endMinute,
                            onChange = { onChange(schedule.copy(endMinute = it)) },
                            modifier = Modifier.weight(1f),
                            isError = invalid,
                        )
                    }
                    BreaksEditor(
                        breaks = schedule.breaks,
                        onChange = { onChange(schedule.copy(breaks = it)) },
                        workStart = schedule.startMinute,
                        workEnd = schedule.endMinute,
                        isError = invalid,
                    )
                }
            }
        }
    }
}
