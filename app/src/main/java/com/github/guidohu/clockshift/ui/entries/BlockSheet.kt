package com.github.guidohu.clockshift.ui.entries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.domain.EntryKind
import com.github.guidohu.clockshift.domain.toMinuteOfDay
import com.github.guidohu.clockshift.ui.components.ClockTimePickerDialog
import com.github.guidohu.clockshift.ui.components.TimeField
import com.github.guidohu.clockshift.ui.components.ValueField
import java.time.LocalDateTime

/** Why a block cannot be saved. */
enum class BlockError(val messageRes: Int) {
    EndNotAfterStart(R.string.block_error_order),
    NoWorkAtThatTime(R.string.block_error_no_work),
    BreakInFuture(R.string.block_error_future),
}

/** Marks "no end yet" for a running block. */
const val RUNNING = -1

/**
 * Edits one work block or break. Times are minutes of the day; [RUNNING] as end means the block is
 * still running. [errorFor] decides whether the entered times can be saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockSheet(
    kind: EntryKind,
    isNew: Boolean,
    initialStart: Int,
    initialEnd: Int,
    errorFor: (start: Int, end: Int) -> BlockError?,
    onSave: (start: Int, end: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var start by rememberSaveable { mutableIntStateOf(initialStart) }
    var end by rememberSaveable { mutableIntStateOf(initialEnd) }
    var endPickerOpen by rememberSaveable { mutableStateOf(false) }
    val error = errorFor(start, end)
    val isWork = kind == EntryKind.Work
    val endsNextDay = isWork && end != RUNNING && end < start

    val title = when {
        isWork && isNew -> R.string.block_sheet_work_new
        isWork -> R.string.block_sheet_work_edit
        isNew -> R.string.block_sheet_break_new
        else -> R.string.block_sheet_break_edit
    }
    val hint = when {
        error != null -> error.messageRes
        end == RUNNING -> R.string.block_hint_running
        endsNextDay -> R.string.block_hint_next_day
        isWork -> R.string.block_hint_work
        else -> R.string.block_hint_break
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField(
                    label = stringResource(R.string.label_start),
                    minuteOfDay = start,
                    onChange = { start = it },
                    modifier = Modifier.weight(1f),
                    isError = error == BlockError.EndNotAfterStart,
                )
                if (end == RUNNING) {
                    ValueField(
                        label = stringResource(R.string.label_end),
                        value = stringResource(R.string.entry_running),
                        onClick = { endPickerOpen = true },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    TimeField(
                        label = stringResource(R.string.label_end),
                        minuteOfDay = end,
                        onChange = { end = it },
                        modifier = Modifier.weight(1f),
                        isError = error == BlockError.EndNotAfterStart,
                    )
                }
            }
            Text(
                text = stringResource(hint),
                style = MaterialTheme.typography.bodyMedium,
                color = if (error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { onSave(start, end) }, enabled = error == null) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }

    if (endPickerOpen) {
        ClockTimePickerDialog(
            initialMinute = LocalDateTime.now().toLocalTime().toMinuteOfDay(),
            onConfirm = {
                end = it
                endPickerOpen = false
            },
            onDismiss = { endPickerOpen = false },
        )
    }
}
