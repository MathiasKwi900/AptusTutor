package com.nexttechtitan.aptustutor.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        StudentProfile::class,
        TutorProfile::class,
        ClassProfile::class, // New
        ClassRosterCrossRef::class, // New
        Session::class, // New
        SessionAttendance::class // New
    ],
    version = 1,
    exportSchema = false
)
abstract class AptusTutorDatabase : RoomDatabase() {
    abstract fun studentProfileDao(): StudentProfileDao
    abstract fun tutorProfileDao(): TutorProfileDao
    abstract fun classDao(): ClassDao
    abstract fun sessionDao(): SessionDao
}