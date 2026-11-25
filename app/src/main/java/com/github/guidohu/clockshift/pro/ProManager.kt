package com.github.guidohu.clockshift.pro

import android.app.Activity
import android.content.Context
import android.util.Log
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

/** The one-time Pro purchase as offered in the Play Store, e.g. "CHF 4.90". */
data class ProOffer(
    val formattedPrice: String,
    internal val offerToken: String?,
)

/** Where the connection to the Play Store stands, so the UI can say why Pro can't be bought right now. */
enum class ProStoreStatus {
    /** Still connecting to the Play Store or loading the product. */
    Loading,

    /** The product is loaded and can be bought. */
    Ready,

    /** Google Play billing isn't reachable: no Play Store, not signed in, or an outdated Play Store. */
    BillingUnavailable,

    /** Billing works but the Play Store doesn't know [ProManager.PRODUCT_ID] for this app and account. */
    ProductNotFound,
}

/**
 * Pro, sold as a one-time in-app product through Google Play Billing.
 *
 * The entitlement is cached locally so the background parts of the app (geofence, Wi-Fi and reminder
 * receivers) can check it without talking to the Play Store. It is refreshed whenever the app comes to
 * the foreground; a refunded purchase disappears from the Play Store's list and turns Pro off again.
 * Billing talks to the Play Store app over IPC, so it works without the Internet permission.
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

    private val _offer = MutableStateFlow<ProOffer?>(null)

    /** The price the Play Store offers this user; null until loaded or when billing is unavailable. */
    val offer: StateFlow<ProOffer?> = _offer.asStateFlow()

    private val _status = MutableStateFlow(ProStoreStatus.Loading)
    val status: StateFlow<ProStoreStatus> = _status.asStateFlow()

    private val _pending = MutableStateFlow(false)

    /** A purchase was made but the payment isn't confirmed yet (e.g. cash at a store). */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    /** Whether Pro is unlocked for testing only (debug builds). */
    val debugUnlock: StateFlow<Boolean> = debugUnlocked.asStateFlow()

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(app)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    /** Re-reads the purchase and the price from the Play Store. Safe to call often; also restores a purchase. */
    fun refresh() {
        if (client.isReady) {
            queryPurchases()
            queryOffer()
            return
        }
        if (_status.value != ProStoreStatus.Ready) _status.value = ProStoreStatus.Loading
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                    queryOffer()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.responseCode} ${result.debugMessage}")
                    _status.value = ProStoreStatus.BillingUnavailable
                }
            }

            // Reconnection is automatic (enableAutoServiceReconnection); the cached entitlement stays valid meanwhile.
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Opens the Play Store purchase sheet for [offer]. */
    fun purchase(activity: Activity, offer: ProOffer) {
        val details = productDetails.value ?: return refresh()
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        offer.offerToken?.let(productParams::setOfferToken)
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams.build()))
            .build()
        client.launchBillingFlow(activity, params)
    }

    fun setDebugUnlock(unlocked: Boolean) {
        if (!BuildConfig.DEBUG) return
        prefs.edit { putBoolean(KEY_DEBUG_PRO, unlocked) }
        debugUnlocked.value = unlocked
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.let { handlePurchases(it) }
            // Bought earlier (e.g. on another device or before a reinstall): read it back.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases()
            else -> Unit
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handlePurchases(purchases, authoritative = true)
        }
    }

    /**
     * Grants Pro for a completed purchase and acknowledges new ones (Play refunds purchases that are not
     * acknowledged within three days). With [authoritative] the list is the complete set of owned
     * products, so an empty list revokes Pro.
     */
    private fun handlePurchases(purchases: List<Purchase>, authoritative: Boolean = false) {
        val ours = purchases.filter { PRODUCT_ID in it.products }
        val active = ours.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        active.filterNot { it.isAcknowledged }.forEach { purchase ->
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(params) { }
        }
        if (active.isNotEmpty() || authoritative) setEntitled(active.isNotEmpty())
        if (ours.isNotEmpty() || authoritative) _pending.value = ours.any { it.purchaseState == Purchase.PurchaseState.PENDING }
    }

    private fun setEntitled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ENTITLED, value) }
        entitled.value = value
    }

    private fun queryOffer() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Product query failed: ${result.responseCode} ${result.debugMessage}")
                _status.value = ProStoreStatus.BillingUnavailable
                return@queryProductDetailsAsync
            }
            val found = details.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
            val offer = found?.toOffer()
            if (offer == null) {
                Log.w(TAG, "Product $PRODUCT_ID not found; unfetched: ${details.unfetchedProductList}")
            }
            productDetails.value = found
            _offer.value = offer
            _status.value = if (offer == null) ProStoreStatus.ProductNotFound else ProStoreStatus.Ready
        }
    }

    /**
     * The cheapest purchase option. Discounted offers are only returned when the user is eligible, so the
     * lowest price is the one this user can actually get.
     */
    private fun ProductDetails.toOffer(): ProOffer? {
        val options = oneTimePurchaseOfferDetailsList ?: listOfNotNull(oneTimePurchaseOfferDetails)
        val best = options.minByOrNull { it.priceAmountMicros } ?: return null
        return ProOffer(best.formattedPrice, best.offerToken)
    }

    companion object {
        /** One-time product id, created in the Play Console under Monetize › Products › One-time products. */
        const val PRODUCT_ID = "clockshift_pro"

        private const val TAG = "ProManager"

        private const val PREFS = "pro"
        private const val KEY_ENTITLED = "entitled"
        private const val KEY_DEBUG_PRO = "debug_pro"
    }
}
