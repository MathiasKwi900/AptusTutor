package com.nexttechtitan.aptustutor.ui.roleselection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.data.AptusTutorRepository
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the RoleSelectionScreen.
 * Handles the business logic of saving the user's chosen role (Tutor/Student)
 * and their name to both local preferences and the main repository.
 */
@HiltViewModel
class RoleSelectionViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val repository: AptusTutorRepository
) : ViewModel() {

    /**
     * Persists the user's role and name, marks onboarding as complete,
     * and creates their initial profile in the database.
     *
     * @param role The role selected by the user ("TUTOR" or "STUDENT").
     * @param name The name entered by the user.
     * @param onComplete A callback to trigger navigation after the save operations are finished.
     */
    fun saveRoleAndDetails(role: String, name: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            // Save to local preferences for session management and app configuration.
            userPreferencesRepository.saveRoleAndDetails(role, name)
            val userId = userPreferencesRepository.userIdFlow.first()
            if (userId != null) {
                when (role) {
                    "TUTOR" -> repository.saveTutorProfile(userId, name)
                    "STUDENT" -> repository.saveStudentProfile(userId, name)
                }
            }
            // Signal the UI that the process is complete.
            onComplete()
        }
    }
}