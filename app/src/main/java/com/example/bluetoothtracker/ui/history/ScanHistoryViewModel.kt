package com.example.bluetoothtracker.ui.history

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetoothtracker.data.local.SightingEntity
import com.example.bluetoothtracker.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ScanHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: DeviceRepository
) : ViewModel() {

    private val mac: String =
        checkNotNull(savedStateHandle["mac"])

    var layoutManagerState: Parcelable? = null

    val sightings = repository.observeSightings(mac)
        .map { list -> filterValidLocations(list) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    companion object {
        internal fun isValidLocation(sighting: SightingEntity): Boolean =
            sighting.hasLocation &&
                sighting.latitude.isFinite() &&
                sighting.longitude.isFinite() &&
                sighting.latitude in -90.0..90.0 &&
                sighting.longitude in -180.0..180.0 &&
                !(sighting.latitude == 0.0 && sighting.longitude == 0.0)

        internal fun filterValidLocations(list: List<SightingEntity>): List<SightingEntity> =
            list.filter(::isValidLocation)
    }
}
