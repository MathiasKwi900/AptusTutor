package com.nexttechtitan.aptustutor.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import com.nexttechtitan.aptustutor.ui.AptusTutorScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        determineStartDestination()
    }

    private fun determineStartDestination() {
        viewModelScope.launch {
            val isOnboardingComplete = userPreferencesRepository.onboardingCompleteFlow.first()
            if (isOnboardingComplete) {
                val userRole = userPreferencesRepository.userRoleFlow.first()
                if (userRole == "TUTOR") {
                    _startDestination.value = AptusTutorScreen.TutorDashboard.name
                } else {
                    _startDestination.value = AptusTutorScreen.StudentDashboard.name
                }
            } else {
                _startDestination.value = AptusTutorScreen.RoleSelection.name
            }
        }
    }
}