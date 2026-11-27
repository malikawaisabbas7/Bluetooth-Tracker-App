package com.example.bluetoothtracker.domain.logic

import com.example.bluetoothtracker.core.Constants
import com.example.bluetoothtracker.data.local.DeviceEntity
import com.example.bluetoothtracker.data.local.SightingEntity
import com.example.bluetoothtracker.domain.model.DeviceCategory
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class RssiTrend {
    STRONGER,
    WEAKER,
    STEADY,
    INSUFFICIENT_DATA
}

enum class TrackingWarningLevel {
    NONE,
    NEARBY_DEVICE,
    REPEATED_ENCOUNTER,
    POTENTIAL_UNWANTED_TRACKING
}

data class EncounterSummary(
    val encounterCount: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val observationSpanMs: Long,
    val distinctLocationCount: Int,
    val rssiTrend: RssiTrend,
    val warningLevel: TrackingWarningLevel
)

/**
 * Analyzes repeated BLE observations for a single device identifier without falsely merging
 * unrelated rotating MAC addresses. Produces conservative, explainable warning levels.
 */
object EncounterAnalyzer {
    internal const val DISTINCT_LOCATION_METERS = 100.0
    internal const val MIN_ENCOUNTERS_FOR_REPEATED = 3
    internal const val RSSI_TREND_DELTA_DBM = 6.0

    fun analyze(
        device: DeviceEntity,
        sightings: List<SightingEntity>,
        nowMs: Long = System.currentTimeMillis()
    ): EncounterSummary {
        val sorted = sightings.sortedBy { it.timestamp }
        val encounterCount = sorted.size.coerceAtLeast(1)
        val firstSeen = minOf(device.firstSeenAt, sorted.firstOrNull()?.timestamp ?: device.firstSeenAt)
        val lastSeen = maxOf(device.lastSeenAt, sorted.lastOrNull()?.timestamp ?: device.lastSeenAt)
        val spanMs = (lastSeen - firstSeen).coerceAtLeast(0L)
        val distinctLocations = countDistinctLocations(sorted)
        val trend = computeRssiTrend(sorted)

        if (device.isIgnored) {
            return EncounterSummary(
                encounterCount = encounterCount,
                firstSeenAt = firstSeen,
                lastSeenAt = lastSeen,
                observationSpanMs = spanMs,
                distinctLocationCount = distinctLocations,
                rssiTrend = trend,
                warningLevel = TrackingWarningLevel.NONE
            )
        }

        val category = runCatching { DeviceCategory.valueOf(device.category) }
            .getOrDefault(DeviceCategory.BLUETOOTH_DEVICE)
        val isSupportedTag = category == DeviceCategory.AIR_TAG || category == DeviceCategory.OTHER_TAG
        val seenRecently = (nowMs - lastSeen) <= Constants.FOLLOW_RECENT_MS
        val isNearbyNow = (nowMs - lastSeen) <= Constants.STALE_AFTER_MS

        val warningLevel = when {
            isSupportedTag &&
                seenRecently &&
                encounterCount >= MIN_ENCOUNTERS_FOR_REPEATED &&
                spanMs >= Constants.FOLLOW_MIN_SPAN_MS &&
                distinctLocations >= 2 -> TrackingWarningLevel.POTENTIAL_UNWANTED_TRACKING

            encounterCount >= MIN_ENCOUNTERS_FOR_REPEATED &&
                spanMs >= Constants.FOLLOW_MIN_SPAN_MS -> TrackingWarningLevel.REPEATED_ENCOUNTER

            isNearbyNow -> TrackingWarningLevel.NEARBY_DEVICE

            else -> TrackingWarningLevel.NONE
        }

        return EncounterSummary(
            encounterCount = encounterCount,
            firstSeenAt = firstSeen,
            lastSeenAt = lastSeen,
            observationSpanMs = spanMs,
            distinctLocationCount = distinctLocations,
            rssiTrend = trend,
            warningLevel = warningLevel
        )
    }

    fun isValidCoordinate(lat: Double, lon: Double): Boolean =
        lat in -90.0..90.0 &&
            lon in -180.0..180.0 &&
            !(lat == 0.0 && lon == 0.0)

    internal fun countDistinctLocations(sortedSightings: List<SightingEntity>): Int {
        val anchors = mutableListOf<Pair<Double, Double>>()
        for (s in sortedSightings) {
            if (!s.hasLocation || !isValidCoordinate(s.latitude, s.longitude)) continue
            val isNewCluster = anchors.none { (lat, lon) ->
                haversineMeters(lat, lon, s.latitude, s.longitude) < DISTINCT_LOCATION_METERS
            }
            if (isNewCluster) {
                anchors += s.latitude to s.longitude
            }
        }
        return anchors.size
    }

    internal fun computeRssiTrend(sortedSightings: List<SightingEntity>): RssiTrend {
        if (sortedSightings.size < 4) return RssiTrend.INSUFFICIENT_DATA
        val mid = sortedSightings.size / 2
        val earlierAvg = sortedSightings.subList(0, mid).map { it.rssi }.average()
        val recentAvg = sortedSightings.subList(mid, sortedSightings.size).map { it.rssi }.average()
        val delta = recentAvg - earlierAvg
        return when {
            delta >= RSSI_TREND_DELTA_DBM -> RssiTrend.STRONGER
            delta <= -RSSI_TREND_DELTA_DBM -> RssiTrend.WEAKER
            else -> RssiTrend.STEADY
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusM = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusM * c
    }
}
