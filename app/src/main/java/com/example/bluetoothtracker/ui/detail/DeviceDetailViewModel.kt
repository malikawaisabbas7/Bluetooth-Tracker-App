package com.example.bluetoothtracker.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetoothtracker.core.Constants
import com.example.bluetoothtracker.data.location.LocationProvider
import com.example.bluetoothtracker.data.repository.DeviceRepository
import com.example.bluetoothtracker.data.repository.ScanManager
import com.example.bluetoothtracker.data.settings.SettingsRepository
import com.example.bluetoothtracker.domain.logic.DistanceCalculator
import com.example.bluetoothtracker.domain.logic.EncounterAnalyzer
import com.example.bluetoothtracker.domain.logic.RssiSmoother
import com.example.bluetoothtracker.domain.model.DeviceCategory
import com.example.bluetoothtracker.domain.model.DistanceLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val BOND_BONDED = 12 // BluetoothDevice.BOND_BONDED

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: DeviceRepository,
    private val scanManager: ScanManager,
    private val locationProvider: LocationProvider,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val mac: String = checkNotNull(savedStateHandle["mac"])

    var savedScrollY: Int
        get() = savedStateHandle[KEY_SCROLL_Y] ?: 0
        set(value) {
            savedStateHandle[KEY_SCROLL_Y] = value
        }

    private val smoother = RssiSmoother(size = 5)
    private var lastSampleAt = 0L
    private var startedScan = false

    /** Ticks every second so "Out of range" appears even when no new advertisements arrive. */


    private var lastAverageRssi = 0.0

    private val deviceWithAverage = repository.observeDevice(mac)
        .filterNotNull()
        .map { device ->
            // Add a sample only when a NEW advertisement arrived (toggling Ignore re-emits the row)
            if (device.lastSeenAt != lastSampleAt) {
                if (device.lastSeenAt - lastSampleAt > Constants.STALE_AFTER_MS) smoother.clear()
                lastSampleAt = device.lastSeenAt
                lastAverageRssi = smoother.add(device.rssi)
            }
            device to lastAverageRssi
        }

    private val unitFlow = settingsRepository.settings.map { it.unit }

    val scanErrors = scanManager.errors

    val state: StateFlow<DeviceDetailUiState?> = combine(
        deviceWithAverage,
        repository.observeSightings(mac),
        unitFlow
    ) { pair, sightings, unit ->
        val (device, avgRssi) = pair
        val meters = DistanceCalculator.estimateMeters(avgRssi)
        val level = DistanceCalculator.levelFor(meters)
        val category = runCatching { DeviceCategory.valueOf(device.category) }
            .getOrDefault(DeviceCategory.BLUETOOTH_DEVICE)
        val encounterSummary = EncounterAnalyzer.analyze(device, sightings)
        DeviceDetailUiState(
            name = device.name,
            mac = device.mac,
            firstSeenAt = device.firstSeenAt,
            lastSeenAt = device.lastSeenAt,
            rssi = device.rssi,
            isBonded = device.bondState == BOND_BONDED,
            company = device.companyName,
            category = category,
            isIgnored = device.isIgnored,
            distanceMeters = meters,
            level = level,
            unit = unit,
            encounterSummary = encounterSummary,
            sightings = sightings,
            phoneLocation = locationProvider.currentOrNull()?.let { it.latitude to it.longitude }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setIgnored(ignored: Boolean) {
        if (mac.isBlank()) return
        viewModelScope.launch { repository.setIgnored(mac, ignored) }
    }

    /** Keep advertisements flowing while this screen is visible. */
    fun acquireScan() {
        if (!startedScan && scanManager.start(Constants.DETAIL_SCAN_DURATION_MS, newSession = false)) {
            startedScan = true
        }
    }

    /** Stop only a scan this screen started itself. */
    fun releaseScan() {
        if (startedScan) {
            scanManager.stop()
            startedScan = false
        }
    }

    override fun onCleared() {
        releaseScan()
        super.onCleared()
    }

    companion object {
        private const val KEY_SCROLL_Y = "detail_scroll_y"
    }
}
