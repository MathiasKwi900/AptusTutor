package com.nexttechtitan.aptustutor.data

import java.util.UUID

// Payloads for Nearby Connections
data class SessionAdvertisementPayload(val sessionId: String, val tutorName: String, val className: String)
data class ConnectionRequestPayload(val studentId: String, val studentName: String, val classPin: String)

data class StudentAssessmentQuestion(
    val id: String,
    val text: String,
    val type: QuestionType,
    val questionImageFile: String? = null,
    val maxScore: Int
)

data class AssessmentBlueprint(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val title: String,
    val questions: List<AssessmentQuestion>,
    val durationInMinutes: Int
)

// The assessment payload sent to the student
data class AssessmentForStudent(
    val id: String,
    val sessionId: String,
    val title: String,
    val questions: List<StudentAssessmentQuestion>,
    val durationInMinutes: Int
)

// A generic wrapper for all messages sent via BYTES payload
data class PayloadWrapper(
    val type: String, // e.g., "START_ASSESSMENT", "SUBMISSION_METADATA"
    val jsonData: String
)

data class EmbeddedFileHeader(
    val sessionId: String,
    val questionId: String,
    val submissionId: String? = null
)

data class SessionEndPayload(
    val session: Session,
    val classProfile: ClassProfile,
    val attendance: SessionAttendance
)

data class GradedFeedbackPayload(
    val submission: AssessmentSubmission,
    val attendanceRecord: SessionAttendance,
    val session: Session,
    val classProfile: ClassProfile
)

data class FeedbackAckPayload( // "Ack" is short for "Acknowledgement"
    val submissionId: String
)