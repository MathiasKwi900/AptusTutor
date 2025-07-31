package com.nexttechtitan.aptustutor.data

import androidx.work.WorkInfo
import java.util.UUID

/** Represents a tutor's session discovered by a student on the network. */
data class DiscoveredSession(
    val endpointId: String,
    val sessionId: String,
    val tutorName: String,
    val className: String
)

/** Represents a student's request to join a session, as displayed on the tutor's screen. */
data class ConnectionRequest(
    val endpointId: String,
    val studentId: String,
    val studentName: String,
    val status: VerificationStatus
)


data class ConnectedStudent(
    val endpointId: String,
    val studentId: String,
    val name: String
)

/** The verification status of a student's connection request. */
enum class VerificationStatus {
    PENDING_APPROVAL, // For students already on the roster
    PIN_VERIFIED_PENDING_APPROVAL, // For new students who passed the PIN check
    REJECTED // For students with the wrong PIN
}

/**
 * A single, immutable object representing the entire state of the Tutor's dashboard.
 * Using a single state object makes state management predictable and easier to debug.
 */
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

/**
 * A single, immutable object representing the entire state of the Student's dashboard.
 */
data class StudentDashboardUiState(
    val isDiscovering: Boolean = false,
    val discoveredSessions: List<DiscoveredSession> = emptyList(),
    val connectedSession: DiscoveredSession? = null,
    val connectionStatus: String = "Idle",
    val joiningSessionId: String? = null,
    val activeAssessment: AssessmentForStudent? = null,
    val error: String? = null
)

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

/** The delivery status of feedback sent from a tutor to a student. */
enum class FeedbackStatus {
    PENDING_SEND, // Graded by tutor, but not yet sent (e.g., student was offline).
    SENT_PENDING_ACK, // Sent to student, awaiting confirmation of receipt.
    DELIVERED // Student has confirmed receipt.
}

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

data class AiSettingsUiState(
    val downloadState: WorkInfo.State? = null,
    val downloadProgress: Int = 0
)