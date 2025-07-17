package com.nexttechtitan.aptustutor.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// Payloads for Nearby Connections
data class SessionAdvertisementPayload(val sessionId: String, val tutorName: String, val className: String)
data class ConnectionRequestPayload(val studentId: String, val studentName: String, val classPin: String)

@Singleton
class AptusTutorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val classDao: ClassDao,
    private val sessionDao: SessionDao,
    private val studentProfileDao: StudentProfileDao,
    private val tutorProfileDao: TutorProfileDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val gson: Gson
) {
    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }
    private val serviceId = "com.nexttechtitan.aptustutor.SERVICE_ID"
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    private val pendingFileHeaders = mutableMapOf<Long, FileHeader>()

    // --- State Management ---
    private val _tutorUiState = MutableStateFlow(TutorDashboardUiState())
    val tutorUiState = _tutorUiState.asStateFlow()

    private val _studentUiState = MutableStateFlow(StudentDashboardUiState())
    val studentUiState = _studentUiState.asStateFlow()

    // --- Tutor Functions ---

    fun getClassesForTutor(tutorId: String) = classDao.getClassesForTutor(tutorId)

    suspend fun createNewClass(className: String): Boolean {
        val tutorId = userPreferencesRepository.userIdFlow.first() ?: return false
        val pin = (1000..9999).random().toString()
        val classProfile = ClassProfile(tutorOwnerId = tutorId, className = className, classPin = pin)
        val result = classDao.insertClass(classProfile)
        return result != -1L
    }

    suspend fun startTutorSession(classId: Long) {
        val tutorId = userPreferencesRepository.userIdFlow.first() ?: return
        val tutorName = userPreferencesRepository.userNameFlow.first() ?: "Tutor"
        val classWithStudents = classDao.getClassWithStudents(classId).first()

        val sessionId = UUID.randomUUID().toString()
        val session = Session(sessionId, classId, tutorId, System.currentTimeMillis())
        sessionDao.insertSession(session)

        _tutorUiState.update { it.copy(isAdvertising = true, activeSession = session, activeClass = classWithStudents) }

        val advertisement = gson.toJson(SessionAdvertisementPayload(sessionId, tutorName, classWithStudents.classProfile.className))
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()

        try {
            connectionsClient.startAdvertising(advertisement.toByteArray(Charsets.UTF_8), serviceId, connectionLifecycleCallback, advertisingOptions).await()
        } catch (e: Exception) {
            _tutorUiState.update { it.copy(isAdvertising = false, error = e.localizedMessage) }
        }
    }

    fun stopTutorSession() {
        val activeSession = _tutorUiState.value.activeSession
        if (activeSession != null) {
            repositoryScope.launch {
                activeSession.endTime = System.currentTimeMillis()
                sessionDao.insertSession(activeSession)
            }
        }
        connectionsClient.stopAllEndpoints()
        _tutorUiState.value = TutorDashboardUiState() // Reset state
    }

    suspend fun acceptStudent(request: ConnectionRequest) {
        connectionsClient.acceptConnection(request.endpointId, payloadCallback).await()
        // Add to roster if they are new
        repositoryScope.launch {
            val classId = _tutorUiState.value.activeClass?.classProfile?.classId ?: return@launch
            classDao.addStudentToRoster(ClassRosterCrossRef(classId, request.studentId))
        }
        // Move from pending to connected
        _tutorUiState.update { currentState ->
            currentState.copy(
                connectionRequests = currentState.connectionRequests.filterNot { it.endpointId == request.endpointId },
                connectedStudents = currentState.connectedStudents + ConnectedStudent(request.endpointId, request.studentId, request.studentName)
            )
        }
    }

    suspend fun rejectStudent(endpointId: String) {
        connectionsClient.rejectConnection(endpointId).await()
        _tutorUiState.update { it.copy(connectionRequests = it.connectionRequests.filterNot { req -> req.endpointId == endpointId }) }
    }

    suspend fun acceptAllVerified() {
        val requestsToAccept = _tutorUiState.value.connectionRequests
            .filter { it.status == VerificationStatus.PIN_VERIFIED_PENDING_APPROVAL }

        requestsToAccept.forEach { request ->
            acceptStudent(request)
        }
    }

    suspend fun sendAssessmentToAllStudents(assessment: Assessment) {
        _tutorUiState.update { it.copy(isAssessmentActive = true, activeAssessment = assessment, assessmentSubmissions = emptyMap()) }
        val wrapper = PayloadWrapper(type = "START_ASSESSMENT", jsonData = gson.toJson(assessment))
        val payload = Payload.fromBytes(gson.toJson(wrapper).toByteArray(Charsets.UTF_8))

        _tutorUiState.value.connectedStudents.forEach { student ->
            connectionsClient.sendPayload(student.endpointId, payload)
        }
    }

    // --- Student Functions ---

    suspend fun startStudentDiscovery() {
        _studentUiState.update { it.copy(isDiscovering = true, discoveredSessions = emptyList()) }
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        try {
            connectionsClient.startDiscovery(serviceId, endpointDiscoveryCallback, discoveryOptions).await()
        } catch (e: Exception) {
            _studentUiState.update { it.copy(isDiscovering = false, error = e.localizedMessage) }
        }
    }

    fun stopStudentDiscovery() {
        connectionsClient.stopDiscovery()
        _studentUiState.update { it.copy(isDiscovering = false) }
    }

    suspend fun requestToJoinSession(session: DiscoveredSession, pin: String) {
        val studentId = userPreferencesRepository.userIdFlow.first() ?: return
        val studentName = userPreferencesRepository.userNameFlow.first() ?: "Student"

        _studentUiState.update { it.copy(connectionStatus = "Connecting...") }

        val payload = gson.toJson(ConnectionRequestPayload(studentId, studentName, pin))
        try {
            connectionsClient.requestConnection(studentId, session.endpointId, connectionLifecycleCallback)
                .addOnSuccessListener {
                    connectionsClient.sendPayload(session.endpointId, Payload.fromBytes(payload.toByteArray(Charsets.UTF_8)))
                }
                .await()
        } catch (e: Exception) {
            _studentUiState.update { it.copy(connectionStatus = "Failed", error = e.localizedMessage) }
        }
    }

    suspend fun submitAssessment(submission: AssessmentSubmission, imageAnswers: Map<String, Uri>) {
        val tutorEndpointId = _studentUiState.value.connectedSession?.endpointId ?: return

        // 1. Send metadata about the submission first
        val metadataWrapper = PayloadWrapper("SUBMISSION_METADATA", gson.toJson(submission))
        val metadataPayload = Payload.fromBytes(gson.toJson(metadataWrapper).toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(tutorEndpointId, metadataPayload).await()

        // 2. For each image, send a header, then the file
        imageAnswers.forEach { (questionId, uri) ->
            val fileHeader = FileHeader(submission.submissionId, questionId)
            val headerWrapper = PayloadWrapper("FILE_HEADER", gson.toJson(fileHeader))
            val headerPayload = Payload.fromBytes(gson.toJson(headerWrapper).toByteArray(Charsets.UTF_8))

            // Send header
            connectionsClient.sendPayload(tutorEndpointId, headerPayload).await()

            // Send file
            val filePayload = Payload.fromFile(context.contentResolver.openFileDescriptor(uri, "r")!!)
            connectionsClient.sendPayload(tutorEndpointId, filePayload)
        }
    }

    // --- Shared Callbacks ---

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            // This callback is now primarily for the Student side to know an invitation is coming.
            // The tutor's logic will be handled via the payload.
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                // Student successfully connected
                _studentUiState.update { it.copy(connectionStatus = "Connected") }
            } else {
                // Disconnected or failed
                handleDisconnect(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            handleDisconnect(endpointId)
        }
    }

    private fun handleDisconnect(endpointId: String) {
        // Tutor side
        _tutorUiState.update { currentState ->
            currentState.copy(
                connectedStudents = currentState.connectedStudents.filterNot { it.endpointId == endpointId },
                connectionRequests = currentState.connectionRequests.filterNot { it.endpointId == endpointId }
            )
        }
        // Student side
        if (_studentUiState.value.connectedSession?.endpointId == endpointId) {
            _studentUiState.value = StudentDashboardUiState() // Reset student state
        }
    }


    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            try {
                val payloadString = String(info.endpointInfo, Charsets.UTF_8)
                val ad = gson.fromJson(payloadString, SessionAdvertisementPayload::class.java)
                val discoveredSession = DiscoveredSession(endpointId, ad.sessionId, ad.tutorName, ad.className)
                _studentUiState.update {
                    val currentSessions = it.discoveredSessions.filterNot { s -> s.endpointId == endpointId }
                    it.copy(discoveredSessions = currentSessions + discoveredSession)
                }
            } catch (e: JsonSyntaxException) { /* Ignore malformed advertisement */ }
        }

        override fun onEndpointLost(endpointId: String) {
            _studentUiState.update { it.copy(discoveredSessions = it.discoveredSessions.filterNot { s -> s.endpointId == endpointId }) }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> handleBytesPayload(endpointId, payload)
                Payload.Type.FILE -> handleFilePayload(endpointId, payload)
                else -> { /* Ignore Stream payloads for now */ }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Can be used to update UI with file transfer progress
        }
    }

    private fun handleConnectionRequest(endpointId: String, request: ConnectionRequestPayload) {
        val activeClass = _tutorUiState.value.activeClass ?: return // Safety check

        repositoryScope.launch {
            val expectedPin = activeClass.classProfile.classPin
            val isAlreadyOnRoster = activeClass.students.any { it.studentId == request.studentId }

            val status = when {
                isAlreadyOnRoster -> VerificationStatus.PENDING_APPROVAL
                request.classPin == expectedPin -> VerificationStatus.PIN_VERIFIED_PENDING_APPROVAL
                else -> VerificationStatus.REJECTED
            }

            if (status != VerificationStatus.REJECTED) {
                val newRequest = ConnectionRequest(endpointId, request.studentId, request.studentName, status)
                _tutorUiState.update { it.copy(connectionRequests = it.connectionRequests + newRequest) }
            } else {
                rejectStudent(endpointId)
            }
        }
    }

    suspend fun saveTutorProfile(tutorId: String, name: String) {
        val profile = TutorProfile(tutorId = tutorId, name = name)
        tutorProfileDao.insertOrUpdateTutor(profile)
    }

    suspend fun saveStudentProfile(studentId: String, name: String) {
        val profile = StudentProfile(studentId = studentId, name = name)
        studentProfileDao.insertOrUpdateStudent(profile)
    }

    suspend fun markStudentAsAbsent(sessionId: String, studentId: String) {
        val attendanceRecord = SessionAttendance(
            sessionId = sessionId,
            studentId = studentId,
            status = "Absent"
        )
        // This will overwrite any previous "Present" record for this session
        sessionDao.recordAttendance(attendanceRecord)

        // Also, disconnect them from the session
        val endpointId = _tutorUiState.value.connectedStudents.find { it.studentId == studentId }?.endpointId
        if (endpointId != null) {
            connectionsClient.disconnectFromEndpoint(endpointId)
            // The onDisconnected callback will handle removing them from the UI list
        }
    }

    fun markAttendanceForSession(sessionId: String, connectedStudents: List<ConnectedStudent>) {
        repositoryScope.launch {
            connectedStudents.forEach { student ->
                val attendanceRecord = SessionAttendance(
                    sessionId = sessionId,
                    studentId = student.studentId,
                    status = "Present"
                )
                sessionDao.recordAttendance(attendanceRecord)
            }
        }
    }

    fun getSessionHistoryForStudent(studentId: String): Flow<List<SessionWithClassDetails>> {
        return sessionDao.getSessionHistoryWithDetailsForStudent(studentId)
    }

    private fun handleBytesPayload(endpointId: String, payload: Payload) {
        val payloadString = String(payload.asBytes()!!, Charsets.UTF_8)
        try {
            val wrapper = gson.fromJson(payloadString, PayloadWrapper::class.java)
            when (wrapper.type) {
                "CONNECTION_REQUEST" -> handleConnectionRequest(endpointId, gson.fromJson(wrapper.jsonData, ConnectionRequestPayload::class.java))
                "START_ASSESSMENT" -> handleStartAssessment(gson.fromJson(wrapper.jsonData, Assessment::class.java))
                "SUBMISSION_METADATA" -> handleSubmissionMetadata(gson.fromJson(wrapper.jsonData, AssessmentSubmission::class.java))
                "FILE_HEADER" -> handleFileHeader(payload.id, gson.fromJson(wrapper.jsonData, FileHeader::class.java))
            }
        } catch (e: JsonSyntaxException) { /* Malformed payload, ignore */ }
    }

    private fun handleFilePayload(endpointId: String, payload: Payload) {
        val fileHeader = pendingFileHeaders.remove(payload.id) ?: return // No header for this file, ignore

        // Find the pending submission
        val submission = _tutorUiState.value.assessmentSubmissions[fileHeader.submissionId] ?: return

        // Create a safe place to store files
        val sessionDir = File(context.filesDir, "assessment_files/${submission.sessionId}")
        if (!sessionDir.exists()) sessionDir.mkdirs()

        val destinationFile = File(sessionDir, "${fileHeader.submissionId}_${fileHeader.questionId}.jpg")

        // Copy the received file
        val inputStream = context.contentResolver.openInputStream(payload.asFile()!!.asUri()!!)
        val outputStream = FileOutputStream(destinationFile)
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()

        // Update the submission object with the local file path
        val updatedAnswers = submission.answers.map {
            if (it.questionId == fileHeader.questionId) {
                it.copy(imageFilePath = destinationFile.absolutePath)
            } else {
                it
            }
        }
        val updatedSubmission = submission.copy(answers = updatedAnswers)

        _tutorUiState.update {
            val updatedSubmissions = it.assessmentSubmissions + (updatedSubmission.studentId to updatedSubmission)
            it.copy(assessmentSubmissions = updatedSubmissions)
        }
    }

    // --- Payload Handling Sub-routines ---
    private fun handleStartAssessment(assessment: Assessment) {
        _studentUiState.update { it.copy(activeAssessment = assessment) }
    }

    private fun handleSubmissionMetadata(submission: AssessmentSubmission) {
        // CRITICAL VALIDATION: Ignore if submission is not for the active session or from a non-connected student
        val activeSessionId = _tutorUiState.value.activeSession?.sessionId
        val isFromConnectedStudent = _tutorUiState.value.connectedStudents.any { it.studentId == submission.studentId }

        if (submission.sessionId != activeSessionId || !isFromConnectedStudent) {
            return // Ignore invalid submission
        }

        // Store the initial submission metadata
        _tutorUiState.update {
            val updatedSubmissions = it.assessmentSubmissions + (submission.studentId to submission)
            it.copy(assessmentSubmissions = updatedSubmissions)
        }
    }

    private fun handleFileHeader(payloadId: Long, fileHeader: FileHeader) {
        pendingFileHeaders[payloadId] = fileHeader
    }

    fun clearActiveAssessmentForStudent() {
        _studentUiState.update { it.copy(activeAssessment = null) }
    }
}