package com.nexttechtitan.aptustutor.ui.tutor

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.ai.AiGradeResponse
import com.nexttechtitan.aptustutor.ai.GemmaAiService
import com.nexttechtitan.aptustutor.data.AssessmentAnswer
import com.nexttechtitan.aptustutor.data.AssessmentQuestion
import com.nexttechtitan.aptustutor.data.ModelStatus
import com.nexttechtitan.aptustutor.data.QuestionType
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import com.nexttechtitan.aptustutor.di.AiDispatcher
import com.nexttechtitan.aptustutor.utils.DeviceCapability
import com.nexttechtitan.aptustutor.utils.DeviceHealthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// --- Data Models for the Sandbox UI State ---

data class SandboxStudentAnswer(
    val studentId: String = UUID.randomUUID().toString(),
    var answerText: String = "",
    var answerImage: Bitmap? = null,
    var isGrading: Boolean = false,
    var result: AiGradeResponse? = null,
    var statusText: String = "Pending"
)

data class SandboxQuestion(
    val questionId: String = UUID.randomUUID().toString(),
    var questionText: String = "What is the powerhouse of the cell?",
    var markingGuide: String = "The powerhouse of the cell is the mitochondrion.",
    var maxScore: String = "10",
    val studentAnswers: MutableList<SandboxStudentAnswer> = mutableStateListOf(SandboxStudentAnswer())
)

data class AptusHubUiState(
    val isGrading: Boolean = false,
    val gradingProgressText: String = ""
)

@HiltViewModel
class AptusHubViewModel @Inject constructor(
    private val gemmaAiService: GemmaAiService,
    private val deviceHealthManager: DeviceHealthManager,
    userPreferencesRepository: UserPreferencesRepository,
    @AiDispatcher private val aiDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(AptusHubUiState())
    val uiState = _uiState.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    val sandboxQuestions = mutableStateListOf(SandboxQuestion())

    val modelStatus = userPreferencesRepository.aiModelStatusFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelStatus.NOT_DOWNLOADED)

    val gemmaModelState = gemmaAiService.modelState

    fun addQuestion() {
        sandboxQuestions.add(SandboxQuestion())
    }

    fun removeQuestion(question: SandboxQuestion) {
        sandboxQuestions.remove(question)
    }

    fun addStudentAnswer(question: SandboxQuestion) {
        question.studentAnswers.add(SandboxStudentAnswer())
    }

    fun removeStudentAnswer(question: SandboxQuestion, answer: SandboxStudentAnswer) {
        question.studentAnswers.remove(answer)
    }

    fun runSandboxGrading() {
        viewModelScope.launch(aiDispatcher) {
            val initialCheck = deviceHealthManager.checkDeviceCapability()
            if (initialCheck.capability == DeviceCapability.UNSUPPORTED) {
                _toastEvents.emit(initialCheck.message)
                return@launch
            }

            _uiState.update { it.copy(isGrading = true) }
            var totalGraded = 0
            val warmedPrefixes = mutableSetOf<String>()

            for (question in sandboxQuestions) {
                for ((index, studentAnswer) in question.studentAnswers.withIndex()) {
                    val loopCheck = deviceHealthManager.checkDeviceCapability()
                    if (loopCheck.capability == DeviceCapability.UNSUPPORTED) {
                        _toastEvents.emit("Grading paused: ${loopCheck.message}")
                        _uiState.update { it.copy(isGrading = false, gradingProgressText = "Paused") }
                        return@launch
                    }

                    val isFirstForThisQuestion =!warmedPrefixes.contains(question.questionId)
                    val status = if (isFirstForThisQuestion) "Warming & Grading..." else "Grading..."
                    updateStudentStatus(question, studentAnswer, isGrading = true, status = status)
                    totalGraded++
                    _uiState.update { it.copy(gradingProgressText = "Grading answer $totalGraded...") }

                    // --- Programmatic Grading for Blank Answers ---
                    if (studentAnswer.answerText.isBlank() && studentAnswer.answerImage == null) {
                        val blankResult = AiGradeResponse(0, "No answer provided.")
                        updateStudentStatus(question, studentAnswer, isGrading = false, status = "Graded (Instant)", result = blankResult)
                        delay(500) // Small delay for UI visibility
                        continue
                    }

                    // --- Call the new AI Service method ---
                    val result = gemmaAiService.grade(
                        question = AssessmentQuestion(
                            id = question.questionId,
                            text = question.questionText,
                            markingGuide = question.markingGuide,
                            maxScore = question.maxScore.toIntOrNull()?: 10,
                            type = if (studentAnswer.answerImage!= null) QuestionType.HANDWRITTEN_IMAGE else QuestionType.TEXT_INPUT
                        ),
                        answer = AssessmentAnswer(
                            questionId = question.questionId,
                            textResponse = studentAnswer.answerText
                        ),
                        image = studentAnswer.answerImage,
                        studentIdentifier = studentAnswer.studentId
                    )

                    if (isFirstForThisQuestion) {
                        warmedPrefixes.add(question.questionId)
                    }

                    val finalResult = result.getOrElse { AiGradeResponse(null, "Error: ${it.message}") }
                    updateStudentStatus(question, studentAnswer, isGrading = false, status = "Graded", result = finalResult)
                }
            }

            _uiState.update { it.copy(isGrading = false, gradingProgressText = "Completed") }
            _toastEvents.emit("Sandbox grading complete!")
        }
    }

    private fun updateStudentStatus(
        question: SandboxQuestion,
        answer: SandboxStudentAnswer,
        isGrading: Boolean,
        status: String,
        result: AiGradeResponse? = answer.result
    ) {
        val questionIndex = sandboxQuestions.indexOfFirst { it.questionId == question.questionId }
        if (questionIndex == -1) return
        val answerIndex = sandboxQuestions[questionIndex].studentAnswers.indexOfFirst { it.studentId == answer.studentId }
        if (answerIndex == -1) return

        sandboxQuestions[questionIndex].studentAnswers[answerIndex] = answer.copy(
            isGrading = isGrading,
            statusText = status,
            result = result
        )
    }

    fun showComingSoonToast() {
        viewModelScope.launch {
            _toastEvents.emit("Feature coming soon!")
        }
    }
}