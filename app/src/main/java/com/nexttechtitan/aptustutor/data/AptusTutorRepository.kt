package com.nexttechtitan.aptustutor.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.gson.Gson
import com.nexttechtitan.aptustutor.utils.ImageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A one-shot event sent from the Repository to the student Dashboard to notify
 * them of a new feedback message.
 */
sealed class RepositoryEvent {
    data object NewFeedbackReceived : RepositoryEvent()
}

/**
 * The central data layer for the app. It acts as a single source of truth by:
 * 1. Managing all P2P network interactions via Google Nearby Connections.
 * 2. Orchestrating all local database operations through the DAOs.
 * 3. Exposing reactive UI state to ViewModels via StateFlows.
 */
@Singleton
class AptusTutorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AptusTutorDatabase,
    private val classDao: ClassDao,
    private val sessionDao: SessionDao,
    private val studentProfileDao: StudentProfileDao,
    internal val assessmentDao: AssessmentDao,
    private val tutorProfileDao: TutorProfileDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val gson: Gson
) {
    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }
    private val serviceId = "com.nexttechtitan.aptustutor.SERVICE_ID"
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    // Caches for handling Nearby Connections race conditions. For example, a file payload
    // might arrive before its corresponding metadata payload.
    private val pendingConnectionPayloads = mutableMapOf<String, ConnectionRequestPayload>()
    private val pendingAnswerFiles = mutableMapOf<String, File>()
    private val pendingQuestionFiles = mutableMapOf<String, MutableMap<String, File>>()
    private val incomingFilePayloads = mutableMapOf<Long, Payload>()

    // Private mutable state holders, exposed as immutable public flows.
    private val _tutorUiState = MutableStateFlow(TutorDashboardUiState())
    val tutorUiState = _tutorUiState.asStateFlow()

    private val _studentUiState = MutableStateFlow(StudentDashboardUiState())
    val studentUiState = _studentUiState.asStateFlow()

    private val _events = MutableSharedFlow<RepositoryEvent>()
    val events = _events.asSharedFlow()

    fun getClassesForTutor(tutorId: String) = classDao.getClassesForTutor(tutorId)

    suspend fun createNewClass(className: String): Boolean = withContext(Dispatchers.IO) {
        val tutorId = userPreferencesRepository.userIdFlow.first() ?: return@withContext false
        val pin = (1000..9999).random().toString()
        val classProfile = ClassProfile(tutorOwnerId = tutorId, className = className, classPin = pin)
        val result = classDao.insertClass(classProfile)
        result != -1L
    }

    /**
     * Starts advertising on the network, making the tutor's session discoverable by students.
     * This updates the UI state and begins listening for connection requests.
     *
     * @param classId The class for which the session is being started.
     * @return A [Result] indicating success or failure of the advertising operation.
     */
    suspend fun startTutorSession(classId: Long): Result<Unit> {
        return try {
            val tutorId = userPreferencesRepository.userIdFlow.first()
                ?: return Result.failure(IllegalStateException("User ID not found."))
            val tutorName = userPreferencesRepository.userNameFlow.first()?: "Tutor"
            val classWithStudents = classDao.getClassWithStudents(classId).first()
                ?: return Result.failure(IllegalStateException("Class not found."))

            val sessionId = UUID.randomUUID().toString()
            val session = Session(sessionId, classId, tutorId, System.currentTimeMillis())
            sessionDao.insertSession(session)

            _tutorUiState.update { it.copy(isAdvertising = true, activeSession = session, activeClass = classWithStudents) }

            val advertisingName = tutorName
            val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()

            connectionsClient.startAdvertising(advertisingName, serviceId, connectionLifecycleCallback, advertisingOptions).await()
            Result.success(Unit)

        } catch (e: Exception) {
            _tutorUiState.update { it.copy(isAdvertising = false, error = e.localizedMessage) }
            Result.failure(e)
        }
    }

    /**
     * Stops advertising and disconnects all endpoints. Resets the tutor's UI state.
     * Persists the session end time to the database.
     */
    fun stopTutorSession() {
        val activeSession = _tutorUiState.value.activeSession
        if (activeSession != null) {
            repositoryScope.launch {
                activeSession.endTime = System.currentTimeMillis()
                sessionDao.insertSession(activeSession)
            }
        }
        connectionsClient.stopAllEndpoints()
        _tutorUiState.value = TutorDashboardUiState()
    }

    /**
     * Approves a student's connection request. This function orchestrates several steps:
     * 1. Adds the student to the local database roster.
     * 2. Updates the UI state to move the student from "pending" to "connected".
     * 3. Sends a "CONNECTION_APPROVED" payload to the student.
     * 4. Sends a "SESSION_INFO" payload with class details.
     * 5. Checks for and sends any pending/undelivered feedback for that student.
     */
    suspend fun acceptStudent(request: ConnectionRequest) {
        val activeSession = _tutorUiState.value.activeSession?: return
        val activeClass = _tutorUiState.value.activeClass?: return

        repositoryScope.launch {
            classDao.addStudentToRoster(ClassRosterCrossRef(activeClass.classProfile.classId, request.studentId))
            studentProfileDao.insertOrUpdateStudent(StudentProfile(request.studentId, request.studentName))
        }
        _tutorUiState.update { currentState ->
            val updatedStudentList = currentState.activeClass?.students.orEmpty() +
                    StudentProfile(request.studentId, request.studentName)
            val updatedActiveClass = currentState.activeClass?.copy(students = updatedStudentList.distinctBy { it.studentId })

            currentState.copy(
                connectionRequests = currentState.connectionRequests.filterNot { it.endpointId == request.endpointId },
                connectedStudents = currentState.connectedStudents + ConnectedStudent(request.endpointId, request.studentId, request.studentName),
                activeClass = updatedActiveClass
            )
        }

        val approvalPayloadWrapper = PayloadWrapper("CONNECTION_APPROVED", "{}")
        val approvalPayloadBytes = gson.toJson(approvalPayloadWrapper).toByteArray(Charsets.UTF_8)
        connectionsClient.sendPayload(request.endpointId, Payload.fromBytes(approvalPayloadBytes))

        val sessionInfoPayloadData = SessionAdvertisementPayload(
            sessionId = activeSession.sessionId,
            tutorName = userPreferencesRepository.userNameFlow.first()?: "Tutor",
            className = activeClass.classProfile.className
        )
        val sessionInfoWrapper = PayloadWrapper("SESSION_INFO", gson.toJson(sessionInfoPayloadData))
        val sessionInfoBytes = gson.toJson(sessionInfoWrapper).toByteArray(Charsets.UTF_8)
        connectionsClient.sendPayload(request.endpointId, Payload.fromBytes(sessionInfoBytes))

        resendPendingFeedbackForStudent(request.studentId, request.endpointId)
    }

    fun rejectStudent(endpointId: String) {
        connectionsClient.disconnectFromEndpoint(endpointId)
    }

    suspend fun acceptAllVerified() {
        val requestsToAccept = _tutorUiState.value.connectionRequests
            .filter { it.status == VerificationStatus.PIN_VERIFIED_PENDING_APPROVAL }

        requestsToAccept.forEach { request ->
            acceptStudent(request)
        }
    }

    suspend fun sendAssessmentToAllStudents(assessmentBlueprint: AssessmentBlueprint): Result<Unit> {
        return try {
            // Saves the assessment to database before sending to students
            val assessmentEntity = Assessment(
                id = assessmentBlueprint.id,
                sessionId = assessmentBlueprint.sessionId,
                title = assessmentBlueprint.title,
                questions = assessmentBlueprint.questions,
                durationInMinutes = assessmentBlueprint.durationInMinutes,
                sentTimestamp = assessmentBlueprint.sentTimestamp
            )
            assessmentDao.insertAssessment(assessmentEntity)

            // Creates a sanitized version of the assessment for the student, excluding crucial
            // details such as the marking guide
            val studentQuestions = assessmentBlueprint.questions.map {
                StudentAssessmentQuestion(
                    id = it.id,
                    text = it.text,
                    type = it.type,
                    questionImageFile = it.questionImagePath?.let { path -> File(path).name },
                    maxScore = it.maxScore,
                    options = it.options
                )
            }
            val assessmentForStudent = AssessmentForStudent(
                id = assessmentBlueprint.id,
                sessionId = assessmentBlueprint.sessionId,
                title = assessmentBlueprint.title,
                questions = studentQuestions,
                durationInMinutes = assessmentBlueprint.durationInMinutes,
                sentTimestamp = assessmentBlueprint.sentTimestamp
            )

            // Sends the main assessment data first
            _tutorUiState.update {
                it.copy(
                    sentAssessments = it.sentAssessments + assessmentEntity,
                    viewingAssessmentId = assessmentEntity.id
                )
            }
            val wrapper = PayloadWrapper(type = "START_ASSESSMENT", jsonData = gson.toJson(assessmentForStudent))
            val mainPayload = Payload.fromBytes(gson.toJson(wrapper).toByteArray(Charsets.UTF_8))

            val studentEndpoints = _tutorUiState.value.connectedStudents.map { it.endpointId }
            if (studentEndpoints.isEmpty()) {
                return Result.success(Unit)
            }

            studentEndpoints.forEach { connectionsClient.sendPayload(it, mainPayload).await() }

            // Sends the compressed question image files using the atomic payload method
            for (question in assessmentBlueprint.questions) {
                question.questionImagePath?.let { path ->
                    val imageFile = File(path)
                    if (imageFile.exists()) {
                        // Images are first compressed to reduce their size before sending
                        when (val compressionResult = ImageUtils.compressImage(context, Uri.fromFile(imageFile))) {
                            is ImageUtils.ImageCompressionResult.Success -> {
                                val compressedBytes = compressionResult.byteArray
                                val fileHeader = EmbeddedFileHeader(
                                    sessionId = assessmentBlueprint.sessionId,
                                    questionId = question.id
                                )
                                val headerJson = gson.toJson(fileHeader)
                                val headerBytes = headerJson.toByteArray(Charsets.UTF_8)
                                val combinedFile = File.createTempFile("atomic_q_img_", ".bin", context.cacheDir)
                                FileOutputStream(combinedFile).use { fos ->
                                    // 1. Write header size (as a 4-byte Int)
                                    fos.write(java.nio.ByteBuffer.allocate(4).putInt(headerBytes.size).array())
                                    // 2. Write header JSON
                                    fos.write(headerBytes)
                                    // 3. Write THE COMPRESSED image bytes
                                    fos.write(compressedBytes)
                                }

                                val filePayload = Payload.fromFile(combinedFile)
                                for (endpointId in studentEndpoints) {
                                    connectionsClient.sendPayload(endpointId, filePayload).await()
                                }
                                combinedFile.delete()
                            }
                            is ImageUtils.ImageCompressionResult.Error -> {
                                throw IOException("Failed to compress question image: ${compressionResult.message}")
                            }
                        }
                    }
                }
            }
            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveSubmissionDraft(submission: AssessmentSubmission) {
        assessmentDao.insertSubmission(submission)
    }

    suspend fun sendFeedbackAndMarkAttendance(submission: AssessmentSubmission) {
        val updatedSubmission = submission.copy(feedbackStatus = FeedbackStatus.SENT_PENDING_ACK)
        assessmentDao.insertSubmission(updatedSubmission)
        val studentEndpoint = _tutorUiState.value.connectedStudents.find { it.studentId == submission.studentId }?.endpointId
        val activeSession = _tutorUiState.value.activeSession
        val activeClassProfile = _tutorUiState.value.activeClass?.classProfile

        if (studentEndpoint != null && activeSession != null && activeClassProfile != null) {
            val attendanceRecord = SessionAttendance(sessionId = submission.sessionId, studentId = submission.studentId, status = "Present")
            sessionDao.recordAttendance(attendanceRecord)

            val feedbackPayload = GradedFeedbackPayload(submission = updatedSubmission, attendanceRecord = attendanceRecord, session = activeSession, classProfile = activeClassProfile)
            val wrapper = PayloadWrapper("ASSESSMENT_RESULT", gson.toJson(feedbackPayload))
            val payload = Payload.fromBytes(gson.toJson(wrapper).toByteArray(Charsets.UTF_8))
            connectionsClient.sendPayload(studentEndpoint, payload)
        } else {
            // Student is not connected, feedback queued
        }
    }

    suspend fun sendAllPendingFeedbackForSession(sessionId: String) {
        val allSubmissions = assessmentDao.getSubmissionsForSession(sessionId).first()
        val pendingSubmissions = allSubmissions.filter { it.feedbackStatus == FeedbackStatus.SENT_PENDING_ACK }
        for (submission in pendingSubmissions) {
            val studentEndpoint = _tutorUiState.value.connectedStudents
                .find { it.studentId == submission.studentId }?.endpointId

            if (studentEndpoint != null) {
                val session = _tutorUiState.value.activeSession!!
                val classProfile = _tutorUiState.value.activeClass!!.classProfile
                val attendance = SessionAttendance(sessionId = submission.sessionId, studentId = submission.studentId, status = "Present")
                val feedbackPayload = GradedFeedbackPayload(submission, attendance, session, classProfile)
                val wrapper = PayloadWrapper("ASSESSMENT_RESULT", gson.toJson(feedbackPayload))
                val payload = Payload.fromBytes(gson.toJson(wrapper).toByteArray(Charsets.UTF_8))
                connectionsClient.sendPayload(studentEndpoint, payload)
                delay(200)
            }
        }
    }

    fun startStudentDiscovery() {
        _studentUiState.update { it.copy(isDiscovering = true, discoveredSessions = emptyList()) }
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        connectionsClient.startDiscovery(serviceId, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener {
                //
            }
            .addOnFailureListener { e ->
                val apiException = e as? com.google.android.gms.common.api.ApiException
                val statusCode = apiException?.statusCode ?: "N/A"
                val errorDetails = "Error: ${e.message}, Status Code: $statusCode"
                _studentUiState.update { it.copy(isDiscovering = false, error = errorDetails) }
            }
    }

    fun stopStudentDiscovery() {
        connectionsClient.stopDiscovery()
        _studentUiState.update { it.copy(isDiscovering = false) }
    }

    suspend fun requestToJoinSession(session: DiscoveredSession, pin: String) {
        val studentId = userPreferencesRepository.userIdFlow.first() ?: return
        val studentName = userPreferencesRepository.userNameFlow.first() ?: "Student"
        _studentUiState.update { it.copy(connectionStatus = "Requesting...") }
        val payloadData = ConnectionRequestPayload(studentId = studentId, studentName = studentName, classPin = pin)
        pendingConnectionPayloads[session.endpointId] = payloadData

        connectionsClient.requestConnection(studentName, session.endpointId, connectionLifecycleCallback)
            .addOnSuccessListener {
                //
            }
            .addOnFailureListener { e ->
                _studentUiState.update { it.copy(connectionStatus = "Failed to connect", error = e.localizedMessage) }
                pendingConnectionPayloads.remove(session.endpointId)
            }
    }

    suspend fun submitAssessment(submission: AssessmentSubmission, imageAnswers: Map<String, Uri>) {
        val tutorEndpointId = _studentUiState.value.connectedSession?.endpointId ?: return
        assessmentDao.insertSubmission(submission)
        val metadataWrapper = PayloadWrapper("SUBMISSION_METADATA", gson.toJson(submission))
        val metadataPayload = Payload.fromBytes(gson.toJson(metadataWrapper).toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(tutorEndpointId, metadataPayload).await()

        imageAnswers.forEach { (questionId, uri) ->
            val fileHeader = EmbeddedFileHeader(
                sessionId = submission.sessionId,
                submissionId = submission.submissionId,
                questionId = questionId
            )
            val headerJson = gson.toJson(fileHeader)
            val headerBytes = headerJson.toByteArray(Charsets.UTF_8)

            val combinedFile = File.createTempFile("submission_ans_img_", ".bin", context.cacheDir)

            try {
                FileOutputStream(combinedFile).use { fos ->
                    // 1. Write header size
                    fos.write(java.nio.ByteBuffer.allocate(4).putInt(headerBytes.size).array())
                    // 2. Write header JSON
                    fos.write(headerBytes)
                    // 3. Directly read all bytes from the file and write them.
                    val imageBytes = File(uri.path!!).readBytes()
                    fos.write(imageBytes)
                }

                val filePayload = Payload.fromFile(combinedFile)
                connectionsClient.sendPayload(tutorEndpointId, filePayload).await()

            } catch (e: Exception) {
                //
            } finally {
                combinedFile.delete()
            }
            delay(500L)
        }
    }

    /**
     * Handles the lifecycle of a Nearby Connection for both Tutor and Student roles.
     */
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        /**
         * Called when a connection is initiated. The Tutor provisionally accepts to open
         * a communication channel, allowing the student to send their PIN for verification.
         * The Student accepts to complete the handshake.
         */
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            val role = if (_tutorUiState.value.activeClass != null) "TUTOR" else "STUDENT"
            if (role == "TUTOR") {
                connectionsClient.acceptConnection(endpointId, payloadCallback)
            } else {
                _studentUiState.update { it.copy(connectionStatus = "Handshaking...") }
                connectionsClient.acceptConnection(endpointId, payloadCallback)
                    .addOnSuccessListener {
                        //
                    }
                    .addOnFailureListener { e ->
                        _studentUiState.update { it.copy(connectionStatus = "Failed", error = e.localizedMessage) }
                    }
            }
        }

        /**
         * Called with the final result of a connection attempt. If successful, the Student
         * sends their cached connection request payload (containing the PIN). The Tutor
         * simply waits for this payload.
         */
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val role = if (_tutorUiState.value.activeClass != null) "TUTOR" else "STUDENT"
            if (result.status.isSuccess) {
                if (role == "STUDENT") {
                    val payloadData = pendingConnectionPayloads.remove(endpointId)
                    if (payloadData != null) {
                        val wrapper = PayloadWrapper("CONNECTION_REQUEST", gson.toJson(payloadData))
                        val payload = Payload.fromBytes(gson.toJson(wrapper).toByteArray(Charsets.UTF_8))
                        connectionsClient.sendPayload(endpointId, payload)

                        _studentUiState.update {
                            it.copy(
                                connectionStatus = "Verifying PIN...",
                                connectedSession = it.discoveredSessions.find { s -> s.endpointId == endpointId }
                            )
                        }
                    } else {
                        //
                    }
                } else {
                    //
                }
            } else {
                pendingConnectionPayloads.remove(endpointId)
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
            _studentUiState.value = StudentDashboardUiState()
        }
    }

    /**
     * Handles discovery of nearby advertising tutors.
     */
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        /**
         * Called when a student discovers a tutor. The tutor's name is taken directly
         * from the advertisement info, and a temporary session object is created in the UI state.
         */
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val discoveredSession = DiscoveredSession(
                endpointId = endpointId,
                sessionId = "",
                tutorName = info.endpointName,
                className = "Connecting..."
            )

            _studentUiState.update {
                val currentSessions = it.discoveredSessions.filterNot { s -> s.endpointId == endpointId }
                it.copy(discoveredSessions = currentSessions + discoveredSession)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _studentUiState.update { it.copy(discoveredSessions = it.discoveredSessions.filterNot { s -> s.endpointId == endpointId }) }
        }
    }

    /**
     * The main entry point for processing all incoming data from other devices.
     */
    private val payloadCallback = object : PayloadCallback() {
        /**
         * Delegates incoming payloads to the appropriate handler based on type (BYTES or FILE).
         * All processing is done on an IO-scoped coroutine to avoid blocking the main thread.
         */
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    repositoryScope.launch { handleBytesPayload(endpointId, payload) }
                }
                Payload.Type.FILE -> {
                    incomingFilePayloads[payload.id] = payload
                }
                else -> { /* Ignore Stream payloads */ }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val payload = incomingFilePayloads.remove(update.payloadId)
                if (payload != null && payload.type == Payload.Type.FILE) {
                    repositoryScope.launch {
                        handleFilePayload(payload)
                    }
                }
            } else if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                incomingFilePayloads.remove(update.payloadId)
            }
        }
    }

    private fun handleConnectionRequest(endpointId: String, request: ConnectionRequestPayload) {
        val activeClass = _tutorUiState.value.activeClass ?: return

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
                connectionsClient.disconnectFromEndpoint(endpointId)
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
        sessionDao.recordAttendance(attendanceRecord)

        val endpointId = _tutorUiState.value.connectedStudents.find { it.studentId == studentId }?.endpointId
        if (endpointId != null) {
            connectionsClient.disconnectFromEndpoint(endpointId)
        }
    }

    fun markAttendanceForSession(sessionId: String, connectedStudents: List<ConnectedStudent>) {
        repositoryScope.launch {
            val session = _tutorUiState.value.activeSession ?: return@launch
            val classProfile = _tutorUiState.value.activeClass?.classProfile ?: return@launch

            for (student in connectedStudents) {
                val attendanceRecord = SessionAttendance(sessionId = sessionId, studentId = student.studentId, status = "Present")
                sessionDao.recordAttendance(attendanceRecord)

                val endPayloadData = SessionEndPayload(session, classProfile, attendanceRecord)
                val payloadWrapper = PayloadWrapper("SESSION_END_DATA", gson.toJson(endPayloadData))
                val payloadBytes = gson.toJson(payloadWrapper).toByteArray(Charsets.UTF_8)

                connectionsClient.sendPayload(student.endpointId, Payload.fromBytes(payloadBytes))
            }
        }
    }

    /**
     * Central handler for all structured data (JSON) payloads. It deserializes the
     * generic [PayloadWrapper] to determine the message type and then forwards the
     * specific JSON data to the correct processing function.
     */
    private suspend fun handleBytesPayload(endpointId: String, payload: Payload) {
        val payloadString = String(payload.asBytes()!!, Charsets.UTF_8)
        try {
            val wrapper = gson.fromJson(payloadString, PayloadWrapper::class.java)
            when (wrapper.type) {
                "CONNECTION_REQUEST" -> handleConnectionRequest(endpointId, gson.fromJson(wrapper.jsonData, ConnectionRequestPayload::class.java))
                "START_ASSESSMENT" -> handleStartAssessment(gson.fromJson(wrapper.jsonData, AssessmentForStudent::class.java))
                "SUBMISSION_METADATA" -> {
                    val submission = gson.fromJson(wrapper.jsonData, AssessmentSubmission::class.java)
                    handleSubmissionMetadata(submission)?.let { validatedSubmission ->
                        _tutorUiState.update { uiState ->
                            uiState.copy(error = "${validatedSubmission.studentName} has submitted.")
                        }
                    }
                }
                "CONNECTION_APPROVED" -> {
                    withContext(Dispatchers.Main) {
                        _studentUiState.update { it.copy(connectionStatus = "Connected") }
                    }
                }
                "SESSION_INFO" -> {
                    val sessionInfo = gson.fromJson(wrapper.jsonData, SessionAdvertisementPayload::class.java)
                    _studentUiState.update { state ->
                        val updatedSession = state.connectedSession?.copy(
                            sessionId = sessionInfo.sessionId,
                            className = sessionInfo.className
                        )
                        state.copy(connectedSession = updatedSession)
                    }
                }
                "ASSESSMENT_RESULT" -> handleAssessmentResult(endpointId, wrapper.jsonData)
                "FEEDBACK_ACK" -> {
                    val ack = gson.fromJson(wrapper.jsonData, FeedbackAckPayload::class.java)
                    assessmentDao.getSubmissionById(ack.submissionId)?.let {
                        assessmentDao.insertSubmission(it.copy(feedbackStatus = FeedbackStatus.DELIVERED))
                    }
                }
                "SESSION_END_DATA" -> {
                    val endPayload = gson.fromJson(wrapper.jsonData, SessionEndPayload::class.java)
                    classDao.insertClass(endPayload.classProfile)
                    sessionDao.insertSession(endPayload.session)
                    sessionDao.recordAttendance(endPayload.attendance)
                }
            }
        } catch (e: Exception) {
            //
        }
    }

    private suspend fun handleAssessmentResult(tutorEndpointId: String, jsonData: String) {
        try {
            val feedbackPayload = gson.fromJson(jsonData, GradedFeedbackPayload::class.java)
            val gradedSubmission = feedbackPayload.submission
            db.withTransaction {
                classDao.insertClass(feedbackPayload.classProfile)
                sessionDao.insertSession(feedbackPayload.session)
                sessionDao.recordAttendance(feedbackPayload.attendanceRecord)

                val localSubmission = assessmentDao.getSubmissionById(gradedSubmission.submissionId)
                if (localSubmission == null) {
                    assessmentDao.insertSubmission(gradedSubmission)
                } else {
                    val updatedAnswers = localSubmission.answers?.map { localAnswer ->
                        val gradedAnswer =
                            gradedSubmission.answers?.find { it.questionId == localAnswer.questionId }
                        if (gradedAnswer != null) {
                            localAnswer.copy(
                                score = gradedAnswer.score,
                                feedback = gradedAnswer.feedback
                            )
                        } else {
                            localAnswer
                        }
                    }
                    val finalSubmission = localSubmission.copy(answers = updatedAnswers)
                    assessmentDao.insertSubmission(finalSubmission)
                }
            }
            _events.emit(RepositoryEvent.NewFeedbackReceived)

            val ackPayload = FeedbackAckPayload(submissionId = feedbackPayload.submission.submissionId)
            val wrapper = PayloadWrapper("FEEDBACK_ACK", gson.toJson(ackPayload))
            val payload = Payload.fromBytes(gson.toJson(wrapper).toByteArray(Charsets.UTF_8))

            try {
                connectionsClient.sendPayload(tutorEndpointId, payload).await()
            } catch (e: Exception) {
                //
            }

        } catch (e: Exception) {
            //
        }
    }

    /**
     * Handles incoming files (mainly answer images for now). It uses a "smart"
     * file structure where the first part of the file is a JSON header, and the rest is
     * the image data. This allows the receiver to know the file's context (which submission
     * or question it belongs to) before processing it.
     */
    private suspend fun handleFilePayload(payload: Payload) = withContext(Dispatchers.IO) {
        val receivedFilePayload = payload.asFile() ?: return@withContext
        val receivedUri = receivedFilePayload.asUri() ?: return@withContext

        var header: EmbeddedFileHeader? = null
        var finalImageFile: File? = null

        try {
            context.contentResolver.openInputStream(receivedUri)?.use { inputStream ->
                val headerSizeBuffer = ByteArray(4)
                if (inputStream.read(headerSizeBuffer) != 4) throw IOException("Could not read header size.")
                val headerSize = java.nio.ByteBuffer.wrap(headerSizeBuffer).int

                val headerBuffer = ByteArray(headerSize)
                if (inputStream.read(headerBuffer) != headerSize) throw IOException("Could not read full header.")
                val headerJson = String(headerBuffer, Charsets.UTF_8)
                header = gson.fromJson(headerJson, EmbeddedFileHeader::class.java)

                finalImageFile = File.createTempFile("final_img_", ".jpg", context.cacheDir)
                FileOutputStream(finalImageFile).use { fileOutputStream ->
                    inputStream.copyTo(fileOutputStream)
                }
            }
        } catch (e: Exception) {
            finalImageFile?.delete()
            return@withContext
        }

        if (header == null || finalImageFile == null) {
            finalImageFile?.delete()
            return@withContext
        }

        if (header!!.submissionId != null) {
            val submission = assessmentDao.getSubmissionById(header!!.submissionId!!)
            if (submission == null) {
                val cacheKey = "${header!!.submissionId}_${header!!.questionId}"
                pendingAnswerFiles[cacheKey] = finalImageFile!!
            } else {
                processAnswerImage(submission, header!!.questionId, finalImageFile!!)
                finalImageFile!!.delete()
            }
        } else {
            val activeAssessment = _studentUiState.value.activeAssessment
            if (activeAssessment == null || activeAssessment.sessionId != header!!.sessionId) {
                val sessionFiles = pendingQuestionFiles.getOrPut(header!!.sessionId) { mutableMapOf() }
                sessionFiles[header!!.questionId] = finalImageFile!!
            } else {
                processQuestionImage(header!!.questionId, finalImageFile!!)
                finalImageFile!!.delete()
            }
        }
    }

    private suspend fun processAnswerImage(submission: AssessmentSubmission, questionId: String, tempCopiedFile: File) = withContext(Dispatchers.IO) {
        val sessionDir = File(context.filesDir, "assessment_files/${submission.sessionId}")
        sessionDir.mkdirs()
        val destinationFile = File(sessionDir, "${submission.submissionId}_${questionId}.jpg")

        try {
            tempCopiedFile.copyTo(destinationFile, overwrite = true)
            tempCopiedFile.delete()

            val updatedAnswers = submission.answers?.map {
                if (it.questionId == questionId) it.copy(imageFilePath = destinationFile.absolutePath) else it
            }
            assessmentDao.insertSubmission(submission.copy(answers = updatedAnswers))
        } catch (e: Exception) {
            //
        }
    }

    private suspend fun processQuestionImage(questionId: String, tempCopiedFile: File) = withContext(Dispatchers.IO) {
        val activeAssessment = _studentUiState.value.activeAssessment ?: return@withContext
        val question = activeAssessment.questions.find { it.id == questionId } ?: return@withContext
        val destinationFilename = question.questionImageFile ?: return@withContext

        val destinationDir = File(context.filesDir, "question_images")
        destinationDir.mkdirs()
        val destinationFile = File(destinationDir, destinationFilename)

        try {
            tempCopiedFile.copyTo(destinationFile, overwrite = true)
            tempCopiedFile.delete()

            withContext(Dispatchers.Main) {
                val updatedQuestions = _studentUiState.value.activeAssessment?.questions?.map {
                    if (it.id == questionId) it.copy(questionImageFile = destinationFile.absolutePath) else it
                } ?: emptyList()
                _studentUiState.update { it.copy(activeAssessment = it.activeAssessment?.copy(questions = updatedQuestions)) }
            }
        } catch (e: Exception) {
           //
        }
    }

    private fun resendPendingFeedbackForStudent(studentId: String, endpointId: String) {
        repositoryScope.launch {
            val pendingSubmissions = assessmentDao.getSubmissionsForStudent(studentId).first()
                .filter { it.feedbackStatus == FeedbackStatus.SENT_PENDING_ACK }

            if (pendingSubmissions.isNotEmpty()) {
                val session = _tutorUiState.value.activeSession ?: return@launch
                val classProfile = _tutorUiState.value.activeClass?.classProfile ?: return@launch

                for (submission in pendingSubmissions) {
                    val attendance = SessionAttendance(sessionId = submission.sessionId, studentId = studentId, status = "Present")
                    val feedbackPayload = GradedFeedbackPayload(submission, attendance, session, classProfile)
                    val wrapper = PayloadWrapper("ASSESSMENT_RESULT", gson.toJson(feedbackPayload))
                    val payload = Payload.fromBytes(gson.toJson(wrapper).toByteArray(Charsets.UTF_8))
                    connectionsClient.sendPayload(endpointId, payload)
                    delay(200)
                }
            }
        }
    }

    private suspend fun handleStartAssessment(assessmentForStudent: AssessmentForStudent) {
        val assessmentEntity = Assessment(
            id = assessmentForStudent.id,
            sessionId = assessmentForStudent.sessionId,
            title = assessmentForStudent.title,
            questions = assessmentForStudent.questions.map {
                AssessmentQuestion(
                    id = it.id,
                    text = it.text,
                    type = it.type,
                    markingGuide = "",
                    questionImagePath = it.questionImageFile,
                    maxScore = it.maxScore,
                    options = it.options
                )
            },
            durationInMinutes = assessmentForStudent.durationInMinutes,
            sentTimestamp = assessmentForStudent.sentTimestamp
        )

        assessmentDao.insertAssessment(assessmentEntity)

        withContext(Dispatchers.Main) {
            _studentUiState.update { it.copy(activeAssessment = assessmentForStudent) }
        }

        pendingQuestionFiles.remove(assessmentForStudent.sessionId)?.forEach { (questionId, file) ->
            processQuestionImage(questionId, file)
            file.delete()
        }
    }

    suspend fun handleSubmissionMetadata(submission: AssessmentSubmission): AssessmentSubmission? {
        val activeSessionId = _tutorUiState.value.activeSession?.sessionId
        val isFromConnectedStudent = _tutorUiState.value.connectedStudents.any { it.studentId == submission.studentId }

        if (submission.sessionId != activeSessionId || !isFromConnectedStudent) {
            return null
        }
        assessmentDao.insertSubmission(submission)

        submission.answers?.forEach { answer ->
            val cacheKey = "${submission.submissionId}_${answer.questionId}"
            pendingAnswerFiles.remove(cacheKey)?.let { file ->
                processAnswerImage(submission, answer.questionId, file)
                file.delete()
            }
        }
        _tutorUiState.update { uiState ->
            uiState.copy(error = "${submission.studentName} has submitted.")
        }
        return submission
    }

    fun getAttendedSessionsForStudent(studentId: String): Flow<List<SessionWithClassDetails>> {
        return sessionDao.getAttendedSessionsForStudent(studentId)
    }

    fun getSubmissionsForStudent(studentId: String): Flow<List<AssessmentSubmission>> {
        return assessmentDao.getSubmissionsForStudent(studentId)
    }

    fun clearActiveAssessmentForStudent() {
        _studentUiState.update { it.copy(activeAssessment = null) }
    }

    fun getSubmissionsFlowForAssessment(assessmentId: String): Flow<List<AssessmentSubmission>> {
        return assessmentDao.getSubmissionsForAssessment(assessmentId)
    }

    fun getSubmissionFlow(submissionId: String): Flow<AssessmentSubmission?> {
        return assessmentDao.getSubmissionFlow(submissionId)
    }

    fun getAssessmentFlowBySubmission(submissionId: String): Flow<Assessment?> {
        return assessmentDao.getAssessmentForSubmission(submissionId)
    }

    suspend fun switchUserRole(newRole: String) {
        userPreferencesRepository.switchUserRole(newRole)
    }

    fun disconnectFromSession() {
        val endpointId = _studentUiState.value.connectedSession?.endpointId ?: return
        connectionsClient.disconnectFromEndpoint(endpointId)
    }

    fun setJoiningState(sessionId: String) {
        _studentUiState.update { it.copy(joiningSessionId = sessionId) }
    }

    fun errorShown() {
        _studentUiState.update { it.copy(error = null) }
        _tutorUiState.update { it.copy(error = null) }
    }

    fun getTutorHistory(tutorId: String): Flow<List<SessionWithClassDetails>> {
        return sessionDao.getTutorSessionHistory(tutorId)
    }

    fun getAssessmentById(assessmentId: String): Flow<Assessment?> {
        return assessmentDao.getAssessmentById(assessmentId)
    }

    fun selectAssessmentToView(assessmentId: String?) {
        _tutorUiState.update { it.copy(viewingAssessmentId = assessmentId) }
    }
}