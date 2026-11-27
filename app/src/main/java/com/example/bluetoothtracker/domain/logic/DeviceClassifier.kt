package com.example.bluetoothtracker.domain.logic

import com.example.bluetoothtracker.domain.model.DeviceCategory

/**
 * Decides which list a device belongs to from its BLE advertisement.
 * Ordinary Apple Continuity frames (AirPods, Handoff, Nearby Info, etc.) remain
 * [DeviceCategory.BLUETOOTH_DEVICE]; only full Apple Find My Offline Finding payloads
 * (type 0x12 with length >= 0x19) are classified as [DeviceCategory.AIR_TAG].
 */
object DeviceClassifier {
    const val APPLE_COMPANY_ID = 0x004C
    const val APPLE_FIND_MY_TYPE = 0x12
    const val APPLE_FIND_MY_MIN_LENGTH = 0x19

    /**
     * 16-bit service UUIDs advertised by common non-Apple trackers:
     * Tile (FEED, FD84), Samsung SmartTag (FD5A), Chipolo (FE33).
     */
    val TAG_SERVICE_UUIDS: List<String> = listOf("feed", "fd84", "fd5a", "fe33")
        .map { "0000$it-0000-1000-8000-00805f9b34fb" }

    fun classify(
        companyId: Int?,
        manufacturerPayload: ByteArray?,
        serviceUuids: List<String>
    ): DeviceCategory {
        if (companyId == APPLE_COMPANY_ID &&
            manufacturerPayload != null &&
            hasSeparatedFindMyPayload(manufacturerPayload)
        ) {
            return DeviceCategory.AIR_TAG
        }

        val isKnownTag = serviceUuids.any { it.lowercase() in TAG_SERVICE_UUIDS }
        if (isKnownTag) return DeviceCategory.OTHER_TAG

        return DeviceCategory.BLUETOOTH_DEVICE
    }

    internal fun hasSeparatedFindMyPayload(payload: ByteArray): Boolean {
        if (payload.size < 2) return false
        var offset = 0
        while (offset + 1 < payload.size) {
            val type = payload[offset].toInt() and 0xFF
            val length = payload[offset + 1].toInt() and 0xFF
            if (type == APPLE_FIND_MY_TYPE &&
                (length >= APPLE_FIND_MY_MIN_LENGTH || (offset == 0 && payload.size >= 27))
            ) {
                return true
            }
            if (length <= 0) break
            offset += 2 + length
        }
        return false
    }
}
