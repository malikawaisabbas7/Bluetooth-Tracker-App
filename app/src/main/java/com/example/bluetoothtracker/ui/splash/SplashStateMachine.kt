package com.example.bluetoothtracker.ui.splash

import com.example.bluetoothtracker.core.Constants

/**
 * Pure-Kotlin state machine coordinating Splash progress, eased radar angle,
 * initialization readiness, cancellation, and navigate-once guarantees.
 */
class SplashStateMachine {

    var isCancelled: Boolean = false
        private set

    var isAnimationCompleted: Boolean = false
        private set

    var isInitializationReady: Boolean = false
        private set

    var hasNavigated: Boolean = false
        private set

    var lastProgressPercent: Int = 0
        private set

    var currentAngleDegrees: Float = 0f
        private set

    private val reportedMilestones = mutableSetOf<Int>()

    fun resetForStart() {
        isCancelled = false
        isAnimationCompleted = false
        lastProgressPercent = 0
        currentAngleDegrees = 0f
        reportedMilestones.clear()
    }

    /**
     * Updates normalized [rawFraction] (0f..1f) and returns a newly crossed milestone
     * (0, 25, 50, 75, 100) if any, or null.
     */
    fun onProgress(rawFraction: Float, sweeps: Int = RADAR_SWEEPS): Int? {
        val clamped = rawFraction.coerceIn(0f, 1f)
        lastProgressPercent = (clamped * 100f).toInt().coerceIn(0, 100)
        currentAngleDegrees = smoothEase(clamped) * 360f * sweeps

        return MILESTONES.lastOrNull { milestone ->
            lastProgressPercent >= milestone && reportedMilestones.add(milestone)
        }
    }

    fun onInitializationReady(): Boolean {
        isInitializationReady = true
        return canNavigateNow()
    }

    fun onAnimationCompleted(): Boolean {
        if (isCancelled) return false
        isAnimationCompleted = true
        lastProgressPercent = 100
        currentAngleDegrees = 360f * RADAR_SWEEPS
        return canNavigateNow()
    }

    fun onCancelled() {
        isCancelled = true
    }

    fun markNavigated(): Boolean {
        if (hasNavigated || !canNavigateNow()) return false
        hasNavigated = true
        return true
    }

    private fun canNavigateNow(): Boolean =
        !hasNavigated && !isCancelled && isAnimationCompleted && isInitializationReady

    companion object {
        const val DURATION_MS = Constants.SPLASH_DURATION_MS
        const val RADAR_SWEEPS = 2
        private val MILESTONES = listOf(0, 25, 50, 75, 100)

        fun elapsedToProgress(elapsedMs: Long, durationMs: Long = DURATION_MS): Float {
            if (durationMs <= 0L) return 1f
            return (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }

        /** Smoothstep ease-in/ease-out independent of ValueAnimator / system scale. */
        fun smoothEase(t: Float): Float {
            val c = t.coerceIn(0f, 1f)
            return (3f * c * c) - (2f * c * c * c)
        }
    }
}
