package com.nexttechtitan.aptustutor.ui.roleselection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttechtitan.aptustutor.data.AptusTutorRepository
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoleSelectionViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val repository: AptusTutorRepository
) : ViewModel() {

    fun saveRoleAndDetails(role: String, name: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            userPreferencesRepository.saveRoleAndDetails(role, name)
            val userId = userPreferencesRepository.userIdFlow.first()
            if (userId != null) {
                when (role) {
                    "TUTOR" -> repository.saveTutorProfile(userId, name)
                    "STUDENT" -> repository.saveStudentProfile(userId, name)
                }
            }
            onComplete()
        }
    }
}