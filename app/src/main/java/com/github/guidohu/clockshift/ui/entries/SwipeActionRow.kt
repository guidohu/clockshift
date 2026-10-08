package com.clockshift.app.ui.entries

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clockshift.app.R
import kotlinx.coroutines.launch

private val DeleteActionWidth = 88.dp

/**
 * A list row that reveals a red "Delete" button when swiped to the left (same gesture as in the
 * expense tracker). Tapping the row opens it, or closes the button when it is open. Only one row of
 * a list is revealed at a time: [isRevealed] / [onRevealedChange] let the caller close the others.
 */
@Composable
fun SwipeActionRow(
    isRevealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { DeleteActionWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Closes this row whenever the caller says another row became the revealed one.
    LaunchedEffect(isRevealed) {
        if (!isRevealed && offsetX.value != 0f) offsetX.animateTo(0f, spring())
    }

    val draggableState = rememberDraggableState { delta ->
        val clamped = (offsetX.value + delta).coerceIn(-deleteWidthPx, 0f)
        scope.launch { offsetX.snapTo(clamped) }
    }

    // The reveal is real layout width, so the content just gets narrower instead of sliding off screen.
    val revealedPx = (-offsetX.value).coerceIn(0f, deleteWidthPx)
    val revealedWidth = with(density) { revealedPx.toDp() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        scope.launch {
                            val open = offsetX.value < -deleteWidthPx / 2
                            offsetX.animateTo(if (open) -deleteWidthPx else 0f, spring())
                            onRevealedChange(open)
                        }
                    },
                )
                .clickable {
                    if (offsetX.value != 0f) {
                        scope.launch { offsetX.animateTo(0f, spring()) }
                        onRevealedChange(false)
                    } else {
                        onTap()
                    }
                },
        ) {
            content()
        }

        if (revealedPx > 0f) {
            // The clip window grows with the drag while the button keeps its full size.
            Box(
                modifier = Modifier
                    .width(revealedWidth)
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.extraSmall),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .requiredWidth(DeleteActionWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable {
                            scope.launch { offsetX.animateTo(0f, spring()) }
                            onRevealedChange(false)
                            onDelete()
                        },
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}
