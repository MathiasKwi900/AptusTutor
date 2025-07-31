package com.nexttechtitan.aptustutor.ui.tutor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/** UI state for the tutor's session history screen. */
data class TutorHistoryUiState(
    val isLoading: Boolean = true,
    val sessions: List<SessionWithClassDetails> = emptyList(),
    val error: String? = null
)

/**
 * ViewModel for the tutor's history screen.
 * It fetches all past sessions conducted by the tutor to be displayed in a list.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TutorHistoryViewModel @Inject constructor(
    private val repository: AptusTutorRepository,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TutorHistoryUiState())
    val uiState: StateFlow<TutorHistoryUiState> = _uiState.asStateFlow()

    init {
        // This flow automatically re-fetches history if the user ID changes.
        userPreferencesRepository.userIdFlow
            .filterNotNull()
            .flatMapLatest { tutorId ->
                repository.getTutorHistory(tutorId)
            }
            .onEach { sessions ->
                _uiState.update { it.copy(isLoading = false, sessions = sessions) }
            }
            .launchIn(viewModelScope)
    }

    fun getAssessmentsForSession(sessionId: String) =
        repository.assessmentDao.getAssessmentsForSession(sessionId)
}