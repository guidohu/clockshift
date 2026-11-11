package com.github.guidohu.clockshift.pro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.github.guidohu.clockshift.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A subscription plan as offered in the Play Store, e.g. "CHF 1.50" per "P1M". */
data class ProOffer(
    val basePlanId: String,
    val formattedPrice: String,
    /** ISO 8601 period of the recurring price: P1M, P1Y, … */
    val billingPeriod: String,
    internal val offerToken: String,
)

/**
 * The Pro subscription, sold through Google Play Billing.
 *
 * The entitlement is cached locally so the background parts of the app (geofence, Wi-Fi and reminder
 * receivers) can check it without talking to the Play Store. It is refreshed whenever the app comes to
 * the foreground; an expired or refunded subscription disappears from the Play Store's list and turns
 * Pro off again. Billing talks to the Play Store app over IPC, so it works without the Internet permission.
 *
 * Debug builds can unlock Pro locally, so the features can be tried without a Play Console setup.
 */
class ProManager(context: Context, scope: CoroutineScope) : PurchasesUpdatedListener {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val entitled = MutableStateFlow(prefs.getBoolean(KEY_ENTITLED, false))
    private val debugUnlocked = MutableStateFlow(BuildConfig.DEBUG && prefs.getBoolean(KEY_DEBUG_PRO, false))
    private val productDetails = MutableStateFlow<ProductDetails?>(null)

    val isPro: StateFlow<Boolean> = combine(entitled, debugUnlocked) { real, debug -> real || debug }
        .stateIn(scope, SharingStarted.Eagerly, entitled.value || debugUnlocked.value)

    private val _offers = MutableStateFlow<List<ProOffer>>(emptyList())

    /** Plans the Play Store offers this user; empty until loaded or when billing is unavailable. */
    val offers: StateFlow<List<ProOffer>> = _offers.asStateFlow()

    /** Whether Pro is unlocked for testing only (debug builds). */
    val debugUnlock: StateFlow<Boolean> = debugUnlocked.asStateFlow()

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(app)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    /** Re-reads the subscription and the plans from the Play Store. Safe to call often. */
    fun refresh() {
        if (client.isReady) {
            queryPurchases()
            queryOffers()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                    queryOffers()
                }
            }

            // Reconnection is automatic (enableAutoServiceReconnection); the cached entitlement stays valid meanwhile.
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Opens the Play Store purchase sheet for [offer]. */
    fun purchase(activity: Activity, offer: ProOffer) {
        val details = productDetails.value ?: return refresh()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    /** Opens the Play Store page where the user can change or cancel the subscription. */
    fun manageSubscription(context: Context) {
        val uri = Uri.parse("https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID&package=${app.packageName}")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun setDebugUnlock(unlocked: Boolean) {
        if (!BuildConfig.DEBUG) return
        prefs.edit { putBoolean(KEY_DEBUG_PRO, unlocked) }
        debugUnlocked.value = unlocked
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handlePurchases(purchases, authoritative = true)
        }
    }

    /**
     * Grants Pro for every active purchase and acknowledges new ones (Play refunds purchases that are not
     * acknowledged within three days). With [authoritative] the list is the complete set of active
     * subscriptions, so an empty list revokes Pro.
     */
    private fun handlePurchases(purchases: List<Purchase>, authoritative: Boolean = false) {
        val active = purchases.filter { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        active.filterNot { it.isAcknowledged }.forEach { purchase ->
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(params) { }
        }
        if (active.isNotEmpty() || authoritative) setEntitled(active.isNotEmpty())
    }

    private fun setEntitled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ENTITLED, value) }
        entitled.value = value
    }

    private fun queryOffers() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val subscription = details.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
            productDetails.value = subscription
            _offers.value = subscription?.toOffers().orEmpty()
        }
    }

    /**
     * One offer per base plan (e.g. monthly and yearly). Developer offers such as a free trial are only
     * returned when the user is eligible, so they are preferred over the plain base plan.
     */
    private fun ProductDetails.toOffers(): List<ProOffer> =
        subscriptionOfferDetails.orEmpty()
            .groupBy { it.basePlanId }
            .mapNotNull { (basePlanId, offers) ->
                val offer = offers.firstOrNull { it.offerId != null } ?: offers.first()
                val recurring = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
                ProOffer(basePlanId, recurring.formattedPrice, recurring.billingPeriod, offer.offerToken)
            }
            .sortedBy { periodSortKey(it.billingPeriod) }

    private fun periodSortKey(period: String): Int = when (period.lastOrNull()) {
        'W' -> 0
        'M' -> 1
        'Y' -> 2
        else -> 3
    }

    companion object {
        /** Subscription product id; create it with monthly and/or yearly base plans in the Play Console. */
        const val PRODUCT_ID = "clockshift_pro"

        private const val PREFS = "pro"
        private const val KEY_ENTITLED = "entitled"
        private const val KEY_DEBUG_PRO = "debug_pro"
    }
}
