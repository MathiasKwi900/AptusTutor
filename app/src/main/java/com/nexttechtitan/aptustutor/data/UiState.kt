// File: data/UiState.kt
package com.nexttechtitan.aptustutor.data

// --- Data for UI display ---

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
    val isAssessmentActive: Boolean = false,
    val activeAssessment: Assessment? = null,
    val assessmentSubmissions: Map<String, AssessmentSubmission> = emptyMap(),
    val error: String? = null
)

data class StudentDashboardUiState(
    val isDiscovering: Boolean = false,
    val discoveredSessions: List<DiscoveredSession> = emptyList(),
    val connectedSession: DiscoveredSession? = null,
    val connectionStatus: String = "Idle",
    val activeAssessment: Assessment? = null,
    val error: String? = null
)