package com.nexttechtitan.aptustutor.ui.tutor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.ai.AiGradeResponse
import com.nexttechtitan.aptustutor.ai.GemmaAiService
import com.nexttechtitan.aptustutor.data.AptusTutorRepository
import com.nexttechtitan.aptustutor.data.Assessment
import com.nexttechtitan.aptustutor.data.AssessmentAnswer
import com.nexttechtitan.aptustutor.data.AssessmentQuestion
import com.nexttechtitan.aptustutor.data.AssessmentSubmission
import com.nexttechtitan.aptustutor.data.FeedbackStatus
import com.nexttechtitan.aptustutor.data.ModelStatus
import com.nexttechtitan.aptustutor.data.QuestionType
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import com.nexttechtitan.aptustutor.di.AiDispatcher
import com.nexttechtitan.aptustutor.utils.CapabilityResult
import com.nexttechtitan.aptustutor.utils.DeviceCapability
import com.nexttechtitan.aptustutor.utils.DeviceHealthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The immutable UI state for the submission grading screen. */
data class SubmissionDetailsUiState(
    val submission: AssessmentSubmission? = null,
    val assessment: Assessment? = null,
    /** A map holding the tutor's draft scores and feedback before they are saved. */
    val draftAnswers: Map<String, AssessmentAnswer> = emptyMap(),
    val feedbackSent: Boolean = false,
    /** The ID of the question currently being graded by the AI, or null if none. */
    val isGradingQuestionId: String? = null,
    val isGradingEntireSubmission: Boolean = false,
    val gradingProgressText: String? = null,
    val deviceHealth: CapabilityResult? = null
)

/**
 * ViewModel for the [SubmissionDetailsScreen].
 * This screen is for grading a single student's submission. The ViewModel orchestrates:
 * - Loading the submission and its associated assessment.
 * - Managing draft scores and feedback in memory.
 * - Triggering on-device AI grading for one or all questions.
 * - Saving drafts or sending final feedback to the repository.
 */

