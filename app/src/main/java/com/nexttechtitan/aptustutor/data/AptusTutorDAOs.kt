package com.nexttechtitan.aptustutor.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * A "relation" model to fetch a submission along with its parent assessment details.
 * This is used for display purposes and is not a table in the database.
 */
data class SubmissionWithAssessment(
    @Embedded val submission: AssessmentSubmission,
    @Relation(
        parentColumn = "assessmentId",
        entityColumn = "id"
    )
    val assessment: Assessment?
)

/**
 * Data Access Object for student profile operations.
 */
@Dao
interface StudentProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStudent(student: StudentProfile)

    @Query("SELECT * FROM student_profiles WHERE studentId = :studentId")
    fun getStudentById(studentId: String): Flow<StudentProfile?>
}

/**
 * Data Access Object for tutor profile operations.
 */
@Dao
interface TutorProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTutor(tutor: TutorProfile)

    @Query("SELECT * FROM tutor_profiles WHERE tutorId = :tutorId")
    fun getTutorById(tutorId: String): Flow<TutorProfile?>
}

/**
 * Data Access Object for managing classes and student rosters.
 */
@Dao
interface ClassDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClass(classProfile: ClassProfile): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addStudentToRoster(roster: ClassRosterCrossRef)

    /**
     * Retrieves all classes owned by a specific tutor, along with the list of enrolled students.
     * The @Transaction annotation ensures that fetching the class and its related students
     * happens as a single, atomic operation.
     */
    @Transaction
    @Query("SELECT * FROM class_profiles WHERE tutorOwnerId = :tutorId ORDER BY className ASC")
    fun getClassesForTutor(tutorId: String): Flow<List<ClassWithStudents>>

    /**
     * Retrieves a single class and its full student roster.
     */
    @Transaction
    @Query("SELECT * FROM class_profiles WHERE classId = :classId")
    fun getClassWithStudents(classId: Long): Flow<ClassWithStudents>
}

/**
 * Data Access Object for managing session history and attendance.
 */
@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordAttendance(attendance: SessionAttendance)

    /**
     * Fetches the complete session history for a student, including details of the class
     * for each session they attended. Useful for the student's history screen.
     */
    @Transaction
    @Query("""
    SELECT * FROM sessions
    WHERE sessionId IN (
        SELECT sessionId FROM session_attendance WHERE studentId = :studentId AND status = 'Present'
    )
    ORDER BY sessionTimestamp DESC
""")
    fun getSessionHistoryWithDetailsForStudent(studentId: String): Flow<List<SessionWithClassDetails>>

    @Transaction
    @Query("""
    SELECT S.* FROM sessions AS S
    INNER JOIN session_attendance AS SA ON S.sessionId = SA.sessionId
    WHERE SA.studentId = :studentId AND SA.status = 'Present'
    ORDER BY S.sessionTimestamp DESC
""")
    fun getAttendedSessionsForStudent(studentId: String): Flow<List<SessionWithClassDetails>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE tutorId = :tutorId AND endTime IS NOT NULL ORDER BY sessionTimestamp DESC")
    fun getTutorSessionHistory(tutorId: String): Flow<List<SessionWithClassDetails>>
}

/**
 * Data Access Object for all operations related to assessments and student submissions.
 */
@Dao
interface AssessmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssessment(assessment: Assessment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubmission(submission: AssessmentSubmission)

    @Query("SELECT * FROM assessment_submissions WHERE assessmentId = :assessmentId")
    fun getSubmissionsForAssessment(assessmentId: String): Flow<List<AssessmentSubmission>>

    @Query("SELECT * FROM assessment_submissions WHERE sessionId = :sessionId")
    fun getSubmissionsForSession(sessionId: String): Flow<List<AssessmentSubmission>>

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

    /**
     * Retrieves a student's submission for a specific assessment within a session,
     * bundled with the parent assessment details.
     * @Transaction ensures both the submission and assessment are read atomically.
     */
    @Transaction
    @Query("""
    SELECT * FROM assessment_submissions
    WHERE sessionId = :sessionId AND studentId = :studentId
    LIMIT 1
""")
    fun getSubmissionWithAssessment(sessionId: String, studentId: String): Flow<SubmissionWithAssessment?>

    @Query("SELECT * FROM assessment_submissions WHERE studentId = :studentId")
    fun getSubmissionsForStudent(studentId: String): Flow<List<AssessmentSubmission>>

    @Query("SELECT * FROM assessments WHERE id = :assessmentId")
    fun getAssessmentById(assessmentId: String): Flow<Assessment?>

    @Query("SELECT * FROM assessments WHERE sessionId = :sessionId ORDER BY sentTimestamp DESC")
    fun getAssessmentsForSession(sessionId: String): Flow<List<Assessment>>

    @Transaction
    @Query("""
        SELECT * FROM assessment_submissions
        WHERE studentId = :studentId AND assessmentId = :assessmentId
        LIMIT 1
    """)
    fun getSubmissionForStudentByAssessment(studentId: String, assessmentId: String): Flow<SubmissionWithAssessment?>
}