package com.example.bluetoothtracker.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalLevelTest {

    @Test
    fun `fromRssi maps expected signal bar thresholds`() {
        assertEquals(4, SignalLevel.fromRssi(-45))
        assertEquals(4, SignalLevel.fromRssi(-55))
        assertEquals(3, SignalLevel.fromRssi(-56))
        assertEquals(3, SignalLevel.fromRssi(-70))
        assertEquals(2, SignalLevel.fromRssi(-71))
        assertEquals(2, SignalLevel.fromRssi(-85))
        assertEquals(1, SignalLevel.fromRssi(-86))
        assertEquals(1, SignalLevel.fromRssi(-105))
    }
}
