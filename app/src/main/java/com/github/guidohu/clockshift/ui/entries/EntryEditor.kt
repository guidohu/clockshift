package com.clockshift.app.ui.entries

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.clockshift.app.R
import com.clockshift.app.domain.AddOutcome
import com.clockshift.app.domain.DaySchedule
import com.clockshift.app.domain.Entry
import com.clockshift.app.domain.EntryEdit
import com.clockshift.app.domain.EntryKind
import com.clockshift.app.domain.EntryOps
import com.clockshift.app.domain.toMinuteOfDay
import com.clockshift.app.ui.ClockActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

private const val NO_REQUEST = -1
private const val NEW_ENTRY = 0L
private const val DEFAULT_BREAK_MINUTES = 30
private const val DEFAULT_BREAK_START = 12 * 60
private const val DEFAULT_WORK_START = 9 * 60
private const val DEFAULT_WORK_END = 17 * 60

/**
 * Adding, editing and deleting the work blocks and breaks of one [date], including the "Undo" snackbar.
 * Shared by the Today and the day screens.
 */
class EntryEditor internal constructor(
    private val date: LocalDate,
    private val entries: List<Entry>,
    private val schedule: DaySchedule,
    private val now: LocalDateTime,
    private val actions: ClockActions,
    private val snackbar: SnackbarHostState,
    private val scope: CoroutineScope,
    private val context: Context,
    private val requestKind: Int,
    private val requestEntryId: Long,
    private val setRequest: (kind: Int, entryId: Long) -> Unit,
) {
    fun openAdd(kind: EntryKind) = setRequest(kind.ordinal, NEW_ENTRY)

    fun openEdit(entry: Entry) = setRequest(entry.kind.ordinal, entry.id)

    fun delete(entry: Entry) = commit(EntryOps.remove(entries, entry), R.string.snack_entry_deleted)

    private fun close() = setRequest(NO_REQUEST, NEW_ENTRY)

    private fun at(minute: Int): LocalDateTime = date.atTime(minute / 60, minute % 60)

    /** Converts the sheet's minutes into the date-times to apply; null when the times make no sense. */
    private fun interval(kind: EntryKind, start: Int, end: Int): Pair<LocalDateTime, LocalDateTime?>? = when {
        end == RUNNING -> at(start) to null
        end == start -> null
        end > start -> at(start) to at(end)
        kind == EntryKind.Work -> at(start) to at(end).plusDays(1)
        else -> null
    }

    private fun outcome(kind: EntryKind, start: Int, end: Int, editing: Entry?): AddOutcome? {
        val (from, to) = interval(kind, start, end) ?: return null
        return EntryOps.add(entries, kind, from, to, now, replacing = editing)
    }

    private fun defaultTimes(kind: EntryKind): Pair<Int, Int> {
        if (kind == EntryKind.Work) {
            return if (schedule.working) schedule.startMinute to schedule.endMinute else DEFAULT_WORK_START to DEFAULT_WORK_END
        }
        // The first scheduled break that is not already in the day, else a lunch-time default.
        val scheduled = schedule.takeIf { it.working }?.breaks.orEmpty().let { options ->
            options.firstOrNull { option ->
                entries.none { it.isBreak && it.start < at(option.endMinute) && (it.end ?: LocalDateTime.MAX) > at(option.startMinute) }
            } ?: options.firstOrNull()
        }
        val length = scheduled?.minutes ?: DEFAULT_BREAK_MINUTES
        val planned = scheduled?.startMinute ?: DEFAULT_BREAK_START
        val nowMinute = now.toLocalTime().toMinuteOfDay()
        // On the current day a break cannot lie in the future: suggest the last stretch instead.
        return if (date == now.toLocalDate() && planned + length > nowMinute && nowMinute >= length) {
            (nowMinute - length) to nowMinute
        } else {
            planned to (planned + length).coerceAtMost(MINUTES_PER_DAY - 1)
        }
    }

    private fun commit(edit: EntryEdit, messageRes: Int) {
        if (edit.isEmpty) return
        scope.launch {
            val touched = entries.filter { old -> old.id in edit.delete || edit.save.any { it.id == old.id } }
            val written = actions.applyEdit(edit)
            val result = snackbar.showSnackbar(
                message = context.getString(messageRes),
                actionLabel = context.getString(R.string.snack_undo),
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                written.forEach { actions.deleteEntry(it) }
                touched.forEach { actions.saveEntry(it) }
            }
        }
    }

    /** The bottom sheet; emits nothing while no block is being added or edited. */
    @Composable
    fun Sheet() {
        if (requestKind == NO_REQUEST) return
        val kind = EntryKind.entries[requestKind]
        val editing = entries.firstOrNull { it.id == requestEntryId }.takeIf { requestEntryId != NEW_ENTRY }
        if (requestEntryId != NEW_ENTRY && editing == null) {
            // The entry vanished (for example through Undo); nothing left to edit.
            LaunchedEffect(Unit) { close() }
            return
        }
        val (defaultStart, defaultEnd) = defaultTimes(kind)
        key(requestKind, requestEntryId) {
            BlockSheet(
                kind = kind,
                isNew = editing == null,
                initialStart = editing?.start?.toLocalTime()?.toMinuteOfDay() ?: defaultStart,
                initialEnd = if (editing == null) defaultEnd else editing.end?.toLocalTime()?.toMinuteOfDay() ?: RUNNING,
                errorFor = { start, end ->
                    val result = outcome(kind, start, end, editing)
                    when (result) {
                        null -> BlockError.EndNotAfterStart
                        is AddOutcome.Ok -> null
                        AddOutcome.NoWork -> BlockError.NoWorkAtThatTime
                        AddOutcome.InFuture -> BlockError.BreakInFuture
                    }
                },
                onSave = { start, end ->
                    close()
                    (outcome(kind, start, end, editing) as? AddOutcome.Ok)?.let {
                        val message = when {
                            editing != null -> R.string.snack_entry_saved
                            kind == EntryKind.Work -> R.string.snack_block_added
                            else -> R.string.snack_break_added
                        }
                        commit(it.edit, message)
                    }
                },
                onDelete = editing?.let { entry ->
                    {
                        close()
                        delete(entry)
                    }
                },
                onDismiss = ::close,
            )
        }
    }

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

@Composable
fun rememberEntryEditor(
    date: LocalDate,
    entries: List<Entry>,
    schedule: DaySchedule,
    now: LocalDateTime,
    actions: ClockActions,
    snackbar: SnackbarHostState,
): EntryEditor {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableIntStateOf(NO_REQUEST) }
    var entryId by rememberSaveable { mutableLongStateOf(NEW_ENTRY) }
    val setRequest = remember { { k: Int, id: Long -> kind = k; entryId = id } }
    return EntryEditor(date, entries, schedule, now, actions, snackbar, scope, context, kind, entryId, setRequest)
}
