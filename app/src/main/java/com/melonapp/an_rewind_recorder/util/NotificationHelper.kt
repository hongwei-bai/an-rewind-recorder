package com.melonapp.an_rewind_recorder.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.melonapp.an_rewind_recorder.MainActivity
import com.melonapp.an_rewind_recorder.R
import com.melonapp.an_rewind_recorder.service.BacktrackService

object NotificationHelper {

    const val CHANNEL_ID = "backtrack_recording_channel"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Backtrack Audio Recorder"
            val descriptionText = "Persistent notification while retroactive audio buffer is listening"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildForegroundNotification(
        context: Context,
        isPaused: Boolean = false,
        bufferedDurationText: String = "00:00 / 10:00"
    ): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val save5Intent = Intent(context, BacktrackService::class.java).apply {
            action = BacktrackService.ACTION_SAVE_5
        }
        val save5PendingIntent = PendingIntent.getService(
            context,
            1,
            save5Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val save10Intent = Intent(context, BacktrackService::class.java).apply {
            action = BacktrackService.ACTION_SAVE_10
        }
        val save10PendingIntent = PendingIntent.getService(
            context,
            2,
            save10Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, BacktrackService::class.java).apply {
            action = BacktrackService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            3,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isPaused) {
            "Backtrack: Paused (Call / Focus Lost)"
        } else {
            "Backtrack: Listening..."
        }

        val content = if (isPaused) {
            "Recording paused temporarily. Will resume automatically."
        } else {
            "RAM Buffer: $bufferedDurationText • Tap to capture"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_save, "Save 5 Min", save5PendingIntent)
            .addAction(android.R.drawable.ic_menu_save, "Save 10 Min", save10PendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }
}
