package com.nexttechtitan.aptustutor

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Custom Application class for AptusTutor.
 * Annotated with @HiltAndroidApp to enable dependency injection throughout the application.
 * Also implements Configuration.Provider to provide a custom WorkManager configuration,
 * allowing Hilt to inject dependencies into our background workers.
 */
@HiltAndroidApp
class AptusTutorApplication : Application(), Configuration.Provider {

    // Injected by Hilt, this factory is used to create Workers with their dependencies.
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // Provides the custom WorkManager configuration to the system.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
    }
}