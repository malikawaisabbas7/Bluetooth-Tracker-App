package com.example.bluetoothtracker.domain.logic

import com.example.bluetoothtracker.domain.model.DeviceCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceClassifierTest {

    @Test
    fun `regular non-Apple BLE device classifies as BLUETOOTH_DEVICE`() {
        val category = DeviceClassifier.classify(
            companyId = 0x0075, // Samsung electronics (non-tag)
            manufacturerPayload = byteArrayOf(0x01, 0x02, 0x03),
            serviceUuids = emptyList()
        )
        assertEquals(DeviceCategory.BLUETOOTH_DEVICE, category)
    }

    @Test
    fun `separated Apple Find My payload classifies as AIR_TAG`() {
        val payload = ByteArray(27).apply {
            this[0] = DeviceClassifier.APPLE_FIND_MY_TYPE.toByte()
            this[1] = DeviceClassifier.APPLE_FIND_MY_MIN_LENGTH.toByte()
        }
        val category = DeviceClassifier.classify(
            companyId = DeviceClassifier.APPLE_COMPANY_ID,
            manufacturerPayload = payload,
            serviceUuids = emptyList()
        )
        assertEquals(DeviceCategory.AIR_TAG, category)
    }

    @Test
    fun `ordinary Apple Nearby Info packet classifies as BLUETOOTH_DEVICE`() {
        val payload = byteArrayOf(0x10, 0x05, 0x01, 0x18, 0x00, 0x00, 0x00)
        val category = DeviceClassifier.classify(
            companyId = DeviceClassifier.APPLE_COMPANY_ID,
            manufacturerPayload = payload,
            serviceUuids = emptyList()
        )
        assertEquals(DeviceCategory.BLUETOOTH_DEVICE, category)
    }

    @Test
    fun `short Apple Find My nearby packet without full payload classifies as BLUETOOTH_DEVICE`() {
        val payload = byteArrayOf(0x12, 0x02, 0x00, 0x01)
        val category = DeviceClassifier.classify(
            companyId = DeviceClassifier.APPLE_COMPANY_ID,
            manufacturerPayload = payload,
            serviceUuids = emptyList()
        )
        assertEquals(DeviceCategory.BLUETOOTH_DEVICE, category)
    }

    @Test
    fun `supported non-Apple tracker service UUID classifies as OTHER_TAG`() {
        val tileUuid = "0000feed-0000-1000-8000-00805f9b34fb"
        val category = DeviceClassifier.classify(
            companyId = null,
            manufacturerPayload = null,
            serviceUuids = listOf(tileUuid)
        )
        assertEquals(DeviceCategory.OTHER_TAG, category)
    }

    @Test
    fun `multi-frame Apple payload containing separated Find My subframe classifies as AIR_TAG`() {
        val payload = ByteArray(31).apply {
            this[0] = 0x10 // Nearby Info subframe (len=2)
            this[1] = 0x02
            this[2] = 0x01
            this[3] = 0x18
            this[4] = DeviceClassifier.APPLE_FIND_MY_TYPE.toByte()
            this[5] = DeviceClassifier.APPLE_FIND_MY_MIN_LENGTH.toByte()
        }
        val category = DeviceClassifier.classify(
            companyId = DeviceClassifier.APPLE_COMPANY_ID,
            manufacturerPayload = payload,
            serviceUuids = emptyList()
        )
        assertEquals(DeviceCategory.AIR_TAG, category)
    }

    @Test
    fun `unknown device with no metadata classifies as BLUETOOTH_DEVICE`() {
        val category = DeviceClassifier.classify(
            companyId = null,
            manufacturerPayload = null,
            serviceUuids = emptyList()
        )
        assertEquals(DeviceCategory.BLUETOOTH_DEVICE, category)
    }
}
