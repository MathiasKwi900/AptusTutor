package com.nexttechtitan.aptustutor

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nexttechtitan.aptustutor.ai.PleCacheWorker
import com.nexttechtitan.aptustutor.data.ModelStatus
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AptusTutorApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workManager: WorkManager

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository


    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            val userRole = userPreferencesRepository.userRoleFlow.filterNotNull().first()
            Log.i("AptusTutorApplication", "Read from DataStore -> User Role: $userRole")
            if (userRole == "TUTOR") {
                Log.i("AptusTutorApplication", "User is a Tutor. Checking if PLE caching is needed.")
                val modelStatus = userPreferencesRepository.aiModelStatusFlow.first()
                val isCacheComplete = userPreferencesRepository.pleCacheCompleteFlow.first()
                Log.i("AptusTutorApplication", "Read from DataStore -> Model Status: $modelStatus")
                Log.i("AptusTutorApplication", "Read from DataStore -> Cache Complete: $isCacheComplete")


                if (modelStatus == ModelStatus.DOWNLOADED && !isCacheComplete) {
                    schedulePleCaching()
                } else {
                    Log.i("AptusTutorApplication", "PLE caching is not needed at this time.")
                }
            } else {
                Log.i("AptusTutorApplication", "User is not a Tutor. Skipping PLE cache check.")
            }
        }
    }

    private fun schedulePleCaching() {
        val pleCacheRequest = OneTimeWorkRequestBuilder<PleCacheWorker>()
            .build()

        workManager.enqueueUniqueWork(
            PleCacheWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            pleCacheRequest
        )

        Log.i("AptusTutorApplication", "One-time PLE Caching work has been scheduled.")
    }
}