package com.example.bluetoothtracker.domain.logic

import android.graphics.Color
import com.example.bluetoothtracker.domain.model.DistanceUnit
import java.util.Locale

object DistanceFormatter {

    private const val FEET_PER_METER = 3.28084

    private const val NEAR_LIMIT = 3.5
    private const val MEDIUM_LIMIT = 8.65

    fun format(
        meters: Double,
        unit: DistanceUnit
    ): String = when (unit) {

        DistanceUnit.METER ->
            String.format(
                Locale.getDefault(),
                "%.2f meter",
                meters
            )

        DistanceUnit.FOOT ->
            String.format(
                Locale.getDefault(),
                "%.2f ft",
                meters * FEET_PER_METER
            )
    }

    fun getDistanceColor(meters: Double): Int {
        return when {
            meters < NEAR_LIMIT ->
                Color.parseColor("#E53935") // Red

            meters <= MEDIUM_LIMIT ->
                Color.parseColor("#FB8C00") // Orange

            else ->
                Color.parseColor("#43A047") // Green
        }
    }
}
