package com.nexttechtitan.aptustutor.ui.tutor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.data.AptusTutorRepository
import com.nexttechtitan.aptustutor.data.Assessment
import com.nexttechtitan.aptustutor.data.AssessmentSubmission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import androidx.compose.runtime.mutableStateMapOf
import com.nexttechtitan.aptustutor.data.AssessmentAnswer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubmissionDetailsUiState(
    val submission: AssessmentSubmission? = null,
    val assessment: Assessment? = null,
    val draftAnswers: Map<String, AssessmentAnswer> = emptyMap()
)

@HiltViewModel
class SubmissionDetailsViewModel @Inject constructor(
    private val repository: AptusTutorRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val submissionId: String = savedStateHandle.get("submissionId")!!

    private val _draftAnswersFlow = MutableStateFlow<Map<String, AssessmentAnswer>>(emptyMap())

    val uiState: StateFlow<SubmissionDetailsUiState> = combine(
        repository.getSubmissionFlow(submissionId),
        repository.getAssessmentFlow(submissionId),
        _draftAnswersFlow // Combine the draft state flow
    ) { submission, assessment, draftAnswers ->
        // Initialize drafts the first time the submission loads
        if (submission != null && draftAnswers.isEmpty()) {
            _draftAnswersFlow.value = submission.answers.associateBy { it.questionId }
        }
        SubmissionDetailsUiState(
            submission = submission,
            assessment = assessment,
            draftAnswers = draftAnswers
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SubmissionDetailsUiState()
    )

    fun onScoreChange(questionId: String, newScore: String) {
        val currentAnswers = _draftAnswersFlow.value.toMutableMap()
        currentAnswers[questionId]?.let {
            currentAnswers[questionId] = it.copy(score = newScore.toIntOrNull())
            _draftAnswersFlow.value = currentAnswers
        }
    }

    fun onFeedbackChange(questionId: String, newFeedback: String) {
        val currentAnswers = _draftAnswersFlow.value.toMutableMap()
        currentAnswers[questionId]?.let {
            currentAnswers[questionId] = it.copy(feedback = newFeedback)
            _draftAnswersFlow.value = currentAnswers
        }
    }

    fun saveGrade(questionId: String) {
        viewModelScope.launch {
            val answerToSave = _draftAnswersFlow.value[questionId]
            // Only save if the score and feedback are valid
            if (answerToSave?.score != null && answerToSave.feedback != null) {
                repository.saveManualGrade(
                    submissionId = submissionId,
                    questionId = questionId,
                    score = answerToSave.score!!,
                    feedback = answerToSave.feedback!!
                )
            }
        }
    }

    fun sendFeedback() {
        viewModelScope.launch {
            uiState.value.submission?.let {
                // Ensure the submission being sent has the latest draft answers
                val updatedSubmission = it.copy(answers = _draftAnswersFlow.value.values.toList())
                repository.sendFeedbackToStudent(updatedSubmission)
            }
        }
    }
}