package com.nexttechtitan.aptustutor.ai

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.storage.FirebaseStorage
import com.nexttechtitan.aptustutor.data.ModelStatus
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import java.io.File

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userPreferencesRepo: UserPreferencesRepository, // CORRECTED
    private val storage: FirebaseStorage
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        userPreferencesRepo.setAiModel(ModelStatus.DOWNLOADING)

        // IMPORTANT: Ensure this path matches your Firebase Storage exactly
        val modelRef = storage.reference.child("models/gemma-3n-e2b-it-int4.task")
        val destinationFile = File(appContext.filesDir, "gemma-model.task")

        return try {
            modelRef.getFile(destinationFile).await()
            userPreferencesRepo.setAiModel(ModelStatus.DOWNLOADED, destinationFile.absolutePath)
            Result.success()
        } catch (e: Exception) {
            userPreferencesRepo.setAiModel(ModelStatus.NOT_DOWNLOADED)
            destinationFile.delete() // Clean up partial download
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "ModelDownloadWorker"
    }
}