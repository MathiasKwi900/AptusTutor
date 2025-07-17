package com.nexttechtitan.aptustutor.ui.tutor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.data.AptusTutorRepository
import com.nexttechtitan.aptustutor.data.Assessment
import com.nexttechtitan.aptustutor.data.ClassWithStudents
import com.nexttechtitan.aptustutor.data.ConnectionRequest
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TutorDashboardViewModel @Inject constructor(
    private val repository: AptusTutorRepository,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState = repository.tutorUiState

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val tutorClasses: StateFlow<List<ClassWithStudents>> =
        userPreferencesRepository.userIdFlow.flatMapLatest { tutorId ->
            repository.getClassesForTutor(tutorId ?: "")
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createNewClass(className: String) {
        viewModelScope.launch {
            val success = repository.createNewClass(className)
            if (!success) {
                _toastEvents.emit("A class with that name already exists.")
            }
        }
    }

    fun startSession(classId: Long) {
        viewModelScope.launch {
            repository.startTutorSession(classId)
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

    fun sendAssessment(assessment: Assessment) {
        viewModelScope.launch {
            repository.sendAssessmentToAllStudents(assessment)
        }
    }
}