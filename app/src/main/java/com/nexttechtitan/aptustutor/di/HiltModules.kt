// File: di/AppModule.kt
package com.nexttechtitan.aptustutor.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase.Callback
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.WorkManager
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.gson.Gson
import com.nexttechtitan.aptustutor.data.AptusTutorDatabase
import com.nexttechtitan.aptustutor.data.ClassDao
import com.nexttechtitan.aptustutor.data.SessionDao
import com.nexttechtitan.aptustutor.data.AssessmentDao
import com.nexttechtitan.aptustutor.data.StudentProfileDao
import com.nexttechtitan.aptustutor.data.TutorProfileDao
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(@ApplicationContext appContext: Context): UserPreferencesRepository {
        return UserPreferencesRepository(appContext)
    }

    // --- Start of New Providers ---

    @Provides
    @Singleton
    fun provideAptusTutorDatabase(@ApplicationContext context: Context): AptusTutorDatabase {
        return Room.databaseBuilder(
            context,
            AptusTutorDatabase::class.java,
            "aptus_tutor_db"
        ).build()
    }

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

    @Provides
    fun provideAssessmentDao(db: AptusTutorDatabase): AssessmentDao = db.assessmentDao()

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
}