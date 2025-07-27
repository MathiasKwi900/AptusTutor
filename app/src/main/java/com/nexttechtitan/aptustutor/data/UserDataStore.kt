// File: data/UserPreferencesRepository.kt
package com.nexttechtitan.aptustutor.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aptus_tutor_preferences")

enum class ModelStatus {
    NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED
}

@Singleton
class UserPreferencesRepository @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        val USER_ROLE_KEY = stringPreferencesKey("user_role")
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val USER_NAME_KEY = stringPreferencesKey("user_name")
        val ONBOARDING_COMPLETE_KEY = stringPreferencesKey("onboarding_complete")
        val AI_MODEL_STATUS_KEY = stringPreferencesKey("ai_model_status")
        val AI_MODEL_PATH_KEY = stringPreferencesKey("ai_model_path")
    }

    val userRoleFlow: Flow<String?> = context.dataStore.data.map { it[USER_ROLE_KEY] }
    val userIdFlow: Flow<String?> = context.dataStore.data.map { it[USER_ID_KEY] }
    val userNameFlow: Flow<String?> = context.dataStore.data.map { it[USER_NAME_KEY] }
    val onboardingCompleteFlow: Flow<Boolean> = context.dataStore.data.map { (it[ONBOARDING_COMPLETE_KEY] ?: "false").toBoolean() }
    val aiModelStatusFlow: Flow<ModelStatus> = context.dataStore.data.map { preferences ->
        ModelStatus.valueOf(preferences[AI_MODEL_STATUS_KEY] ?: ModelStatus.NOT_DOWNLOADED.name)
    }
    val aiModelPathFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[AI_MODEL_PATH_KEY]
    }

    suspend fun setAiModel(status: ModelStatus, path: String? = null) {
        context.dataStore.edit { preferences ->
            preferences[AI_MODEL_STATUS_KEY] = status.name
            if (path != null) {
                preferences[AI_MODEL_PATH_KEY] = path
            } else {
                preferences.remove(AI_MODEL_PATH_KEY)
            }
        }
    }
    suspend fun saveRoleAndDetails(role: String, name: String) {
        Log.d("UserPreferencesRepo", "Saving role: '$role' and name: '$name'")
        context.dataStore.edit { preferences ->
            val currentId = preferences[USER_ID_KEY]
            if (currentId == null) {
                preferences[USER_ID_KEY] = UUID.randomUUID().toString()
            }
            preferences[USER_ROLE_KEY] = role
            preferences[USER_NAME_KEY] = name
            preferences[ONBOARDING_COMPLETE_KEY] = "true"
        }
    }
    suspend fun switchUserRole(newRole: String) {
        Log.d("UserPreferencesRepo", "Switching user role to: '$newRole'")
        context.dataStore.edit { preferences ->
            preferences[USER_ROLE_KEY] = newRole
        }
    }
}