package com.nexttechtitan.aptustutor.ui.tutor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.ai.GemmaAiService
import com.nexttechtitan.aptustutor.ai.ThermalManager
import com.nexttechtitan.aptustutor.data.AptusTutorRepository
import com.nexttechtitan.aptustutor.data.Assessment
import com.nexttechtitan.aptustutor.data.AssessmentAnswer
import com.nexttechtitan.aptustutor.data.AssessmentQuestion
import com.nexttechtitan.aptustutor.data.AssessmentSubmission
import com.nexttechtitan.aptustutor.data.FeedbackStatus
import com.nexttechtitan.aptustutor.data.QuestionType
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubmissionDetailsUiState(
    val submission: AssessmentSubmission? = null,
    val assessment: Assessment? = null,
    val draftAnswers: Map<String, AssessmentAnswer> = emptyMap(),
    val feedbackSent: Boolean = false,
    val isGradingQuestionId: String? = null,
    val isGradingEntireSubmission: Boolean = false,
    val gradingProgressText: String? = null
)

@HiltViewModel
class SubmissionDetailsViewModel @Inject constructor(
    private val userPreferencesRepo: UserPreferencesRepository,
    private val repository: AptusTutorRepository,
    private val gemmaAiService: GemmaAiService,
    private val thermalManager: ThermalManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val submissionId: String = savedStateHandle.get("submissionId")!!
    private val TAG = "AptusTutorDebug"

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    private val _navigationEvents = MutableSharedFlow<Unit>()
    val navigationEvents: SharedFlow<Unit> = _navigationEvents.asSharedFlow()

    private val _uiState = MutableStateFlow(SubmissionDetailsUiState())
    val uiState: StateFlow<SubmissionDetailsUiState> = _uiState.asStateFlow()

    val modelState = gemmaAiService.modelState
    val modelInitialized = userPreferencesRepo.aiModelInitializedFlow

    init {
        Log.d(TAG, "Initializing for submissionId: $submissionId")
        viewModelScope.launch {
            val initialSubmission = repository.getSubmissionFlow(submissionId).filterNotNull().first()
            val hasFeedbackBeenSent = initialSubmission.feedbackStatus == FeedbackStatus.DELIVERED ||
                    initialSubmission.feedbackStatus == FeedbackStatus.SENT_PENDING_ACK

            _uiState.update {
                it.copy(
                    submission = initialSubmission,
                    draftAnswers = initialSubmission.answers?.associateBy { answer -> answer.questionId }?: emptyMap(),
                    feedbackSent = hasFeedbackBeenSent
                )
            }
        }

        repository.getSubmissionFlow(submissionId)
            .filterNotNull()
            .onEach { dbSubmission -> _uiState.update { it.copy(submission = dbSubmission) } }
            .launchIn(viewModelScope)

        repository.getAssessmentFlowBySubmission(submissionId)
            .onEach { assessment -> _uiState.update { it.copy(assessment = assessment) } }
            .launchIn(viewModelScope)
    }

    private fun isEntireSubmissionGraded(submission: AssessmentSubmission): Boolean {
        return submission.answers?.all { it.score != null && !it.feedback.isNullOrBlank() } ?: false
    }

    // Called when the score text field changes.
    fun onScoreChange(questionId: String, newScore: String, maxScore: Int) {
        val score = newScore.toIntOrNull()?.coerceIn(0, maxScore)
        _uiState.update { currentState ->
            val newDrafts = currentState.draftAnswers.toMutableMap()
            newDrafts[questionId]?.let {
                newDrafts[questionId] = it.copy(score = score)
            }
            // Any edit "unsaves" the question, requiring the tutor to save again.
            currentState.copy(
                draftAnswers = newDrafts
            )
        }
    }

    // Called when the feedback text field changes.
    fun onFeedbackChange(questionId: String, newFeedback: String) {
        _uiState.update { currentState ->
            val newDrafts = currentState.draftAnswers.toMutableMap()
            newDrafts[questionId]?.let {
                newDrafts[questionId] = it.copy(feedback = newFeedback)
            }
            // Any edit "unsaves" the question.
            currentState.copy(
                draftAnswers = newDrafts
            )
        }
    }

    fun sendFeedback() {
        viewModelScope.launch {
            _uiState.value.submission?.let { submission ->
                // Ensure the submission object being sent has the latest drafts.
                val finalSubmission = submission.copy(answers = _uiState.value.draftAnswers.values.toList())

                if (!isEntireSubmissionGraded(finalSubmission)) {
                    _toastEvents.emit("All questions must be graded and saved before sending.")
                    return@launch
                }

                repository.sendFeedbackAndMarkAttendance(finalSubmission)
                _uiState.update { it.copy(feedbackSent = true) }
                _toastEvents.emit("Feedback sent to student!")
                _navigationEvents.emit(Unit) // Navigate back after sending.
            }
        }
    }

    private fun getQuestionNumber(questionId: String): Int {
        return (_uiState.value.assessment?.questions?.indexOfFirst { it.id == questionId } ?: -1) + 1
    }

    fun gradeEntireSubmission() {
        viewModelScope.launch(Dispatchers.Default) {
            Log.d(TAG, "gradeEntireSubmission called.")
            if (gemmaAiService.modelState.value !is GemmaAiService.ModelState.Ready &&
                gemmaAiService.modelState.value !is GemmaAiService.ModelState.ModelReadyCold) {
                _toastEvents.emit("AI is not ready yet. Still preparing in the background.")
                return@launch
            }
            if (uiState.value.isGradingEntireSubmission || uiState.value.isGradingQuestionId != null) {
                Log.w(TAG, "AI is already busy grading the entire submission.")
                _toastEvents.emit("AI is already busy.")
                return@launch
            }

            val questionsToGrade = uiState.value.assessment?.questions ?: emptyList()
            if (questionsToGrade.isEmpty()) {
                Log.w(TAG, "No questions found in assessment to grade.")
                _toastEvents.emit("No questions to grade.")
                return@launch
            }

            Log.i(TAG, "Starting full submission AI grading for ${questionsToGrade.size} questions.")
            _uiState.update { it.copy(isGradingEntireSubmission = true) }
            _toastEvents.emit("Starting full submission AI grading...")

            for ((index, question) in questionsToGrade.withIndex()) {
                while (!thermalManager.isSafeToProceed()) {
                    Log.w(TAG, "Device is overheating. Pausing grading for 15 seconds.")
                    _uiState.update { it.copy(gradingProgressText = "Device is hot. Cooling down...") }
                    kotlinx.coroutines.delay(15000)
                }

                val progressText = "Grading Question ${index + 1} of ${questionsToGrade.size}..."
                Log.d(TAG, progressText)
                _uiState.update { it.copy(gradingProgressText = progressText) }

                val answer = uiState.value.submission?.answers?.find { it.questionId == question.id }
                gradeSingleQuestionInternal(question, answer)
            }

            Log.i(TAG, "AI grading complete for all questions.")
            _uiState.update { it.copy(isGradingEntireSubmission = false, gradingProgressText = null) }
            _toastEvents.emit("AI grading complete!")
        }
    }

    /**
     * Internal function to handle grading a single question.
     * This contains the core logic that is looped by gradeEntireSubmission.
     */
    private suspend fun gradeSingleQuestionInternal(question: AssessmentQuestion, answer: AssessmentAnswer?) {
        _uiState.update { it.copy(isGradingQuestionId = question.id) }
        Log.d(TAG, "gradeSingleQuestionInternal started for Q-ID: ${question.id}")

        val prompt = buildPrompt(question, answer)
        val imageBitmap: Bitmap? = answer?.imageFilePath?.let { BitmapFactory.decodeFile(it) }

        val result = gemmaAiService.grade(prompt, imageBitmap)

        result.onSuccess { response ->
            Log.i(TAG, "Successfully graded Q-ID: ${question.id}. Score: ${response.score}")
            val clampedScore = response.score?.coerceIn(0, question.maxScore)
            if (clampedScore != response.score) {
                Log.w(TAG, "AI returned score (${response.score}) outside range. Clamped to $clampedScore.")
            }
            _uiState.update { currentState ->
                val newDrafts = currentState.draftAnswers.toMutableMap()
                val existingAnswer = newDrafts[question.id] ?: AssessmentAnswer(questionId = question.id)
                newDrafts[question.id] = existingAnswer.copy(
                    score = clampedScore,
                    feedback = response.feedback
                )
                currentState.copy(draftAnswers = newDrafts)
            }
        }.onFailure { error ->
            Log.e(TAG, "AI Grading Failed for Q-ID: ${question.id}", error)
            _toastEvents.emit("AI Grading Failed for Q#${getQuestionNumber(question.id)}: ${error.message}")
        }

        _uiState.update { it.copy(isGradingQuestionId = null) }
    }

    fun autoGradeMcqQuestions() {
        viewModelScope.launch {
            val assessment = _uiState.value.assessment?: return@launch
            val submission = _uiState.value.submission?: return@launch

            val mcqQuestions = assessment.questions.filter { it.type == QuestionType.MULTIPLE_CHOICE }
            if (mcqQuestions.isEmpty()) {
                _toastEvents.emit("No multiple choice questions found in this assessment.")
                return@launch
            }

            _toastEvents.emit("Auto-grading ${mcqQuestions.size} questions...")

            val newDrafts = _uiState.value.draftAnswers.toMutableMap()
            var gradedCount = 0

            mcqQuestions.forEach { question ->
                val correctAnswerIndex = question.markingGuide.toIntOrNull()
                val studentAnswer = submission.answers?.find { it.questionId == question.id }
                val studentAnswerIndex = studentAnswer?.textResponse?.toIntOrNull()

                if (correctAnswerIndex!= null && studentAnswer!= null) {
                    val score = if (studentAnswerIndex == correctAnswerIndex) question.maxScore else 0
                    val feedback = if (studentAnswerIndex == correctAnswerIndex) {
                        "Correct."
                    } else {
                        val correctOptionText = question.options?.getOrNull(correctAnswerIndex)?: "N/A"
                        "Incorrect. The correct answer was ${('A' + correctAnswerIndex)}: $correctOptionText"
                    }

                    newDrafts[question.id] = studentAnswer.copy(score = score, feedback = feedback)
                    gradedCount++
                }
            }

            _uiState.update {
                it.copy(
                    draftAnswers = newDrafts
                )
            }
            _toastEvents.emit("Auto-grading complete for $gradedCount questions.")
        }
    }

    fun saveAllDrafts() {
        viewModelScope.launch {
            val submission = _uiState.value.submission?: return@launch
            val draftAnswers = _uiState.value.draftAnswers

            val submissionWithDrafts = submission.copy(answers = draftAnswers.values.toList())
            repository.saveSubmissionDraft(submissionWithDrafts)
            _toastEvents.emit("Draft saved successfully.")
        }
    }

    private fun buildPrompt(question: AssessmentQuestion, answer: AssessmentAnswer?): String {
        val studentAnswerText: String
        val answerInstruction: String

        if (answer == null || (answer.textResponse.isNullOrBlank() && answer.imageFilePath.isNullOrBlank())) {
            studentAnswerText = ""
            answerInstruction = "The student did not provide an answer. Your task is to assign a score\n" +
                    "of 0 and use the feedback field to politely provide the correct answer from the MARKING GUIDE."
        } else {
            studentAnswerText = when {
                !answer.textResponse.isNullOrBlank() &&!answer.imageFilePath.isNullOrBlank() -> """
                Typed Answer: "${answer.textResponse}"
                (An image was also provided for context).
                """.trimIndent()
                !answer.textResponse.isNullOrBlank() -> "Typed Answer: \"${answer.textResponse}\""
                else -> "An image of a handwritten answer was provided."
            }
            answerInstruction = "Analyze the student's answer for conceptual understanding; it does\n" +
                    "not need to be a perfect word-for-word match. Use your general knowledge on the topic to determine\n" +
                    "how close their response is to the correct answer's intent. Then, provide specific, comparative\n" +
                    "feedback. The final score MUST be an integer between 0 and ${question.maxScore}, inclusive."
        }

        return """
        ROLE: Expert Teaching Assistant.
        TASK: Grade the student's answer.
        QUESTION: "${question.text}"
        MARKING GUIDE: "${question.markingGuide}"
        MAX SCORE: ${question.maxScore}
        STUDENT ANSWER: $studentAnswerText
        INSTRUCTIONS: $answerInstruction

        OUTPUT FORMAT: Respond ONLY with a valid JSON object. Do not add any other text.
        {
          "score": <integer score>,
          "feedback": "<concise feedback, under 30 words>"
        }
    """.trimIndent()
    }
}