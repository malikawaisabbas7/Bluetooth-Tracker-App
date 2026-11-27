package com.example.bluetoothtracker.data.billing

import androidx.annotation.StringRes

/** What the Premium screen shows: price, billing period and optional free-trial length. */
data class PremiumOffer(
    val price: String,
    @StringRes val periodRes: Int,
    val trialDays: Int?
)

/** Explicit UI states for the Premium subscription screen. */
sealed interface PremiumUiState {
    data object Loading : PremiumUiState
    data class ProductAvailable(val offer: PremiumOffer) : PremiumUiState
    data object ProductUnavailable : PremiumUiState
    data class Error(@StringRes val messageRes: Int) : PremiumUiState
    data object PurchasePending : PremiumUiState
    data class Active(val offer: PremiumOffer? = null) : PremiumUiState
}
