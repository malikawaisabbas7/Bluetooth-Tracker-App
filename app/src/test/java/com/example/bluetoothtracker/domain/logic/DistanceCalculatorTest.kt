package com.example.bluetoothtracker.domain.logic

import com.example.bluetoothtracker.domain.model.DistanceLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceCalculatorTest {
    @Test fun strongerSignalProducesShorterEstimatedDistance() {
        val near = DistanceCalculator.estimateMeters(-55.0)
        val far = DistanceCalculator.estimateMeters(-85.0)
        assertTrue(near < far)
    }

    @Test fun referencePowerGivesOneMeter() {
        assertEquals(1.0, DistanceCalculator.estimateMeters(-59.0), 0.0001)
    }

    @Test fun distanceLevelsFollowThresholds() {
        assertEquals(DistanceLevel.VERY_NEAR, DistanceCalculator.levelFor(0.01))
        assertEquals(DistanceLevel.FAR, DistanceCalculator.levelFor(1000.0))
    }
}
