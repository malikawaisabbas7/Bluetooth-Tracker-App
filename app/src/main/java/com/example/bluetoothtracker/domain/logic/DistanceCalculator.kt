package com.example.bluetoothtracker.domain.logic

import com.example.bluetoothtracker.core.Constants
import com.example.bluetoothtracker.domain.model.DistanceLevel
import kotlin.math.pow

/** Approximate distance from RSSI (log-distance path-loss model). Accuracy is a few metres at best. */
object DistanceCalculator {
    fun estimateMeters(rssi: Double, txPower: Int = -59, pathLoss: Double = 2.5): Double =
        10.0.pow((txPower - rssi) / (10.0 * pathLoss))

    /** Colour bucket for a distance that is currently in range. */
    fun levelFor(meters: Double): DistanceLevel = when {
        meters < Constants.VERY_NEAR_BELOW_M -> DistanceLevel.VERY_NEAR
        meters <= Constants.NEAR_BELOW_M -> DistanceLevel.NEAR
        else -> DistanceLevel.FAR
    }
}
