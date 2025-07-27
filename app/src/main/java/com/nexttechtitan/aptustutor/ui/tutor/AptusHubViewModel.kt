package com.nexttechtitan.aptustutor.ui.tutor

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.ai.AiGradeResponse
import com.nexttechtitan.aptustutor.ai.GemmaAiService
import com.nexttechtitan.aptustutor.data.ModelStatus
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import com.nexttechtitan.aptustutor.di.AiDispatcher
import com.nexttechtitan.aptustutor.utils.CapabilityResult
import com.nexttechtitan.aptustutor.utils.DeviceCapability
import com.nexttechtitan.aptustutor.utils.DeviceHealthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import com.nexttechtitan.aptustutor.data.AssessmentAnswer
import com.nexttechtitan.aptustutor.data.AssessmentQuestion
import com.nexttechtitan.aptustutor.data.QuestionType

@Immutable
data class GradedItem(
    val id: String = UUID.randomUUID().toString(),
    val questionText: String,
    val studentAnswerText: String,
    val studentAnswerImage: Bitmap?,
    val result: AiGradeResponse
)

@Immutable
data class AptusHubUiState(
    // Inputs are now empty by default
    val questionText: String = "",
    val markingGuide: String = "",
    val maxScore: String = "10",
    val studentAnswerText: String = "",
    val studentAnswerImage: Bitmap? = null,

    // Process State
    val isGrading: Boolean = false,
    val gradingStatus: String = "",
    val deviceHealth: CapabilityResult? = null,

    // Outputs
    val gradingHistory: List<GradedItem> = emptyList()
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

    val modelStatus = userPreferencesRepository.aiModelStatusFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelStatus.NOT_DOWNLOADED)

    private var healthMonitoringJob: Job? = null

    // --- Input Updaters ---

    fun onQuestionTextChanged(text: String) {
        _uiState.update { it.copy(questionText = text) }
    }

    fun onMarkingGuideChanged(text: String) {
        _uiState.update { it.copy(markingGuide = text) }
    }

    fun onMaxScoreChanged(text: String) {
        _uiState.update { it.copy(maxScore = text.filter(Char::isDigit)) }
    }

    fun onStudentAnswerTextChanged(text: String) {
        _uiState.update { it.copy(studentAnswerText = text) }
    }

    fun onStudentAnswerImageChanged(bitmap: Bitmap?) {
        _uiState.update { it.copy(studentAnswerImage = bitmap) }
    }

    // --- Core Grading Logic (Unchanged) ---
    fun runGrading() {
        if (_uiState.value.isGrading) {
            viewModelScope.launch { _toastEvents.emit("Grading is already in progress.") }
            return
        }

        viewModelScope.launch(aiDispatcher) {
            val initialCheck = deviceHealthManager.checkDeviceCapability()
            if (initialCheck.capability == DeviceCapability.UNSUPPORTED) {
                _toastEvents.emit(initialCheck.message)
                return@launch
            }

            if (_uiState.value.questionText.isBlank() || _uiState.value.markingGuide.isBlank()) {
                _toastEvents.emit("Please provide a question and marking guide.")
                return@launch
            }
            if (_uiState.value.studentAnswerText.isBlank() && _uiState.value.studentAnswerImage == null) {
                _toastEvents.emit("Please provide a student answer to grade.")
                return@launch
            }

            startHealthMonitoring()
            _uiState.update { it.copy(isGrading = true, gradingStatus = "Initializing AI Engine...") }

            try {
                val question = AssessmentQuestion(
                    id = UUID.randomUUID().toString(),
                    text = _uiState.value.questionText,
                    markingGuide = _uiState.value.markingGuide,
                    maxScore = _uiState.value.maxScore.toIntOrNull() ?: 10,
                    type = if (_uiState.value.studentAnswerImage != null) QuestionType.HANDWRITTEN_IMAGE else QuestionType.TEXT_INPUT
                )

                val answer = AssessmentAnswer(
                    questionId = question.id,
                    textResponse = _uiState.value.studentAnswerText
                )

                _uiState.update { it.copy(gradingStatus = "Grading in progress...\nThis may take some time.") }

                val result = gemmaAiService.grade(
                    question = question,
                    answer = answer,
                    image = _uiState.value.studentAnswerImage
                )

                result.onSuccess { response ->
                    val gradedItem = GradedItem(
                        questionText = question.text,
                        studentAnswerText = answer.textResponse ?: "",
                        studentAnswerImage = _uiState.value.studentAnswerImage,
                        result = response
                    )
                    _uiState.update {
                        it.copy(
                            gradingHistory = listOf(gradedItem) + it.gradingHistory,
                            studentAnswerText = "",
                            studentAnswerImage = null
                        )
                    }
                    _toastEvents.emit("Grading successful!")
                }.onFailure { error ->
                    _toastEvents.emit("Grading failed: ${error.message}")
                }

            } catch (e: Exception) {
                _toastEvents.emit("An unexpected error occurred: ${e.message}")
            } finally {
                _uiState.update { it.copy(isGrading = false, gradingStatus = "") }
                healthMonitoringJob?.cancel()
            }
        }
    }

    private fun startHealthMonitoring() {
        healthMonitoringJob?.cancel()
        healthMonitoringJob = viewModelScope.launch {
            while (isActive) {
                _uiState.update {
                    it.copy(deviceHealth = deviceHealthManager.checkDeviceCapability())
                }
                delay(2000)
            }
        }
    }

    fun onFeatureCardTapped(message: String) {
        viewModelScope.launch {
            _toastEvents.emit(message)
        }
    }
}