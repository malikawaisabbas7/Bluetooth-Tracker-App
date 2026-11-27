package com.example.bluetoothtracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SightingDao {
    @Insert
    suspend fun insert(sighting: SightingEntity)

    @Query("SELECT * FROM sightings WHERE mac = :mac ORDER BY timestamp DESC LIMIT 200")
    fun observeForDevice(mac: String): Flow<List<SightingEntity>>

    @Query("SELECT * FROM sightings WHERE mac = :mac ORDER BY timestamp DESC LIMIT 200")
    suspend fun getForDeviceOnce(mac: String): List<SightingEntity>
}
