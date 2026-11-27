package com.example.bluetoothtracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetoothtracker.data.repository.DeviceRepository
import com.example.bluetoothtracker.data.repository.ScanManager
import com.example.bluetoothtracker.data.settings.SettingsRepository
import com.example.bluetoothtracker.domain.model.DeviceCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: DeviceRepository,
    private val scanManager: ScanManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeUiState> = scanManager.sessionStart.flatMapLatest { since ->
        combine(
            repository.observeCount(DeviceCategory.AIR_TAG, since),
            repository.observeCount(DeviceCategory.OTHER_TAG, since),
            repository.observeCount(DeviceCategory.BLUETOOTH_DEVICE, since),
            scanManager.isScanning
        ) { air, other, bt, scanning -> HomeUiState(scanning, air, other, bt) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val events: Flow<HomeEvent> = scanManager.errors.map { HomeEvent.ScanFailed(it) }

    val isScanning: Boolean get() = scanManager.isScanning.value

    var disclosureAccepted: Boolean = false
    var initialPromptShown: Boolean = false

    fun startScan() {
        viewModelScope.launch {
            // Auto-stop time comes from Settings > Scan Duration
            val duration = settingsRepository.settings.first().scanDurationMs
            scanManager.start(duration, newSession = true)
        }
    }

    fun stopScan() = scanManager.stop()
}
