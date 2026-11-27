package com.example.bluetoothtracker.data.repository

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.example.bluetoothtracker.core.Constants
import com.example.bluetoothtracker.data.ble.BleScanner
import com.example.bluetoothtracker.data.local.DeviceDao
import com.example.bluetoothtracker.data.local.DeviceEntity
import com.example.bluetoothtracker.data.local.SightingDao
import com.example.bluetoothtracker.data.local.SightingEntity
import com.example.bluetoothtracker.data.location.LocationProvider
import com.example.bluetoothtracker.domain.logic.CompanyResolver
import com.example.bluetoothtracker.domain.logic.DeviceClassifier
import com.example.bluetoothtracker.domain.logic.DistanceCalculator
import com.example.bluetoothtracker.domain.logic.EncounterAnalyzer
import com.example.bluetoothtracker.domain.logic.EncounterSummary
import com.example.bluetoothtracker.domain.logic.TrackingWarningLevel
import com.example.bluetoothtracker.domain.model.DeviceCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: BleScanner,
    private val deviceDao: DeviceDao,
    private val sightingDao: SightingDao,
    private val locationProvider: LocationProvider
) {
    private val lastSightingAt = ConcurrentHashMap<String, Long>()

    /** Scans, stores each advertisement, and records throttled sightings with the phone location. */
    fun scan(background: Boolean = false): Flow<Unit> = scanner.scan(background).map { result ->
        val entity = result.toEntity()
        deviceDao.upsert(entity)
        recordSighting(entity)
    }

    private suspend fun recordSighting(device: DeviceEntity) {
        val location = locationProvider.currentOrNull()
        val last = lastSightingAt[device.mac] ?: 0L
        if (device.lastSeenAt - last < Constants.SIGHTING_INTERVAL_MS) return
        lastSightingAt[device.mac] = device.lastSeenAt
        if (lastSightingAt.size > MAX_TRACKED_THROTTLE_ENTRIES) {
            val cutoff = device.lastSeenAt - Constants.SIGHTING_INTERVAL_MS * 6
            lastSightingAt.entries.removeIf { it.value < cutoff }
        }
        sightingDao.insert(
            SightingEntity(
                mac = device.mac,
                timestamp = device.lastSeenAt,
                rssi = device.rssi,
                distanceM = DistanceCalculator.estimateMeters(device.rssi.toDouble()),
                latitude = location?.latitude ?: 0.0,
                longitude = location?.longitude ?: 0.0,
                hasLocation = location != null
            )
        )
    }

    fun observeDevices(category: DeviceCategory, since: Long): Flow<List<DeviceEntity>> =
        if (category == DeviceCategory.AIR_TAG) {
            deviceDao.observeTags(since)
        } else {
            deviceDao.observeByCategory(category.name, since)
        }

    fun observeCount(category: DeviceCategory, since: Long): Flow<Int> =
        if (category == DeviceCategory.AIR_TAG) {
            deviceDao.observeTagsCount(since)
        } else {
            deviceDao.observeCount(category.name, since)
        }

    fun observeDevice(mac: String): Flow<DeviceEntity?> = deviceDao.observeByMac(mac)

    fun observeSightings(mac: String): Flow<List<SightingEntity>> = sightingDao.observeForDevice(mac)

    suspend fun setIgnored(mac: String, ignored: Boolean) = deviceDao.setIgnored(mac, ignored)

    /** Trackers that have been around you for a long time (see Constants.FOLLOW_*). */
    suspend fun findFollowingTrackers(now: Long): List<DeviceEntity> = deviceDao.findFollowing(
        since = now - Constants.FOLLOW_WINDOW_MS,
        minSpan = Constants.FOLLOW_MIN_SPAN_MS,
        recent = now - Constants.FOLLOW_RECENT_MS
    )

    /**
     * Evaluates candidate tags with [EncounterAnalyzer] so alerts only fire when a supported
     * tag is observed repeatedly across multiple distinct locations.
     */
    suspend fun findSuspiciousTrackers(now: Long): List<Pair<DeviceEntity, EncounterSummary>> =
        findFollowingTrackers(now).mapNotNull { device ->
            val sightings = sightingDao.getForDeviceOnce(device.mac)
            val summary = EncounterAnalyzer.analyze(device, sightings, now)
            if (summary.warningLevel == TrackingWarningLevel.POTENTIAL_UNWANTED_TRACKING) {
                device to summary
            } else {
                null
            }
        }

    @SuppressLint("MissingPermission")
    private fun ScanResult.toEntity(): DeviceEntity {
        val record = scanRecord
        val manufacturer = record?.manufacturerSpecificData
        val applePayload = record?.getManufacturerSpecificData(DeviceClassifier.APPLE_COMPANY_ID)
        val companyId = when {
            applePayload != null -> DeviceClassifier.APPLE_COMPANY_ID
            manufacturer != null && manufacturer.size() > 0 -> manufacturer.keyAt(0)
            else -> null
        }
        val payload = applePayload ?: companyId?.let { manufacturer?.get(it) }
        // Some tags (e.g. Tile) put their UUID in service DATA rather than the service-UUID list
        val services = record?.serviceUuids?.map { it.uuid.toString() }.orEmpty() +
            record?.serviceData?.keys?.map { it.uuid.toString() }.orEmpty()
        val bond = try { device.bondState } catch (e: SecurityException) { 10 /* BOND_NONE */ }
        val now = System.currentTimeMillis()

        val category = DeviceClassifier.classify(companyId, payload, services)
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            val payloadHead = payload?.take(4)?.joinToString(" ") { "%02X".format(it) } ?: "none"
            Log.d(
                TAG_CLASSIFIER,
                "name=${record?.deviceName ?: "unknown"} companyId=${companyId?.let { "0x%04X".format(it) }} " +
                    "payloadLen=${payload?.size ?: 0} head=[$payloadHead] uuids=${services.size} -> $category"
            )
        }

        return DeviceEntity(
            mac = device.address,
            name = record?.deviceName,
            companyId = companyId,
            companyName = CompanyResolver.nameFor(companyId),
            category = category.name,
            rssi = rssi,
            bondState = bond,
            firstSeenAt = now,
            lastSeenAt = now
        )
    }

    private companion object {
        const val TAG_CLASSIFIER = "BleClassifier"
        const val MAX_TRACKED_THROTTLE_ENTRIES = 500
    }
}