@HiltViewModel
class SubmissionDetailsViewModel @Inject constructor(
    private val repository: AptusTutorRepository,
    private val gemmaAiService: GemmaAiService,
    private val deviceHealthManager: DeviceHealthManager,
    @AiDispatcher private val aiDispatcher: CoroutineDispatcher,
    userPreferencesRepo: UserPreferencesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val submissionId: String = savedStateHandle.get("submissionId")!!

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    private val _navigationEvents = MutableSharedFlow<Unit>()
    val navigationEvents: SharedFlow<Unit> = _navigationEvents.asSharedFlow()

    private val _uiState = MutableStateFlow(SubmissionDetailsUiState())
    val uiState: StateFlow<SubmissionDetailsUiState> = _uiState.asStateFlow()

    private var healthMonitoringJob: Job? = null

    val modelStatus = userPreferencesRepo.aiModelStatusFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelStatus.NOT_DOWNLOADED)

    val modelState = gemmaAiService.modelState

    init {
        viewModelScope.launch {
            // Perform an initial, one-time read of the submission to populate the drafts.
            // This decouples the tutor's edits from the database until they explicitly save.
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

        // Subsequently, launch flows that continuously observe the database for any
        // external changes and update the UI state accordingly.
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

    /** Updates the score for a question in the local draft state. */
    fun onScoreChange(questionId: String, newScore: String, maxScore: Int) {
        val score = newScore.toIntOrNull()?.coerceIn(0, maxScore)
        _uiState.update { currentState ->
            val newDrafts = currentState.draftAnswers.toMutableMap()
            newDrafts[questionId]?.let {
                newDrafts[questionId] = it.copy(score = score)
            }
            currentState.copy(
                draftAnswers = newDrafts
            )
        }
    }

    /** Updates the feedback for a question in the local draft state. */
    fun onFeedbackChange(questionId: String, newFeedback: String) {
        _uiState.update { currentState ->
            val newDrafts = currentState.draftAnswers.toMutableMap()
            newDrafts[questionId]?.let {
                newDrafts[questionId] = it.copy(feedback = newFeedback)
            }
            currentState.copy(
                draftAnswers = newDrafts
            )
        }
    }

    /**
     * Finalizes the submission with the latest drafts and sends it to the student.
     * This requires all questions to be fully graded first.
     */
    fun sendFeedback() {
        viewModelScope.launch {
            _uiState.value.submission?.let { submission ->
                val finalSubmission = submission.copy(answers = _uiState.value.draftAnswers.values.toList())

                if (!isEntireSubmissionGraded(finalSubmission)) {
                    _toastEvents.emit("All questions must be graded and saved before sending.")
                    return@launch
                }

                repository.sendFeedbackAndMarkAttendance(finalSubmission)
                _uiState.update { it.copy(feedbackSent = true) }
                _toastEvents.emit("Feedback sent to student!")
                _navigationEvents.emit(Unit)
            }
        }
    }

    private fun getQuestionNumber(questionId: String): Int {
        return (_uiState.value.assessment?.questions?.indexOfFirst { it.id == questionId } ?: -1) + 1
    }

    private fun startHealthMonitoring() {
        healthMonitoringJob?.cancel()
        healthMonitoringJob = viewModelScope.launch {
            while (isActive) {
                _uiState.update {
                    it.copy(deviceHealth = deviceHealthManager.checkDeviceCapability())
                }
                delay(2000)
            }
        }
    }

    /**
     * Triggers the AI to grade the entire submission question by question.
     * It runs on a dedicated AI dispatcher, provides progress updates to the UI,
     * and monitors device health to pause if the device overheats.
     */
    fun gradeEntireSubmission() {
        viewModelScope.launch(aiDispatcher) {
            val initialCheck = deviceHealthManager.checkDeviceCapability()
            if (initialCheck.capability == DeviceCapability.UNSUPPORTED) {
                _toastEvents.emit("AI Grading Halted: ${initialCheck.message}")
                return@launch
            }
            if (uiState.value.isGradingEntireSubmission || uiState.value.isGradingQuestionId != null) {
                _toastEvents.emit("AI is already busy.")
                return@launch
            }

            val questionsToGrade = uiState.value.assessment?.questions ?: emptyList()
            if (questionsToGrade.isEmpty()) {
                _toastEvents.emit("No questions to grade.")
                return@launch
            }

            _uiState.update { it.copy(isGradingEntireSubmission = true) }
            _toastEvents.emit("Starting full submission AI grading...")
            startHealthMonitoring()

            try {
                for ((index, question) in questionsToGrade.withIndex()) {
                    val loopCheck = deviceHealthManager.checkDeviceCapability()
                    if (loopCheck.capability == DeviceCapability.UNSUPPORTED) {
                        _toastEvents.emit("Grading paused due to device health: ${loopCheck.message}")
                        _uiState.update {
                            it.copy(
                                isGradingEntireSubmission = false,
                                gradingProgressText = "Paused"
                            )
                        }
                        return@launch
                    }

                    val progressText =
                        "Grading Question ${index + 1} of ${questionsToGrade.size}..."
                    _uiState.update { it.copy(gradingProgressText = progressText) }

                    val answer =
                        uiState.value.submission?.answers?.find { it.questionId == question.id }

                    // This internal function contains the core logic for grading one question.
                    gradeSingleQuestionInternal(question, answer)
                }
            } catch (e: Exception) {
                _toastEvents.emit("An unexpected error occurred: ${e.message}")
            } finally {
                healthMonitoringJob?.cancel()
                _uiState.update { it.copy(isGradingEntireSubmission = false, gradingProgressText = null) }
            }

            _toastEvents.emit("AI grading complete!")
        }
    }

    /**
     * Internal function to handle grading a single question.
     * This contains the core logic that is looped by gradeEntireSubmission.
     */
    private suspend fun gradeSingleQuestionInternal(question: AssessmentQuestion, answer: AssessmentAnswer?) {
        _uiState.update { it.copy(isGradingQuestionId = question.id) }

        val imageBitmap: Bitmap? = answer?.imageFilePath?.let { BitmapFactory.decodeFile(it) }

        if (answer?.textResponse.isNullOrBlank() && imageBitmap == null) {
            val blankResult = AiGradeResponse(0, "No answer provided.")
            updateDraftsWithResult(question, blankResult)
            _uiState.update { it.copy(isGradingQuestionId = null) }
            return
        }

        val result = gemmaAiService.grade(question, answer, imageBitmap)

        result.onSuccess { response ->
            val clampedScore = response.score?.coerceIn(0, question.maxScore)
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
            _toastEvents.emit("AI Grading Failed for Q#${getQuestionNumber(question.id)}: ${error.message}")
        }

        _uiState.update { it.copy(isGradingQuestionId = null) }
    }

    private fun updateDraftsWithResult(question: AssessmentQuestion, response: AiGradeResponse) {
        val clampedScore = response.score?.coerceIn(0, question.maxScore)
        _uiState.update { currentState ->
            val newDrafts = currentState.draftAnswers.toMutableMap()
            val existingAnswer = newDrafts[question.id]?: AssessmentAnswer(questionId = question.id)
            newDrafts[question.id] = existingAnswer.copy(
                score = clampedScore,
                feedback = response.feedback
            )
            currentState.copy(draftAnswers = newDrafts)
        }
    }

    /**
     * Provides a shortcut to automatically grade all multiple-choice questions
     * by comparing the student's answer to the correct answer index stored in the
     * question's marking guide.
     */
    fun autoGradeMcqQuestions() {
        viewModelScope.launch {
            val assessment = _uiState.value.assessment?: return@launch
            val submission = _uiState.value.submission?: return@launch

            val mcqQuestions = assessment.questions.filter { it.type == QuestionType.MULTIPLE_CHOICE }
            if (mcqQuestions.isEmpty()) {
                _toastEvents.emit("No multiple choice questions found in this assessment.")
                return@launch
            }

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
            _toastEvents.emit("Auto-grading complete for $gradedCount question(s).")
        }
    }

    /** Persists the current draft answers to the local database without sending to the student. */
    fun saveAllDrafts() {
        viewModelScope.launch {
            val submission = _uiState.value.submission?: return@launch
            val draftAnswers = _uiState.value.draftAnswers

            val submissionWithDrafts = submission.copy(answers = draftAnswers.values.toList())
            repository.saveSubmissionDraft(submissionWithDrafts)
            _toastEvents.emit("Draft saved successfully.")
            _navigationEvents.emit(Unit)
        }
    }
}