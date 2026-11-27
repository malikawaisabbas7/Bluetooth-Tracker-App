package com.example.bluetoothtracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One "I saw this device here" record: the PHONE's location at the time of detection when [hasLocation] is true. */
@Entity(tableName = "sightings", indices = [Index("mac")])
data class SightingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mac: String,
    val timestamp: Long,
    val rssi: Int,
    val distanceM: Double,
    val latitude: Double,
    val longitude: Double,
    val hasLocation: Boolean = true
)
