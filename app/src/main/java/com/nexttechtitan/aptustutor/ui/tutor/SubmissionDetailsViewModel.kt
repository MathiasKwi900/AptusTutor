package com.nexttechtitan.aptustutor.ui.tutor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.data.AptusTutorRepository
import com.nexttechtitan.aptustutor.data.Assessment
import com.nexttechtitan.aptustutor.data.AssessmentSubmission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubmissionDetailsUiState(
    val submission: AssessmentSubmission? = null,
    val assessment: Assessment? = null
)

@HiltViewModel
class SubmissionDetailsViewModel @Inject constructor(
    private val repository: AptusTutorRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val submissionId: String = savedStateHandle.get("submissionId")!!

    val uiState: StateFlow<SubmissionDetailsUiState> =
        combine(
            repository.getSubmissionFlow(submissionId),
            repository.getAssessmentFlow(submissionId) // We'll create this next
        ) { submission, assessment ->
            SubmissionDetailsUiState(submission, assessment)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SubmissionDetailsUiState()
        )

    fun saveGrade(questionId: String, score: Int, feedback: String) {
        viewModelScope.launch {
            repository.saveManualGrade(submissionId, questionId, score, feedback)
        }
    }

    fun sendFeedback() {
        viewModelScope.launch {
            uiState.value.submission?.let {
                repository.sendFeedbackToStudent(it)
            }
        }
    }
}