package com.example.bluetoothtracker.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class RssiSmootherTest {

    @Test
    fun `sliding window computes moving average and evicts oldest samples`() {
        val smoother = RssiSmoother(size = 3)
        assertEquals(-60.0, smoother.add(-60), 0.001)
        assertEquals(-65.0, smoother.add(-70), 0.001)
        assertEquals(-70.0, smoother.add(-80), 0.001)
        // Window now holds [-70, -80, -90] -> average -80.0
        assertEquals(-80.0, smoother.add(-90), 0.001)
    }

    @Test
    fun `clear resets accumulated window`() {
        val smoother = RssiSmoother(size = 3)
        smoother.add(-50)
        smoother.add(-70)
        smoother.clear()
        assertEquals(-90.0, smoother.add(-90), 0.001)
    }
}
