package com.nexttechtitan.aptustutor.ui.student

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SubmissionResultUiState(
    val isLoading: Boolean = true,
    val submissionWithAssessment: SubmissionWithAssessment? = null,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubmissionResultViewModel @Inject constructor(
    repository: AptusTutorRepository,
    userPreferencesRepository: UserPreferencesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val assessmentId: String = savedStateHandle.get("assessmentId")!!

    val uiState: StateFlow<SubmissionResultUiState> = userPreferencesRepository.userIdFlow
        .filterNotNull()
        .flatMapLatest { studentId ->
            repository.assessmentDao.getSubmissionForStudentByAssessment(studentId, assessmentId)
        }
        .map { result ->
            if (result?.submission != null && result.assessment != null) {
                SubmissionResultUiState(isLoading = false, submissionWithAssessment = result)
            } else {
                SubmissionResultUiState(isLoading = false, error = "Could not find submission results.")
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SubmissionResultUiState()
        )
}