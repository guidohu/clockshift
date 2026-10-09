package com.github.guidohu.clockshift.ui.entries

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.guidohu.clockshift.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val DeleteActionWidth = 88.dp
private val SelectBumpDistance = 36.dp

/**
 * A list row that reveals a red "Delete" button when swiped to the left (same gesture as in the
 * expense tracker). Tapping the row opens it, or closes the button when it is open. Only one row of
 * a list is revealed at a time: [isRevealed] / [onRevealedChange] let the caller close the others.
 *
 * Passing [onToggleSelect] also makes the row selectable, like in the expense tracker: a long press,
 * a swipe to the right or a tap on the dot toggles the selection, and while [selectionModeActive]
 * a tap toggles it too and swiping is off.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeActionRow(
    isRevealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    selectionModeActive: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { DeleteActionWidth.toPx() }
    val bumpPx = with(density) { SelectBumpDistance.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var toggledThisGesture by remember { mutableStateOf(false) }

    // Closes this row whenever the caller says another row became the revealed one.
    LaunchedEffect(isRevealed) {
        if (!isRevealed && offsetX.value != 0f) offsetX.animateTo(0f, spring())
    }

    LaunchedEffect(selectionModeActive) {
        if (selectionModeActive && offsetX.value != 0f) offsetX.animateTo(0f, spring())
    }

    val draggableState = rememberDraggableState { delta ->
        if (selectionModeActive) return@rememberDraggableState
        val proposed = offsetX.value + delta
        val clamped = if (proposed >= 0f) {
            if (onToggleSelect != null) proposed.coerceIn(0f, bumpPx * 1.5f) else 0f
        } else {
            proposed.coerceIn(-deleteWidthPx, 0f)
        }
        scope.launch { offsetX.snapTo(clamped) }
        if (onToggleSelect != null && !toggledThisGesture && clamped >= bumpPx) {
            toggledThisGesture = true
            onToggleSelect()
        }
    }

    // The reveal is real layout width, so the content just gets narrower instead of sliding off screen.
    val revealedPx = (-offsetX.value).coerceIn(0f, deleteWidthPx)
    val revealedWidth = with(density) { revealedPx.toDp() }
    // The right-swipe "select" bump is momentary: it always springs back to 0.
    val bumpOffsetPx = offsetX.value.coerceAtLeast(0f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(visible = selectionModeActive && onToggleSelect != null) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, end = 0.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .let {
                        if (isSelected) it.background(MaterialTheme.colorScheme.primary)
                        else it.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    }
                    .clickable { onToggleSelect?.invoke() },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.cd_selected),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .offset { IntOffset(bumpOffsetPx.roundToInt(), 0) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    enabled = !selectionModeActive,
                    onDragStarted = { toggledThisGesture = false },
                    onDragStopped = {
                        scope.launch {
                            if (offsetX.value > 0f) {
                                offsetX.animateTo(0f, spring())
                            } else {
                                val open = offsetX.value < -deleteWidthPx / 2
                                offsetX.animateTo(if (open) -deleteWidthPx else 0f, spring())
                                onRevealedChange(open)
                            }
                        }
                    },
                )
                .combinedClickable(
                    onLongClick = onToggleSelect,
                    onClick = {
                        when {
                            offsetX.value != 0f -> {
                                scope.launch { offsetX.animateTo(0f, spring()) }
                                onRevealedChange(false)
                            }
                            selectionModeActive && onToggleSelect != null -> onToggleSelect()
                            else -> onTap()
                        }
                    },
                ),
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
