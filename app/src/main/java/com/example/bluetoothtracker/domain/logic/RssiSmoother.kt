package com.example.bluetoothtracker.domain.logic

/** Moving average of the last [size] RSSI samples, to reduce jitter in the distance. */
class RssiSmoother(private val size: Int = 5) {
    private val window = ArrayDeque<Int>()

    fun add(rssi: Int): Double {
        window.addLast(rssi)
        if (window.size > size) window.removeFirst()
        return window.average()
    }

    fun clear() = window.clear()
}
