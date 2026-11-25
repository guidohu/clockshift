package com.github.guidohu.clockshift.ui.pro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.pro.ProManager
import com.github.guidohu.clockshift.pro.ProOffer
import com.github.guidohu.clockshift.pro.ProStoreStatus
import com.github.guidohu.clockshift.ui.settings.SwitchRow

/** The Pro status and what the UI can do about it. Provided by the activity; locked in previews. */
@Immutable
data class ProAccess(
    val isPro: Boolean = false,
    val offer: ProOffer? = null,
    val status: ProStoreStatus = ProStoreStatus.Loading,
    val pending: Boolean = false,
    val debugUnlockAvailable: Boolean = false,
    val debugUnlocked: Boolean = false,
    val purchase: (Activity, ProOffer) -> Unit = { _, _ -> },
    /** Re-reads price and purchases from the Play Store; also restores an earlier purchase. */
    val refresh: () -> Unit = {},
    val setDebugUnlock: (Boolean) -> Unit = {},
    /** Opens the "Enable Pro features?" dialog; locked features call this when tapped. */
    val showUpgrade: () -> Unit = {},
)

val LocalPro = compositionLocalOf { ProAccess() }

/** How strongly locked Pro features are faded, matching Material's disabled content. */
const val LOCKED_ALPHA = 0.38f

/** Small "PRO" pill next to a Pro feature's title. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.pro_badge),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * The first item in Settings until Pro is bought: what Pro adds and the three steps to get it.
 * Tapping it opens the upgrade dialog.
 */
@Composable
fun ProActivateCard(modifier: Modifier = Modifier) {
    val pro = LocalPro.current
    Card(
        onClick = pro.showUpgrade,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = onContainer)
                Text(
                    stringResource(R.string.pro_activate_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = onContainer,
                    modifier = Modifier.weight(1f),
                )
                ProBadge()
            }
            Text(stringResource(R.string.pro_activate_summary), style = MaterialTheme.typography.bodyMedium, color = onContainer)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProStep(1, R.string.pro_activate_step_1, onContainer)
                ProStep(2, R.string.pro_activate_step_2, onContainer)
                ProStep(3, R.string.pro_activate_step_3, onContainer)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pro.offer?.let { stringResource(R.string.pro_buy, it.formattedPrice) } ?: stringResource(R.string.pro_activate_action),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * "Enable Pro features?": shown when a locked feature is tapped or from the Settings card. Lists every
 * benefit and buys Pro in one tap; turns into a thank-you once the purchase went through.
 */
@Composable
fun ProUpgradeDialog(onDismiss: () -> Unit) {
    val pro = LocalPro.current
    val context = LocalContext.current
    if (pro.isPro) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null) },
            title = { Text(stringResource(R.string.pro_active)) },
            text = { Text(stringResource(R.string.pro_dialog_active)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pro_dialog_done)) } },
        )
        return
    }
    val offer = pro.offer
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null) },
        title = { Text(stringResource(R.string.pro_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.pro_dialog_intro), style = MaterialTheme.typography.bodyMedium)
                ProBenefits(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary)
                HorizontalDivider()
                ProStatus(color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (pro.debugUnlockAvailable) DebugUnlockRow()
            }
        },
        confirmButton = {
            if (offer != null && !pro.pending) {
                Button(onClick = { context.findActivity()?.let { pro.purchase(it, offer) } }) {
                    Text(stringResource(R.string.pro_buy, offer.formattedPrice))
                }
            } else if (pro.status == ProStoreStatus.BillingUnavailable || pro.status == ProStoreStatus.ProductNotFound) {
                TextButton(onClick = pro.refresh) { Text(stringResource(R.string.pro_retry)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pro_dialog_not_now)) } },
    )
}

/** What Pro adds and how to buy it, as a card inside a screen (setup wizard, export sheet). */
@Composable
fun ProUpsellCard(modifier: Modifier = Modifier) {
    val pro = LocalPro.current
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        val onContainer = MaterialTheme.colorScheme.onSecondaryContainer
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pro_title), style = MaterialTheme.typography.titleLarge)
                ProBadge()
            }
            Text(stringResource(R.string.pro_pitch), style = MaterialTheme.typography.bodyMedium, color = onContainer)
            ProBenefits(onContainer, onContainer)
            val offer = pro.offer
            if (offer != null && !pro.pending) {
                Button(onClick = { context.findActivity()?.let { pro.purchase(it, offer) } }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pro_buy, offer.formattedPrice))
                }
            }
            ProStatus(color = onContainer)
            if (pro.status == ProStoreStatus.BillingUnavailable || pro.status == ProStoreStatus.ProductNotFound) {
                TextButton(onClick = pro.refresh) { Text(stringResource(R.string.pro_retry)) }
            }
            if (pro.debugUnlockAvailable) DebugUnlockRow()
        }
    }
}

/** Shown instead of the offer once Pro is active. */
@Composable
fun ProActiveRow(modifier: Modifier = Modifier) {
    val pro = LocalPro.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.pro_active), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.pro_active_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (pro.debugUnlocked) {
            TextButton(onClick = { pro.setDebugUnlock(false) }) { Text(stringResource(R.string.pro_debug_lock)) }
        }
    }
}

@Composable
private fun ProBenefits(textColor: Color, iconColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProPoint(Icons.Filled.MyLocation, R.string.pro_point_auto, textColor, iconColor)
        ProPoint(Icons.Filled.Schedule, R.string.pro_point_start, textColor, iconColor)
        ProPoint(Icons.Filled.TaskAlt, R.string.pro_point_done, textColor, iconColor)
        ProPoint(Icons.Outlined.IosShare, R.string.pro_point_export, textColor, iconColor)
        ProPoint(Icons.Filled.Backup, R.string.pro_point_backup, textColor, iconColor)
    }
}

@Composable
private fun ProPoint(icon: ImageVector, text: Int, textColor: Color, iconColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = textColor)
    }
}

@Composable
private fun ProStep(number: Int, text: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** The purchase's fine print: one-time, pending, or why the Play Store can't sell it right now, plus restore. */
@Composable
private fun ProStatus(color: Color) {
    val pro = LocalPro.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val message = when {
            pro.pending -> stringResource(R.string.pro_pending)
            pro.status == ProStoreStatus.Ready -> stringResource(R.string.pro_buy_hint)
            pro.status == ProStoreStatus.Loading -> stringResource(R.string.pro_loading)
            pro.status == ProStoreStatus.BillingUnavailable -> stringResource(R.string.pro_unavailable)
            pro.debugUnlockAvailable -> stringResource(R.string.pro_not_found_debug, ProManager.PRODUCT_ID)
            else -> stringResource(R.string.pro_not_found)
        }
        Text(message, style = MaterialTheme.typography.bodySmall, color = color)
        if (pro.status == ProStoreStatus.Ready || pro.pending) {
            TextButton(onClick = pro.refresh, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.pro_restore), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DebugUnlockRow() {
    val pro = LocalPro.current
    SwitchRow(
        title = stringResource(R.string.pro_debug_unlock),
        summary = stringResource(R.string.pro_debug_unlock_summary),
        checked = pro.debugUnlocked,
        onCheckedChange = pro.setDebugUnlock,
    )
}

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
