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
import com.nexttechtitan.aptustutor.data.AssessmentAnswer
import com.nexttechtitan.aptustutor.data.AssessmentQuestion
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import com.nexttechtitan.aptustutor.utils.JsonExtractionUtils
import com.nexttechtitan.aptustutor.utils.MemoryLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class AiGradeResponse(val score: Int?, val feedback: String?)

@Singleton
class GemmaAiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val memoryLogger: MemoryLogger,
    private val userPreferencesRepo: UserPreferencesRepository,
) {
    private val TAG = "AptusTutorDebug"
    private val inferenceMutex = Mutex()

    /**
     * This is the core of the new architecture. We maintain a map of dedicated, pre-warmed
     * LlmInference engines. Each question gets its own engine to ensure 100% context isolation.
     * The key is the unique questionId, and the value is the heavy LlmInference engine instance.
     */
    private val warmedTextEngines = mutableMapOf<String, LlmInference>()
    private val warmedVisionEngines = mutableMapOf<String, LlmInference>()

    sealed class ModelState {
        object Uninitialized : ModelState()
        object LoadingModel : ModelState()
        object Busy : ModelState()
        object Ready : ModelState()
        data class Failed(val error: Throwable) : ModelState()
    }

    private val _modelState = MutableStateFlow<ModelState>(ModelState.Uninitialized)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    /**
     * The primary public method for grading. It accepts structured data, which is a robust
     * API contract.
     */
    suspend fun grade(
        question: AssessmentQuestion,
        answer: AssessmentAnswer?,
        image: Bitmap? = null,
        studentIdentifier: String
    ): Result<AiGradeResponse> {
        return inferenceMutex.withLock {
            _modelState.value = ModelState.Busy
            Log.i(TAG, "================== NEW GRADING TASK FOR: $studentIdentifier ==================")

            val (prefixPrompt, suffixPrompt) = buildPrompts(question, answer)
            val questionId = question.id
            val isVision = image!= null

            val engineResult = getOrCreateWarmedEngine(questionId, prefixPrompt, isVision)

            if (engineResult.isFailure) {
                _modelState.value = ModelState.Ready
                return@withLock Result.failure(engineResult.exceptionOrNull()!!)
            }
            val engine = engineResult.getOrNull()!!

            val result = gradeInternalSuffix(engine, suffixPrompt, image, studentIdentifier, questionId)
            _modelState.value = ModelState.Ready
            result
        }
    }

    private suspend fun getOrCreateWarmedEngine(
        questionId: String,
        prefixPrompt: String,
        isVision: Boolean
    ): Result<LlmInference> {
        val engineMap = if (isVision) warmedVisionEngines else warmedTextEngines

        Log.d(TAG, "[CACHE CHECK] Checking for existing warmed engine for Q-ID: $questionId")
        engineMap[questionId]?.let {
            Log.i(TAG, " Found existing warmed engine for Q-ID: $questionId. Reusing it.")
            return Result.success(it)
        }

        Log.w(TAG, " No engine for Q-ID: $questionId. Creating and warming a new one. THIS WILL BE SLOW.")
        _modelState.value = ModelState.LoadingModel

        val modelPath = userPreferencesRepo.aiModelPathFlow.first()
        if (modelPath.isNullOrBlank()) {
            val error = IllegalArgumentException("Model path not set.")
            _modelState.value = ModelState.Failed(error)
            return Result.failure(error)
        }

        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .apply { if (isVision) setMaxNumImages(1) }
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()

            memoryLogger.logMemory(TAG, "Before creating new engine for Q-ID: $questionId")
            val newEngine = LlmInference.createFromOptions(context, options)
            memoryLogger.logMemory(TAG, "After creating new engine for Q-ID: $questionId")

            Log.d(TAG, " Warming engine for Q-ID: $questionId with prefix prompt.")
            warmEngine(newEngine, prefixPrompt, isVision)
            Log.i(TAG, " Engine for Q-ID: $questionId is now warm and cached.")

            engineMap[questionId] = newEngine
            _modelState.value = ModelState.Ready
            Result.success(newEngine)
        } catch (e: Exception) {
            _modelState.value = ModelState.Failed(e)
            Log.e(TAG, "Failed to create or warm new engine for Q-ID: $questionId", e)
            Result.failure(e)
        }
    }

    private fun gradeInternalSuffix(
        engine: LlmInference,
        suffixPrompt: String,
        image: Bitmap? = null,
        studentIdentifier: String,
        questionId: String
    ): Result<AiGradeResponse> {
        var session: LlmInferenceSession? = null
        Log.i(TAG, "Attempting to create LlmInferenceSession for $studentIdentifier from engine for Q-ID: $questionId")
        try {
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(1)
                .setTemperature(0.1f)
                .build()

            session = LlmInferenceSession.createFromOptions(engine, sessionOptions)
            Log.d(TAG, " Successfully created session for $studentIdentifier.")

            session.addQueryChunk(suffixPrompt)
            if (image!= null) {
                session.addImage(BitmapImageBuilder(image).build())
            }

            Log.d(TAG, " Generating response for $studentIdentifier...")
            val response = session.generateResponse()
            Log.i(TAG, " Successfully generated response for $studentIdentifier.")

            val parsedResponse = parseJsonResponse(response)
            return if (parsedResponse.score == null || parsedResponse.feedback.isNullOrBlank()) {
                Result.failure(Exception("AI response was incomplete or invalid."))
            } else {
                Result.success(parsedResponse)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during grading for $studentIdentifier. This point is reached if session creation fails or during inference.", e)
            return Result.failure(e)
        } finally {
            session?.close()
        }
    }

    /**
     * Performs the expensive, one-time prefill operation to populate an engine's shared KV Cache.
     * This function is synchronous and will throw an exception on failure, which is caught
     * by the calling function.
     */
    private fun warmEngine(engine: LlmInference, warmupPrompt: String, isVision: Boolean) {
        var session: LlmInferenceSession? = null
        try {
            val sessionOptionsBuilder = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(1)
                .setTemperature(0.1f)

            if (isVision) {
                sessionOptionsBuilder.setGraphOptions(
                    GraphOptions.builder().setEnableVisionModality(true).build()
                )
            }
            session = LlmInferenceSession.createFromOptions(engine, sessionOptionsBuilder.build())
            session.addQueryChunk(warmupPrompt)
            memoryLogger.logMemory(TAG, " Memory before expensive prefill")
            session.generateResponse()
            memoryLogger.logMemory(TAG, " Memory after expensive prefill")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to warm engine.", e)
            throw e
        } finally {
            session?.close()
        }
    }

    /**
     * Encapsulates the logic for building the static prefix and dynamic suffix.
     * Returns a Pair where the first element is the prefix and the second is the suffix.
     */
    private fun buildPrompts(question: AssessmentQuestion, answer: AssessmentAnswer?): Pair<String, String> {
        val studentAnswerContent = if (answer == null || (answer.textResponse.isNullOrBlank() && answer.imageFilePath.isNullOrBlank())) {
            ""
        } else {
            when {
                !answer.textResponse.isNullOrBlank() &&!answer.imageFilePath.isNullOrBlank() ->
                    """
                    STUDENT_TEXT: "${answer.textResponse}"
                    STUDENT_IMAGE_CONTEXT: provided
                    """.trimIndent()

                !answer.textResponse.isNullOrBlank() -> "STUDENT_TEXT: \"${answer.textResponse}\""
                else -> "STUDENT_IMAGE_CONTEXT: provided"
            }
        }

        val answerInstruction = "Analyze and score the student's answer against the marking guide. The score MUST be an integer between 0 to ${question.maxScore}, inclusive. Also, provide concise feedback that must be under 30 words."

        val prefix = """
            GRADE_TASK
            QUESTION: "${question.text}"
            MARKING_GUIDE: "${question.markingGuide}"
            MAX_SCORE: ${question.maxScore}
            INSTRUCTIONS: $answerInstruction
            OUTPUT_FORMAT: Respond with a single, compact JSON object only. Example: {"score":<int>,"feedback":"<string>"}
        """.trimIndent()

        val suffix = studentAnswerContent

        return Pair(prefix, suffix)
    }

    /**
     * Releases all cached engine instances to free up memory.
     */
    suspend fun releaseModels() {
        inferenceMutex.withLock {
            Log.i(TAG, "Releasing all LlmInference engines...")
            warmedTextEngines.values.forEach { it.close() }
            warmedTextEngines.clear()
            warmedVisionEngines.values.forEach { it.close() }
            warmedVisionEngines.clear()
            _modelState.value = ModelState.Uninitialized
            Log.i(TAG, "All LlmInference model instances released.")
            memoryLogger.logMemory(TAG, "After releasing models")
        }
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
}