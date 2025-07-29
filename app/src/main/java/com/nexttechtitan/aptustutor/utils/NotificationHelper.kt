package com.nexttechtitan.aptustutor.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.nexttechtitan.aptustutor.R

/**
 * A helper class that encapsulates all logic for creating and managing
 * Android notifications for the application, such as download progress.
 */
class NotificationHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "aptus_tutor_downloads"
        const val CHANNEL_NAME = "Model Downloads"
    }

    /**
     * Creates (but does not display) a notification builder for a foreground service.
     * Used to show ongoing download progress.
     */
    fun createNotification(
        title: String,
        contentText: String,
        progress: Int = 100,
        progressCurrent: Int = 0,
        progressIndeterminate: Boolean = true,
    ): NotificationCompat.Builder {
        createNotificationChannel()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(progress, progressCurrent, progressIndeterminate)
            .setOngoing(true)
    }

    /** Displays a final, non-ongoing notification for download completion or failure. */
    fun showDownloadCompleteNotification(notificationId: Int, title: String, contentText: String) {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    fun notify(id: Int, notificationBuilder: NotificationCompat.Builder) {
        try {
            notificationManager.notify(id, notificationBuilder.build())
        } catch (e: Exception) {
            //
        }
    }

    /**
     * Creates the required notification channel for Android 8.0 (Oreo) and above.
     * This must be called before any notifications can be shown on modern devices.
     */
    private fun createNotificationChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for model download progress and completion notifications"
            }
            notificationManager.createNotificationChannel(channel)
        } else {
            //
        }
    }
}