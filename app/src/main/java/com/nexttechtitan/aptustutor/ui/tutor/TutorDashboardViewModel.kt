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
import com.nexttechtitan.aptustutor.ai.ThermalManager
import com.nexttechtitan.aptustutor.data.AssessmentAnswer
import com.nexttechtitan.aptustutor.data.AssessmentQuestion
import com.nexttechtitan.aptustutor.data.ModelStatus
import com.nexttechtitan.aptustutor.data.QuestionType
import com.nexttechtitan.aptustutor.ui.AptusTutorScreen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
    private val thermalManager: ThermalManager,
    private val gemmaAiService: GemmaAiService
) : ViewModel() {

    val uiState = repository.tutorUiState

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

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
            val assessmentId = state.activeAssessment?.id
            if (assessmentId != null) {
                // Combine the submissions flow with the assessment flow
                combine(
                    repository.getSubmissionsFlowForAssessment(assessmentId),
                    repository.getAssessmentById(assessmentId).filterNotNull()
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

    init {
        viewModelScope.launch {
            val userRole = userPreferencesRepository.userRoleFlow.first()
            Log.d("AptusTutorDebug", "User role is $userRole")
            if (userRole == "TUTOR") {
                val isInitialized = userPreferencesRepository.aiModelInitializedFlow.first()
                Log.d("AptusTutorDebug", "AI model initialized: $isInitialized")
                if (isInitialized) {
                    gemmaAiService.ensureModelIsLoaded()
                }
            }
        }
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
        }
    }

    fun startAiBatchGrading() {
        val assessmentId = uiState.value.activeAssessment?.id ?: return
        viewModelScope.launch { _toastEvents.emit("AI batch grading has been scheduled...") }

        // Create the input data for the worker
        val inputData = Data.Builder()
            .putString(AIBatchGradingWorker.KEY_ASSESSMENT_ID, assessmentId)
            .build()

        // Build the request
        val batchGradingRequest = OneTimeWorkRequestBuilder<AIBatchGradingWorker>()
            .setInputData(inputData)
            .build()

        // Enqueue the work
        workManager.enqueue(batchGradingRequest)
    }

    fun errorShown() {
        repository.errorShown()
    }

    // Delete the following code after testing
    fun testAiGrading(questionText: String, markingGuide: String, maxScore: Int, studentAnswer: String, image: Bitmap?) {
        viewModelScope.launch {
            Log.d("AptusTutorDebug", "Starting manual AI grading test.")

            // Check for thermal throttling before proceeding.
            while (!thermalManager.isSafeToProceed()) {
                Log.w("AptusTutorDebug", "Device is overheating. Pausing AI test for 15 seconds.")
                _toastEvents.emit("Device is hot. Pausing test for 15 seconds...")
                kotlinx.coroutines.delay(15000)
            }

            // Once safe, proceed with grading.
            _toastEvents.emit("Device temperature is okay. Running test...")
            Log.d("AptusTutorDebug", "Device is cool enough to proceed with AI test.")

            val dummyQuestion = AssessmentQuestion(
                id = "test_q_01",
                text = questionText,
                markingGuide = markingGuide,
                maxScore = maxScore,
                type = QuestionType.TEXT_INPUT
            )

            val dummyAnswer = AssessmentAnswer(
                questionId = "test_q_01",
                textResponse = studentAnswer
            )

            val prompt = buildPromptForTest(dummyQuestion, dummyAnswer, image)
            Log.d("AptusTutorDebug", "Generated Prompt:\n$prompt")

            val result = gemmaAiService.grade(prompt, image)

            result.onSuccess { response ->
                Log.i("AptusTutorDebug", "--- AI TEST SUCCESS ---")
                Log.i("AptusTutorDebug", "Response Score: ${response.score}")
                Log.i("AptusTutorDebug", "Response Feedback: ${response.feedback}")
                _toastEvents.emit("Test successful! Check Logcat for details.")
            }.onFailure { error ->
                Log.e("AptusTutorDebug", "--- AI TEST FAILED ---", error)
                _toastEvents.emit("Test failed: ${error.message}")
            }
        }
    }

    private fun buildPromptForTest(question: AssessmentQuestion, answer: AssessmentAnswer?, image: Bitmap?): String {
        val studentAnswerText: String
        val answerInstruction: String

        if ((answer == null || answer.textResponse.isNullOrBlank()) && image == null) {
            studentAnswerText = ""
            answerInstruction = "The student did not provide an answer. Your task is to assign a score\n" +
                    "of 0 and use the feedback field to politely provide the correct answer from the MARKING GUIDE."
        } else {
            studentAnswerText = when {
                !answer?.textResponse.isNullOrBlank() && image != null -> """
                Typed Answer: "${answer.textResponse}"
                (An image was also provided for context).
                """.trimIndent()
                !answer?.textResponse.isNullOrBlank() -> "Typed Answer: \"${answer.textResponse}\""
                else -> "An image of a handwritten answer was provided."
            }
            answerInstruction = "Analyze the student's answer for conceptual understanding; it does\n" +
                    "not need to be a perfect word-for-word match. Use your general knowledge on the topic to determine\n" +
                    "how close their response is to the correct answer's intent. Then, provide specific, comparative\n" +
                    "feedback. The final score MUST be an integer between 0 and ${question.maxScore}, inclusive."
        }

        return """
        ROLE: Expert Teaching Assistant.
        TASK: Grade the student's answer.
        QUESTION: "${question.text}"
        MARKING GUIDE: "${question.markingGuide}"
        MAX SCORE: ${question.maxScore}
        STUDENT ANSWER: $studentAnswerText
        INSTRUCTIONS: $answerInstruction

        OUTPUT FORMAT: Respond ONLY with a valid JSON object. Do not add any other text.
        {
          "score": <integer score>,
          "feedback": "<concise feedback, under 30 words>"
        }
    """.trimIndent()
    }
}