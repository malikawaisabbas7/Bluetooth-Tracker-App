package com.example.bluetoothtracker.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetoothtracker.data.settings.AppSettings
import com.example.bluetoothtracker.data.settings.SettingsRepository
import com.example.bluetoothtracker.domain.model.AppTheme
import com.example.bluetoothtracker.domain.model.DistanceUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TrackingActivationState {
    OFF,
    CHECKING_REQUIREMENTS,
    WAITING_FOR_PERMISSION,
    WAITING_FOR_BLUETOOTH,
    WAITING_FOR_LOCATION,
    STARTING,
    ON,
    ERROR
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings()
        )

    val pendingTrackingActivation: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_PENDING_TRACKING, false)

    var savedScrollY: Int
        get() = savedStateHandle[KEY_SCROLL_Y] ?: 0
        set(value) {
            savedStateHandle[KEY_SCROLL_Y] = value
        }

    fun setPendingTrackingActivation(pending: Boolean) {
        savedStateHandle[KEY_PENDING_TRACKING] = pending
    }

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch {
            repository.setAppTheme(theme)
        }
    }

    fun setUnit(unit: DistanceUnit) {
        viewModelScope.launch {
            repository.setUnit(unit)
        }
    }

    fun setScanDuration(ms: Long) {
        viewModelScope.launch {
            repository.setScanDuration(ms)
        }
    }

    fun setBackgroundTracking(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBackgroundTracking(enabled)
        }
    }

    companion object {
        private const val KEY_PENDING_TRACKING = "pending_tracking_activation"
        private const val KEY_SCROLL_Y = "settings_scroll_y"
    }
}
