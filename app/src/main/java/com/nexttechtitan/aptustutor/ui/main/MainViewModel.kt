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

/**
 * Main ViewModel for the application.
 * Its primary responsibility is to determine the correct starting screen for the user
 * based on their onboarding status and role (Tutor/Student). This prevents flashes of
 * incorrect screens on app startup.
 */

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    // Holds the determined start route. The UI observes this to navigate.
    // It's null initially, allowing the splash screen to remain visible.
    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        determineStartDestination()
    }

    /**
     * Checks user preferences to decide the navigation starting point.
     * - If onboarding is not complete, directs to RoleSelectionScreen.
     * - If complete, directs to the appropriate dashboard based on the saved user role.
     */
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