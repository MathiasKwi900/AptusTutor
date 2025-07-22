package com.nexttechtitan.aptustutor.ai

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexttechtitan.aptustutor.data.AptusTutorRepository
import com.nexttechtitan.aptustutor.data.FeedbackStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@HiltWorker
class AIBatchGradingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: AptusTutorRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val assessmentId = inputData.getString(KEY_ASSESSMENT_ID) ?: return Result.failure()
        Log.d("AIBatchGradingWorker", "Worker started for assessment ID: $assessmentId")

        return try {
            val assessment = repository.getAssessmentById(assessmentId).first()
            val submissions = repository.getSubmissionsFlowForAssessment(assessmentId).first()
                .filter { it.feedbackStatus == FeedbackStatus.PENDING_SEND }

            if (assessment == null || submissions.isEmpty()) {
                Log.d("AIBatchGradingWorker", "No assessment or submissions found to grade.")
                return Result.success()
            }

            // --- THIS IS THE GEMMA 3N PLACEHOLDER ---
            // 1. You will format the submissions and marking guide into a single prompt for Gemma 3n.
            // 2. You will invoke the Google AI Edge SDK here to run the model.
            // 3. The model will return a structured response (e.g., JSON) with scores and feedback.
            // 4. You will parse that response and save the results.

            // For now, we simulate the work with a placeholder.
            Log.d("AIBatchGradingWorker", "Simulating AI grading for ${submissions.size} submissions...")
            delay(10000) // Simulate a 10-second grading process.

            for (submission in submissions) {
                val gradedAnswers = submission.answers?.map { answer ->
                    val question = assessment.questions.find { it.id == answer.questionId }
                    // Placeholder AI logic:
                    val score = (1..question!!.maxScore).random() // Give a random score.
                    val feedback = "This is AI-generated placeholder feedback for student ${submission.studentName}."
                    answer.copy(score = score, feedback = feedback)
                }
                // Save the "AI-graded" submission to the database.
                // It's now ready to be sent by the tutor.
                val gradedSubmission = submission.copy(
                    answers = gradedAnswers,
                    feedbackStatus = FeedbackStatus.SENT_PENDING_ACK // Mark as ready to send
                )
                repository.assessmentDao.insertSubmission(gradedSubmission)
            }

            Log.d("AIBatchGradingWorker", "AI batch grading simulation complete.")
            Result.success()

        } catch (e: Exception) {
            Log.e("AIBatchGradingWorker", "Error during batch grading", e)
            Result.failure()
        }
    }

    companion object {
        const val KEY_ASSESSMENT_ID = "KEY_ASSESSMENT_ID"
    }
}