package com.nexttechtitan.aptustutor.ui.tutor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.data.AptusTutorRepository
import com.nexttechtitan.aptustutor.data.Assessment
import com.nexttechtitan.aptustutor.data.AssessmentAnswer
import com.nexttechtitan.aptustutor.data.AssessmentQuestion
import com.nexttechtitan.aptustutor.data.AssessmentSubmission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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
    // This map holds the "live" edits the tutor is making.
    val draftAnswers: Map<String, AssessmentAnswer> = emptyMap(),
    // This set tracks which individual questions have been committed to the DB via the "Save" button.
    val savedQuestionIds: Set<String> = emptySet(),
    // This flag tracks if the final feedback has been sent to the student.
    val feedbackSent: Boolean = false
)

@HiltViewModel
class SubmissionDetailsViewModel @Inject constructor(
    private val repository: AptusTutorRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val submissionId: String = savedStateHandle.get("submissionId")!!

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    private val _navigationEvents = MutableSharedFlow<Unit>()
    val navigationEvents: SharedFlow<Unit> = _navigationEvents.asSharedFlow()

    private val _uiState = MutableStateFlow(SubmissionDetailsUiState())
    val uiState: StateFlow<SubmissionDetailsUiState> = _uiState.asStateFlow()

    init {
        // 1. Initialize the draft state ONCE from the database.
        viewModelScope.launch {
            val initialSubmission = repository.getSubmissionFlow(submissionId).filterNotNull().first()
            val hasFeedbackBeenSent = isEntireSubmissionGraded(initialSubmission)

            _uiState.update {
                it.copy(
                    submission = initialSubmission,
                    draftAnswers = initialSubmission.answers?.associateBy { answer -> answer.questionId } ?: emptyMap(),
                    savedQuestionIds = if (hasFeedbackBeenSent) initialSubmission.answers?.map { a -> a.questionId }?.toSet() ?: emptySet() else emptySet(),
                    feedbackSent = hasFeedbackBeenSent
                )
            }
        }

        // 2. Continuously observe the submission from the DB to keep the base object in sync.
        // This does NOT touch the draftAnswers, allowing the user's edits to persist in the UI.
        repository.getSubmissionFlow(submissionId)
            .filterNotNull()
            .onEach { dbSubmission -> _uiState.update { it.copy(submission = dbSubmission) } }
            .launchIn(viewModelScope)

        // 3. Observe the associated assessment.
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
                draftAnswers = newDrafts,
                savedQuestionIds = currentState.savedQuestionIds - questionId
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
                draftAnswers = newDrafts,
                savedQuestionIds = currentState.savedQuestionIds - questionId
            )
        }
    }

    // Persists the current draft for a single question to the local Room database.
    fun saveGrade(questionId: String) {
        viewModelScope.launch {
            val answerToSave = _uiState.value.draftAnswers[questionId]
            if (answerToSave?.score != null && !answerToSave.feedback.isNullOrBlank()) {
                repository.saveManualGrade(
                    submissionId = submissionId,
                    questionId = questionId,
                    score = answerToSave.score!!,
                    feedback = answerToSave.feedback!!
                )
                _toastEvents.emit("Grade for Q#${getQuestionNumber(questionId)} saved")
                _uiState.update {
                    it.copy(savedQuestionIds = it.savedQuestionIds + questionId)
                }
            } else {
                _toastEvents.emit("A score and feedback are required to save.")
            }
        }
    }

    // Sends the final, graded submission back to the student. This is a final action.
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
}