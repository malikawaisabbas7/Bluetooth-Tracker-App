package com.example.bluetoothtracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.bluetoothtracker.data.repository.DeviceRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Handles notification actions (such as ignoring a flagged tracker) without opening the UI. */
@AndroidEntryPoint
class TrackerAlertReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: DeviceRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_IGNORE_TRACKER) return
        val mac = intent.getStringExtra(EXTRA_MAC)?.takeIf { it.isNotBlank() } ?: return
        NotificationManagerCompat.from(context).cancel(mac.hashCode())
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.setIgnored(mac, true)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_IGNORE_TRACKER = "com.example.bluetoothtracker.IGNORE_TRACKER"
        const val EXTRA_MAC = "extra_mac"
    }
}
