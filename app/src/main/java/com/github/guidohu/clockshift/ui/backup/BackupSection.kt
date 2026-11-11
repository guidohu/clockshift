package com.github.guidohu.clockshift.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.backup.BackupData
import com.github.guidohu.clockshift.backup.BackupError
import com.github.guidohu.clockshift.backup.BackupFormatException
import com.github.guidohu.clockshift.backup.BackupManager
import com.github.guidohu.clockshift.backup.BackupTargetKind
import com.github.guidohu.clockshift.container
import com.github.guidohu.clockshift.ui.components.rememberDateLabels
import com.github.guidohu.clockshift.ui.components.rememberTimeFormatter
import com.github.guidohu.clockshift.ui.pro.LocalPro
import com.github.guidohu.clockshift.ui.pro.ProBadge
import com.github.guidohu.clockshift.ui.settings.SwitchRow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Manual backup and restore (free) plus the daily backup (Pro), for Settings. */
@Composable
fun BackupSection(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.backup_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BackupRestoreButtons(showBackup = true)
        HorizontalDivider()
        DailyBackup()
    }
}

/**
 * "Back up now" (optional) and "Restore" buttons with the confirmation dialog. Also used by the setup
 * wizard, so a new phone can start from a backup.
 */
@Composable
fun BackupRestoreButtons(showBackup: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val backup = context.container.backup
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<Int?>(null) }
    var failed by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<BackupData?>(null) }

    fun report(text: Int, error: Boolean) {
        message = text
        failed = error
    }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME)) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                if (backup.writeTo(uri)) report(R.string.backup_saved, false) else report(R.string.backup_save_failed, true)
            }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    pending = backup.readFrom(uri)
                    message = null
                } catch (e: BackupFormatException) {
                    report(R.string.backup_invalid, true)
                }
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showBackup) {
                OutlinedButton(onClick = { create.launch(backup.fileName()) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.backup_now))
                }
            }
            // Some storage apps report JSON as plain text or as a generic file.
            OutlinedButton(
                onClick = { open.launch(arrayOf(BackupManager.MIME, "text/plain", "application/octet-stream")) },
                modifier = if (showBackup) Modifier.weight(1f) else Modifier,
            ) { Text(stringResource(R.string.backup_restore)) }
        }
        message?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }

    pending?.let { data ->
        RestoreDialog(
            data = data,
            onConfirm = {
                pending = null
                scope.launch {
                    backup.restore(data)
                    report(R.string.backup_restored, false)
                }
            },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun RestoreDialog(data: BackupData, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val dateLabels = rememberDateLabels()
    val timeFormat = rememberTimeFormatter()
    val created = "${dateLabels.dayMonth(data.createdAt.toLocalDate())}, ${timeFormat.format(data.createdAt)}"
    val entries = data.entries.count { it.kind == 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_title, created)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    pluralStringResource(R.plurals.backup_restore_entries, entries, entries) + " · " +
                        pluralStringResource(R.plurals.backup_restore_days_off, data.daysOff.size, data.daysOff.size),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(stringResource(R.string.backup_restore_warning), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_restore_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Daily backup to a folder or file the user picks (Pro). */
@Composable
private fun DailyBackup() {
    val context = LocalContext.current
    val backup = context.container.backup
    val isPro = LocalPro.current.isPro
    val state by backup.daily.collectAsStateWithLifecycle()
    val active = isPro && state.enabled && state.targetUri != null

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            backup.setDailyTarget(uri, BackupTargetKind.Folder)
            backup.runNow()
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME)) { uri: Uri? ->
        if (uri != null) {
            backup.setDailyTarget(uri, BackupTargetKind.File)
            backup.runNow()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchRow(
            title = stringResource(R.string.backup_daily),
            summary = stringResource(R.string.backup_daily_summary),
            checked = active,
            enabled = isPro,
            onCheckedChange = { on ->
                when {
                    !on -> backup.setDailyEnabled(false)
                    state.targetUri == null -> pickFolder.launch(null)
                    else -> backup.setDailyEnabled(true)
                }
            },
            trailing = { if (!isPro) ProBadge() },
        )
        if (!isPro) {
            Text(
                stringResource(R.string.backup_daily_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        if (!active) return@Column

        val name = state.targetName ?: stringResource(R.string.backup_target_unknown)
        Text(
            if (state.kind == BackupTargetKind.Folder) {
                pluralStringResource(R.plurals.backup_target_folder, BackupManager.KEEP, name, BackupManager.KEEP)
            } else {
                stringResource(R.string.backup_target_file, name)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        BackupStatus(state.lastSuccess, state.lastError, state.lastErrorAt)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { pickFolder.launch(null) }) {
                Text(stringResource(if (state.kind == BackupTargetKind.Folder) R.string.backup_change_folder else R.string.backup_use_folder))
            }
            TextButton(onClick = backup::runNow) { Text(stringResource(R.string.backup_run_now)) }
        }
        if (state.kind == BackupTargetKind.Folder) {
            TextButton(onClick = { pickFile.launch(SINGLE_FILE_NAME) }) { Text(stringResource(R.string.backup_use_file)) }
            Text(
                stringResource(R.string.backup_use_file_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackupStatus(lastSuccess: LocalDateTime?, lastError: BackupError?, lastErrorAt: LocalDateTime?) {
    val dateLabels = rememberDateLabels()
    val timeFormat = rememberTimeFormatter()
    fun format(time: LocalDateTime) = "${dateLabels.dayMonth(time.toLocalDate())}, ${timeFormat.format(time)}"
    val (text, isError) = when {
        lastError != null && lastErrorAt != null -> stringResource(
            R.string.backup_status_failed,
            format(lastErrorAt),
            stringResource(if (lastError == BackupError.NoAccess) R.string.backup_error_no_access else R.string.backup_error_write),
        ) to true

        lastSuccess != null -> stringResource(R.string.backup_status_ok, format(lastSuccess)) to false
        else -> stringResource(R.string.backup_status_none) to false
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val SINGLE_FILE_NAME = "clockshift-backup.json"
