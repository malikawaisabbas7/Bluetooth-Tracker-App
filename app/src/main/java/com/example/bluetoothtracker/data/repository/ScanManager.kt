package com.example.bluetoothtracker.data.repository

import com.example.bluetoothtracker.data.location.LocationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide owner of the running scan, so it survives screen changes (Home -> Device Info).
 * UI never starts BLE directly; it asks this class.
 */
@Singleton
class ScanManager @Inject constructor(
    private val repository: DeviceRepository,
    private val locationProvider: LocationProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scanMutex = Mutex()
    private var job: Job? = null
    private var isBackgroundScan = false

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** Lists show devices seen since this moment. Starts at app launch so old rows are hidden. */
    private val _sessionStart = MutableStateFlow(System.currentTimeMillis())
    val sessionStart: StateFlow<Long> = _sessionStart.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** Returns the scan job to its owner, or null if an existing scan takes priority. */
    @Synchronized
    fun start(durationMs: Long, newSession: Boolean, background: Boolean = false): Job? {
        val previousJob = job
        if (previousJob?.isActive == true && (background || !isBackgroundScan)) return null
        previousJob?.cancel()
        if (newSession) _sessionStart.value = System.currentTimeMillis()

        isBackgroundScan = background
        _isScanning.value = !background

        val newJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                scanMutex.withLock {
                    try {
                        locationProvider.start()
                        withTimeoutOrNull(durationMs) { repository.scan(background).collect {} }
                    } finally {
                        locationProvider.stop()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.tryEmit(e.message ?: "unknown")
            }
        }
        job = newJob
        newJob.invokeOnCompletion { onScanFinished(newJob) }
        newJob.start()
        return newJob
    }

    @Synchronized
    private fun onScanFinished(finishedJob: Job) {
        // Only clean up if no newer scan replaced this one
        if (job === finishedJob) {
            job = null
            isBackgroundScan = false
            _isScanning.value = false
        }
    }

    @Synchronized
    fun stop() {
        if (!isBackgroundScan) job?.cancel()
    }
}
