package com.nexttechtitan.aptustutor.ai

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
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
import kotlinx.coroutines.tasks.await
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

        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

    private fun copyFileToPublicDownloads(sourceFile: File) {
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
            Log.i(TAG, "Successfully copied model to public Downloads folder.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model to public downloads", e)
        }
    }
}