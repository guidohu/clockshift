package com.github.guidohu.clockshift.ui.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.data.AppState
import com.github.guidohu.clockshift.domain.Timesheet
import com.github.guidohu.clockshift.domain.TimesheetBuilder
import com.github.guidohu.clockshift.export.ExportFormat
import com.github.guidohu.clockshift.export.TimesheetExport
import com.github.guidohu.clockshift.ui.components.DateRangeDialog
import com.github.guidohu.clockshift.ui.components.RangeKind
import com.github.guidohu.clockshift.ui.components.rememberDateLabels
import com.github.guidohu.clockshift.ui.components.resolveRange
import com.github.guidohu.clockshift.ui.pro.LocalPro
import com.github.guidohu.clockshift.ui.pro.ProBadge
import com.github.guidohu.clockshift.ui.pro.ProUpsellCard
import com.github.guidohu.clockshift.ui.settings.SwitchRow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.WeekFields
import java.util.Locale

/** Bottom sheet to export a timesheet (Pro): period, PDF or CSV, then share or save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    state: AppState,
    onDismiss: () -> Unit,
    initialKind: RangeKind = RangeKind.LastMonth,
    initialFrom: LocalDate? = null,
    initialTo: LocalDate? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pro = LocalPro.current
    val dateLabels = rememberDateLabels()
    val today = LocalDate.now()
    val firstDayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }

    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var customFrom by rememberSaveable { mutableLongStateOf((initialFrom ?: today.minusDays(29)).toEpochDay()) }
    var customTo by rememberSaveable { mutableLongStateOf((initialTo ?: today).toEpochDay()) }
    var format by rememberSaveable { mutableStateOf(ExportFormat.Pdf) }
    var entries by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }

    val range = resolveRange(kind, today, LocalDate.ofEpochDay(customFrom), LocalDate.ofEpochDay(customTo), firstDayOfWeek)
    val sheet: Timesheet? = remember(state, range) {
        TimesheetBuilder.build(state.schedules, state.entries, state.daysOff, state.settings, range.first, range.second, LocalDateTime.now())
    }

    // The document is rendered when the user has picked where to save it.
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    fun onCreated(uri: android.net.Uri?) {
        val bytes = pendingBytes
        pendingBytes = null
        if (uri == null || bytes == null) return
        scope.launch {
            message = if (TimesheetExport.save(context, uri, bytes)) R.string.export_saved else R.string.export_failed
        }
    }
    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.Pdf.mimeType), ::onCreated)
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.Csv.mimeType), ::onCreated)

    fun export(share: Boolean) {
        val current = sheet ?: return
        busy = true
        message = null
        scope.launch {
            val bytes = TimesheetExport.render(context, current, format, entries)
            val name = TimesheetExport.fileName(current, format)
            busy = false
            if (share) {
                TimesheetExport.share(context, bytes, name, format)
            } else {
                pendingBytes = bytes
                if (format == ExportFormat.Pdf) createPdf.launch(name) else createCsv.launch(name)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.export_title), style = MaterialTheme.typography.headlineSmall)
                if (!pro.isPro) ProBadge()
            }
            if (!pro.isPro) {
                ProUpsellCard(Modifier.padding(horizontal = 16.dp))
                return@Column
            }

            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RangeKind.entries.toList(), key = { it.name }) { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { if (option == RangeKind.Custom) pickerOpen = true else kind = option },
                        label = { Text(stringResource(option.labelRes)) },
                    )
                }
            }
            Text(
                if (sheet == null) {
                    stringResource(R.string.export_nothing)
                } else {
                    stringResource(R.string.export_range, dateLabels.dayMonth(sheet.from), dateLabels.dayMonth(sheet.to))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (sheet == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ExportFormat.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = format == option,
                            onClick = { format = option },
                            shape = SegmentedButtonDefaults.itemShape(index, ExportFormat.entries.size),
                        ) {
                            Text(stringResource(if (option == ExportFormat.Pdf) R.string.export_format_pdf else R.string.export_format_csv))
                        }
                    }
                }
                Text(
                    stringResource(if (format == ExportFormat.Pdf) R.string.export_format_pdf_hint else R.string.export_format_csv_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(
                    title = stringResource(R.string.export_entries),
                    summary = stringResource(R.string.export_entries_summary),
                    checked = entries,
                    onCheckedChange = { entries = it },
                )
            }

            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { export(share = false) }, enabled = sheet != null && !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.export_save))
                }
                Button(onClick = { export(share = true) }, enabled = sheet != null && !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.export_share))
                }
            }
            message?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it == R.string.export_failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
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
