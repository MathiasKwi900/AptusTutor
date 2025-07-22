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
import com.nexttechtitan.aptustutor.utils.JsonExtractionUtils
import com.nexttechtitan.aptustutor.utils.MemoryLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AiGradeResponse(val score: Int?, val feedback: String?)

@Singleton
class GemmaAiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val memoryLogger: MemoryLogger
) {
    private var llmInference: LlmInference? = null
    private val TAG = "AptusTutorDebug"

    suspend fun grade(
        prompt: String,
        modelPath: String,
        image: Bitmap? = null
    ): Result<AiGradeResponse> = withContext(Dispatchers.IO) {
        var session: LlmInferenceSession? = null
        try {
            getOrCreateLlmInferenceInstance(modelPath)
                ?: return@withContext Result.failure(Exception("Base AI model could not be loaded."))

            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTemperature(0.8f)
                .setGraphOptions(GraphOptions.builder().setEnableVisionModality(image != null).build())
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

    internal fun getOrCreateLlmInferenceInstance(modelPath: String): LlmInference? {
        if (llmInference == null) {
            try {
                memoryLogger.logMemory(TAG, "Before loading Gemma model")
                Log.i(TAG, "Creating main LlmInference instance from $modelPath...")
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTopK(40)
                    .setMaxTokens(512)
                    .setMaxNumImages(1)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
                Log.i(TAG, "Main LlmInference instance created successfully.")
                memoryLogger.logMemory(TAG, "After loading Gemma model")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create main LlmInference instance", e)
                return null
            }
        }
        return llmInference
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
}