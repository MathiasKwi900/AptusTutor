package com.nexttechtitan.aptustutor.ai

import android.content.Context
import android.graphics.Bitmap
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** A data class representing the structured response expected from the AI model. */
data class AiGradeResponse(val score: Int?, val feedback: String?)

/**
 * Provides a high-level interface to the on-device Gemma 3n model for grading tasks.
 * This service is the core AI engine of the application, encapsulating the complex logic
 * of model interaction, resource management, and prompt engineering.
 *
 * ### Architectural Rationale: The Ephemeral Model — A Deliberate Choice for Stability
 * This service employs a **fully ephemeral architecture**. For every individual grading task,
 * a new `LlmInference` engine and `LlmInferenceSession` are created from scratch, used for a
 * single inference, and then immediately destroyed.
 *
 * This design was a deliberate and necessary engineering decision, arrived at after a deep
 * investigation into the MediaPipe `tasks-genai:0.10.25` library. An advanced, high-performance
 * caching architecture ("one engine per question") was initially developed to amortize the
 * model's expensive prefill cost. However, rigorous testing revealed a critical bug in the
 * library's native session management: after a session was used and closed, the parent
 * `LlmInference` engine would enter an unrecoverable deadlocked state, preventing any
 * subsequent use. This is corroborated by documented issues of state corruption and context
 * leakage in this version range of the library.
 *
 * Therefore, the current model, while incurring the full computational cost on each call,
 * is the only architecture that **guarantees correctness, absolute context isolation, and
 * stability against library-level deadlocks**. The observed latency is a transparent
 * reflection of the model's demands on the target hardware, not an implementation flaw, but
 * a trade-off made in favor of reliability.
 */
@Singleton
class GemmaAiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val userPreferencesRepo: UserPreferencesRepository
) {
    /**
     * A mutex to ensure that only one grading operation can execute at a time. This is a
     * critical safeguard on memory-constrained devices to prevent multiple, concurrent
     * attempts to load the multi-gigabyte model into RAM, which would cause an OOM crash.
     */
    private val inferenceMutex = Mutex()

    /** Represents the real-time state of the AI service for UI observation. */
    sealed class ModelState {
        object LoadingModel : ModelState() // The model file is being loaded into the engine.
        object Busy : ModelState()         // The engine is actively processing a prompt.
        object Ready : ModelState()        // The service is idle and ready for a new task.
        data class Failed(val error: Throwable) : ModelState()
    }

    private val _modelState = MutableStateFlow<ModelState>(ModelState.Ready)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    /**
     * Initiates the complete ephemeral lifecycle for a single grading task.
     *
     * @param question The assessment question, including text, marking guide, and max score.
     * @param answer The student's answer, which can be text-based.
     * @param image An optional bitmap of the student's handwritten answer.
     * @return A containing the on success, or an exception on failure.
     */
    suspend fun grade(
        question: AssessmentQuestion,
        answer: AssessmentAnswer?,
        image: Bitmap? = null
    ): Result<AiGradeResponse> {
        // Lock the mutex to ensure serialized access to the AI service.
        return inferenceMutex.withLock {
            _modelState.value = ModelState.Busy
            _modelState.value = ModelState.LoadingModel

            val modelPath = userPreferencesRepo.aiModelPathFlow.first()
            if (modelPath.isNullOrBlank()) {
                val error = IllegalArgumentException("Model path not set.")
                _modelState.value = ModelState.Failed(error)
                return@withLock Result.failure(error)
            }

            val isVisionRequest = image!= null

            var engine: LlmInference? = null
            var session: LlmInferenceSession? = null

            try {
                // **Ephemeral Engine Creation**
                // A new engine is created for every call. This is resource-intensive but
                // is the only method confirmed to be stable, avoiding the session management
                // deadlocks discovered in the MediaPipe library during development.
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(1024)
                    // This `apply` block leverages the Gemma 3n model's "Conditional Parameter
                    // Loading" feature. The vision-specific parameters are only loaded if
                    // an image is present, conserving memory for text-only tasks.
                    .apply { if (isVisionRequest) setMaxNumImages(1) }
                    .setPreferredBackend(LlmInference.Backend.CPU)
                    .build()
                engine = LlmInference.createFromOptions(context, options)

                // Update state to reflect that the model is loaded and processing will now begin.
                _modelState.value = ModelState.Busy

                val sessionOptionsBuilder = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(1)
                    // A low temperature is used to force deterministic, factual output,
                    // which is essential for a reliable and fair grading task.
                    .setTemperature(0.1f)

                if (isVisionRequest) {
                    sessionOptionsBuilder.setGraphOptions(
                        GraphOptions.builder().setEnableVisionModality(true).build()
                    )
                }
                session = LlmInferenceSession.createFromOptions(engine, sessionOptionsBuilder.build())

                val (prefixPrompt, suffixPrompt) = buildPrompts(question, answer)
                val fullPrompt = "$prefixPrompt\n$suffixPrompt"

                session.addQueryChunk(fullPrompt)
                if (image!= null) {
                    session.addImage(BitmapImageBuilder(image).build())
                }

                // This synchronous call executes the entire inference pipeline, including the
                // lengthy, compute-bound prefill phase.
                val response = session.generateResponse()

                val parsedResponse = parseJsonResponse(response)
                if (parsedResponse.score == null || parsedResponse.feedback.isNullOrBlank()) {
                    Result.failure(Exception("AI response was incomplete or invalid."))
                } else {
                    Result.success(parsedResponse)
                }
            } catch (e: Exception) {
                _modelState.value = ModelState.Failed(e)
                Result.failure(e)
            } finally {
                // **Critical Resource Cleanup**
                // It is imperative to close both the session and the engine to release
                // native memory and prevent memory leaks or crashes.
                session?.close()
                engine?.close()
                _modelState.value = ModelState.Ready
            }
        }
    }

    /**
     * Constructs the structured prompt for the Gemma model. This function is a key part of
     * the "prompt engineering" process, designed to constrain the model's output for
     * reliable, automated parsing.
     *
     * @return A pair containing the prefix (task context) and suffix (student answer).
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

        // The "prefix" contains the static context and instructions for the task.
        // In a caching architecture, this is the part that would be "warmed".
        val prefix = """
            GRADE_TASK
            QUESTION: "${question.text}"
            MARKING_GUIDE: "${question.markingGuide}"
            MAX_SCORE: ${question.maxScore}
            INSTRUCTIONS: $answerInstruction
            OUTPUT_FORMAT: Respond with a single, compact JSON object only. Example: {"score":<int>,"feedback":"<string>"}
        """.trimIndent()

        // The "suffix" contains the dynamic student data.
        val suffix = studentAnswerContent

        return Pair(prefix, suffix)
    }

    /**
     * Parses the raw text response from the LLM, extracting the JSON payload.
     * This is designed to be resilient to common LLM inconsistencies, such as extra
     * conversational text or malformed markdown code fences.
     */
    private fun parseJsonResponse(response: String): AiGradeResponse {
        val jsonString = JsonExtractionUtils.extractJsonObject(response)
        if (jsonString == null) {
            // If JSON extraction fails, we return the raw response as feedback for debugging.
            return AiGradeResponse(null, "Error: The AI returned an invalid format. Raw response: $response")
        }
        return try {
            gson.fromJson(jsonString, AiGradeResponse::class.java)
        } catch (e: JsonSyntaxException) {
            AiGradeResponse(null, "Error: The AI returned an invalid format. Raw response: $response")
        }
    }
}