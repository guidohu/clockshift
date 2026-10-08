package com.github.guidohu.clockshift.ui.entries

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.guidohu.clockshift.R

/** Speed-dial: a plus button that fans out "Add work block" and "Add break". */
@Composable
fun AddBlockFab(expanded: Boolean, onToggle: () -> Unit, onAddWork: () -> Unit, onAddBreak: () -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(
                    onClick = onAddBreak,
                    icon = { Icon(Icons.Filled.FreeBreakfast, contentDescription = null) },
                    text = { Text(stringResource(R.string.fab_add_break)) },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                ExtendedFloatingActionButton(
                    onClick = onAddWork,
                    icon = { Icon(Icons.Filled.Work, contentDescription = null) },
                    text = { Text(stringResource(R.string.fab_add_work)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        FloatingActionButton(onClick = onToggle) {
            Icon(
                imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = stringResource(if (expanded) R.string.cd_close_menu else R.string.cd_add_time),
            )
        }
    }
}

/** Dims the screen behind an open [AddBlockFab]; tapping it closes the menu. */
@Composable
fun FabScrim(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(interactionSource = null, indication = null, onClick = onDismiss),
    )
}
