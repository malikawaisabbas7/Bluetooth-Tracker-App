package com.example.bluetoothtracker.ui.premium

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetoothtracker.data.billing.BillingManager
import com.example.bluetoothtracker.data.billing.PremiumOffer
import com.example.bluetoothtracker.data.billing.PremiumUiState
import com.example.bluetoothtracker.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val billing: BillingManager,
    settingsRepository: SettingsRepository
) : ViewModel() {
    val offer: StateFlow<PremiumOffer?> = billing.offer
    val messages: SharedFlow<Int> = billing.messages
    val isLoading: StateFlow<Boolean> = billing.isLoading

    val isPremium: StateFlow<Boolean> = settingsRepository.settings
        .map { it.isPremium }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val uiState: StateFlow<PremiumUiState> = combine(
        billing.uiState,
        isPremium
    ) { billingState, premium ->
        if (premium) {
            PremiumUiState.Active(billing.offer.value)
        } else {
            billingState
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), billing.uiState.value)

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
