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
    // Mutex ensures that only one grading operation (which is now memory and CPU intensive)
    // can run at a time, preventing resource contention.
    private val inferenceMutex = Mutex()

    // Engine caching maps have been removed. They are the source of the deadlock
    // and memory pressure issues.

    sealed class ModelState {
        object Uninitialized : ModelState()
        object LoadingModel : ModelState() // This state is now transient for each call
        object Busy : ModelState()
        object Ready : ModelState()
        data class Failed(val error: Throwable) : ModelState()
    }

    private val _modelState = MutableStateFlow<ModelState>(ModelState.Ready)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    /**
     * The primary public method for grading. This function now encapsulates the entire
     * lifecycle of an LlmInference engine for a single grading task.
     * It creates, uses, and destroys the engine in one atomic operation.
     */
    suspend fun grade(
        question: AssessmentQuestion,
        answer: AssessmentAnswer?,
        image: Bitmap? = null
    ): Result<AiGradeResponse> {
        // The mutex ensures that we only attempt to load and run one model at a time,
        // which is critical for memory stability on low-spec devices.
        return inferenceMutex.withLock {
            _modelState.value = ModelState.Busy
            _modelState.value = ModelState.LoadingModel

            val modelPath = userPreferencesRepo.aiModelPathFlow.first()
            if (modelPath.isNullOrBlank()) {
                val error = IllegalArgumentException("Model path not set.")
                _modelState.value = ModelState.Failed(error)
                return@withLock Result.failure(error)
            }

            // The engine and session are now local variables, not class members.
            // They exist only for the duration of this function call.
            var engine: LlmInference? = null
            var session: LlmInferenceSession? = null

            try {
                // Step 1: Create a new, ephemeral engine for this specific task.
                Log.d(TAG, "Creating new ephemeral engine for grading task.")
                val isVision = image!= null
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(1024) // Ensure this is sufficient for prompt + response
                    .apply { if (isVision) setMaxNumImages(1) }
                    .setPreferredBackend(LlmInference.Backend.CPU)
                    .build()
                engine = LlmInference.createFromOptions(context, options)
                _modelState.value = ModelState.Ready // Model is loaded, ready for inference
                _modelState.value = ModelState.Busy   // Now performing inference

                // Step 2: Create a single-use session from the new engine.
                val sessionOptionsBuilder = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(1)
                    .setTemperature(0.1f)

                if (isVision) {
                    sessionOptionsBuilder.setGraphOptions(
                        GraphOptions.builder().setEnableVisionModality(true).build()
                    )
                }
                session = LlmInferenceSession.createFromOptions(engine, sessionOptionsBuilder.build())

                // Step 3: Build the full prompt and perform inference.
                // The prefix and suffix are combined into a single prompt.
                // The expensive prefill cost is paid here, on every single call.
                val (prefixPrompt, suffixPrompt) = buildPrompts(question, answer)
                val fullPrompt = "$prefixPrompt\n$suffixPrompt"

                session.addQueryChunk(fullPrompt)
                if (image!= null) {
                    session.addImage(BitmapImageBuilder(image).build())
                }

                memoryLogger.logMemory(TAG, "[Ephemeral] Before full inference")
                val response = session.generateResponse()
                memoryLogger.logMemory(TAG, "[Ephemeral] After full inference")

                // Step 4: Parse the response.
                val parsedResponse = parseJsonResponse(response)
                if (parsedResponse.score == null || parsedResponse.feedback.isNullOrBlank()) {
                    Result.failure(Exception("AI response was incomplete or invalid."))
                } else {
                    Result.success(parsedResponse)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during ephemeral AI grading", e)
                _modelState.value = ModelState.Failed(e)
                Result.failure(e)
            } finally {
                // Step 5: CRITICAL CLEANUP.
                // The session and engine MUST be closed to release memory and other resources.
                // This `finally` block guarantees cleanup even if an exception occurs.
                Log.d(TAG, "Closing ephemeral session and engine.")
                session?.close()
                engine?.close()
                _modelState.value = ModelState.Ready
                memoryLogger.logMemory(TAG, "[Ephemeral] After closing engine")
            }
        }
    }

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