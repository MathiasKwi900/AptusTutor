package com.nexttechtitan.aptustutor.data

import java.util.UUID

/** A data payload sent from tutor to student containing core session info after connection approval. */
data class SessionAdvertisementPayload(val sessionId: String, val tutorName: String, val className: String)

/** A data payload sent from student to tutor to initiate a connection, containing their PIN for verification. */
data class ConnectionRequestPayload(val studentId: String, val studentName: String, val classPin: String)

data class StudentAssessmentQuestion(
    val id: String,
    val text: String,
    val type: QuestionType,
    val questionImageFile: String? = null,
    val maxScore: Int,
    val options: List<String>? = null
)

data class AssessmentBlueprint(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val title: String,
    val questions: List<AssessmentQuestion>,
    val durationInMinutes: Int,
    val sentTimestamp: Long = System.currentTimeMillis()
)

data class AssessmentForStudent(
    val id: String,
    val sessionId: String,
    val title: String,
    val questions: List<StudentAssessmentQuestion>,
    val durationInMinutes: Int,
    val sentTimestamp: Long
)

/**
 * A generic wrapper for all BYTES payloads. This standardizes communication, allowing
 * the receiver to first parse the `type` to determine how to handle the `jsonData`.
 */
data class PayloadWrapper(
    val type: String, // e.g., "START_ASSESSMENT", "SUBMISSION_METADATA"
    val jsonData: String
)

/**
 * A JSON header embedded at the start of a FILE payload. It provides context for the
 * raw file data that follows, linking it to a specific session, question, and/or submission.
 */
data class EmbeddedFileHeader(
    val sessionId: String,
    val questionId: String,
    val submissionId: String? = null // Null for question images, non-null for answer images
)

data class SessionEndPayload(
    val session: Session,
    val classProfile: ClassProfile,
    val attendance: SessionAttendance
)

/** The payload containing a student's final graded submission and attendance record from the tutor. */
data class GradedFeedbackPayload(
    val submission: AssessmentSubmission,
    val attendanceRecord: SessionAttendance,
    val session: Session,
    val classProfile: ClassProfile
)

/** A payload sent from the student back to the tutor to confirm receipt of graded feedback. */
data class FeedbackAckPayload( // "Ack" is short for "Acknowledgement"
    val submissionId: String
)