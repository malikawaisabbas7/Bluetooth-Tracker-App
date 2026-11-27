package com.example.bluetoothtracker

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Trace
import com.example.bluetoothtracker.core.SplashDebug
import com.example.bluetoothtracker.data.billing.BillingManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {

    @Inject
    lateinit var billingManager: dagger.Lazy<BillingManager>

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val billingRefreshed = AtomicBoolean(false)

    override fun attachBaseContext(base: Context) {
        Trace.beginSection("App.attachBaseContext")
        processAttachElapsedMs = SystemClock.elapsedRealtime()
        super.attachBaseContext(base)
        Trace.endSection()
    }

    override fun onCreate() {
        Trace.beginSection("App.onCreate")
        val startMs = SystemClock.elapsedRealtime()
        super.onCreate()

        // Start async disk load of theme_cache early without blocking the main thread.
        getSharedPreferences("theme_cache", MODE_PRIVATE)

        appScope.launch {
            initializeOpenStreetMap()
        }

        val elapsedMs = SystemClock.elapsedRealtime() - startMs
        val sinceAttachMs = SystemClock.elapsedRealtime() - processAttachElapsedMs
        SplashDebug.log(
            this,
            "App.onCreate mainThreadMs=$elapsedMs sinceAttachMs=$sinceAttachMs pid=${android.os.Process.myPid()}"
        )
        Trace.endSection()
    }

    /** Defers BillingClient creation and Play connection until after the first UI frame. */
    fun onFirstFrameDrawn() {
        if (billingRefreshed.compareAndSet(false, true)) {
            Handler(Looper.getMainLooper()).post {
                billingManager.get().refreshAsync()
            }
        }
    }

    fun refreshBillingOnResume() {
        if (billingRefreshed.get()) {
            billingManager.get().refreshAsync()
        }
    }

    companion object {
        @Volatile
        var processAttachElapsedMs: Long = 0L
            private set
    }

    private fun initializeOpenStreetMap() {
        val basePath = File(cacheDir, "osmdroid")
        val tileCachePath = File(basePath, "tiles")

        basePath.mkdirs()
        tileCachePath.mkdirs()

        Configuration.getInstance().apply {
            load(
                this@App,
                getSharedPreferences("osmdroid", MODE_PRIVATE)
            )

            // Stable, identifiable application User-Agent
            userAgentValue =
                "BluetoothTracker/1.0 ($packageName)"

            osmdroidBasePath = basePath
            osmdroidTileCache = tileCachePath

            // Avoid excessive simultaneous tile requests
            tileDownloadThreads = 2
        }
    }
}
