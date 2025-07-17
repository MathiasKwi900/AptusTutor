// File: data/DataModels.kt
package com.nexttechtitan.aptustutor.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

/**
 * Represents a unique user with the role of a student.
 * Stored on BOTH tutor and student devices.
 */
@Entity(tableName = "student_profiles")
data class StudentProfile(
    @PrimaryKey val studentId: String, // A unique, self-generated ID
    val name: String
)

/**
 * Represents a unique user with the role of a tutor.
 */
@Entity(tableName = "tutor_profiles")
data class TutorProfile(
    @PrimaryKey val tutorId: String, // A unique, self-generated ID
    val name: String
)

/**
 * Represents a specific class created by a tutor.
 * Stored on the TUTOR's device.
 */
@Entity(
    tableName = "class_profiles",
    indices = [Index(value = ["tutorOwnerId", "className"], unique = true)]
)
data class ClassProfile(
    @PrimaryKey(autoGenerate = true) val classId: Long = 0,
    val tutorOwnerId: String,
    val className: String,
    val classPin: String
)

/**
 * A joining table to create a many-to-many relationship
 * between classes and students (the class roster).
 */
@Entity(primaryKeys = ["classId", "studentId"])
data class ClassRosterCrossRef(
    val classId: Long,
    val studentId: String
)

/**
 * Represents a specific class with its roster of students.
 * This is a "relation" model for querying, not a table itself.
 */
data class ClassWithStudents(
    @Embedded val classProfile: ClassProfile,
    @Relation(
        parentColumn = "classId",
        entityColumn = "studentId",
        associateBy = Junction(ClassRosterCrossRef::class)
    )
    val students: List<StudentProfile>
)

/**
 * A "relation" model that combines a Session with the details of the
 * Class it belongs to. This is for querying, not a table itself.
 */
data class SessionWithClassDetails(
    @Embedded
    val session: Session,
    @Relation(
        parentColumn = "classId",
        entityColumn = "classId"
    )
    val classProfile: ClassProfile
)

/**
 * Represents a single teaching session for a specific class.
 * Stored on BOTH tutor and student devices.
 */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey val sessionId: String, // Unique ID for this specific session instance
    val classId: Long,
    val tutorId: String,
    val sessionTimestamp: Long,
    var endTime: Long? = null
)

/**
 * Represents a student's attendance for a specific session.
 * Stored on the STUDENT's device.
 */
@Entity(
    tableName = "session_attendance",
    indices = [Index(value = ["sessionId", "studentId"], unique = true)]
)
data class SessionAttendance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val studentId: String,
    val status: String, // e.g., "Present", "Absent"
    // We can add quiz results here later
)

enum class QuestionType {
    TEXT_INPUT,
    HANDWRITTEN_IMAGE
}

// The definition of a single question
data class AssessmentQuestion(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val type: QuestionType
)

// The entire assessment package sent from Tutor to Student
data class Assessment(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String, // CRITICAL: Links assessment to this specific session
    val title: String,
    val questions: List<AssessmentQuestion>,
    val durationInMinutes: Int
)

// A student's answer to a single question
data class AssessmentAnswer(
    val questionId: String,
    val textResponse: String? = null,
    // On the tutor's side, this will be populated with the path to the saved image file
    var imageFilePath: String? = null
)

// The student's complete submission package
data class AssessmentSubmission(
    val submissionId: String = UUID.randomUUID().toString(), // Unique ID for this attempt
    val sessionId: String, // CRITICAL: Must match the active session
    val studentId: String,
    val studentName: String,
    val assessmentId: String,
    var answers: List<AssessmentAnswer>
)

// A generic wrapper for all messages sent via BYTES payload
data class PayloadWrapper(
    val type: String, // e.g., "START_ASSESSMENT", "SUBMISSION_METADATA"
    val jsonData: String
)

// A specific header sent right before an image file
data class FileHeader(
    val submissionId: String,
    val questionId: String
)