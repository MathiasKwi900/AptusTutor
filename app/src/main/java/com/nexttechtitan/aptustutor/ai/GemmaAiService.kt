package com.nexttechtitan.aptustutor.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.nexttechtitan.aptustutor.data.ModelStatus
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import com.nexttechtitan.aptustutor.utils.JsonExtractionUtils
import com.nexttechtitan.aptustutor.utils.MemoryLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class AiGradeResponse(val score: Int?, val feedback: String?)

@Singleton
class GemmaAiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val memoryLogger: MemoryLogger,
    private val userPreferencesRepo: UserPreferencesRepository
) {
    private var llmInference: LlmInference? = null
    private val TAG = "AptusTutorDebug"

    sealed class ModelState {
        object Uninitialized : ModelState()
        object LoadingGraph : ModelState()
        object LoadedPendingCache : ModelState() // Model is usable, but slow.
        object GeneratingCache : ModelState() // The long process is running.
        object Ready : ModelState()           // Ready for fast inference.
        data class Failed(val error: Throwable) : ModelState()
    }

    private val _modelState = MutableStateFlow<ModelState>(ModelState.Uninitialized)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    suspend fun generatePleCache()= withContext(Dispatchers.IO) {
        loadModelGraph()
        if (_modelState.value !is ModelState.LoadedPendingCache || llmInference == null) {
            Log.w(TAG, "Cannot generate cache. Model graph not loaded first.")
            return@withContext
        }
        _modelState.value = ModelState.GeneratingCache
        var session: LlmInferenceSession? = null
        try {
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTemperature(0.8f)
                .setGraphOptions(GraphOptions.builder().setEnableVisionModality(false).build())
                .build()
            session = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
            Log.i(TAG, "Executing dummy inference to generate PLE cache. This will take several minutes...")
            session.addQueryChunk("Hi!")
            memoryLogger.logMemory(TAG, "[PLE Cache] Before single inference")
            val response = session.generateResponse()
            Log.d(TAG, "Raw AI Response: $response")
            memoryLogger.logMemory(TAG, "[PLE Cache] After single inference")
            Log.i(TAG, "PLE cache generation complete.")

            userPreferencesRepo.setPleCacheComplete(true)
            _modelState.value = ModelState.Ready
            Log.i(TAG, "Stage 2/2 Complete: PLE Cache is ready. AI is now fast.")
        } catch (e: Exception) {
            _modelState.value = ModelState.Failed(e)
            Log.e(TAG, "Failed to initialize and pre-warm AI Engine", e)
            llmInference?.close()
            llmInference = null
        } finally {
            session?.close()
        }
    }

    suspend fun loadModelGraph() {
        if (llmInference != null) return
        if (_modelState.value !is ModelState.Uninitialized && _modelState.value !is ModelState.Failed) {
            Log.d(TAG, "Model already initialized. Current state: ${_modelState.value}")
            return
        }
        _modelState.value = ModelState.LoadingGraph
        val modelPath = userPreferencesRepo.aiModelPathFlow.first()

        try {
            memoryLogger.logMemory(TAG, "[Engine] Before loading Gemma model")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTopK(40)
                .setMaxTokens(512)
                .setMaxNumImages(1)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            memoryLogger.logMemory(TAG, "[Engine] After loading Gemma model")
            _modelState.value = ModelState.LoadedPendingCache
            Log.i(TAG, "Stage 1/2 Complete: Model graph loaded.")
        } catch (e: Exception) {
            _modelState.value = ModelState.Failed(e)
            Log.e(TAG, "Failed to initialize AI Engine", e)
            llmInference?.close()
            llmInference = null
        }
    }

    suspend fun grade(prompt: String, image: Bitmap? = null
    ): Result<AiGradeResponse> = withContext(Dispatchers.IO) {
        loadModelGraph()
        var session: LlmInferenceSession? = null
        try {
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTemperature(0.8f)
                .setGraphOptions(
                    GraphOptions.builder()
                        .setEnableVisionModality(image != null)
                        .build())
                .build()

            session = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
            session.addQueryChunk(prompt)
            if (image != null) {
                session.addImage(BitmapImageBuilder(image).build())
            }

            memoryLogger.logMemory(TAG, "Before single inference")
            val response = session.generateResponse()
            memoryLogger.logMemory(TAG, "After single inference")

            Log.d(TAG, "Raw AI Response: $response")
            val parsedResponse = parseJsonResponse(response)

            if (parsedResponse.score == null || parsedResponse.feedback.isNullOrBlank()) {
                Result.failure(Exception("AI response was incomplete or in an invalid format."))
            } else {
                Result.success(parsedResponse)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during AI grading", e)
            Result.failure(e)
        } finally {
            session?.close()
        }
    }

    fun releaseModel() {
        llmInference?.close()
        llmInference = null
        Log.i(TAG, "Main LlmInference model instance released.")
        memoryLogger.logMemory(TAG, "After releasing model")
    }

    private fun parseJsonResponse(response: String): AiGradeResponse {
        val jsonString = JsonExtractionUtils.extractJsonObject(response)
        if (jsonString == null) {
            Log.e(TAG, "Failed to extract JSON object from response: $response")
            return AiGradeResponse(null, "Error: The AI returned an invalid format. Raw response: $response")
        }
        return try {
            gson.fromJson(jsonString, AiGradeResponse::class.java)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse JSON string: $jsonString", e)
            AiGradeResponse(null, "Error: The AI returned an invalid format. Raw response: $response")
        }
    }

    suspend fun initiateLoadModelGraph() {
        if (llmInference == null) {
            loadModelGraph()
        }
    }
}

