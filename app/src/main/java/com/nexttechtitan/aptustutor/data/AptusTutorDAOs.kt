package com.nexttechtitan.aptustutor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStudent(student: StudentProfile)

    @Query("SELECT * FROM student_profiles WHERE studentId = :studentId")
    fun getStudentById(studentId: String): Flow<StudentProfile?>
}

@Dao
interface TutorProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTutor(tutor: TutorProfile)

    @Query("SELECT * FROM tutor_profiles WHERE tutorId = :tutorId")
    fun getTutorById(tutorId: String): Flow<TutorProfile?>
}

@Dao
interface ClassDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClass(classProfile: ClassProfile): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addStudentToRoster(roster: ClassRosterCrossRef)

    @Transaction
    @Query("SELECT * FROM class_profiles WHERE tutorOwnerId = :tutorId ORDER BY className ASC")
    fun getClassesForTutor(tutorId: String): Flow<List<ClassWithStudents>>

    @Transaction
    @Query("SELECT * FROM class_profiles WHERE classId = :classId")
    fun getClassWithStudents(classId: Long): Flow<ClassWithStudents>
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordAttendance(attendance: SessionAttendance)

    /**
     * Corrected Query: This fetches the session details by looking through the
     * attendance records for a given student. This is the correct way to get
     * a student's session history.
     */
    @Transaction
    @Query("""
        SELECT S.* FROM sessions AS S
        INNER JOIN session_attendance AS SA ON S.sessionId = SA.sessionId
        WHERE SA.studentId = :studentId
        ORDER BY S.sessionTimestamp DESC
    """)
    fun getSessionHistoryForStudent(studentId: String): Flow<List<Session>>

    @Transaction
    @Query("""
    SELECT * FROM sessions
    WHERE sessionId IN (
        SELECT sessionId FROM session_attendance WHERE studentId = :studentId AND status = 'Present'
    )
    ORDER BY sessionTimestamp DESC
""")
    fun getSessionHistoryWithDetailsForStudent(studentId: String): Flow<List<SessionWithClassDetails>>
}

@Dao
interface AssessmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssessment(assessment: Assessment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubmission(submission: AssessmentSubmission)

    @Query("SELECT * FROM assessment_submissions WHERE assessmentId = :assessmentId")
    fun getSubmissionsForAssessment(assessmentId: String): Flow<List<AssessmentSubmission>>

    @Query("SELECT * FROM assessment_submissions WHERE submissionId = :submissionId")
    suspend fun getSubmissionById(submissionId: String): AssessmentSubmission?

    @Query("""
    SELECT A.* FROM assessments AS A
    INNER JOIN assessment_submissions AS S ON A.id = S.assessmentId
    WHERE S.submissionId = :submissionId
""")
    fun getAssessmentForSubmission(submissionId: String): Flow<Assessment?>

    @Query("SELECT * FROM assessment_submissions WHERE submissionId = :submissionId")
    fun getSubmissionFlow(submissionId: String): Flow<AssessmentSubmission?>
}