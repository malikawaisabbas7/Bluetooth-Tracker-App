package com.example.bluetoothtracker.core

object Constants {
    const val SUPPORT_URL = "https://github.com/malikawaisabbas7/Bluetooth-Tracker-App/issues/new"
    const val DEFAULT_SCAN_DURATION_MS = 25_000L
    const val DETAIL_SCAN_DURATION_MS = 5 * 60_000L
    const val SPLASH_DURATION_MS = 3_600L
    const val STALE_AFTER_MS = 10_000L
    const val SIGHTING_INTERVAL_MS = 10_000L
    const val MAX_LOCATION_AGE_MS = 2 * 60_000L
    const val VERY_NEAR_BELOW_M = 1.5
    const val NEAR_BELOW_M = 5.0

    const val PRIVACY_URL = "https://github.com/malikawaisabbas7/Bluetooth-Tracker-App/blob/main/docs/privacy-policy.html"
    const val TERMS_URL = "https://play.google.com/about/play-terms/"

    // These IDs must exactly match your activated Google Play subscription/base plan.
    const val PRODUCT_ID_PREMIUM = "premium_weekly"
    const val BASE_PLAN_ID_PREMIUM = "weekly"

    const val BACKGROUND_SCAN_MS = 15_000L
    const val BACKGROUND_INTERVAL_MS = 2 * 60_000L
    const val FOLLOW_MIN_SPAN_MS = 20 * 60_000L
    const val FOLLOW_WINDOW_MS = 3 * 60 * 60_000L
    const val FOLLOW_RECENT_MS = 10 * 60_000L
    const val ALERT_COOLDOWN_MS = 6 * 60 * 60_000L
}
