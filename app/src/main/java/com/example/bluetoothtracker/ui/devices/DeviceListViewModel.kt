package com.example.bluetoothtracker.ui.devices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetoothtracker.data.local.DeviceEntity
import com.example.bluetoothtracker.data.repository.DeviceRepository
import com.example.bluetoothtracker.data.repository.ScanManager
import com.example.bluetoothtracker.domain.logic.RssiSmoother
import com.example.bluetoothtracker.domain.model.DeviceCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
@HiltViewModel
class DeviceListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: DeviceRepository,
    scanManager: ScanManager
) : ViewModel() {

    val category: DeviceCategory =
        DeviceCategory.valueOf(checkNotNull(savedStateHandle["category"]))

    private val smoothers = ConcurrentHashMap<String, RssiSmoother>()
    private val lastSampleAt = ConcurrentHashMap<String, Long>()
    private val lastAverage = ConcurrentHashMap<String, Int>()
    private val discoveryOrder = ConcurrentHashMap<String, Int>()
    private val discoveryCounter = AtomicInteger(0)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val devices: StateFlow<List<DeviceEntity>> = scanManager.sessionStart
        .flatMapLatest { since ->
            smoothers.clear()
            lastSampleAt.clear()
            lastAverage.clear()
            discoveryOrder.clear()
            discoveryCounter.set(0)
            repository.observeDevices(category, since)
        }
        .map { list ->
            // Append newly discovered devices in stable arrival order so existing rows never shift
            list.forEach { device ->
                discoveryOrder.getOrPut(device.mac) {
                    discoveryCounter.incrementAndGet()
                }
            }

            // Smooth RSSI per device and keep stable discovery order so rows never jump during scanning.
            list.map { device ->
                val smoother = smoothers.getOrPut(device.mac) { RssiSmoother(size = 6) }
                // Any device update re-emits the whole list; only feed the smoother when THIS
                // device actually advertised again, or idle devices would drift toward stale values.
                if (lastSampleAt[device.mac] != device.lastSeenAt) {
                    lastSampleAt[device.mac] = device.lastSeenAt
                    lastAverage[device.mac] = smoother.add(device.rssi).toInt()
                }
                device.copy(rssi = lastAverage[device.mac] ?: device.rssi)
            }.sortedWith(
                compareBy<DeviceEntity> {
                    discoveryOrder[it.mac] ?: Int.MAX_VALUE
                }.thenBy {
                    it.mac
                }
            )
        }
        .sample(250)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
