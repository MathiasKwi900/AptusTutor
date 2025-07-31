package com.nexttechtitan.aptustutor.ui.student

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/** Immutable UI state for the submission result screen. */
data class SubmissionResultUiState(
    val isLoading: Boolean = true,
    val submissionWithAssessment: SubmissionWithAssessment? = null,
    val error: String? = null
)

/**
 * ViewModel for the [SubmissionResultScreen]. Its sole responsibility is to
 * load the complete, graded results for a specific assessment and provide it to the UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubmissionResultViewModel @Inject constructor(
    repository: AptusTutorRepository,
    userPreferencesRepository: UserPreferencesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val assessmentId: String = savedStateHandle.get("assessmentId")!!

    /**
     * A reactive flow that provides the UI state. This flow is constructed to be
     * efficient and robust:
     * 1. Starts with `userIdFlow` to ensure we have the current user.
     * 2. `flatMapLatest` automatically triggers a new database query if the user ID changes.
     * 3. `map` transforms the loaded data into the appropriate [SubmissionResultUiState].
     * 4. `stateIn` converts the cold flow into a hot `StateFlow`, caching the last
     * result for the UI and keeping the data subscription alive while the UI is visible.
     */
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