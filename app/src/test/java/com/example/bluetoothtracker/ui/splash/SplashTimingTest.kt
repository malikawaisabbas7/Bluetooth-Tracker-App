package com.example.bluetoothtracker.ui.splash

import com.example.bluetoothtracker.core.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashTimingTest {

    @Test
    fun `authoritative splash duration is fixed 3600ms with 2 full sweeps`() {
        assertEquals(3_600L, Constants.SPLASH_DURATION_MS)
        assertEquals(3_600L, SplashStateMachine.DURATION_MS)
        assertEquals(2, SplashStateMachine.RADAR_SWEEPS)
    }

    @Test
    fun `elapsed time maps monotonically to normalized progress independent of system scale`() {
        assertEquals(0f, SplashStateMachine.elapsedToProgress(0L), 0.0001f)
        assertEquals(0.25f, SplashStateMachine.elapsedToProgress(900L), 0.0001f)
        assertEquals(0.50f, SplashStateMachine.elapsedToProgress(1_800L), 0.0001f)
        assertEquals(0.75f, SplashStateMachine.elapsedToProgress(2_700L), 0.0001f)
        assertEquals(1.0f, SplashStateMachine.elapsedToProgress(3_600L), 0.0001f)
        assertEquals(1.0f, SplashStateMachine.elapsedToProgress(5_000L), 0.0001f)
    }

    @Test
    fun `progress synchronizes percent and eased two-sweep angle with milestone tracking`() {
        val sm = SplashStateMachine()
        sm.resetForStart()

        assertEquals(0, sm.onProgress(0f))
        assertEquals(0, sm.lastProgressPercent)
        assertEquals(0f, sm.currentAngleDegrees, 0.01f)

        assertEquals(25, sm.onProgress(0.25f))
        assertEquals(25, sm.lastProgressPercent)
        assertTrue(sm.currentAngleDegrees in 100f..180f)

        assertEquals(50, sm.onProgress(0.50f))
        assertEquals(50, sm.lastProgressPercent)
        assertEquals(360f, sm.currentAngleDegrees, 0.01f)

        assertEquals(75, sm.onProgress(0.75f))
        assertEquals(75, sm.lastProgressPercent)
        assertTrue(sm.currentAngleDegrees in 540f..620f)

        assertEquals(100, sm.onProgress(1.0f))
        assertEquals(100, sm.lastProgressPercent)
        assertEquals(720f, sm.currentAngleDegrees, 0.01f)
    }

    @Test
    fun `navigation executes only once after both animation and initialization complete`() {
        val sm = SplashStateMachine()
        sm.resetForStart()

        assertFalse(sm.onAnimationCompleted())
        assertTrue(sm.onInitializationReady())
        assertTrue(sm.markNavigated())
        assertFalse(sm.markNavigated())
    }

    @Test
    fun `cancelled animation never triggers navigation`() {
        val sm = SplashStateMachine()
        sm.resetForStart()
        sm.onInitializationReady()
        sm.onCancelled()

        assertFalse(sm.onAnimationCompleted())
        assertFalse(sm.markNavigated())
    }
}
