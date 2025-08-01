package com.nexttechtitan.aptustutor.ai

import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext as getCoroutineContext

/**
 * A background worker responsible for downloading the large AI model file from
 * Firebase Storage. It runs as a foreground service to ensure the download is not
 * killed by the OS, provides progress updates via its WorkInfo, and shows a
 * user-facing notification.
 */
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
        private const val MODEL_REMOTE_PATH = "models/gemma-3n-E2B-it-int4.task"
        private const val MODEL_FILENAME = "gemma-3n-E2B-it-int4.task"
    }

    private lateinit var downloadTask: com.google.firebase.storage.StorageTask<com.google.firebase.storage.FileDownloadTask.TaskSnapshot>
    override suspend fun doWork(): Result {
        val currentStatus = userPreferencesRepo.aiModelStatusFlow.first()
        val currentPath   = userPreferencesRepo.aiModelPathFlow.first()
        if (currentStatus == ModelStatus.DOWNLOADED && currentPath != null) {
            return Result.success()
        }
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

        try {
            val job = getCoroutineContext[Job] ?: error("CoroutineWorker should have a job")
            val metadata = modelRef.metadata.await()
            val totalBytes = metadata.sizeBytes

            if (totalBytes <= 0) {
                throw IllegalStateException("Could not determine file size from metadata. The file on the server may be corrupt.")
            }

            if (privateFile.exists() && privateFile.length() == totalBytes) {
                copyFileToPublicDownloads(privateFile)
                notificationHelper.showDownloadCompleteNotification(notificationId, "AI Model Ready", "The AptusTutor model has been downloaded.")
                userPreferencesRepo.setAiModel(ModelStatus.DOWNLOADED, privateFile.absolutePath)
                return Result.success()
            }

            downloadTask = modelRef.getFile(privateFile)
            downloadTask.addOnProgressListener { taskSnapshot ->
                try {
                    job.ensureActive()
                } catch (e: CancellationException) {
                    if (!downloadTask.isCanceled) {
                        downloadTask.cancel()
                    }
                    return@addOnProgressListener
                }

                val bytesTransferred = taskSnapshot.bytesTransferred
                val progress = (100.0 * bytesTransferred / totalBytes).toInt().coerceIn(0, 100)

                setProgressAsync(workDataOf(PROGRESS to progress))

                val progressNotification = notificationHelper.createNotification(
                    title = "Downloading AI Model",
                    contentText = "$progress% complete",
                    progress = 100,
                    progressCurrent = progress,
                    progressIndeterminate = false
                )
                notificationHelper.notify(notificationId, progressNotification)
            }

            downloadTask.await()

            copyFileToPublicDownloads(privateFile)
            notificationHelper.showDownloadCompleteNotification(notificationId, "AI Model Ready", "The AptusTutor model has been downloaded.")
            userPreferencesRepo.setAiModel(ModelStatus.DOWNLOADED, privateFile.absolutePath)
            return Result.success()

        } catch (e: Exception) {
            if (this::downloadTask.isInitialized && !downloadTask.isCanceled) {
                downloadTask.cancel()
            }
            notificationHelper.showDownloadCompleteNotification(notificationId, "Download Failed", "Could not download the AI model.")
            userPreferencesRepo.setAiModel(ModelStatus.NOT_DOWNLOADED)
            if(privateFile.exists()) {
                privateFile.delete()
            }
            return Result.failure()
        }
    }

    /**
     * Copies the downloaded model from the app's private storage to the public
     * 'Downloads' directory. This uses the modern MediaStore API for Android Q+
     * for better system integration and security.
     */
    private suspend fun copyFileToPublicDownloads(sourceFile: File) {
        withContext(Dispatchers.IO) {
            val resolver = appContext.contentResolver

            // On modern Android, we use MediaStore to save to a public collection.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, MODEL_FILENAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
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
                    } catch (e: Exception) {
                        resolver.delete(uri, null, null)
                    }
                } else {
                    //
                }
            } else {
                // This acts as a fallback for older devices (API < 29)
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
                } catch (e: Exception) {
                    //
                }
            }
        }
    }
}