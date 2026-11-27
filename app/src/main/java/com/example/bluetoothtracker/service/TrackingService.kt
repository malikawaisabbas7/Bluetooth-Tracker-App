package com.example.bluetoothtracker.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.core.Constants
import com.example.bluetoothtracker.core.PermissionHelper
import com.example.bluetoothtracker.data.repository.DeviceRepository
import com.example.bluetoothtracker.data.repository.ScanManager
import com.example.bluetoothtracker.data.settings.SettingsRepository
import com.example.bluetoothtracker.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Periodically scans in a foreground service and checks for following trackers. */
@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var scanManager: ScanManager
    @Inject lateinit var repository: DeviceRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val foregroundTypes = resolveForegroundServiceTypes()
            ServiceCompat.startForeground(
                this,
                NotificationHelper.TRACKING_ID,
                NotificationHelper.trackingNotification(this),
                foregroundTypes
            )
            _lastStartError.value = null
            _isRunning.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground tracking service", e)
            _lastStartError.value = e.message ?: "Failed to start tracking service"
            _isRunning.value = false
            stopSelf()
            return START_NOT_STICKY
        }

        if (loop?.isActive != true) {
            loop = scope.launch { runLoop() }
        }
        return START_STICKY
    }

    private fun resolveForegroundServiceTypes(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var types = 0
        val hasConnectedDevicePerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val hasLocationPerm = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (hasConnectedDevicePerm) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        if (hasLocationPerm) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        return types
    }

    private suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                if (!settingsRepository.settings.first().backgroundTracking) {
                    stopSelf()
                    return
                }
                if (PermissionHelper.hasBleScanPermissions(this) &&
                    PermissionHelper.isBluetoothEnabled(this)
                ) {
                    val scan = scanManager.start(
                        Constants.BACKGROUND_SCAN_MS, newSession = false, background = true
                    )
                    if (scan != null) {
                        try {
                            scan.join()
                        } finally {
                            scan.cancel()
                        }
                        if (settingsRepository.settings.first().backgroundTracking) checkForFollowers()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Background scan cycle failed", e)
            }
            delay(Constants.BACKGROUND_INTERVAL_MS)
        }
    }

    private suspend fun checkForFollowers() {
        val now = System.currentTimeMillis()
        val prefs = getSharedPreferences("alerts", Context.MODE_PRIVATE)
        repository.findSuspiciousTrackers(now).forEach { (device, summary) ->
            currentCoroutineContext().ensureActive()
            val lastAlert = prefs.getLong(device.mac, 0L)
            if (now - lastAlert >= Constants.ALERT_COOLDOWN_MS &&
                NotificationHelper.notifyTrackerFollowing(
                    this,
                    device.mac,
                    device.name,
                    summary.distinctLocationCount
                )
            ) {
                prefs.edit().putLong(device.mac, now).apply()
            }
        }
    }

    override fun onDestroy() {
        _isRunning.value = false
        loop?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "TrackingService"
        const val ACTION_OPEN_HOME = "com.example.bluetoothtracker.OPEN_HOME"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _lastStartError = MutableStateFlow<String?>(null)
        val lastStartError: StateFlow<String?> = _lastStartError.asStateFlow()

        fun start(context: Context) {
            if (_isRunning.value) return
            _lastStartError.value = null
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java)
            )
        }

        fun stop(context: Context) {
            _isRunning.value = false
            context.stopService(Intent(context, TrackingService::class.java))
        }
    }
}
