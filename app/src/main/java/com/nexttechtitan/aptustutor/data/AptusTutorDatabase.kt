package com.nexttechtitan.aptustutor.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
@TypeConverters(Converters::class)
abstract class AptusTutorDatabase : RoomDatabase() {
    abstract fun studentProfileDao(): StudentProfileDao
    abstract fun tutorProfileDao(): TutorProfileDao
    abstract fun classDao(): ClassDao
    abstract fun sessionDao(): SessionDao
    abstract fun assessmentDao(): AssessmentDao
}