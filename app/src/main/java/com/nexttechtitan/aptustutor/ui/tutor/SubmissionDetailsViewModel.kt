package com.nexttechtitan.aptustutor.ui.tutor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.data.AptusTutorRepository
import com.nexttechtitan.aptustutor.data.Assessment
import com.nexttechtitan.aptustutor.data.AssessmentAnswer
import com.nexttechtitan.aptustutor.data.AssessmentSubmission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubmissionDetailsUiState(
    val submission: AssessmentSubmission? = null,
    val assessment: Assessment? = null,
    val draftAnswers: Map<String, AssessmentAnswer> = emptyMap(),
    val savedQuestionIds: Set<String> = emptySet()
)

@HiltViewModel
class SubmissionDetailsViewModel @Inject constructor(
    private val repository: AptusTutorRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val submissionId: String = savedStateHandle.get("submissionId")!!

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    private val _uiState = MutableStateFlow(SubmissionDetailsUiState())
    val uiState: StateFlow<SubmissionDetailsUiState> = _uiState

    init {
        repository.getSubmissionFlow(submissionId)
            .filterNotNull()
            .onEach { dbSubmission ->
                _uiState.update { currentState ->
                    currentState.copy(
                        submission = dbSubmission,
                        draftAnswers = dbSubmission.answers.associateBy { it.questionId }
                    )
                }
            }
            .launchIn(viewModelScope)

        repository.getAssessmentFlow(submissionId)
            .onEach { assessment ->
                _uiState.update { it.copy(assessment = assessment) }
            }
            .launchIn(viewModelScope)
    }

    fun onScoreChange(questionId: String, newScore: String) {
        _uiState.update { currentState ->
            val newDrafts = currentState.draftAnswers.toMutableMap()
            newDrafts[questionId]?.let {
                newDrafts[questionId] = it.copy(score = newScore.toIntOrNull())
            }
            currentState.copy(
                draftAnswers = newDrafts,
                savedQuestionIds = currentState.savedQuestionIds - questionId
            )
        }
    }

    fun onFeedbackChange(questionId: String, newFeedback: String) {
        _uiState.update { currentState ->
            val newDrafts = currentState.draftAnswers.toMutableMap()
            newDrafts[questionId]?.let {
                newDrafts[questionId] = it.copy(feedback = newFeedback)
            }
            currentState.copy(
                draftAnswers = newDrafts,
                savedQuestionIds = currentState.savedQuestionIds - questionId
            )
        }
    }

    fun saveGrade(questionId: String) {
        viewModelScope.launch {
            val answerToSave = _uiState.value.draftAnswers[questionId]
            // The score must not be null and the feedback must not be blank.
            if (answerToSave?.score != null && answerToSave.feedback.orEmpty().isNotBlank()) {
                repository.saveManualGrade(
                    submissionId = submissionId,
                    questionId = questionId,
                    score = answerToSave.score!!, // Safe due to check
                    feedback = answerToSave.feedback!! // Safe due to check
                )
                _toastEvents.emit("Grade saved successfully")
                _uiState.update {
                    it.copy(savedQuestionIds = it.savedQuestionIds + questionId)
                }
            } else {
                // Optional: Provide feedback to the user that they can't save yet.
                _toastEvents.emit("Please provide a score and feedback before saving.")
            }
        }
    }

    fun sendFeedback() {
        viewModelScope.launch {
            _uiState.value.submission?.let {
                val updatedSubmission = it.copy(answers = _uiState.value.draftAnswers.values.toList())
                repository.sendFeedbackToStudent(updatedSubmission)
            }
        }
    }
}