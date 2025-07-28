package com.nexttechtitan.aptustutor.ai

import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.storage.FirebaseStorage
import com.nexttechtitan.aptustutor.data.ModelStatus
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import com.nexttechtitan.aptustutor.utils.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "ModelDownloadWorker"

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userPreferencesRepo: UserPreferencesRepository,
    private val storage: FirebaseStorage
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "ModelDownloadWorker"
        const val PROGRESS = "Progress"
        private const val MODEL_REMOTE_PATH = "models/gemma-3n-e2b-it-int4.task"
        private const val MODEL_FILENAME = "gemma-3n-e2b-it-int4.task"
    }

    override suspend fun doWork(): Result {
        userPreferencesRepo.setAiModel(ModelStatus.DOWNLOADING)

        val notificationHelper = NotificationHelper(appContext)
        val notificationId = id.hashCode()
        val notification = notificationHelper.createNotification(
            title = "Downloading AI Model",
            contentText = "Preparing download..."
        )

        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(notificationId, notification.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification.build())
        }
        setForeground(foregroundInfo)

        val modelRef = storage.reference.child(MODEL_REMOTE_PATH)
        val privateFile = File(appContext.filesDir, MODEL_FILENAME)

        return try {
            modelRef.getFile(privateFile).addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                setProgressAsync(workDataOf(PROGRESS to progress))

                val progressNotification = notificationHelper.createNotification(
                    title = "Downloading AI Model",
                    contentText = "$progress% complete",
                    progress = 100,
                    progressCurrent = progress,
                    progressIndeterminate = false
                )
                notificationHelper.notify(notificationId, progressNotification)
            }.await()

            Log.i(TAG, "Model download to private storage successful.")
            copyFileToPublicDownloads(privateFile)
            notificationHelper.showDownloadCompleteNotification(notificationId, "AI Model Ready", "The AptusTutor model has been downloaded.")

            userPreferencesRepo.setAiModel(ModelStatus.DOWNLOADED, privateFile.absolutePath)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            notificationHelper.showDownloadCompleteNotification(notificationId, "Download Failed", "Could not download the AI model.")
            userPreferencesRepo.setAiModel(ModelStatus.NOT_DOWNLOADED)
            privateFile.delete()
            Result.failure()
        }
    }

    private suspend fun copyFileToPublicDownloads(sourceFile: File) {
        // Use withContext to switch to the IO dispatcher for this file operation
        withContext(Dispatchers.IO) {
            val resolver = appContext.contentResolver

            // On modern Android, we use MediaStore to save to a public collection.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, MODEL_FILENAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream") // Generic byte stream type
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    try {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            sourceFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        Log.i(TAG, "Successfully copied model to public Downloads folder using MediaStore.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to copy model to public downloads via MediaStore", e)
                        // If copy fails, clean up the incomplete MediaStore entry.
                        resolver.delete(uri, null, null)
                    }
                } else {
                    Log.e(TAG, "MediaStore returned a null URI, cannot save to public downloads.")
                }
            } else {
                // For older devices (API < 29), your original method is acceptable,
                // but let's make it safer.
                try {
                    val publicDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!publicDownloadsDir.exists()) {
                        publicDownloadsDir.mkdirs()
                    }
                    val destinationFile = File(publicDownloadsDir, MODEL_FILENAME)

                    FileOutputStream(destinationFile).use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Log.i(TAG, "Successfully copied model to public Downloads folder (Legacy).")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy model to public downloads (Legacy)", e)
                }
            }
        }
    }
}