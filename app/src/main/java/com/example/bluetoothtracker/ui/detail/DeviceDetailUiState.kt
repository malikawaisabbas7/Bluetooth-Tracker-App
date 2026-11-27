package com.example.bluetoothtracker.ui.detail

import com.example.bluetoothtracker.data.local.SightingEntity
import com.example.bluetoothtracker.domain.logic.EncounterSummary
import com.example.bluetoothtracker.domain.model.DeviceCategory
import com.example.bluetoothtracker.domain.model.DistanceLevel
import com.example.bluetoothtracker.domain.model.DistanceUnit

data class DeviceDetailUiState(
    val name: String?,
    val mac: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val rssi: Int,
    val isBonded: Boolean,
    val company: String?,
    val category: DeviceCategory,
    val isIgnored: Boolean,
    val distanceMeters: Double,
    val hasFreshSignal: Boolean,
    val level: DistanceLevel,
    val unit: DistanceUnit,
    val encounterSummary: EncounterSummary,
    /** Newest first. */
    val sightings: List<SightingEntity>,
    /** Phone's current (lat, lng) if known; used to centre the map when there are no sightings yet. */
    val phoneLocation: Pair<Double, Double>? = null
) {
    val latestSighting: SightingEntity? get() = sightings.firstOrNull()
}
