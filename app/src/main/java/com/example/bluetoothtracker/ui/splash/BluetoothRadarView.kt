package com.example.bluetoothtracker.ui.splash

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Choreographer
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.bluetoothtracker.R
import kotlin.math.cos
import kotlin.math.sin

class BluetoothRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val primaryColor: Int = TypedValue().let { value ->
        val found = context.theme.resolveAttribute(
            R.attr.themePrimaryColor,
            value,
            true
        )

        when {
            !found -> Color.rgb(25, 118, 210)
            value.resourceId != 0 ->
                ContextCompat.getColor(context, value.resourceId)
            else -> value.data
        }
    }

    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val sweepBounds = RectF()

    private var currentAngle = 0f
    private var isRunning = false
    private var startTimeUptimeMs = 0L
    private var activeDurationMs = 0L
    private var activeSweeps = SplashStateMachine.RADAR_SWEEPS

    private var onProgressCallback: ((Float) -> Unit)? = null
    private var onCancelCallback: (() -> Unit)? = null
    private var onCompleteCallback: (() -> Unit)? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            val elapsedMs = (SystemClock.uptimeMillis() - startTimeUptimeMs).coerceAtLeast(0L)
            val rawProgress = SplashStateMachine.elapsedToProgress(elapsedMs, activeDurationMs)
            val eased = SplashStateMachine.smoothEase(rawProgress)

            currentAngle = eased * 360f * activeSweeps
            onProgressCallback?.invoke(rawProgress)
            invalidate()

            if (rawProgress < 1f) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                isRunning = false
                currentAngle = 360f * activeSweeps
                invalidate()
                val complete = onCompleteCallback
                onProgressCallback = null
                onCancelCallback = null
                onCompleteCallback = null
                // Allow the final 100% / 720° frame to draw before navigation runs.
                postOnAnimation { complete?.invoke() }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        val cx = width / 2f
        val cy = height / 2f

        val density = resources.displayMetrics.density
        val radius = minOf(width, height) / 2f - 8f * density
        val innerRadius = 43f * density

        val angle = currentAngle % 360f

        // A subtle translucent radar sector.
        sweepBounds.set(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius
        )

        sweepPaint.color = ColorUtils.setAlphaComponent(
            primaryColor,
            24
        )

        canvas.drawArc(
            sweepBounds,
            angle - 45f,
            45f,
            true,
            sweepPaint
        )

        // Three rotating radar rays with different brightness.
        val rayOffsets = listOf(0f, -18f, -36f)
        val rayAlphas = listOf(200, 110, 55)

        rayOffsets.forEachIndexed { index, offset ->

            val radians = Math.toRadians(
                (angle + offset).toDouble()
            )

            val startX = cx + innerRadius * cos(radians).toFloat()
            val startY = cy + innerRadius * sin(radians).toFloat()

            val endX = cx + radius * cos(radians).toFloat()
            val endY = cy + radius * sin(radians).toFloat()

            // Soft glow behind each ray.
            glowPaint.color = ColorUtils.setAlphaComponent(
                primaryColor,
                rayAlphas[index] / 3
            )
            glowPaint.strokeWidth = 7f * density

            canvas.drawLine(
                startX,
                startY,
                endX,
                endY,
                glowPaint
            )

            // Main visible radar ray.
            beamPaint.color = ColorUtils.setAlphaComponent(
                primaryColor,
                rayAlphas[index]
            )
            beamPaint.strokeWidth = 2f * density

            canvas.drawLine(
                startX,
                startY,
                endX,
                endY,
                beamPaint
            )
        }
    }

    /**
     * Runs an independent Choreographer + SystemClock.uptimeMillis() sweep for [sweeps] rotations
     * over [durationMs], unaffected by system animator duration scales.
     */
    fun startSweep(
        sweeps: Int,
        durationMs: Long,
        onProgress: ((Float) -> Unit)? = null,
        onCancel: (() -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        stopRadar(notifyCancel = false)

        activeSweeps = sweeps.coerceAtLeast(1)
        activeDurationMs = durationMs.coerceAtLeast(1L)
        onProgressCallback = onProgress
        onCancelCallback = onCancel
        onCompleteCallback = onComplete

        currentAngle = 0f
        startTimeUptimeMs = SystemClock.uptimeMillis()
        isRunning = true

        onProgressCallback?.invoke(0f)
        invalidate()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stopRadar(notifyCancel: Boolean = false) {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        val wasRunning = isRunning
        isRunning = false
        val cancelCb = onCancelCallback
        onProgressCallback = null
        onCancelCallback = null
        onCompleteCallback = null
        if (wasRunning && notifyCancel) {
            cancelCb?.invoke()
        }
    }

    override fun onDetachedFromWindow() {
        stopRadar(notifyCancel = false)
        super.onDetachedFromWindow()
    }
}
