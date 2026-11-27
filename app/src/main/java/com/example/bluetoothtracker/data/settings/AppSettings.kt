
package com.example.bluetoothtracker.data.settings

import com.example.bluetoothtracker.core.AppLanguage
import com.example.bluetoothtracker.core.Constants
import com.example.bluetoothtracker.domain.model.AppTheme
import com.example.bluetoothtracker.domain.model.DistanceUnit

data class AppSettings(
    val backgroundTracking: Boolean = false,
    val unit: DistanceUnit = DistanceUnit.METER,
    val scanDurationMs: Long = Constants.DEFAULT_SCAN_DURATION_MS,
    val appTheme: AppTheme = AppTheme.BLUE,
    val languageTag: String = AppLanguage.DEFAULT.tag,
    val languageSelected: Boolean = false,
    val onboardingCompleted: Boolean = false
)
