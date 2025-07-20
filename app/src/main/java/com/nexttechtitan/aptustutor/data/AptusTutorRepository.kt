package com.nexttechtitan.aptustutor.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.nexttechtitan.aptustutor.utils.ImageUtils
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AptusTutorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val classDao: ClassDao,
    private val sessionDao: SessionDao,
    private val studentProfileDao: StudentProfileDao,
    private val assessmentDao: AssessmentDao,
    private val tutorProfileDao: TutorProfileDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val gson: Gson
) {
    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }
    private val serviceId = "com.nexttechtitan.aptustutor.SERVICE_ID"
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    private val pendingConnectionPayloads = mutableMapOf<String, ConnectionRequestPayload>()
    private val pendingAnswerFiles = mutableMapOf<String, File>()
    private val pendingQuestionFiles = mutableMapOf<String, MutableMap<String, File>>()


    // --- State Management ---
    private val _tutorUiState = MutableStateFlow(TutorDashboardUiState())
    val tutorUiState = _tutorUiState.asStateFlow()

    private val _studentUiState = MutableStateFlow(StudentDashboardUiState())
    val studentUiState = _studentUiState.asStateFlow()

    // --- Tutor Functions ---

    fun getClassesForTutor(tutorId: String) = classDao.getClassesForTutor(tutorId)



    suspend fun createNewClass(className: String): Boolean = withContext(Dispatchers.IO) {
        val tutorId = userPreferencesRepository.userIdFlow.first() ?: return@withContext false
        val pin = (1000..9999).random().toString()
        val classProfile = ClassProfile(tutorOwnerId = tutorId, className = className, classPin = pin)
        val result = classDao.insertClass(classProfile)
        result != -1L
    }

    suspend fun startTutorSession(classId: Long): Result<Unit> {
        return try {
            val tutorId = userPreferencesRepository.userIdFlow.first()
                ?: return Result.failure(IllegalStateException("User ID not found."))
            val tutorName = userPreferencesRepository.userNameFlow.first() ?: "Tutor"
            val classWithStudents = classDao.getClassWithStudents(classId).first()
                ?: return Result.failure(IllegalStateException("Class not found."))
            val className = classWithStudents.classProfile.className

            val sessionId = UUID.randomUUID().toString()
            val session = Session(sessionId, classId, tutorId, System.currentTimeMillis())
            sessionDao.insertSession(session)

            _tutorUiState.update { it.copy(isAdvertising = true, activeSession = session, activeClass = classWithStudents) }

            val advertisementPayload = SessionAdvertisementPayload(sessionId, tutorName, className)
            val advertisementName = gson.toJson(advertisementPayload)

            Log.d("AptusTutorDebug", "[TUTOR] startTutorSession: Attempting to advertise with name: $advertisementName")
            val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()

            connectionsClient.startAdvertising(advertisementName, serviceId, connectionLifecycleCallback, advertisingOptions).await()
            Log.d("AptusTutorDebug", "[TUTOR] startTutorSession: Advertising successfully started.")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e("AptusTutorDebug", "[TUTOR] startTutorSession: Advertising FAILED", e)
            _tutorUiState.update { it.copy(isAdvertising = false, error = e.localizedMessage) }
            Result.failure(e)
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
        Log.d("AptusTutorDebug", "[TUTOR] Manually accepting ${request.studentName}. Moving to connected list.")
        repositoryScope.launch {
            val classId = _tutorUiState.value.activeClass?.classProfile?.classId ?: return@launch
            classDao.addStudentToRoster(ClassRosterCrossRef(classId, request.studentId))
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

        val approvalPayload = PayloadWrapper("CONNECTION_APPROVED", "{}")
        val payloadBytes = gson.toJson(approvalPayload).toByteArray(Charsets.UTF_8)
        connectionsClient.sendPayload(request.endpointId, Payload.fromBytes(payloadBytes))
    }

    fun rejectStudent(endpointId: String) {
        Log.d("AptusTutorDebug", "[TUTOR] Manually rejecting/disconnecting student with endpoint $endpointId")
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
            Log.d("AptusTutorDebug", "[TUTOR] Sending assessment '${assessmentBlueprint.title}'")
            // 1. Save to database
            val assessmentEntity = Assessment(
                id = assessmentBlueprint.id,
                sessionId = assessmentBlueprint.sessionId,
                title = assessmentBlueprint.title,
                questions = assessmentBlueprint.questions,
                durationInMinutes = assessmentBlueprint.durationInMinutes
            )
            assessmentDao.insertAssessment(assessmentEntity)

            // 2. Create sanitized version for student
            val studentQuestions = assessmentBlueprint.questions.map {
                StudentAssessmentQuestion(
                    id = it.id,
                    text = it.text,
                    type = it.type,
                    questionImageFile = it.questionImagePath?.let { path -> File(path).name }
                )
            }
            val assessmentForStudent = AssessmentForStudent(
                id = assessmentBlueprint.id,
                sessionId = assessmentBlueprint.sessionId,
                title = assessmentBlueprint.title,
                questions = studentQuestions,
                durationInMinutes = assessmentBlueprint.durationInMinutes
            )

            // 3. Send the main assessment data first
            _tutorUiState.update { it.copy(isAssessmentActive = true, activeAssessment = assessmentEntity) }
            val wrapper = PayloadWrapper(type = "START_ASSESSMENT", jsonData = gson.toJson(assessmentForStudent))
            val mainPayload = Payload.fromBytes(gson.toJson(wrapper).toByteArray(Charsets.UTF_8))

            val studentEndpoints = _tutorUiState.value.connectedStudents.map { it.endpointId }
            if (studentEndpoints.isEmpty()) {
                Log.w("AptusTutorDebug", "No connected students to send assessment to.")
                return Result.success(Unit)
            }

            studentEndpoints.forEach { connectionsClient.sendPayload(it, mainPayload).await() }

            // 4. Send the compressed question image files using the atomic payload method
            for (question in assessmentBlueprint.questions) {
                question.questionImagePath?.let { path ->
                    val imageFile = File(path)
                    if (imageFile.exists()) {
                        // --- YOUR COMPRESSION LOGIC IS PRESERVED HERE ---
                        when (val compressionResult = ImageUtils.compressImage(context, Uri.fromFile(imageFile))) {
                            is ImageUtils.ImageCompressionResult.Success -> {
                                // compressionResult.byteArray contains the compressed image data
                                val compressedBytes = compressionResult.byteArray

                                // Create the new, more robust header
                                val fileHeader = EmbeddedFileHeader(
                                    sessionId = assessmentBlueprint.sessionId,
                                    questionId = question.id
                                )
                                val headerJson = gson.toJson(fileHeader)
                                val headerBytes = headerJson.toByteArray(Charsets.UTF_8)

                                // Create a temporary file to be our "smart package"
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
                                Log.d("AptusTutorDebug", "[TUTOR] Sending atomic question image for qId: ${question.id}")
                                for (endpointId in studentEndpoints) {
                                    connectionsClient.sendPayload(endpointId, filePayload).await()
                                }
                                combinedFile.delete() // Clean up
                            }
                            is ImageUtils.ImageCompressionResult.Error -> {
                                throw IOException("Failed to compress question image: ${compressionResult.message}")
                            }
                        }
                    }
                }
            }
            Log.d("AptusTutorDebug", "Assessment and all files sent successfully.")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e("AptusTutorDebug", "[TUTOR] sendAssessmentToAllStudents: FAILED", e)
            Result.failure(e)
        }
    }

    suspend fun saveManualGrade(submissionId: String, questionId: String, score: Int, feedback: String) = withContext(Dispatchers.IO) {
        val submission = assessmentDao.getSubmissionById(submissionId) ?: return@withContext
        val updatedAnswers = submission.answers.map {
            if (it.questionId == questionId) {
                it.copy(score = score, feedback = feedback)
            } else {
                it
            }
        }
        assessmentDao.insertSubmission(submission.copy(answers = updatedAnswers))
    }

    suspend fun sendFeedbackToStudent(submission: AssessmentSubmission) {
        // Find the student's endpointId from the connected list
        val studentEndpoint = _tutorUiState.value.connectedStudents.find { it.studentId == submission.studentId }?.endpointId
        studentEndpoint?.let {
            val wrapper = PayloadWrapper("ASSESSMENT_RESULT", gson.toJson(submission))
            val payload = Payload.fromBytes(gson.toJson(wrapper).toByteArray(Charsets.UTF_8))
            connectionsClient.sendPayload(it, payload)
        }
    }

    // --- Student Functions ---

    suspend fun startStudentDiscovery() {
        Log.d("AptusTutorDebug", "[STUDENT] startStudentDiscovery: Attempting to start discovery.")
        _studentUiState.update { it.copy(isDiscovering = true, discoveredSessions = emptyList()) }
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        connectionsClient.startDiscovery(serviceId, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener {
                Log.d("AptusTutorDebug", "[STUDENT] startStudentDiscovery: Discovery successfully started.")
                // Discovery started successfully, no need to do anything here
                // as the state is already updated.
            }
            .addOnFailureListener { e ->
                Log.e("AptusTutorDebug", "[STUDENT] startStudentDiscovery: Discovery FAILED.", e)
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
        Log.d("AptusTutorDebug", "[STUDENT] requestToJoinSession: Requesting to join session=${session.sessionId} with endpointId=${session.endpointId}")

        // Store the payload data to be sent AFTER the connection is confirmed.
        val payloadData = ConnectionRequestPayload(studentId = studentId, studentName = studentName, classPin = pin)
        pendingConnectionPayloads[session.endpointId] = payloadData

        connectionsClient.requestConnection(studentName, session.endpointId, connectionLifecycleCallback)
            .addOnSuccessListener {
                // The request was sent successfully. We now wait for onConnectionResult.
                Log.d("AptusTutorDebug", "[STUDENT] requestToJoinSession: Connection request sent. Waiting for connection result.")
            }
            .addOnFailureListener { e ->
                Log.e("AptusTutorDebug", "[STUDENT] requestToJoinSession: Connection request FAILED.", e)
                _studentUiState.update { it.copy(connectionStatus = "Failed to connect", error = e.localizedMessage) }
                // Clean up the pending payload if the request fails immediately.
                pendingConnectionPayloads.remove(session.endpointId)
            }
    }

    suspend fun submitAssessment(submission: AssessmentSubmission, imageAnswers: Map<String, Uri>) {
        val tutorEndpointId = _studentUiState.value.connectedSession?.endpointId ?: return

        // 1. Send metadata about the submission first
        val metadataWrapper = PayloadWrapper("SUBMISSION_METADATA", gson.toJson(submission))
        val metadataPayload = Payload.fromBytes(gson.toJson(metadataWrapper).toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(tutorEndpointId, metadataPayload).await()

        // 2. For each image, create a combined file and send it
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
                    // 3. Write image bytes from the content URI
                    context.contentResolver.openInputStream(uri)?.use { imageInputStream ->
                        imageInputStream.copyTo(fos)
                    } ?: throw IOException("Could not open input stream for URI: $uri")
                }

                val filePayload = Payload.fromFile(combinedFile)
                Log.d("AptusTutorDebug", "[STUDENT] Sending answer image for qId: $questionId with size ${combinedFile.length()}")
                connectionsClient.sendPayload(tutorEndpointId, filePayload).await()

            } catch (e: Exception) {
                Log.e("AptusTutorDebug", "[STUDENT] Failed to create or send combined file for qId: $questionId", e)
            } finally {
                combinedFile.delete() // Always clean up
            }
        }
    }
    // --- Shared Callbacks ---

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            val role = if (_tutorUiState.value.activeClass != null) "TUTOR" else "STUDENT"
            Log.d("AptusTutorDebug", "[$role] onConnectionInitiated: From endpointId=$endpointId, name=${connectionInfo.endpointName}")

            if (role == "TUTOR") {
                // Provisionally accept the connection. This opens the data channel so we can receive the PIN.
                Log.d("AptusTutorDebug", "[TUTOR] Provisionally accepting connection from $endpointId to receive PIN.")
                connectionsClient.acceptConnection(endpointId, payloadCallback)
                    .addOnSuccessListener {
                        Log.d("AptusTutorDebug", "[TUTOR] acceptConnection for $endpointId SUCCEEDED (listener). Now waiting for onConnectionResult callback.")
                    }
                    .addOnFailureListener { e ->
                        Log.e("AptusTutorDebug", "[TUTOR] acceptConnection for $endpointId FAILED (listener).", e)
                    }
            } else {
                _studentUiState.update { it.copy(connectionStatus = "Handshaking...") }
                // Add this line to complete the handshake from the student's side.
                connectionsClient.acceptConnection(endpointId, payloadCallback)
                    .addOnSuccessListener {
                        Log.d("AptusTutorDebug", "[STUDENT] acceptConnection for $endpointId SUCCEEDED. Waiting for onConnectionResult.")
                    }
                    .addOnFailureListener { e ->
                        Log.e("AptusTutorDebug", "[STUDENT] acceptConnection for $endpointId FAILED.", e)
                        _studentUiState.update { it.copy(connectionStatus = "Failed", error = e.localizedMessage) }
                    }
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val role = if (_tutorUiState.value.activeClass != null) "TUTOR" else "STUDENT"
            val status = if (result.status.isSuccess) "SUCCESS" else "FAILURE (Code: ${result.status.statusCode})"
            Log.d("AptusTutorDebug", "[$role] onConnectionResult: For endpointId=$endpointId, Result: $status")

            if (result.status.isSuccess) {
                if (role == "STUDENT") {
                    // The connection is live! Now, retrieve the stored payload and send it.
                    val payloadData = pendingConnectionPayloads.remove(endpointId)
                    if (payloadData != null) {
                        Log.d("AptusTutorDebug", "[STUDENT] Connection successful. Sending PIN payload for endpoint $endpointId.")
                        val wrapper = PayloadWrapper("CONNECTION_REQUEST", gson.toJson(payloadData))
                        val payload = Payload.fromBytes(gson.toJson(wrapper).toByteArray(Charsets.UTF_8))
                        connectionsClient.sendPayload(endpointId, payload)

                        // Update UI to a more descriptive status
                        _studentUiState.update {
                            it.copy(
                                connectionStatus = "Verifying PIN...",
                                connectedSession = it.discoveredSessions.find { s -> s.endpointId == endpointId }
                            )
                        }
                    } else {
                        Log.w("AptusTutorDebug", "[STUDENT] Connection successful, but no pending PIN payload found for $endpointId.")
                    }
                } else { // TUTOR role
                    Log.d("AptusTutorDebug", "[TUTOR] Connection with $endpointId established. Waiting for PIN payload.")
                    // Tutor does nothing; just waits for the payload to arrive in payloadCallback.
                }
            } else {
                // Connection failed, clean up pending data and UI state.
                pendingConnectionPayloads.remove(endpointId) // Student cleans up pending payload
                handleDisconnect(endpointId) // Both roles clean up UI state
            }
        }
        override fun onDisconnected(endpointId: String) {
            val role = if (_tutorUiState.value.activeClass != null) "TUTOR" else "STUDENT"
            Log.d("AptusTutorDebug", "[$role] onDisconnected: From endpointId=$endpointId")
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
            Log.d("AptusTutorDebug", "[STUDENT] onEndpointFound: Found endpointId=$endpointId with raw name=${info.endpointName}")
            try {
                // The endpointName is the JSON string sent by the tutor.
                val payloadString = info.endpointName
                val ad = gson.fromJson(payloadString, SessionAdvertisementPayload::class.java)
                val discoveredSession = DiscoveredSession(endpointId, ad.sessionId, ad.tutorName, ad.className)
                Log.d("AptusTutorDebug", "[STUDENT] onEndpointFound: Successfully parsed session: ${ad.className}")

                _studentUiState.update {
                    val currentSessions = it.discoveredSessions.filterNot { s -> s.endpointId == endpointId }
                    it.copy(discoveredSessions = currentSessions + discoveredSession)
                }
            } catch (e: JsonSyntaxException) {
                Log.e("AptusTutorDebug", "[STUDENT] onEndpointFound: Failed to parse JSON from endpointName", e)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d("AptusTutorDebug", "[STUDENT] onEndpointLost: Lost endpointId=$endpointId")
            _studentUiState.update { it.copy(discoveredSessions = it.discoveredSessions.filterNot { s -> s.endpointId == endpointId }) }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d("AptusTutorDebug", "onPayloadReceived: From endpointId=$endpointId, Payload type: ${payload.type}")
            repositoryScope.launch {
                when (payload.type) {
                    Payload.Type.BYTES -> handleBytesPayload(endpointId, payload)
                    Payload.Type.FILE -> handleFilePayload(payload)
                    else -> { /* Ignore Stream payloads for now */
                    }
                }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Can be used to update UI with file transfer progress
        }
    }

    private fun handleConnectionRequest(endpointId: String, request: ConnectionRequestPayload) {
        Log.d("AptusTutorDebug", "[TUTOR] handleConnectionRequest: Received PIN from ${request.studentName} for endpointId=$endpointId")
        val activeClass = _tutorUiState.value.activeClass ?: return // Safety check

        repositoryScope.launch {
            val expectedPin = activeClass.classProfile.classPin
            val isAlreadyOnRoster = activeClass.students.any { it.studentId == request.studentId }

            val status = when {
                isAlreadyOnRoster -> VerificationStatus.PENDING_APPROVAL
                request.classPin == expectedPin -> VerificationStatus.PIN_VERIFIED_PENDING_APPROVAL
                else -> VerificationStatus.REJECTED
            }

            Log.d("AptusTutorDebug", "[TUTOR] handleConnectionRequest: Expected PIN: $expectedPin, Received PIN: ${request.classPin}")
            if (status != VerificationStatus.REJECTED) {
                Log.d("AptusTutorDebug", "[TUTOR] PIN correct for $endpointId. Adding to pending connection requests list.")
                val newRequest = ConnectionRequest(endpointId, request.studentId, request.studentName, status)
                _tutorUiState.update { it.copy(connectionRequests = it.connectionRequests + newRequest) }
            } else {
                Log.w("AptusTutorDebug", "[TUTOR] PIN Incorrect. Disconnecting endpointId=$endpointId")
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

    fun getSubmissionWithAssessment(sessionId: String, studentId: String): Flow<SubmissionWithAssessment?> {
        return assessmentDao.getSubmissionWithAssessment(sessionId, studentId)
    }

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
                    Log.d("AptusTutorDebug", "[STUDENT] Connection approved by tutor. Fully connected.")
                    _studentUiState.update { it.copy(connectionStatus = "Connected") }
                }
                "ASSESSMENT_RESULT" -> {
                    val gradedSubmission = gson.fromJson(wrapper.jsonData, AssessmentSubmission::class.java)
                    assessmentDao.insertSubmission(gradedSubmission)
                }
                "SESSION_END_DATA" -> {
                    val endPayload = gson.fromJson(wrapper.jsonData, SessionEndPayload::class.java)
                    classDao.insertClass(endPayload.classProfile)
                    sessionDao.insertSession(endPayload.session)
                    sessionDao.recordAttendance(endPayload.attendance)
                }
            }
        } catch (e: JsonSyntaxException) { /* Malformed payload, ignore */ }
    }

    private suspend fun handleFilePayload(payload: Payload) = withContext(Dispatchers.IO) {
        val receivedFilePayload = payload.asFile() ?: return@withContext
        val receivedUri = receivedFilePayload.asUri() ?: return@withContext

        var header: EmbeddedFileHeader? = null
        // FIX: Initialize as nullable to handle potential exceptions during parsing.
        var finalImageFile: File? = null

        try {
            context.contentResolver.openInputStream(receivedUri)?.use { inputStream ->
                // 1. Read the header size (first 4 bytes)
                val headerSizeBuffer = ByteArray(4)
                if (inputStream.read(headerSizeBuffer) != 4) throw IOException("Could not read header size.")
                val headerSize = java.nio.ByteBuffer.wrap(headerSizeBuffer).int

                // 2. Read the header JSON
                val headerBuffer = ByteArray(headerSize)
                if (inputStream.read(headerBuffer) != headerSize) throw IOException("Could not read full header.")
                val headerJson = String(headerBuffer, Charsets.UTF_8)
                header = gson.fromJson(headerJson, EmbeddedFileHeader::class.java)

                Log.d("AptusTutorDebug", "Parsed file header: $header")

                // 3. Create a temporary file to hold the remaining pure image bytes
                finalImageFile = File.createTempFile("final_img_", ".jpg", context.cacheDir)
                FileOutputStream(finalImageFile).use { fileOutputStream ->
                    inputStream.copyTo(fileOutputStream)
                }
            }
        } catch (e: Exception) {
            Log.e("AptusTutorDebug", "Failed to parse incoming file payload", e)
            finalImageFile?.delete() // Clean up partially created file if it exists
            return@withContext
        }

        // Only proceed if the header and file were successfully processed
        if (header == null || finalImageFile == null) {
            Log.e("AptusTutorDebug", "Header or file was null after parsing, cannot process.")
            finalImageFile?.delete()
            return@withContext
        }

        // --- Logic to process the file based on the parsed header ---
        if (header!!.submissionId != null) {
            // This is an ANSWER image from a student, for the TUTOR
            val submission = assessmentDao.getSubmissionById(header!!.submissionId!!)
            if (submission == null) {
                Log.d("AptusTutorDebug", "[TUTOR] Caching answer image: ${finalImageFile!!.name}")
                val cacheKey = "${header!!.submissionId}_${header!!.questionId}"
                pendingAnswerFiles[cacheKey] = finalImageFile!!
            } else {
                Log.d("AptusTutorDebug", "[TUTOR] Processing answer image directly.")
                processAnswerImage(submission, header!!.questionId, finalImageFile!!)
                finalImageFile!!.delete()
            }
        } else {
            // This is a QUESTION image from the tutor, for the STUDENT
            val activeAssessment = _studentUiState.value.activeAssessment
            // ENHANCEMENT: Validate that the file belongs to the student's current session.
            if (activeAssessment == null || activeAssessment.sessionId != header!!.sessionId) {
                Log.w("AptusTutorDebug", "[STUDENT] Caching question image as active assessment is not ready or session ID mismatch.")
                // FIX: Corrected variable name from 'fileHeader' to 'header'
                val sessionFiles = pendingQuestionFiles.getOrPut(header!!.sessionId) { mutableMapOf() }
                sessionFiles[header!!.questionId] = finalImageFile!!
            } else {
                Log.d("AptusTutorDebug", "[STUDENT] Processing question image directly.")
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
            // Move our temp file to its final destination
            tempCopiedFile.renameTo(destinationFile)

            val updatedAnswers = submission.answers.map {
                if (it.questionId == questionId) it.copy(imageFilePath = destinationFile.absolutePath) else it
            }
            assessmentDao.insertSubmission(submission.copy(answers = updatedAnswers))
            Log.d("AptusTutorDebug", "[TUTOR] Successfully processed and saved answer image for sub ${submission.submissionId}")
        } catch (e: Exception) {
            Log.e("AptusTutorDebug", "[TUTOR] Error processing answer image", e)
        }
    }

    private suspend fun processQuestionImage(questionId: String, tempCopiedFile: File) = withContext(Dispatchers.IO) {
        val activeAssessment = _studentUiState.value.activeAssessment ?: return@withContext
        val question = activeAssessment.questions.find { it.id == questionId } ?: return@withContext
        val destinationDir = File(context.filesDir, "question_images")
        destinationDir.mkdirs()
        val destinationFile = File(destinationDir, question.questionImageFile!!)

        try {
            // Move our temp file to its final destination
            tempCopiedFile.renameTo(destinationFile)
            Log.d("AptusTutorDebug", "[STUDENT] Saved question image to ${destinationFile.absolutePath}")

            withContext(Dispatchers.Main) {
                val updatedQuestions = _studentUiState.value.activeAssessment?.questions?.map {
                    if (it.id == questionId) it.copy(questionImageFile = destinationFile.absolutePath) else it
                } ?: emptyList()
                _studentUiState.update { it.copy(activeAssessment = it.activeAssessment?.copy(questions = updatedQuestions)) }
            }
        } catch (e: Exception) {
            Log.e("AptusTutorDebug", "[STUDENT] Failed to save question image", e)
        }
    }

    // --- Payload Handling Sub-routines ---
    private suspend fun handleStartAssessment(assessment: AssessmentForStudent) {
        Log.d("AptusTutorDebug", "[STUDENT] Received assessment '${assessment.title}'")
        _studentUiState.update { it.copy(activeAssessment = assessment) }

        pendingQuestionFiles.remove(assessment.sessionId)?.forEach { (questionId, file) ->
            Log.d("AptusTutorDebug", "[STUDENT] Found cached question image for ${assessment.title}. Processing now.")
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

        submission.answers.forEach { answer ->
            val cacheKey = "${submission.submissionId}_${answer.questionId}"
            pendingAnswerFiles.remove(cacheKey)?.let { file ->
                Log.d("AptusTutorDebug", "[TUTOR] Found cached answer image for submission ${submission.submissionId}. Processing now.")
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

    fun getSubmissionsFlow(assessmentId: String): Flow<List<AssessmentSubmission>> {
        return assessmentDao.getSubmissionsForAssessment(assessmentId)
    }

    fun getSubmissionFlow(submissionId: String): Flow<AssessmentSubmission?> {
        return assessmentDao.getSubmissionFlow(submissionId)
    }

    fun getAssessmentFlow(submissionId: String): Flow<Assessment?> {
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
}