package com.example.bluetoothtracker.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import java.util.concurrent.atomic.AtomicInteger

/** Debug-only lifecycle and transition logger for diagnosing startup and recreation flashes. */
object SplashDebugThemeDiag {
    private val recreationCounter = AtomicInteger(0)

    fun nextRecreationCount(): Int = recreationCounter.incrementAndGet()

    fun currentRecreationCount(): Int = recreationCounter.get()

    fun describeWindowBackground(context: Context): String {
        val tv = TypedValue()
        val resolved = context.theme.resolveAttribute(android.R.attr.windowBackground, tv, true)
        if (!resolved) return "unresolved"
        return if (tv.resourceId != 0) {
            runCatching { context.resources.getResourceEntryName(tv.resourceId) }.getOrDefault("res#${tv.resourceId}")
        } else {
            "color=0x${Integer.toHexString(tv.data)}"
        }
    }

    fun animationScales(context: Context): String {
        val cr = context.contentResolver
        val win = runCatching { Settings.Global.getFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, 1f) }.getOrDefault(1f)
        val trans = runCatching { Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) }.getOrDefault(1f)
        val anim = runCatching { Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
        return "win=$win,trans=$trans,anim=$anim"
    }
}

object ThemeTransitionDebug {
    private const val TAG = "ThemeTransitionDebug"

    fun log(context: Context?, message: String) {
        val ctx = context ?: return
        val isDebuggable = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            Log.d(TAG, "[pid=${Process.myPid()} t=${System.currentTimeMillis() % 100_000}] $message")
        }
    }
}
