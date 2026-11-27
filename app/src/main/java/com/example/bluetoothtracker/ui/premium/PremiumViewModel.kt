package com.example.bluetoothtracker.ui.premium

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.example.bluetoothtracker.data.billing.BillingManager
import com.example.bluetoothtracker.data.billing.PremiumOffer
import com.example.bluetoothtracker.data.billing.PremiumUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val billing: BillingManager
) : ViewModel() {
    val offer: StateFlow<PremiumOffer?> = billing.offer
    val messages: SharedFlow<Int> = billing.messages
    val isLoading: StateFlow<Boolean> = billing.isLoading

    val uiState: StateFlow<PremiumUiState> = billing.uiState

    init {
        billing.refreshAsync()
    }

    fun startTrial(activity: Activity) {
        billing.launchPurchase(activity)
    }

    fun retry() {
        billing.refreshAsync(force = true)
    }
}
