package com.nexttechtitan.aptustutor.ui.tutor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.data.AptusTutorRepository
import com.nexttechtitan.aptustutor.data.Assessment
import com.nexttechtitan.aptustutor.data.AssessmentBlueprint
import com.nexttechtitan.aptustutor.data.AssessmentSubmission
import com.nexttechtitan.aptustutor.data.ClassWithStudents
import com.nexttechtitan.aptustutor.data.ConnectionRequest
import com.nexttechtitan.aptustutor.data.FeedbackStatus
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import com.nexttechtitan.aptustutor.ui.AptusTutorScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A UI model that bundles a submission with a calculated, human-readable status. */
data class SubmissionWithStatus(
    val submission: AssessmentSubmission,
    val statusText: String,
    val feedbackStatus: FeedbackStatus
)

/**
 * Manages the UI state and logic for the [TutorDashboardScreen].
 * This is the primary ViewModel for the tutor experience. It handles:
 * - Displaying the tutor's list of classes.
 * - Starting and stopping class sessions.
 * - Managing student connection requests and the live roster.
 * - Sending assessments and tracking their submission status.
 */
@HiltViewModel
class TutorDashboardViewModel @Inject constructor(
    private val repository: AptusTutorRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    /** The single source of truth for the active session UI, sourced from the repository. */
    val uiState = repository.tutorUiState

    // Standard event flows for one-off UI actions (toasts and navigation).
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()
    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents = _navigationEvents.asSharedFlow()

    /** A reactive flow that provides the tutor's list of created classes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val tutorClasses: StateFlow<List<ClassWithStudents>> =
        userPreferencesRepository.userIdFlow.filterNotNull().flatMapLatest { tutorId ->
            repository.getClassesForTutor(tutorId)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * A flow that provides a live list of submissions for the currently viewed assessment,
     * each enhanced with a calculated grading status string.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val submissionsWithStatus: StateFlow<List<SubmissionWithStatus>> =
        uiState.flatMapLatest { state ->
            val assessmentId = state.viewingAssessmentId
            if (assessmentId != null) {
                // Combine the submissions list with the assessment details to calculate status.
                combine(
                    repository.getSubmissionsFlowForAssessment(assessmentId),
                    flowOf(state.sentAssessments.find { it.id == assessmentId }).filterNotNull()
                ) { submissions, assessment ->
                    submissions.map { submission ->
                        SubmissionWithStatus(
                            submission = submission,
                            statusText = calculateGradingStatus(submission, assessment),
                            feedbackStatus = submission.feedbackStatus
                        )
                    }
                }
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** A derived state flow that is true if any submission requires grading action. */
    val hasUngradedSubmissions: StateFlow<Boolean> =
        submissionsWithStatus.map { submissions ->
            submissions.any { it.statusText == "Not Graded" || it.statusText.startsWith("In Progress") }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val hasPendingFeedback: StateFlow<Boolean> =
        submissionsWithStatus.map { list ->
            list.any {
                it.submission.feedbackStatus == FeedbackStatus.SENT_PENDING_ACK
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val hasSubmissionsToGrade: StateFlow<Boolean> =
        submissionsWithStatus.map { list ->
            list.any { it.feedbackStatus == FeedbackStatus.PENDING_SEND }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * A derived state flow that determines if a new assessment can be sent.
     * This is a key piece of logic to prevent tutors from sending multiple assessments
     * while one is still active. A new one is allowed if:
     * 1. The timer for the previous assessment has run out.
     * OR
     * 2. All currently connected students have submitted their answers.
     * The flow combines submissions with a 1-second ticker to re-evaluate the time condition periodically.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isNewAssessmentAllowed: StateFlow<Boolean> =
        uiState.flatMapLatest { state ->
            val lastAssessment = state.sentAssessments.maxByOrNull { it.sentTimestamp }

            // If no assessment has been sent, it's always allowed.
            if (lastAssessment == null) {
                return@flatMapLatest flowOf(true)
            }

            val submissionsForLastAssessmentFlow = repository.getSubmissionsFlowForAssessment(lastAssessment.id)

            combine(
                submissionsForLastAssessmentFlow,
                flow { while (true) { emit(Unit); delay(1000) } }
            ) { submissions, _ ->

                // Condition 1: The timer has run out.
                val timeIsUp = System.currentTimeMillis() >= (lastAssessment.sentTimestamp + (lastAssessment.durationInMinutes * 60 * 1000))

                // Condition 2: All connected students have submitted.
                val connectedStudentIds = state.connectedStudents.map { it.studentId }.toSet()
                val submittedStudentIds = submissions.map { it.studentId }.toSet()

                // This is true if the set of connected students is not empty and is a subset of (or equal to) the set of submitted students.
                val allHaveSubmitted = connectedStudentIds.isNotEmpty() && connectedStudentIds.all { it in submittedStudentIds }

                // The button is enabled if either condition is met.
                timeIsUp || allHaveSubmitted
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true // Start with the button enabled.
        )

    fun selectAssessmentToView(assessmentId: String?) {
        repository.selectAssessmentToView(assessmentId)
    }

    fun sendAllPendingFeedback() {
        val sessionId = uiState.value.activeSession?.sessionId ?: return
        viewModelScope.launch {
            repository.sendAllPendingFeedbackForSession(sessionId)
        }
    }

    /** Calculates a human-readable string representing the grading progress of a submission. */
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

    fun createNewClass(className: String) {
        viewModelScope.launch {
            val success = repository.createNewClass(className)
            if (!success) {
                _toastEvents.emit("A class with that name already exists.")
            }
        }
    }

    /** Initiates a session for the given class, making it discoverable by students. */
    fun startSession(classId: Long) {
        viewModelScope.launch {
            repository.startTutorSession(classId)
                .onSuccess {
                    _toastEvents.emit("Session started successfully.")
                }
                .onFailure {
                    _toastEvents.emit("Error: Could not start session.")
                }
        }
    }

    /**
     * Sends a newly created assessment to all connected students. The repository
     * handles the complex payload serialization and network transfer.
     */
    fun sendAssessment(assessmentBlueprint: AssessmentBlueprint) {
        viewModelScope.launch {
            repository.sendAssessmentToAllStudents(assessmentBlueprint)
                .onSuccess {
                    _toastEvents.emit("Assessment sent to class.")
                }
                .onFailure {
                    _toastEvents.emit("Error: Failed to send assessment.")
                }
        }
    }

    fun stopSession() {
        repository.stopTutorSession()
    }

    fun acceptStudent(request: ConnectionRequest) {
        viewModelScope.launch {
            repository.acceptStudent(request)
        }
    }

    fun rejectStudent(endpointId: String) {
        viewModelScope.launch {
            repository.rejectStudent(endpointId)
        }
    }

    fun acceptAll() {
        viewModelScope.launch {
            repository.acceptAllVerified()
        }
    }

    fun takeAttendanceAndStop() {
        val connectedStudents = uiState.value.connectedStudents
        val sessionId = uiState.value.activeSession?.sessionId

        viewModelScope.launch {
            if (sessionId != null && connectedStudents.isNotEmpty()) {
                repository.markAttendanceForSession(sessionId, connectedStudents)
            }
            repository.stopTutorSession()
        }
    }

    fun markStudentAbsent(studentId: String) {
        val sessionId = uiState.value.activeSession?.sessionId ?: return
        viewModelScope.launch {
            repository.markStudentAsAbsent(sessionId, studentId)
        }
    }

    /**
     * Toggles the user's role between TUTOR and STUDENT in preferences and
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

    fun errorShown() {
        repository.errorShown()
    }
}