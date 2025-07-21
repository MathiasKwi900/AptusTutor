package com.nexttechtitan.aptustutor.ui.tutor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class TutorHistoryUiState(
    val isLoading: Boolean = true,
    val sessions: List<SessionWithClassDetails> = emptyList(),
    val selectedSessionId: String? = null,
    val submissionsForSelectedSession: List<SubmissionWithStatus> = emptyList(),
    val error: String? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TutorHistoryViewModel @Inject constructor(
    private val repository: AptusTutorRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TutorHistoryUiState())
    val uiState: StateFlow<TutorHistoryUiState> = _uiState.asStateFlow()

    private val selectedSessionId = MutableStateFlow<String?>(null)

    init {
        // Fetch the list of all past sessions for the logged-in tutor
        userPreferencesRepository.userIdFlow
            .filterNotNull()
            .flatMapLatest { tutorId ->
                repository.getTutorHistory(tutorId)
            }
            .onEach { sessions ->
                _uiState.update { it.copy(isLoading = false, sessions = sessions) }
            }
            .launchIn(viewModelScope)

        // When a session is selected, fetch its submissions and their statuses
        selectedSessionId
            .filterNotNull()
            .flatMapLatest { sessionId ->
                // We need both the submissions and the original assessment to calculate status
                val submissionsFlow = repository.getSubmissionsForSession(sessionId)
                val assessmentFlow = submissionsFlow.filter { it.isNotEmpty() }
                    .flatMapLatest { list -> repository.getAssessmentById(list.first().assessmentId) }
                    .filterNotNull()

                combine(submissionsFlow, assessmentFlow) { submissions, assessment ->
                    submissions.map { submission ->
                        SubmissionWithStatus(
                            submission = submission,
                            statusText = calculateGradingStatus(submission, assessment),
                            feedbackStatus = submission.feedbackStatus
                        )
                    }
                }
            }
            .onEach { submissionsWithStatus ->
                _uiState.update { it.copy(submissionsForSelectedSession = submissionsWithStatus) }
            }
            .launchIn(viewModelScope)
    }

    fun selectSession(sessionId: String) {
        selectedSessionId.value = sessionId
        _uiState.update { it.copy(selectedSessionId = sessionId) }
    }

    fun clearSelectedSession() {
        selectedSessionId.value = null
        _uiState.update { it.copy(selectedSessionId = null, submissionsForSelectedSession = emptyList()) }
    }

    private fun calculateGradingStatus(submission: AssessmentSubmission, assessment: Assessment): String {
        val totalQuestions = assessment.questions.size
        val gradedQuestions = submission.answers?.count { it.score != null && it.feedback != null } ?: 0


        return when {
            gradedQuestions == 0 -> "Not Graded"
            gradedQuestions < totalQuestions -> "In Progress ($gradedQuestions/$totalQuestions)"
            else -> {
                val score = submission.answers?.sumOf { it.score ?: 0 } ?: 0
                val maxScore = assessment.questions.sumOf { it.maxScore }
                "Graded: $score/$maxScore"
            }
        }
    }
}