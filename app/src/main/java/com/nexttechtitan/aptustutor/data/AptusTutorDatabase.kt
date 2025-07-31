package com.nexttechtitan.aptustutor.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The main Room database definition for the AptusTutor application.
 * It serves as the single source of truth for all local, persistent data.
 */
@Database(
    entities = [
        StudentProfile::class, TutorProfile::class,
        ClassProfile::class, ClassRosterCrossRef::class,
        Session::class, SessionAttendance::class,
        Assessment::class, AssessmentSubmission::class
    ],
    version = 1,
    exportSchema = false
)

/**
 * Enables Room to use the custom Converters class to handle types it doesn't natively
 * support, such as converting a List of objects to a JSON string for storage.
 */
@TypeConverters(Converters::class)
abstract class AptusTutorDatabase : RoomDatabase() {
    abstract fun studentProfileDao(): StudentProfileDao
    abstract fun tutorProfileDao(): TutorProfileDao
    abstract fun classDao(): ClassDao
    abstract fun sessionDao(): SessionDao
    abstract fun assessmentDao(): AssessmentDao
}