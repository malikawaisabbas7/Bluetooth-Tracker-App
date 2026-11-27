package com.example.bluetoothtracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DeviceDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(device: DeviceEntity)

    // Keeps firstSeenAt and isIgnored. A device's category/company only upgrade, never fall back to
    // "generic", because one device sends several advertisement types.
    @Query(
        """UPDATE devices SET
            name = COALESCE(:name, name),
            companyId = COALESCE(:companyId, companyId),
            companyName = COALESCE(:companyName, companyName),
            category = CASE WHEN :category != 'BLUETOOTH_DEVICE' THEN :category ELSE category END,
            rssi = :rssi,
            bondState = :bondState,
            lastSeenAt = :lastSeenAt
        WHERE mac = :mac"""
    )
    abstract suspend fun updateSeen(
        mac: String, name: String?, companyId: Int?, companyName: String?,
        category: String, rssi: Int, bondState: Int, lastSeenAt: Long
    )

    @Transaction
    open suspend fun upsert(d: DeviceEntity) {
        insertIgnore(d)
        updateSeen(d.mac, d.name, d.companyId, d.companyName, d.category, d.rssi, d.bondState, d.lastSeenAt)
    }

    /** Only devices seen since [since] (the start of the current scan session). */
    @Query(
        """SELECT * FROM devices
        WHERE category = :category AND isIgnored = 0 AND lastSeenAt >= :since
        ORDER BY rssi DESC"""
    )
    abstract fun observeByCategory(category: String, since: Long): Flow<List<DeviceEntity>>

    @Query(
        """SELECT * FROM devices
        WHERE category IN ('AIR_TAG', 'OTHER_TAG') AND isIgnored = 0 AND lastSeenAt >= :since
        ORDER BY rssi DESC"""
    )
    abstract fun observeTags(since: Long): Flow<List<DeviceEntity>>

    @Query(
        """SELECT COUNT(*) FROM devices
        WHERE category = :category AND isIgnored = 0 AND lastSeenAt >= :since"""
    )
    abstract fun observeCount(category: String, since: Long): Flow<Int>

    @Query(
        """SELECT COUNT(*) FROM devices
        WHERE category IN ('AIR_TAG', 'OTHER_TAG') AND isIgnored = 0 AND lastSeenAt >= :since"""
    )
    abstract fun observeTagsCount(since: Long): Flow<Int>

    @Query("SELECT * FROM devices WHERE mac = :mac")
    abstract fun observeByMac(mac: String): Flow<DeviceEntity?>

    @Query("UPDATE devices SET isIgnored = :ignored WHERE mac = :mac")
    abstract suspend fun setIgnored(mac: String, ignored: Boolean)

    /**
     * Tags seen recently whose sightings span at least [minSpan] ms inside the window starting
     * at [since]: i.e. a tracker that has stayed around you for a long time.
     */
    @Query(
        """SELECT * FROM devices
        WHERE isIgnored = 0
          AND category IN ('AIR_TAG', 'OTHER_TAG')
          AND lastSeenAt BETWEEN :recent AND :now
          AND mac IN (
              SELECT mac FROM sightings WHERE timestamp BETWEEN :since AND :now
              GROUP BY mac HAVING MAX(timestamp) - MIN(timestamp) >= :minSpan
          )"""
    )
    abstract suspend fun findFollowing(since: Long, minSpan: Long, recent: Long, now: Long): List<DeviceEntity>
}
