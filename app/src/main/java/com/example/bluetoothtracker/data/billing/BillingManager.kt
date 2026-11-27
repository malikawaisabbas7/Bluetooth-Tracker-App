package com.example.bluetoothtracker.data.billing

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
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
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.core.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Google Play checkout and server-verified subscription access. */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext context: Context
) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectionMutex = Mutex()
    private val refreshMutex = Mutex()
    private var expiryJob: Job? = null
    private var refreshJob: Job? = null

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    private var productDetails: ProductDetails? = null
    private var offerToken: String? = null
    private var purchaseFlowLaunching = false

    private val _offer = MutableStateFlow<PremiumOffer?>(null)
    val offer: StateFlow<PremiumOffer?> = _offer.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiState = MutableStateFlow<PremiumUiState>(PremiumUiState.Loading)
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    fun refreshAsync(force: Boolean = false) {
        if (!force && refreshJob?.isActive == true) {
            logDebug("refreshAsync skipped: refresh already in flight")
            return
        }
        refreshJob = scope.launch {
            refreshMutex.withLock {
                val showLoading = force || _offer.value == null
                _isLoading.value = true
                if (showLoading && _uiState.value !is PremiumUiState.Active) {
                    updateUiState(PremiumUiState.Loading)
                }
                try {
                    val connected = BillingVerifier.isConfigured && connect()
                    if (!connected) {
                        clearOffer()
                        updateUiState(PremiumUiState.Error(R.string.billing_unavailable))
                        _messages.tryEmit(R.string.billing_unavailable)
                        return@withLock
                    }
                    loadProduct()
                    syncPurchases()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logDebug("refreshAsync exception=${e::class.simpleName}")
                    clearOffer()
                    updateUiState(PremiumUiState.Error(R.string.billing_unavailable))
                    _messages.tryEmit(R.string.billing_unavailable)
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    private suspend fun connect(): Boolean = connectionMutex.withLock {
        if (client.isReady) {
            logDebug("connect: BillingClient already ready")
            return@withLock true
        }
        val result = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        logDebug("onBillingSetupFinished code=${result.responseCode}")
                        if (cont.isActive) {
                            cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        logDebug("onBillingServiceDisconnected")
                        if (cont.isActive) cont.resume(false)
                    }
                })
            }
        }
        if (result == null) {
            logDebug("connect timed out after ${CONNECT_TIMEOUT_MS}ms")
            return@withLock false
        }
        result
    }

    private fun clearOffer() {
        productDetails = null
        offerToken = null
        _offer.value = null
    }

    private suspend fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(Constants.PRODUCT_ID_PREMIUM)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        val response = withTimeoutOrNull(QUERY_TIMEOUT_MS) {
            client.queryProductDetails(params)
        }
        if (response == null) {
            logDebug("queryProductDetails timed out after ${QUERY_TIMEOUT_MS}ms")
            clearOffer()
            updateUiState(PremiumUiState.Error(R.string.billing_unavailable))
            _messages.tryEmit(R.string.billing_unavailable)
            return
        }

        val responseCode = response.billingResult.responseCode
        val detailsList = response.productDetailsList.orEmpty()
        logDebug("queryProductDetails code=$responseCode products=${detailsList.size}")

        if (responseCode != BillingClient.BillingResponseCode.OK) {
            clearOffer()
            updateUiState(PremiumUiState.Error(R.string.billing_unavailable))
            _messages.tryEmit(R.string.billing_unavailable)
            return
        }

        val details = detailsList.firstOrNull { it.productId == Constants.PRODUCT_ID_PREMIUM }
        if (details == null) {
            clearOffer()
            updateUiState(PremiumUiState.ProductUnavailable)
            return
        }

        val offers = details.subscriptionOfferDetails.orEmpty()
        // The Play response contains offers eligible for the current user.
        // Prefer a trial on the configured weekly base plan, otherwise its base offer.
        val weeklyOffers = offers.filter { it.basePlanId == Constants.BASE_PLAN_ID_PREMIUM }
        val chosen = weeklyOffers.firstOrNull { candidate ->
            candidate.pricingPhases.pricingPhaseList.firstOrNull()?.priceAmountMicros == 0L
        } ?: weeklyOffers.firstOrNull { it.offerId == null }
        ?: weeklyOffers.firstOrNull()
        if (chosen == null) {
            logDebug("no matching offer for basePlan=${Constants.BASE_PLAN_ID_PREMIUM} offers=${offers.size}")
            clearOffer()
            updateUiState(PremiumUiState.ProductUnavailable)
            return
        }

        val phases = chosen.pricingPhases.pricingPhaseList
        val recurring = phases.lastOrNull()
        if (recurring == null) {
            clearOffer()
            updateUiState(PremiumUiState.ProductUnavailable)
            return
        }
        val trial = phases.firstOrNull { it.priceAmountMicros == 0L }
        productDetails = details
        offerToken = chosen.offerToken
        val builtOffer = PremiumOffer(
            price = recurring.formattedPrice,
            periodRes = periodNameRes(recurring.billingPeriod),
            trialDays = trial?.let { periodInDays(it.billingPeriod) }?.takeIf { it > 0 }
        )
        _offer.value = builtOffer
        updateUiState(PremiumUiState.ProductAvailable(builtOffer))
    }

    fun launchPurchase(activity: Activity) {
        if (purchaseFlowLaunching || _uiState.value !is PremiumUiState.ProductAvailable) return
        val details = productDetails
        val token = offerToken
        if (!BillingVerifier.isConfigured || !client.isReady || details == null || token == null || _isLoading.value) {
            _messages.tryEmit(R.string.billing_unavailable)
            refreshAsync()
            return
        }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(token)
                        .build()
                )
            )
            .build()

        purchaseFlowLaunching = true
        val result = try {
            client.launchBillingFlow(activity, params)
        } catch (e: Exception) {
            purchaseFlowLaunching = false
            _messages.tryEmit(R.string.billing_unavailable)
            return
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            purchaseFlowLaunching = false
            if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                refreshAsync()
            } else {
                _messages.tryEmit(R.string.billing_unavailable)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        purchaseFlowLaunching = false
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch {
                refreshMutex.withLock {
                    try {
                        val candidates = purchases.orEmpty()
                        if (candidates.isEmpty()) {
                            syncPurchases()
                        } else {
                            reconcile(candidates)
                        }
                        if (_uiState.value is PremiumUiState.Active) {
                            _messages.tryEmit(R.string.purchase_success)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        updateUiState(PremiumUiState.Error(R.string.billing_unavailable))
                        _messages.tryEmit(R.string.billing_unavailable)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshAsync()
            else -> _messages.tryEmit(R.string.purchase_failed)
        }
    }

    private suspend fun syncPurchases() {
        val response = withTimeoutOrNull(QUERY_TIMEOUT_MS) {
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
        }
        check(response?.billingResult?.responseCode == BillingClient.BillingResponseCode.OK) {
            "Could not query Play purchases"
        }
        reconcile(checkNotNull(response).purchasesList)
    }

    private suspend fun reconcile(purchases: List<Purchase>) {
        val matching = purchases.filter { Constants.PRODUCT_ID_PREMIUM in it.products }
        var validUntil: Long? = null
        var verificationUnavailable = false
        for (purchase in matching.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
            val verifiedUntil = try {
                BillingVerifier.verify(purchase.purchaseToken)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                verificationUnavailable = true
                null
            }
            if (verifiedUntil != null) {
                validUntil = maxOf(validUntil ?: 0L, verifiedUntil)
            }
        }
        expiryJob?.cancel()
        val expiry = validUntil?.takeIf { it > System.currentTimeMillis() }
        when {
            expiry != null -> {
                updateUiState(PremiumUiState.Active(_offer.value))
                expiryJob = scope.launch {
                    delay((expiry - System.currentTimeMillis()).coerceAtLeast(0L))
                    if (_uiState.value is PremiumUiState.Active) {
                        updateUiState(PremiumUiState.Loading)
                        refreshAsync(force = true)
                    }
                }
            }
            verificationUnavailable || matching.any {
                it.purchaseState == Purchase.PurchaseState.PURCHASED
            } -> updateUiState(PremiumUiState.Error(R.string.billing_unavailable))
            matching.any { it.purchaseState == Purchase.PurchaseState.PENDING } ->
                updateUiState(PremiumUiState.PurchasePending)
            _offer.value != null -> updateUiState(PremiumUiState.ProductAvailable(checkNotNull(_offer.value)))
            _uiState.value is PremiumUiState.Active || _uiState.value is PremiumUiState.PurchasePending ->
                updateUiState(PremiumUiState.ProductUnavailable)
        }
    }

    private fun updateUiState(newState: PremiumUiState) {
        if (_uiState.value != newState) {
            logDebug("uiState -> ${newState::class.simpleName}")
            _uiState.value = newState
        }
    }

    private fun logDebug(message: String) {
        val isDebuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            Log.d(TAG, message)
        }
    }

    companion object {
        private const val TAG = "BillingDebug"
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val QUERY_TIMEOUT_MS = 10_000L
    }

    private fun periodNameRes(period: String): Int = when {
        period.endsWith("Y") -> R.string.period_year
        period.endsWith("M") -> R.string.period_month
        period.endsWith("W") -> R.string.period_week
        else -> R.string.period_day
    }

    private fun periodInDays(period: String): Int {
        val n = period.filter { it.isDigit() }.toIntOrNull() ?: return 0
        return when {
            period.endsWith("W") -> n * 7
            period.endsWith("M") -> n * 30
            period.endsWith("Y") -> n * 365
            else -> n
        }
    }
}
