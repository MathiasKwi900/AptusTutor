package com.nexttechtitan.aptustutor.ui.student

import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.data.AptusTutorRepository
import com.nexttechtitan.aptustutor.data.AssessmentAnswer
import com.nexttechtitan.aptustutor.data.AssessmentSubmission
import com.nexttechtitan.aptustutor.data.DiscoveredSession
import com.nexttechtitan.aptustutor.data.RepositoryEvent
import com.nexttechtitan.aptustutor.data.SessionHistoryItem
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import com.nexttechtitan.aptustutor.ui.AptusTutorScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Manages the UI state and business logic for the [StudentDashboardScreen].
 * This ViewModel handles:
 * - Discovering and connecting to nearby tutor sessions.
 * - Managing the lifecycle of an active assessment, including its timer.
 * - Collecting and submitting student answers.
 * - Maintaining and refreshing the student's session history.
 * - Responding to real-time events from the repository, like receiving new feedback.
 */
@HiltViewModel
class StudentDashboardViewModel @Inject constructor(
    private val repository: AptusTutorRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    /** The single source of truth for the student's UI, sourced directly from the repository. */
    val uiState = repository.studentUiState

    // StateFlow for the assessment countdown timer.
    private val _timeLeft = MutableStateFlow(0)
    val timeLeft = _timeLeft.asStateFlow()
    private var timerJob: Job? = null

    // A flow for emitting one-off events to the UI, like snackbar messages.
    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    // A flow for emitting navigation commands to be handled by the UI layer.
    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents = _navigationEvents.asSharedFlow()

    // In-memory cache for student's answers during an active assessment.
    val textAnswers = mutableStateMapOf<String, String>()
    val imageAnswers = mutableStateMapOf<String, Uri>()
    private var currentSubmissionId: String? = null

    private val _sessionHistory = MutableStateFlow<List<SessionHistoryItem>>(emptyList())
    val sessionHistory = _sessionHistory.asStateFlow()

    init {
        viewModelScope.launch {
            repository.events.collect { event ->
                when (event) {
                    is RepositoryEvent.NewFeedbackReceived -> {
                        _events.emit("New graded feedback received!")
                        refreshHistory()
                    }
                }
            }
        }
        refreshHistory()
    }

    /**
     * Fetches and rebuilds the student's session history list.
     * It performs a stable, one-time read of sessions and submissions to prevent
     * redundant processing when the underlying flows emit new data.
     */
    fun refreshHistory() {
        viewModelScope.launch {
            val studentId = userPreferencesRepository.userIdFlow.first() ?: return@launch
            val attendedSessions = repository.getAttendedSessionsForStudent(studentId).first()
            val submissions = repository.getSubmissionsForStudent(studentId).first()

            val historyItems = attendedSessions.map { sessionWithDetails ->
                SessionHistoryItem(
                    sessionWithDetails = sessionWithDetails,
                    hasSubmission = submissions.any { it.sessionId == sessionWithDetails.session.sessionId }
                )
            }
            _sessionHistory.value = historyItems
        }
    }

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

    /**
     * Starts the assessment timer and prepares the state for a new submission.
     * It clears any previous answers and launches a coroutine to decrement the timer,
     * which will auto-submit the assessment when it reaches zero.
     */
    fun startAssessmentTimer(durationInMinutes: Int) {
        timerJob?.cancel()
        textAnswers.clear()
        imageAnswers.clear()
        currentSubmissionId = null
        _timeLeft.value = durationInMinutes * 60
        timerJob = viewModelScope.launch {
            while (_timeLeft.value > 0) {
                delay(1000)
                _timeLeft.value--
            }
            if (_timeLeft.value <= 0) {
                submitAssessment(true)
            }
        }
    }

    fun updateTextAnswer(questionId: String, answer: String) {
        textAnswers[questionId] = answer
    }

    fun updateImageAnswer(questionId: String, uri: Uri) {
        imageAnswers[questionId] = uri
    }

    /**
     * Compiles all cached answers into a final [AssessmentSubmission] object and sends
     * it to the repository for network transfer. This also handles auto-submission logic
     * and clears the active assessment state upon completion.
     */
    fun submitAssessment(isAutoSubmit: Boolean = false) {
        timerJob?.cancel()
        viewModelScope.launch {
            val assessment = uiState.value.activeAssessment ?: return@launch
            val studentId = userPreferencesRepository.userIdFlow.first() ?: return@launch
            val studentName = userPreferencesRepository.userNameFlow.first() ?: "Student"

            val finalAnswers = assessment.questions.map { q ->
                val textResponse = textAnswers[q.id]
                val imageUri = imageAnswers[q.id]
                val finalTextResponse = if (isAutoSubmit && textResponse.isNullOrBlank() && imageUri == null) {
                    "[NO ANSWER - TIME UP]"
                } else {
                    textResponse
                }
                AssessmentAnswer(
                    questionId = q.id,
                    textResponse = finalTextResponse,
                    imageFilePath = imageUri?.path
                )
            }

            val submission = AssessmentSubmission(
                sessionId = assessment.sessionId,
                studentId = studentId,
                submissionId = getSubmissionId(),
                studentName = studentName,
                assessmentId = assessment.id,
                answers = finalAnswers
            )

            repository.submitAssessment(submission, imageAnswers)
            _events.emit("Assessment submitted successfully!")
            repository.clearActiveAssessmentForStudent()
            currentSubmissionId = null
        }
    }

    /**
     * Generates and caches a unique ID for the current submission attempt.
     * This ensures the same ID is used for both the metadata payload and any
     * associated file payloads, linking them together on the tutor's device.
     */
    fun getSubmissionId(): String {
        if (currentSubmissionId == null) {
            currentSubmissionId = UUID.randomUUID().toString()
        }
        return currentSubmissionId!!
    }

    /**
     * Toggles the user's role between STUDENT and TUTOR in preferences and
     * emits a navigation event to switch to the corresponding dashboard.
     */
    fun switchUserRole() {
        viewModelScope.launch {
            val currentRole = userPreferencesRepository.userRoleFlow.first()
            val newRole = if (currentRole == "TUTOR") "STUDENT" else "TUTOR"
            repository.switchUserRole(newRole)

            val destination = if (newRole == "TUTOR") {
                AptusTutorScreen.TutorDashboard.name
            } else {
                AptusTutorScreen.StudentDashboard.name
            }
            _navigationEvents.emit(destination)
        }
    }

    fun getAssessmentsForSession(sessionId: String) =
        repository.assessmentDao.getAssessmentsForSession(sessionId)

    fun leaveSession() {
        repository.disconnectFromSession()
    }

    fun errorShown() {
        repository.errorShown()
    }
}