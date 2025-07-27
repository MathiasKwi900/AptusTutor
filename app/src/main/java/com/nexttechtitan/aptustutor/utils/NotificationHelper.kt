package com.nexttechtitan.aptustutor.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nexttechtitan.aptustutor.R

class NotificationHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "aptus_tutor_downloads"
        const val CHANNEL_NAME = "Model Downloads"
        private const val TAG = "AptusTutorDebug"
    }

    fun createNotification(
        title: String,
        contentText: String,
        progress: Int = 100,
        progressCurrent: Int = 0,
        progressIndeterminate: Boolean = true,
    ): NotificationCompat.Builder {
        Log.d(TAG, "Creating notification: Title='$title', Content='$contentText', ProgressIndeterminate=$progressIndeterminate")
        createNotificationChannel()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure you have this drawable
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(progress, progressCurrent, progressIndeterminate)
            .setOngoing(true)
    }

    fun showDownloadCompleteNotification(notificationId: Int, title: String, contentText: String) {
        Log.d(TAG, "Showing download complete notification: ID=$notificationId, Title='$title'")
        createNotificationChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Notification $notificationId (Complete) displayed.")
    }

    fun notify(id: Int, notificationBuilder: NotificationCompat.Builder) {
        Log.d(TAG, "Notifying with ID: $id")
        try {
            notificationManager.notify(id, notificationBuilder.build())
            Log.d(TAG, "Notification $id displayed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying notification $id: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            Log.d(TAG, "Creating notification channel: ID='$CHANNEL_ID', Name='$CHANNEL_NAME'")
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for model download progress and completion notifications"
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel '$CHANNEL_ID' created.")
        } else {
            Log.d(TAG, "Notification channel '$CHANNEL_ID' already exists.")
        }
    }
}