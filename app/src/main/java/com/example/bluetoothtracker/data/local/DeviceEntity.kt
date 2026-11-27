package com.example.bluetoothtracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val mac: String,
    val name: String?,
    val companyId: Int?,
    val companyName: String?,
    val category: String, // DeviceCategory.name
    val rssi: Int,
    val bondState: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val isIgnored: Boolean = false
)
