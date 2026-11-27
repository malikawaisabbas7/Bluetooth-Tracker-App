package com.example.bluetoothtracker.core

import android.content.Context
import java.util.concurrent.atomic.AtomicInteger

/**
 * Coordinates one-time navigation back to Settings across AppCompat locale recreation,
 * avoiding redundant Settings inflation in the old Activity before recreation.
 */
object PendingLanguageReturn {
    private const val PREFS_NAME = "nav_pending"
    private const val KEY_RETURN_TO_SETTINGS = "pending_return_to_settings"
    private val requestCounter = AtomicInteger(0)

    @Volatile
    private var inMemoryPending = false

    fun nextRequestId(): Int = requestCounter.incrementAndGet()

    fun markPending(context: Context) {
        inMemoryPending = true
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RETURN_TO_SETTINGS, true)
            .commit()
    }

    fun isPending(context: Context): Boolean {
        if (inMemoryPending) return true
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_RETURN_TO_SETTINGS, false)
    }

    fun consumePending(context: Context): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wasPending = inMemoryPending || prefs.getBoolean(KEY_RETURN_TO_SETTINGS, false)
        if (!wasPending) return false
        inMemoryPending = false
        prefs.edit().remove(KEY_RETURN_TO_SETTINGS).commit()
        return true
    }

    fun clear(context: Context) {
        inMemoryPending = false
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_RETURN_TO_SETTINGS)
            .apply()
    }

    /** Pure-Kotlin state helper for unit testing one-time consumption. */
    class OneTimeFlag(initial: Boolean = false) {
        private var pending = initial

        fun mark() {
            pending = true
        }

        fun consume(): Boolean {
            if (!pending) return false
            pending = false
            return true
        }
    }
}
