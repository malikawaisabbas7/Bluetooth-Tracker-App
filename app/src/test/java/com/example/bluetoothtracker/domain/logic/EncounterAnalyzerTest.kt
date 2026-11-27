package com.example.bluetoothtracker.domain.logic

import com.example.bluetoothtracker.core.Constants
import com.example.bluetoothtracker.data.local.DeviceEntity
import com.example.bluetoothtracker.data.local.SightingEntity
import com.example.bluetoothtracker.domain.model.DeviceCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class EncounterAnalyzerTest {

    private fun device(
        category: DeviceCategory = DeviceCategory.AIR_TAG,
        firstSeenAt: Long = 1_000L,
        lastSeenAt: Long = 1_000L,
        isIgnored: Boolean = false
    ) = DeviceEntity(
        mac = "AA:BB:CC:DD:EE:FF",
        name = "Test Device",
        companyId = 0x004C,
        companyName = "Apple, Inc.",
        category = category.name,
        rssi = -65,
        bondState = 10,
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt,
        isIgnored = isIgnored
    )

    private fun sighting(
        ts: Long,
        rssi: Int = -65,
        lat: Double = 31.5204,
        lon: Double = 74.3587
    ) = SightingEntity(
        mac = "AA:BB:CC:DD:EE:FF",
        timestamp = ts,
        rssi = rssi,
        distanceM = 2.0,
        latitude = lat,
        longitude = lon
    )

    @Test
    fun `single recent sighting triggers only NEARBY_DEVICE`() {
        val now = 100_000L
        val d = device(firstSeenAt = now - 2_000L, lastSeenAt = now - 1_000L)
        val summary = EncounterAnalyzer.analyze(d, listOf(sighting(now - 1_000L)), now)
        assertEquals(TrackingWarningLevel.NEARBY_DEVICE, summary.warningLevel)
        assertEquals(1, summary.encounterCount)
    }

    @Test
    fun `repeated tag sightings at single location stay at REPEATED_ENCOUNTER`() {
        val now = 3_000_000L
        val start = now - Constants.FOLLOW_MIN_SPAN_MS - 60_000L
        val d = device(firstSeenAt = start, lastSeenAt = now - 5_000L)
        val list = listOf(
            sighting(start),
            sighting(start + 600_000L),
            sighting(now - 5_000L)
        )
        val summary = EncounterAnalyzer.analyze(d, list, now)
        assertEquals(1, summary.distinctLocationCount)
        assertEquals(TrackingWarningLevel.REPEATED_ENCOUNTER, summary.warningLevel)
    }

    @Test
    fun `repeated supported tag across two distinct locations triggers POTENTIAL_UNWANTED_TRACKING`() {
        val now = 3_000_000L
        val start = now - Constants.FOLLOW_MIN_SPAN_MS - 60_000L
        val d = device(category = DeviceCategory.AIR_TAG, firstSeenAt = start, lastSeenAt = now - 5_000L)
        val list = listOf(
            sighting(start, lat = 31.5204, lon = 74.3587),
            sighting(start + 600_000L, lat = 31.5205, lon = 74.3588), // same ~15m cluster
            sighting(now - 5_000L, lat = 31.5304, lon = 74.3687) // >1km away
        )
        val summary = EncounterAnalyzer.analyze(d, list, now)
        assertEquals(2, summary.distinctLocationCount)
        assertEquals(TrackingWarningLevel.POTENTIAL_UNWANTED_TRACKING, summary.warningLevel)
    }

    @Test
    fun `ordinary bluetooth device across two locations does not trigger POTENTIAL_UNWANTED_TRACKING`() {
        val now = 3_000_000L
        val start = now - Constants.FOLLOW_MIN_SPAN_MS - 60_000L
        val d = device(category = DeviceCategory.BLUETOOTH_DEVICE, firstSeenAt = start, lastSeenAt = now - 5_000L)
        val list = listOf(
            sighting(start, lat = 31.5204, lon = 74.3587),
            sighting(start + 600_000L, lat = 31.5250, lon = 74.3620),
            sighting(now - 5_000L, lat = 31.5304, lon = 74.3687)
        )
        val summary = EncounterAnalyzer.analyze(d, list, now)
        assertEquals(TrackingWarningLevel.REPEATED_ENCOUNTER, summary.warningLevel)
    }

    @Test
    fun `ignored device always returns NONE warning level`() {
        val now = 3_000_000L
        val start = now - Constants.FOLLOW_MIN_SPAN_MS - 60_000L
        val d = device(isIgnored = true, firstSeenAt = start, lastSeenAt = now - 5_000L)
        val list = listOf(
            sighting(start, lat = 31.5204, lon = 74.3587),
            sighting(start + 600_000L, lat = 31.5250, lon = 74.3620),
            sighting(now - 5_000L, lat = 31.5304, lon = 74.3687)
        )
        val summary = EncounterAnalyzer.analyze(d, list, now)
        assertEquals(TrackingWarningLevel.NONE, summary.warningLevel)
    }

    @Test
    fun `invalid zero coordinates are excluded from distinct location count`() {
        val now = 3_000_000L
        val start = now - Constants.FOLLOW_MIN_SPAN_MS - 60_000L
        val d = device(firstSeenAt = start, lastSeenAt = now - 5_000L)
        val list = listOf(
            sighting(start, lat = 0.0, lon = 0.0),
            sighting(start + 600_000L, lat = 0.0, lon = 0.0),
            sighting(now - 5_000L, lat = 0.0, lon = 0.0)
        )
        val summary = EncounterAnalyzer.analyze(d, list, now)
        assertEquals(0, summary.distinctLocationCount)
        assertEquals(TrackingWarningLevel.REPEATED_ENCOUNTER, summary.warningLevel)
    }

    @Test
    fun `sightings with hasLocation false are excluded from distinct location count`() {
        val now = 3_000_000L
        val start = now - Constants.FOLLOW_MIN_SPAN_MS - 60_000L
        val d = device(firstSeenAt = start, lastSeenAt = now - 5_000L)
        val list = listOf(
            sighting(start, lat = 31.5204, lon = 74.3587).copy(hasLocation = false),
            sighting(start + 600_000L, lat = 31.5304, lon = 74.3687).copy(hasLocation = false),
            sighting(now - 5_000L, lat = 0.0, lon = 0.0).copy(hasLocation = false)
        )
        val summary = EncounterAnalyzer.analyze(d, list, now)
        assertEquals(0, summary.distinctLocationCount)
        assertEquals(TrackingWarningLevel.REPEATED_ENCOUNTER, summary.warningLevel)
    }

    @Test
    fun `stale tag sightings outside recent window do not trigger POTENTIAL_UNWANTED_TRACKING`() {
        val now = 10_000_000L
        val lastSeen = now - Constants.FOLLOW_RECENT_MS - 60_000L
        val start = lastSeen - Constants.FOLLOW_MIN_SPAN_MS - 60_000L
        val d = device(category = DeviceCategory.AIR_TAG, firstSeenAt = start, lastSeenAt = lastSeen)
        val list = listOf(
            sighting(start, lat = 31.5204, lon = 74.3587),
            sighting(start + 600_000L, lat = 31.5250, lon = 74.3620),
            sighting(lastSeen, lat = 31.5304, lon = 74.3687)
        )
        val summary = EncounterAnalyzer.analyze(d, list, now)
        assertEquals(3, summary.distinctLocationCount)
        assertEquals(TrackingWarningLevel.REPEATED_ENCOUNTER, summary.warningLevel)
    }

    @Test
    fun `rssi trend detects STRONGER when recent signal improves by 6 dBm or more`() {
        val now = 100_000L
        val d = device(firstSeenAt = now - 40_000L, lastSeenAt = now)
        val list = listOf(
            sighting(now - 40_000L, rssi = -82),
            sighting(now - 30_000L, rssi = -80),
            sighting(now - 10_000L, rssi = -64),
            sighting(now, rssi = -60)
        )
        val summary = EncounterAnalyzer.analyze(d, list, now)
        assertEquals(RssiTrend.STRONGER, summary.rssiTrend)
    }
}
