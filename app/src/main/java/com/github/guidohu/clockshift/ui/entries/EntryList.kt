package com.github.guidohu.clockshift.ui.entries

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.domain.Entry
import com.github.guidohu.clockshift.domain.formatDuration
import com.github.guidohu.clockshift.ui.components.TabularNumbers
import com.github.guidohu.clockshift.ui.components.rememberTimeFormatter

/**
 * The work blocks and breaks of a day in chronological order. Tap a row to edit it, swipe it to the
 * left to reveal Delete.
 */
@Composable
fun EntryListCard(
    entries: List<Entry>,
    onEdit: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val timeFormat = rememberTimeFormatter()
    var revealedId by remember { mutableStateOf<Long?>(null) }
    val sorted = remember(entries) { entries.sortedBy { it.start } }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            sorted.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                SwipeActionRow(
                    isRevealed = revealedId == entry.id,
                    onRevealedChange = { revealed -> revealedId = if (revealed) entry.id else null },
                    onTap = { onEdit(entry) },
                    onDelete = {
                        revealedId = null
                        onDelete(entry)
                    },
                ) {
                    val end = entry.end
                    val range = buildString {
                        append(timeFormat.format(entry.start))
                        append(" – ")
                        append(if (end == null) stringResource(R.string.entry_now) else timeFormat.format(end))
                        if (end != null && end.toLocalDate() != entry.date) append(" (+1)")
                    }
                    ListItem(
                        leadingContent = { KindIcon(entry) },
                        headlineContent = { Text(range, style = MaterialTheme.typography.titleMedium.merge(TabularNumbers)) },
                        supportingContent = {
                            Text(stringResource(if (entry.isBreak) R.string.entry_kind_break else R.string.entry_kind_work))
                        },
                        trailingContent = {
                            Text(
                                text = if (entry.isActive) stringResource(R.string.entry_running) else formatDuration(entry.completedMinutes),
                                style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

@Composable
private fun KindIcon(entry: Entry) {
    val container = if (entry.isBreak) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (entry.isBreak) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(shape = CircleShape, color = container, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (entry.isBreak) Icons.Filled.FreeBreakfast else Icons.Filled.Work,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
