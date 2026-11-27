package com.example.bluetoothtracker.ui.onboarding

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.example.bluetoothtracker.R
import com.google.android.material.color.MaterialColors

/**
 * Static custom-Canvas illustrations for the three onboarding pages.
 * Renders only on draw/page changes with zero continuous animations.
 */
class PremiumOnboardingVisual @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var page = 0

    private val primary: Int
        get() = MaterialColors.getColor(
            this, R.attr.themePrimaryColor, Color.rgb(28, 117, 219)
        )

    private val light: Int
        get() = MaterialColors.getColor(
            this, R.attr.themeLightColor, Color.rgb(229, 243, 255)
        )

    private val ink = Color.rgb(20, 40, 74)
    private val cyan = Color.rgb(45, 201, 247)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setPage(index: Int) {
        val clamped = index.coerceIn(0, 2)
        if (page == clamped) return
        page = clamped
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scale = minOf(width / 320f, height / 320f)
        val save = canvas.save()

        canvas.translate(
            (width - 320f * scale) / 2f,
            (height - 320f * scale) / 2f
        )
        canvas.scale(scale, scale)

        drawStage(canvas)

        when (page) {
            0 -> drawRadar(canvas)
            1 -> drawProximity(canvas)
            2 -> drawHistory(canvas)
        }

        canvas.restoreToCount(save)
    }

    private fun drawStage(c: Canvas) {
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(
            0f, 0f, 320f, 320f,
            ColorUtils.blendARGB(light, Color.WHITE, 0.20f),
            ColorUtils.blendARGB(light, primary, 0.09f),
            Shader.TileMode.CLAMP
        )
        c.drawRoundRect(
            10f, 10f, 310f, 310f,
            34f, 34f, paint
        )
        paint.shader = null
    }

    private fun drawRadar(c: Canvas) {
        val centerX = 160f
        val centerY = 158f

        // Soft radial glow
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            centerX, centerY, 138f,
            intArrayOf(
                ColorUtils.setAlphaComponent(primary, 72),
                ColorUtils.setAlphaComponent(primary, 20),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )
        c.drawCircle(centerX, centerY, 138f, paint)
        paint.shader = null

        // Concentric radar rings
        for (radius in listOf(58f, 90f, 120f)) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.2f
            paint.color = ColorUtils.setAlphaComponent(primary, 85)
            c.drawCircle(centerX, centerY, radius, paint)
        }

        // Static outer radar ring
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = ColorUtils.setAlphaComponent(primary, 45)
        c.drawCircle(centerX, centerY, 134f, paint)

        // Center shadow and button
        drawCircleShadow(c, centerX, centerY, 48f)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            112f, 110f, 210f, 210f,
            ColorUtils.blendARGB(primary, cyan, 0.24f),
            primary,
            Shader.TileMode.CLAMP
        )
        c.drawCircle(centerX, centerY, 48f, paint)
        paint.shader = null

        // Bluetooth glyph
        paint.color = Color.WHITE
        paint.strokeWidth = 5f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE

        val path = Path().apply {
            moveTo(159f, 129f)
            lineTo(179f, 147f)
            lineTo(143f, 177f)
            moveTo(159f, 187f)
            lineTo(179f, 168f)
            lineTo(143f, 139f)
            moveTo(159f, 129f)
            lineTo(159f, 187f)
        }
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL

        // Four stationary floating-style device badges
        badge(c, 77f, 77f, 0)        // Headphones
        badge(c, 252f, 105f, 1)      // Tracker tag
        badge(c, 82f, 247f, 2)       // Smartwatch
        badge(c, 252f, 242f, 3)      // Laptop
    }

    private fun badge(
        c: Canvas,
        x: Float,
        y: Float,
        iconType: Int
    ) {
        val cy = y

        // White circular badge with subtle shadow
        drawCircleShadow(c, x, cy, 24f)

        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        c.drawCircle(x, cy, 24f, paint)

        // Draw blue icons using Canvas paths
        paint.color = primary
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        c.save()
        c.translate(x, cy)

        when (iconType) {
            0 -> {
                // Headphones
                c.drawArc(
                    -12f, -12f, 12f, 12f,
                    180f, 180f, false, paint
                )
                c.drawRoundRect(
                    -14f, -1f, -9f, 10f,
                    2f, 2f, paint
                )
                c.drawRoundRect(
                    9f, -1f, 14f, 10f,
                    2f, 2f, paint
                )
            }

            1 -> {
                // Tracker tag
                val tag = Path().apply {
                    moveTo(-13f, -10f)
                    lineTo(3f, -10f)
                    lineTo(13f, 0f)
                    lineTo(1f, 12f)
                    lineTo(-13f, -2f)
                    close()
                }
                c.drawPath(tag, paint)

                paint.style = Paint.Style.FILL
                c.drawCircle(4f, -4f, 2.5f, paint)
            }

            2 -> {
                // Smartwatch
                c.drawRoundRect(
                    -11f, -9f, 11f, 9f,
                    5f, 5f, paint
                )

                c.drawLine(-6f, -15f, -6f, -9f, paint)
                c.drawLine(6f, -15f, 6f, -9f, paint)
                c.drawLine(-6f, 9f, -6f, 15f, paint)
                c.drawLine(6f, 9f, 6f, 15f, paint)

                paint.style = Paint.Style.FILL
                c.drawCircle(0f, 0f, 2.5f, paint)
            }

            3 -> {
                // Laptop
                c.drawRoundRect(
                    -14f, -11f, 14f, 7f,
                    2f, 2f, paint
                )

                val base = Path().apply {
                    moveTo(-17f, 11f)
                    lineTo(17f, 11f)
                    lineTo(13f, 15f)
                    lineTo(-13f, 15f)
                    close()
                }
                c.drawPath(base, paint)
            }
        }

        c.restore()

        paint.style = Paint.Style.FILL
        paint.clearShadowLayer()
    }


    private fun drawProximity(c: Canvas) {
        val cx = 160f
        val cy = 127f
        val radius = 86f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 17f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = ColorUtils.setAlphaComponent(primary, 40)
        c.drawCircle(cx, cy, radius, paint)

        // Gradient circular arc
        paint.shader = SweepGradient(
            cx, cy,
            intArrayOf(
                cyan,
                primary,
                ColorUtils.blendARGB(primary, cyan, 0.3f),
                cyan
            ),
            null
        )


        val staticSweep = 270f

        c.drawArc(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius,
            -90f,
            staticSweep,
            false,
            paint
        )

        paint.shader = null
        paint.style = Paint.Style.FILL

        // Static arc endpoint
        paint.color = cyan
        c.drawCircle(cx - radius, cy, 8f, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(
            Typeface.DEFAULT, Typeface.BOLD
        )
        paint.color = ink
        paint.textSize = 35f
        c.drawText(context.getString(R.string.onboarding_sample_distance), cx, cy + 8f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 11.5f
        paint.color = ColorUtils.blendARGB(ink, Color.GRAY, 0.45f)
        c.drawText(context.getString(R.string.onboarding_estimated_proximity), cx, cy + 31f, paint)

        // Device card shadow (hardware-accelerated)
        drawRoundRectShadow(c, 29f, 242f, 291f, 302f, 18f)
        paint.color = Color.WHITE
        c.drawRoundRect(
            29f, 242f, 291f, 302f,
            18f, 18f, paint
        )

        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val badgeLeft = if (isRtl) 236f else 42f
        val badgeRight = if (isRtl) 278f else 84f
        val badgeCenterX = (badgeLeft + badgeRight) / 2f

        paint.color = primary
        c.drawRoundRect(
            badgeLeft, 252f, badgeRight, 292f,
            12f, 12f, paint
        )

        // Crisp vector Bluetooth glyph inside badge
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val btBadgePath = Path().apply {
            moveTo(badgeCenterX, 260f)
            lineTo(badgeCenterX + 8f, 267f)
            lineTo(badgeCenterX - 6f, 279f)
            moveTo(badgeCenterX, 284f)
            lineTo(badgeCenterX + 8f, 277f)
            lineTo(badgeCenterX - 6f, 265f)
            moveTo(badgeCenterX, 260f)
            lineTo(badgeCenterX, 284f)
        }
        c.drawPath(btBadgePath, paint)
        paint.style = Paint.Style.FILL

        val textX = if (isRtl) 226f else 94f
        paint.textAlign = if (isRtl) Paint.Align.RIGHT else Paint.Align.LEFT
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = ink
        paint.textSize = 15f
        c.drawText(context.getString(R.string.onboarding_sample_device), textX, 265f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 12f
        paint.color = Color.DKGRAY
        c.drawText(context.getString(R.string.onboarding_sample_rssi), textX, 282f, paint)

        // Signal strength bars
        paint.color = Color.rgb(47, 192, 99)
        val heights = listOf(9f, 15f, 22f, 30f)
        val barsStartX = if (isRtl) 40f else 247f
        heights.forEachIndexed { index, h ->
            val x = barsStartX + index * 9f
            c.drawRoundRect(
                x, 284f - h, x + 6f, 284f,
                3f, 3f, paint
            )
        }
    }

    private fun drawHistory(c: Canvas) {
        // Clip map illustration to the rounded stage
        val clip = Path().apply {
            addRoundRect(
                10f, 10f, 310f, 310f,
                34f, 34f, Path.Direction.CW
            )
        }
        c.save()
        c.clipPath(clip)

        // Parks
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(203, 234, 204)
        c.drawRoundRect(15f, 22f, 94f, 91f, 18f, 18f, paint)
        c.drawRoundRect(240f, 18f, 335f, 96f, 18f, 18f, paint)
        c.drawRoundRect(10f, 215f, 88f, 300f, 18f, 18f, paint)

        // Streets
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 18f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.WHITE

        val roads = listOf(
            floatArrayOf(25f, 85f, 285f, 85f),
            floatArrayOf(25f, 175f, 290f, 175f),
            floatArrayOf(90f, 20f, 90f, 310f),
            floatArrayOf(226f, 10f, 226f, 310f)
        )

        roads.forEach {
            c.drawLine(it[0], it[1], it[2], it[3], paint)
        }

        // River / highlighted route
        paint.color = Color.rgb(120, 198, 251)
        paint.strokeWidth = 13f
        val river = Path().apply {
            moveTo(-15f, 265f)
            cubicTo(70f, 240f, 90f, 150f, 170f, 132f)
            cubicTo(240f, 118f, 260f, 55f, 330f, 28f)
        }
        c.drawPath(river, paint)
        paint.style = Paint.Style.FILL

        // Static location ripple
        paint.color = ColorUtils.setAlphaComponent(primary, 28)
        c.drawCircle(166f, 135f, 52f, paint)

        paint.color = ColorUtils.setAlphaComponent(primary, 60)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        c.drawCircle(166f, 135f, 40f, paint)
        paint.style = Paint.Style.FILL

        // Blue map pin
        paint.color = primary
        val pin = Path().apply {
            moveTo(166f, 170f)
            cubicTo(151f, 150f, 143f, 139f, 143f, 123f)
            cubicTo(143f, 93f, 189f, 93f, 189f, 123f)
            cubicTo(189f, 139f, 181f, 150f, 166f, 170f)
            close()
        }
        c.drawPath(pin, paint)

        paint.color = Color.WHITE
        c.drawCircle(166f, 123f, 10f, paint)

        c.restore()

        // Floating history card (hardware-accelerated shadow)
        drawRoundRectShadow(c, 24f, 229f, 296f, 296f, 18f)
        paint.color = Color.WHITE
        c.drawRoundRect(
            24f, 229f, 296f, 296f,
            18f, 18f, paint
        )

        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val iconCenterX = if (isRtl) 267f else 53f
        val textX = if (isRtl) 239f else 81f
        val chevronX = if (isRtl) 46f else 271f

        paint.color = ColorUtils.setAlphaComponent(primary, 22)
        c.drawCircle(iconCenterX, 262f, 18f, paint)

        paint.color = primary
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        c.drawCircle(iconCenterX, 262f, 10f, paint)
        c.drawLine(iconCenterX, 262f, iconCenterX, 255f, paint)
        c.drawLine(iconCenterX, 262f, iconCenterX + 6f, 266f, paint)
        paint.style = Paint.Style.FILL

        paint.textAlign = if (isRtl) Paint.Align.RIGHT else Paint.Align.LEFT
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 14f
        paint.color = ink
        c.drawText(context.getString(R.string.onboarding_scan_recorded), textX, 258f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 12f
        paint.color = Color.DKGRAY
        c.drawText(context.getString(R.string.onboarding_sample_location), textX, 277f, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 22f
        paint.color = primary
        c.drawText(if (isRtl) "‹" else "›", chevronX, 270f, paint)
    }

    private fun drawCircleShadow(
        c: Canvas,
        x: Float,
        y: Float,
        radius: Float
    ) {
        paint.style = Paint.Style.FILL
        paint.color = ColorUtils.setAlphaComponent(ink, 14)
        c.drawCircle(x, y + 4f, radius + 3f, paint)
        paint.color = ColorUtils.setAlphaComponent(ink, 24)
        c.drawCircle(x, y + 2.5f, radius + 1f, paint)
    }

    private fun drawRoundRectShadow(
        c: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float
    ) {
        paint.style = Paint.Style.FILL
        paint.color = ColorUtils.setAlphaComponent(ink, 12)
        c.drawRoundRect(left - 1f, top + 4f, right + 1f, bottom + 6f, radius + 1f, radius + 1f, paint)
        paint.color = ColorUtils.setAlphaComponent(ink, 22)
        c.drawRoundRect(left, top + 2f, right, bottom + 3f, radius, radius, paint)
    }
}
