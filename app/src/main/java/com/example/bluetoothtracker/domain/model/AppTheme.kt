
package com.example.bluetoothtracker.domain.model

import androidx.annotation.StringRes
import com.example.bluetoothtracker.R

enum class AppTheme(
    val displayName: String,
    @StringRes val labelRes: Int,
    val primaryColor: Long,
    val lightColor: Long,
    val themeResId: Int
) {
    BLUE(
        displayName = "Blue",
        labelRes = R.string.theme_blue,
        primaryColor = 0xFF1976D2,
        lightColor = 0xFFE3F2FD,
        themeResId = R.style.Theme_BluetoothTracker_Blue
    ),

    ORANGE(
        displayName = "Orange",
        labelRes = R.string.theme_orange,
        primaryColor = 0xFFF57C00,
        lightColor = 0xFFFFF3E0,
        themeResId = R.style.Theme_BluetoothTracker_Orange
    ),

    BLACK(
        displayName = "Black",
        labelRes = R.string.theme_black,
        primaryColor = 0xFF212121,
        lightColor = 0xFFEEEEEE,
        themeResId = R.style.Theme_BluetoothTracker_Black
    ),

    GREEN(
        displayName = "Green",
        labelRes = R.string.theme_green,
        primaryColor = 0xFF2E7D32,
        lightColor = 0xFFE8F5E9,
        themeResId = R.style.Theme_BluetoothTracker_Green
    )
}
