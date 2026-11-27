package com.example.bluetoothtracker.ui.history

import com.example.bluetoothtracker.data.local.SightingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanHistoryFilterTest {

    private fun sighting(
        id: Long,
        ts: Long,
        lat: Double,
        lon: Double,
        hasLocation: Boolean = true
    ) = SightingEntity(
        id = id,
        mac = "AA:BB:CC:DD:EE:FF",
        timestamp = ts,
        rssi = -65,
        distanceM = 2.0,
        latitude = lat,
        longitude = lon,
        hasLocation = hasLocation
    )

    @Test
    fun `keeps only valid finite non-zero coordinates and preserves order and duplicates`() {
        val valid1 = sighting(1L, 3000L, 33.6844, 73.0479, hasLocation = true)
        val validSameCoords = sighting(2L, 2000L, 33.6844, 73.0479, hasLocation = true)
        val noLocFlag = sighting(3L, 1900L, 33.6844, 73.0479, hasLocation = false)
        val zeroZero = sighting(4L, 1800L, 0.0, 0.0, hasLocation = true)
        val nanLat = sighting(5L, 1700L, Double.NaN, 73.0, hasLocation = true)
        val infLon = sighting(6L, 1600L, 33.0, Double.POSITIVE_INFINITY, hasLocation = true)
        val outOfRange = sighting(7L, 1500L, 95.0, 73.0, hasLocation = true)

        val filtered = ScanHistoryViewModel.filterValidLocations(
            listOf(valid1, validSameCoords, noLocFlag, zeroZero, nanLat, infLon, outOfRange)
        )

        assertEquals(listOf(valid1, validSameCoords), filtered)
    }

    @Test
    fun `returns empty list when no valid location records exist`() {
        val filtered = ScanHistoryViewModel.filterValidLocations(
            listOf(
                sighting(1L, 1000L, 0.0, 0.0, hasLocation = false),
                sighting(2L, 2000L, 0.0, 0.0, hasLocation = true)
            )
        )
        assertTrue(filtered.isEmpty())
    }
}
