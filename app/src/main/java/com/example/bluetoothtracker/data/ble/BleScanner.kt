package com.example.bluetoothtracker.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.example.bluetoothtracker.core.PermissionHelper
import com.example.bluetoothtracker.domain.logic.DeviceClassifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps BluetoothLeScanner as a cold Flow. Collecting starts the scan; cancelling stops it. */
@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * @param background true for the Background Tracking service. Android (8.1+) delivers NO
     * results to an unfiltered scan while the screen is off, so background scans only look for
     * tracker signatures (Apple Find My + known tag service UUIDs) using hardware filters.
     */
    @SuppressLint("MissingPermission") // permissions are verified via PermissionHelper below
    fun scan(background: Boolean = false): Flow<ScanResult> = callbackFlow {
        if (!PermissionHelper.hasBleScanPermissions(context)) {
            close(SecurityException("Bluetooth scan permission not granted"))
            return@callbackFlow
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val scanner = if (adapter?.isEnabled == true) adapter.bluetoothLeScanner else null
        if (scanner == null) {
            close(IllegalStateException("Bluetooth is unavailable or turned off"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { trySend(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("error code $errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        try {
            scanner.startScan(if (background) trackerFilters() else null, settings, callback)
        } catch (se: SecurityException) {
            close(se)
            return@callbackFlow
        } catch (e: IllegalStateException) {
            close(e)
            return@callbackFlow
        }
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    private fun trackerFilters(): List<ScanFilter> {
        val filters = mutableListOf<ScanFilter>()
        // Apple Offline Finding (AirTag): company 0x004C, first payload byte 0x12
        filters += ScanFilter.Builder()
            .setManufacturerData(
                DeviceClassifier.APPLE_COMPANY_ID,
                byteArrayOf(DeviceClassifier.APPLE_FIND_MY_TYPE.toByte()),
                byteArrayOf(0xFF.toByte())
            )
            .build()
        DeviceClassifier.TAG_SERVICE_UUIDS.forEach { uuid ->
            filters += ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(uuid)).build()
        }
        return filters
    }
}
