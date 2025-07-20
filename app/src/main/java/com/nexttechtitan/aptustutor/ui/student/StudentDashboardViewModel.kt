package com.nexttechtitan.aptustutor.ui.student

import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.data.AptusTutorRepository
import com.nexttechtitan.aptustutor.data.AssessmentAnswer
import com.nexttechtitan.aptustutor.data.AssessmentSubmission
import com.nexttechtitan.aptustutor.data.DiscoveredSession
import com.nexttechtitan.aptustutor.data.QuestionType
import com.nexttechtitan.aptustutor.data.SessionHistoryItem
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudentDashboardViewModel @Inject constructor(
    private val repository: AptusTutorRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState = repository.studentUiState

    private val _timeLeft = MutableStateFlow(0)
    val timeLeft = _timeLeft.asStateFlow()
    private var timerJob: Job? = null

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    val textAnswers = mutableStateMapOf<String, String>()
    val imageAnswers = mutableStateMapOf<String, Uri>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionHistory: StateFlow<List<SessionHistoryItem>> =
        userPreferencesRepository.userIdFlow.filterNotNull().flatMapLatest { studentId ->
            val attendedSessionsFlow = repository.getAttendedSessionsForStudent(studentId)
            val submissionsFlow = repository.getSubmissionsForStudent(studentId)

            combine(attendedSessionsFlow, submissionsFlow) { sessions, submissions ->
                sessions.map { sessionWithDetails ->
                    SessionHistoryItem(
                        sessionWithDetails = sessionWithDetails,
                        hasSubmission = submissions.any { it.sessionId == sessionWithDetails.session.sessionId }
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun startDiscovery() {
        viewModelScope.launch {
            repository.startStudentDiscovery()
        }
    }

    fun stopDiscovery() {
        repository.stopStudentDiscovery()
    }

    fun joinSession(session: DiscoveredSession, pin: String) {
        stopDiscovery()
        repository.setJoiningState(session.sessionId)
        viewModelScope.launch {
            repository.requestToJoinSession(session, pin)
        }
    }

    fun startAssessmentTimer(durationInMinutes: Int) {
        timerJob?.cancel()
        textAnswers.clear()
        imageAnswers.clear()
        _timeLeft.value = durationInMinutes * 60
        timerJob = viewModelScope.launch {
            while (_timeLeft.value > 0) {
                delay(1000)
                _timeLeft.value--
            }
            if (_timeLeft.value <= 0) {
                submitAssessment(true) // Auto-submit when time is up
            }
        }
    }

    fun updateTextAnswer(questionId: String, answer: String) {
        textAnswers[questionId] = answer
    }

    fun updateImageAnswer(questionId: String, uri: Uri) {
        imageAnswers[questionId] = uri
    }

    fun submitAssessment(isAutoSubmit: Boolean = false) {
        timerJob?.cancel()
        viewModelScope.launch {
            val assessment = uiState.value.activeAssessment ?: return@launch
            val studentId = userPreferencesRepository.userIdFlow.first() ?: return@launch
            val studentName = userPreferencesRepository.userNameFlow.first() ?: "Student"

            val finalAnswers = assessment.questions.map { q ->
                val textResponse = textAnswers[q.id]
                val imageResponseProvided = imageAnswers.containsKey(q.id)
                val finalTextResponse = if (isAutoSubmit && textResponse.isNullOrBlank() && !imageResponseProvided) {
                    "[NO ANSWER - TIME UP]"
                } else {
                    textResponse
                }
                AssessmentAnswer(
                    questionId = q.id,
                    textResponse = finalTextResponse
                )
            }

            val submission = AssessmentSubmission(
                sessionId = assessment.sessionId,
                studentId = studentId,
                studentName = studentName,
                assessmentId = assessment.id,
                answers = finalAnswers
            )

            repository.submitAssessment(submission, imageAnswers)
            _events.emit("Assessment submitted successfully!")
            repository.clearActiveAssessmentForStudent()
        }
    }

    fun switchUserRole() {
        viewModelScope.launch {
            val currentRole = userPreferencesRepository.userRoleFlow.first()
            val newRole = if (currentRole == "TUTOR") "STUDENT" else "TUTOR"
            repository.switchUserRole(newRole)
        }
    }

    fun leaveSession() {
        repository.disconnectFromSession()
    }

    fun errorShown() {
        repository.errorShown()
    }
}