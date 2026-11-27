package com.example.bluetoothtracker.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.ui.MainActivity

object NotificationHelper {
    const val CHANNEL_TRACKING = "tracking"
    const val CHANNEL_ALERTS = "alerts"
    const val TRACKING_ID = 1001

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRACKING,
                context.getString(R.string.channel_tracking),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.channel_alerts),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** The ongoing notification Android requires while background tracking runs. */
    fun trackingNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentTitle(context.getString(R.string.notif_tracking_title))
            .setContentText(context.getString(R.string.notif_tracking_text))
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    @SuppressLint("MissingPermission") // areNotificationsEnabled() is checked first
    fun notifyTrackerFollowing(
        context: Context,
        mac: String,
        name: String?,
        distinctLocations: Int = 2
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val label = name?.takeIf { it.isNotBlank() } ?: mac
        val reason = context.getString(R.string.notif_alert_reason, label, distinctLocations.coerceAtLeast(1))
        val ignoreIntent = Intent(context, TrackerAlertReceiver::class.java).apply {
            action = TrackerAlertReceiver.ACTION_IGNORE_TRACKER
            data = Uri.fromParts("tracker", mac, null)
            putExtra(TrackerAlertReceiver.EXTRA_MAC, mac)
        }
        val ignorePendingIntent = PendingIntent.getBroadcast(
            context,
            mac.hashCode(),
            ignoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentTitle(context.getString(R.string.notif_alert_title))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(context))
            .addAction(0, context.getString(R.string.ignore_device_action), ignorePendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(mac.hashCode(), notification)
    }
}
