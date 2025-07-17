// File: data/UserPreferencesRepository.kt
package com.nexttechtitan.aptustutor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

@Singleton
class UserPreferencesRepository @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        val USER_ROLE_KEY = stringPreferencesKey("user_role")
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val USER_NAME_KEY = stringPreferencesKey("user_name")
        val ONBOARDING_COMPLETE_KEY = stringPreferencesKey("onboarding_complete")
    }

    val userRoleFlow: Flow<String?> = context.dataStore.data.map { it[USER_ROLE_KEY] }
    val userIdFlow: Flow<String?> = context.dataStore.data.map { it[USER_ID_KEY] }
    val userNameFlow: Flow<String?> = context.dataStore.data.map { it[USER_NAME_KEY] }
    val onboardingCompleteFlow: Flow<Boolean> = context.dataStore.data.map { (it[ONBOARDING_COMPLETE_KEY] ?: "false").toBoolean() }


    suspend fun saveRoleAndDetails(role: String, name: String) {
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
}