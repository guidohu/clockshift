package com.github.guidohu.clockshift.ui.pro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.pro.ProOffer
import com.github.guidohu.clockshift.ui.settings.SwitchRow

/** The Pro status and what the UI can do about it. Provided by the activity; locked in previews. */
@Immutable
data class ProAccess(
    val isPro: Boolean = false,
    val offers: List<ProOffer> = emptyList(),
    val debugUnlockAvailable: Boolean = false,
    val debugUnlocked: Boolean = false,
    val purchase: (Activity, ProOffer) -> Unit = { _, _ -> },
    val manage: (Context) -> Unit = {},
    val setDebugUnlock: (Boolean) -> Unit = {},
)

val LocalPro = compositionLocalOf { ProAccess() }

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

/** What Pro adds, with a button per subscription plan. */
@Composable
fun ProUpsellCard(modifier: Modifier = Modifier) {
    val pro = LocalPro.current
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pro_title), style = MaterialTheme.typography.titleLarge)
                ProBadge()
            }
            Text(
                stringResource(R.string.pro_pitch),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProPoint(R.string.pro_point_auto)
                ProPoint(R.string.pro_point_start)
                ProPoint(R.string.pro_point_done)
                ProPoint(R.string.pro_point_export)
                ProPoint(R.string.pro_point_backup)
            }
            if (pro.offers.isEmpty()) {
                Text(
                    stringResource(R.string.pro_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                pro.offers.forEachIndexed { index, offer ->
                    val label = offerLabel(offer)
                    val onClick = { context.findActivity()?.let { pro.purchase(it, offer) } ?: Unit }
                    if (index == 0) {
                        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    } else {
                        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    }
                }
            }
            if (pro.debugUnlockAvailable) {
                SwitchRow(
                    title = stringResource(R.string.pro_debug_unlock),
                    summary = stringResource(R.string.pro_debug_unlock_summary),
                    checked = pro.debugUnlocked,
                    onCheckedChange = pro.setDebugUnlock,
                )
            }
        }
    }
}

/** Shown instead of the offer once Pro is active. */
@Composable
fun ProActiveRow(modifier: Modifier = Modifier) {
    val pro = LocalPro.current
    val context = LocalContext.current
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
        } else {
            TextButton(onClick = { pro.manage(context) }) { Text(stringResource(R.string.pro_manage)) }
        }
    }
}

@Composable
private fun ProPoint(text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp).padding(top = 2.dp),
        )
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** "CHF 1.50 per month"; periods other than one week, month or year fall back to the plain price. */
@Composable
private fun offerLabel(offer: ProOffer): String = when (offer.billingPeriod) {
    "P1W" -> stringResource(R.string.pro_price_week, offer.formattedPrice)
    "P1M" -> stringResource(R.string.pro_price_month, offer.formattedPrice)
    "P1Y" -> stringResource(R.string.pro_price_year, offer.formattedPrice)
    else -> stringResource(R.string.pro_price_other, offer.formattedPrice)
}

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
