package com.example.bluetoothtracker.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/** Debug-only logger for diagnosing Splash startup, animation timeline, and routing. */
object SplashDebug {
    const val TAG = "SplashDebug"

    fun log(context: Context?, message: String) {
        val ctx = context ?: return
        val isDebuggable = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            Log.d(TAG, "[ts=${System.currentTimeMillis()}] $message")
        }
    }
}
