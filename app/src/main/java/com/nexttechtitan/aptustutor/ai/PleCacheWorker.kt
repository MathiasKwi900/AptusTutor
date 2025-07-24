package com.nexttechtitan.aptustutor.ai

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import com.nexttechtitan.aptustutor.utils.MemoryLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class PleCacheWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val userPreferencesRepo: UserPreferencesRepository,
    private val memoryLogger: MemoryLogger
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val UNIQUE_WORK_NAME = "PleCacheWorker"
    }

    override suspend fun doWork(): Result {
        if (userPreferencesRepo.aiModelInitializedFlow.first()) {
            Log.i(UNIQUE_WORK_NAME, "PLE Caching has already been completed. Skipping.")
            return Result.success()
        }

        var llmInference: LlmInference? = null
        try {
            Log.i(UNIQUE_WORK_NAME, "Starting one-time PLE Caching process in the background...")
            val modelPath = userPreferencesRepo.aiModelPathFlow.first()
                ?: return Result.retry().also { Log.e(UNIQUE_WORK_NAME, "Model path not found.") }

            memoryLogger.logMemory(UNIQUE_WORK_NAME, "[Worker] Before loading Gemma model")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxNumImages(1)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            Log.i(UNIQUE_WORK_NAME, "Stage 1 Complete: Model Initialized.")
            memoryLogger.logMemory(UNIQUE_WORK_NAME, "[Worker] After loading Gemma model")

            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(1)
                .setTemperature(0.1f)
                .build()

            LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions).use { session ->
                session.addQueryChunk("Hi!")
                memoryLogger.logMemory(UNIQUE_WORK_NAME, "[Worker] Before first inference")
                val response = session.generateResponse()
                memoryLogger.logMemory(UNIQUE_WORK_NAME, "[Worker] After first inference")
                Log.i(UNIQUE_WORK_NAME, "Stage 2 Complete. Dummy response: $response")
            }

            userPreferencesRepo.setAiModelInitialized(true)
            Log.i(UNIQUE_WORK_NAME, "Caching successful. Marked as complete.")
            return Result.success()

        } catch (e: Exception) {
            Log.e(UNIQUE_WORK_NAME, "PLE Caching failed. It will be retried later.", e)
            return Result.retry()
        } finally {
            llmInference?.close()
            Log.i(UNIQUE_WORK_NAME, "Cleaned up main inference engine.")
        }
    }
}