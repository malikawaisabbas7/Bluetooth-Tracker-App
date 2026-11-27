package com.example.bluetoothtracker.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.bluetoothtracker.core.Constants
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the phone's latest location while a scan is running, so each sighting can be saved
 * with "where the phone was". Started and stopped by ScanManager.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @Volatile
    private var latest: Location? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { latest = it }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
                .setMinUpdateIntervalMillis(2_000L)
                .build()
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            client.lastLocation.addOnSuccessListener { if (latest == null) latest = it }
        } catch (e: SecurityException) {
            // permission revoked while running: sightings are simply saved without location
        }
    }

    fun stop() {
        client.removeLocationUpdates(callback)
    }

    /** Latest fix, or null if there is none or it is too old to trust. */
    fun currentOrNull(): Location? {
        val loc = latest ?: return null
        return if (System.currentTimeMillis() - loc.time <= Constants.MAX_LOCATION_AGE_MS) loc else null
    }
}
