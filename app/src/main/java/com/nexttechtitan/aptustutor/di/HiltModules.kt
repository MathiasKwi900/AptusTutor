package com.nexttechtitan.aptustutor.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.nexttechtitan.aptustutor.data.AptusTutorDatabase
import com.nexttechtitan.aptustutor.data.AssessmentDao
import com.nexttechtitan.aptustutor.data.ClassDao
import com.nexttechtitan.aptustutor.data.SessionDao
import com.nexttechtitan.aptustutor.data.StudentProfileDao
import com.nexttechtitan.aptustutor.data.TutorProfileDao
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A Hilt qualifier to distinguish the CoroutineDispatcher used specifically for AI tasks.
 * This ensures AI operations are queued and run sequentially on their own thread.
 */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class AiDispatcher

/**
 * Hilt module that provides singleton-scoped dependencies for the entire application.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Provides the singleton instance of datastore. */
    @Provides
    @Singleton
    fun provideUserPreferencesRepository(@ApplicationContext appContext: Context): UserPreferencesRepository {
        return UserPreferencesRepository(appContext)
    }

    /** Provides the singleton instance of the Room database. */
    @Provides
    @Singleton
    fun provideAptusTutorDatabase(@ApplicationContext context: Context): AptusTutorDatabase {
        return Room.databaseBuilder(
            context,
            AptusTutorDatabase::class.java,
            "aptus_tutor_db"
        ).build()
    }

    /** Provides the singleton instance of the Nearby Connections client. */
    @Provides
    @Singleton
    fun provideNearbyConnectionsClient(@ApplicationContext context: Context): ConnectionsClient {
        return Nearby.getConnectionsClient(context)
    }

    @Provides
    fun provideStudentProfileDao(db: AptusTutorDatabase): StudentProfileDao = db.studentProfileDao()

    @Provides
    fun provideTutorProfileDao(db: AptusTutorDatabase): TutorProfileDao = db.tutorProfileDao()

    @Provides
    fun provideClassDao(db: AptusTutorDatabase): ClassDao = db.classDao()

    @Provides
    fun provideSessionDao(db: AptusTutorDatabase): SessionDao = db.sessionDao()

    /** Provides the AssessmentDao instance, sourced from the singleton database. */
    @Provides
    fun provideAssessmentDao(db: AptusTutorDatabase): AssessmentDao = db.assessmentDao()

    /** Provides a singleton Gson instance for JSON serialization/deserialization. */
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        return FirebaseStorage.getInstance()
    }

    /**
     * Provides a dedicated, single-threaded CoroutineDispatcher for AI model inference.
     * This is crucial to prevent multiple inference tasks from running in parallel,
     * which could lead to race conditions or overwhelm the device's resources.
     */
    @Provides
    @Singleton
    @AiDispatcher
    fun provideAiDispatcher(): CoroutineDispatcher {
        return Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }
}