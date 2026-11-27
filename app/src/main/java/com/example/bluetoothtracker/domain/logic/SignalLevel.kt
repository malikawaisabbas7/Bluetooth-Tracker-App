package com.example.bluetoothtracker.domain.logic

object SignalLevel {
    /** Returns 1..4 bars. Thresholds are tunable. */
    fun fromRssi(rssi: Int): Int = when {
        rssi >= -55 -> 4
        rssi >= -70 -> 3
        rssi >= -85 -> 2
        else -> 1
    }
}
