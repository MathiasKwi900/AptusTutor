package com.nexttechtitan.aptustutor.data

import java.util.UUID

// Represents a session a student can see and request to join
data class DiscoveredSession(
    val endpointId: String,
    val sessionId: String,
    val tutorName: String,
    val className: String
)

// Represents a student's request to join, as seen by the tutor
data class ConnectionRequest(
    val endpointId: String,
    val studentId: String,
    val studentName: String,
    val status: VerificationStatus
)

// Represents a student currently in the tutor's active session
data class ConnectedStudent(
    val endpointId: String,
    val studentId: String,
    val name: String
)

enum class VerificationStatus {
    PENDING_APPROVAL, // For students already on the roster
    PIN_VERIFIED_PENDING_APPROVAL, // For new students who passed the PIN check
    REJECTED // For students with the wrong PIN
}

// --- Main UI State Holders ---

data class TutorDashboardUiState(
    val isAdvertising: Boolean = false,
    val activeSession: Session? = null,
    val activeClass: ClassWithStudents? = null,
    val connectionRequests: List<ConnectionRequest> = emptyList(),
    val connectedStudents: List<ConnectedStudent> = emptyList(),
    val sentAssessments: List<Assessment> = emptyList(),
    val viewingAssessmentId: String? = null,
    val assessmentSubmissions: Map<String, AssessmentSubmission> = emptyMap(),
    val error: String? = null
)

data class StudentDashboardUiState(
    val isDiscovering: Boolean = false,
    val discoveredSessions: List<DiscoveredSession> = emptyList(),
    val connectedSession: DiscoveredSession? = null,
    val connectionStatus: String = "Idle",
    val joiningSessionId: String? = null,
    val activeAssessment: AssessmentForStudent? = null,
    val error: String? = null
)

// The definition of a single question
data class AssessmentQuestion(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val type: QuestionType,
    val markingGuide: String,
    var questionImagePath: String? = null,
    val maxScore: Int = 10,
    val options: List<String>? = null
)

enum class QuestionType {
    TEXT_INPUT,
    HANDWRITTEN_IMAGE,
    MULTIPLE_CHOICE
}

enum class FeedbackStatus {
    PENDING_SEND,
    SENT_PENDING_ACK,
    DELIVERED
}

// A student's answer to a single question
data class AssessmentAnswer(
    val questionId: String,
    val textResponse: String? = null,
    var imageFilePath: String? = null,
    var score: Int? = null,
    var feedback: String? = null
)

data class SessionHistoryItem(
    val sessionWithDetails: SessionWithClassDetails,
    val hasSubmission: Boolean
)