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
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import com.nexttechtitan.aptustutor.utils.JsonExtractionUtils
import com.nexttechtitan.aptustutor.utils.MemoryLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.createBitmap

data class AiGradeResponse(val score: Int?, val feedback: String?)

@Singleton
class GemmaAiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val memoryLogger: MemoryLogger,
    private val userPreferencesRepo: UserPreferencesRepository,
) {
    private var llmInference: LlmInference? = null
    private var activeSession: LlmInferenceSession? = null
    private var isVisionSession: Boolean = false

    private val TAG = "AptusTutorDebug"
    private val inferenceMutex = Mutex()
    private val WARM_UP_PROMPT = "You are a system component. Respond ONLY with the following valid JSON object: {\"score\": 1, \"feedback\": \"OK\"}"

    sealed class ModelState {
        object Uninitialized : ModelState()
        object LoadingModel : ModelState()
        object ModelReadyCold : ModelState() // Model loaded but not warmed up
        data class WarmingUp(val step: String) : ModelState() // e.g., "Text Engine (1/2)"
        object Ready : ModelState() // Fully warmed up and ready for fast inference
        object Busy : ModelState() // Actively grading
        data class Failed(val error: Throwable) : ModelState()
    }

    private val _modelState = MutableStateFlow<ModelState>(ModelState.Uninitialized)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    suspend fun initializeAndWarmUp() = withContext(Dispatchers.IO) {
        inferenceMutex.withLock {
            val isAlreadyInitialized = userPreferencesRepo.aiModelInitializedFlow.first()
            if (isAlreadyInitialized) {
                if (llmInference == null) {
                    loadModelGraphInternal()
                }
                if (_modelState.value!is ModelState.Failed) {
                    _modelState.value = ModelState.ModelReadyCold
                }
                Log.d(TAG, "Model has been previously initialized. Now loaded and ready.")
                return@withLock
            }

            try {
                Log.i(TAG, "Starting ONE-TIME model initialization and warm-up.")
                // Step 1: Load the model if it's not already loaded.
                if (llmInference == null) {
                    loadModelGraphInternal() // This will set state to LoadingModel -> ModelReadyCold
                }

                // Ensure we are in the correct state to proceed
                if (_modelState.value!is ModelState.ModelReadyCold) {
                    throw IllegalStateException("Model is not in a ready state for warm-up.")
                }

                // Step 2: Warm up the text modality
                _modelState.value = ModelState.WarmingUp("Text Engine (1/2)...")
                Log.d(TAG, "Warming up text modality...")
                val textResult = gradeInternal(WARM_UP_PROMPT, null)
                textResult.onFailure { throw it }
                Log.d(TAG, "Text modality warmed up successfully.")

                // Step 3: Warm up the vision modality
                _modelState.value = ModelState.WarmingUp("Vision Engine (2/2)...")
                Log.d(TAG, "Warming up vision modality...")
                val dummyBitmap = createBitmap(1, 1)
                val visionResult = gradeInternal(WARM_UP_PROMPT, dummyBitmap)
                dummyBitmap.recycle()
                visionResult.onFailure { throw it }
                Log.d(TAG, "Vision modality warmed up successfully.")

                // Step 4: Finalize
                userPreferencesRepo.setAiModelInitialized(true)
                _modelState.value = ModelState.Ready
                Log.i(TAG, "AI Engine successfully initialized and warmed up.")

            } catch (e: Exception) {
                _modelState.value = ModelState.Failed(e)
                Log.e(TAG, "Failed to initialize and warm up AI Engine", e)
                releaseModel() // Clean up on failure
            }
        }
    }

    suspend fun grade(prompt: String, image: Bitmap? = null): Result<AiGradeResponse> {
        return inferenceMutex.withLock {
            if (!(_modelState.value is ModelState.Ready || _modelState.value is ModelState.ModelReadyCold)) {
                Log.e(TAG, "Cannot grade, model is not in a ready state. Current state: ${_modelState.value}")
                return@withLock Result.failure(IllegalStateException("Model is not ready for inference."))
            }
            _modelState.value = ModelState.Busy
            val result = gradeInternal(prompt, image)
            _modelState.value = ModelState.Ready
            result
        }
    }


    private suspend fun gradeInternal(prompt: String, image: Bitmap? = null): Result<AiGradeResponse> = withContext(Dispatchers.IO) {
        try {
            val session = getOrCreateSession(isVisionEnabled = image!= null)
            session.addQueryChunk(prompt)
            if (image!= null) {
                session.addImage(BitmapImageBuilder(image).build())
            }

            memoryLogger.logMemory(TAG, "Before single inference")
            val response = session.generateResponse()
            memoryLogger.logMemory(TAG, "After single inference")

            val parsedResponse = parseJsonResponse(response)

            if (parsedResponse.score == null || parsedResponse.feedback.isNullOrBlank()) {
                Result.failure(Exception("AI response was incomplete or in an invalid format."))
            } else {
                Result.success(parsedResponse)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during internal AI grading", e)
            activeSession?.close()
            activeSession = null
            Result.failure(e)
        }
    }

    private suspend fun loadModelGraphInternal() {
        if (llmInference!= null || _modelState.value is ModelState.LoadingModel) return

        _modelState.value = ModelState.LoadingModel
        val modelPath = userPreferencesRepo.aiModelPathFlow.first()
        if (modelPath.isNullOrBlank()) {
            _modelState.value = ModelState.Failed(IllegalArgumentException("Model path is not set."))
            return
        }

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
            _modelState.value = ModelState.ModelReadyCold
            Log.i(TAG, "Model graph loaded and is ready cold.")
        } catch (e: Exception) {
            _modelState.value = ModelState.Failed(e)
            Log.e(TAG, "Failed to initialize AI Engine", e)
            releaseModel()
        }
    }

    suspend fun ensureModelIsLoaded() {
        if (_modelState.value is ModelState.Uninitialized) {
            Log.d(TAG, "Model is uninitialized. Triggering background load.")
            loadModelGraphInternal()
        }
    }

    fun releaseModel() {
        activeSession?.close()
        activeSession = null
        llmInference?.close()
        llmInference = null
        _modelState.value = ModelState.Uninitialized
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

    private fun getOrCreateSession(isVisionEnabled: Boolean): LlmInferenceSession {
        // If a session exists but its modality doesn't match the request, close it.
        if (activeSession!= null && isVisionSession!= isVisionEnabled) {
            activeSession?.close()
            activeSession = null
        }

        // If no session exists, create a new one with the correct options.
        if (activeSession == null) {
            Log.d(TAG, "Creating new LlmInferenceSession. Vision enabled: $isVisionEnabled")
            val sessionOptionsBuilder = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTemperature(0.8f)

            if (isVisionEnabled) {
                sessionOptionsBuilder.setGraphOptions(
                    GraphOptions.builder().setEnableVisionModality(true).build()
                )
            }
            activeSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptionsBuilder.build())
            isVisionSession = isVisionEnabled
        }
        return activeSession!!
    }
}