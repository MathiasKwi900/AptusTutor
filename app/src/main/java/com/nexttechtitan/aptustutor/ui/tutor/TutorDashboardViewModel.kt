package com.nexttechtitan.aptustutor.ui.tutor

import android.graphics.Bitmap
import android.util.Log
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nexttechtitan.aptustutor.ai.AIBatchGradingWorker
import com.nexttechtitan.aptustutor.ai.GemmaAiService
import com.nexttechtitan.aptustutor.ai.GemmaAiService.ModelState
import com.nexttechtitan.aptustutor.utils.ThermalManager
import com.nexttechtitan.aptustutor.data.AssessmentAnswer
import com.nexttechtitan.aptustutor.data.AssessmentQuestion
import com.nexttechtitan.aptustutor.data.ModelStatus
import com.nexttechtitan.aptustutor.data.QuestionType
import com.nexttechtitan.aptustutor.ui.AptusTutorScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
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

data class SubmissionWithStatus(
    val submission: AssessmentSubmission,
    val statusText: String,
    val feedbackStatus: FeedbackStatus
)

@HiltViewModel
class TutorDashboardViewModel @Inject constructor(
    private val repository: AptusTutorRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val workManager: WorkManager,
    private val gemmaAiService: GemmaAiService
) : ViewModel() {

    val uiState = repository.tutorUiState

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents = _navigationEvents.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val tutorClasses: StateFlow<List<ClassWithStudents>> =
        userPreferencesRepository.userIdFlow.filterNotNull().flatMapLatest { tutorId ->
            repository.getClassesForTutor(tutorId)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val submissionsWithStatus: StateFlow<List<SubmissionWithStatus>> =
        uiState.flatMapLatest { state ->
            val assessmentId = state.viewingAssessmentId
            if (assessmentId != null) {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val isNewAssessmentAllowed: StateFlow<Boolean> =
        uiState.flatMapLatest { state ->
            val lastAssessment = state.sentAssessments.maxByOrNull { it.sentTimestamp }

            // If no assessment has been sent, it's always allowed.
            if (lastAssessment == null) {
                return@flatMapLatest flowOf(true)
            }

            // Get the flow of submissions for only the MOST RECENT assessment.
            val submissionsForLastAssessmentFlow = repository.getSubmissionsFlowForAssessment(lastAssessment.id)

            // Now, combine the submissions flow with our 1-second timer.
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