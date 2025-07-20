package com.nexttechtitan.aptustutor.ui.tutor

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.nexttechtitan.aptustutor.data.*
import com.nexttechtitan.aptustutor.ui.AptusTutorScreen
import com.nexttechtitan.aptustutor.ui.student.ComposeFileProvider
import com.nexttechtitan.aptustutor.ui.student.EmptyState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun TutorDashboardScreen(
    viewModel: TutorDashboardViewModel = hiltViewModel(),
    onNavigateToSubmission: (String) -> Unit,
    navController: NavHostController
) {
    // --- BATTLE-TESTED LOGIC BLOCK (PRESERVED EXACTLY) ---
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val classes by viewModel.tutorClasses.collectAsStateWithLifecycle(initialValue = emptyList())
    val submissions by viewModel.assessmentSubmissions.collectAsStateWithLifecycle()

    var showCreateClassDialog by remember { mutableStateOf(false) }
    var showStopSessionDialog by remember { mutableStateOf(false) }
    var showCreateAssessmentDialog by remember { mutableStateOf(false) }
    var selectedClassToStart by remember { mutableStateOf<ClassWithStudents?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.errorShown()
        }
    }

    LaunchedEffect(key1 = true) {
        viewModel.toastEvents.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val requiredPermissions = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION,
            )
            else -> listOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions = requiredPermissions)

    LaunchedEffect(key1 = Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }
    // --- END OF PRESERVED LOGIC BLOCK ---

    val isSessionActive = uiState.isAdvertising && uiState.activeClass != null

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            // DESIGN: The TopAppBar is now conditional, providing a cleaner look for the active session.
            AnimatedVisibility(visible = !isSessionActive) {
                CenterAlignedTopAppBar(
                    title = { Text("Tutor Dashboard") },
                    actions = { SettingsMenu(onSwitchRole = viewModel::switchUserRole, navController = navController) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        },
        // DESIGN: A FloatingActionButton is the standard, intuitive way to handle a primary "create" action.
        floatingActionButton = {
            AnimatedVisibility(visible = !isSessionActive) {
                FloatingActionButton(onClick = { showCreateClassDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Create New Class")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (isSessionActive) {
                ActiveSessionScreen(
                    uiState = uiState,
                    submissions = submissions,
                    onStopSession = { showStopSessionDialog = true },
                    onAcceptRequest = { request -> viewModel.acceptStudent(request) },
                    onRejectRequest = { endpointId -> viewModel.rejectStudent(endpointId) },
                    onAcceptAllRequests = { viewModel.acceptAll() },
                    onMarkAbsent = { studentId -> viewModel.markStudentAbsent(studentId) },
                    onCreateAssessment = { showCreateAssessmentDialog = true },
                    onNavigateToSubmission = onNavigateToSubmission
                )
            } else {
                ClassManagementScreen(
                    classes = classes,
                    onStartSession = { selectedClass -> selectedClassToStart = selectedClass },
                )
            }
        }
    }

    // --- DIALOGS (Wired to original logic, with improved UI) ---
    if (showStopSessionDialog) {
        StopSessionDialog(
            onDismiss = { showStopSessionDialog = false },
            onTakeAttendanceAndStop = {
                viewModel.takeAttendanceAndStop()
                showStopSessionDialog = false
            },
            onStopAnyway = {
                viewModel.stopSession()
                showStopSessionDialog = false
            }
        )
    }

    if (showCreateClassDialog) {
        CreateClassDialog(
            onDismiss = { showCreateClassDialog = false },
            onCreate = { className ->
                viewModel.createNewClass(className)
                showCreateClassDialog = false
            }
        )
    }

    if (showCreateAssessmentDialog && uiState.activeSession?.sessionId != null) {
        CreateAssessmentDialog(
            sessionId = uiState.activeSession!!.sessionId,
            onDismiss = { showCreateAssessmentDialog = false },
            onSend = { assessmentBlueprint ->
                viewModel.sendAssessment(assessmentBlueprint)
                showCreateAssessmentDialog = false
            }
        )
    }

    selectedClassToStart?.let { classToStart ->
        StartSessionDialog(
            className = classToStart.classProfile.className,
            onDismiss = { selectedClassToStart = null },
            onConfirm = {
                viewModel.startSession(classToStart.classProfile.classId)
                selectedClassToStart = null
            }
        )
    }
}


// --- SCREEN STATES ---

@Composable
fun ClassManagementScreen(
    classes: List<ClassWithStudents>,
    onStartSession: (ClassWithStudents) -> Unit,
) {
    if (classes.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Group,
            headline = "No Classes Yet",
            subline = "Tap the '+' button to create your first class and add students."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "My Classes",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(classes, key = { it.classProfile.classId }) { classWithStudents ->
                ClassCard(
                    classWithStudents = classWithStudents,
                    onStart = { onStartSession(classWithStudents) }
                )
            }
        }
    }
}

@Composable
fun ActiveSessionScreen(
    uiState: TutorDashboardUiState,
    submissions: List<AssessmentSubmission>,
    onStopSession: () -> Unit,
    onAcceptRequest: (ConnectionRequest) -> Unit,
    onRejectRequest: (String) -> Unit,
    onAcceptAllRequests: () -> Unit,
    onMarkAbsent: (String) -> Unit,
    onCreateAssessment: () -> Unit,
    onNavigateToSubmission: (String) -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Live Roster", "Assessment")
    val submissionCount = submissions.size

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // DESIGN: A visually distinct header for all critical session info.
        item {
            SessionStatusHeader(
                uiState = uiState,
                onStopSession = onStopSession,
                onCreateAssessment = onCreateAssessment
            )
        }

        // DESIGN: A modern, styled TabRow.
        item {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            if (index == 1) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(title)
                                    Spacer(Modifier.width(8.dp))
                                    Badge(
                                        containerColor = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                    ) {
                                        Text(
                                            "$submissionCount",
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                            color = if (selectedTabIndex == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
                                        )
                                    }
                                }

                            } else {
                                Text(title)
                            }
                        }
                    )
                }
            }
        }

        // --- Tab Content ---
        when (selectedTabIndex) {
            0 -> liveRosterTabContent(uiState, onAcceptRequest, onRejectRequest, onAcceptAllRequests, onMarkAbsent)
            1 -> assessmentTabContent(uiState, submissions, onNavigateToSubmission)
        }
    }
}

// --- REDESIGNED COMPONENT HIERARCHY ---

@Composable
fun SessionStatusHeader(
    uiState: TutorDashboardUiState,
    onStopSession: () -> Unit,
    onCreateAssessment: () -> Unit
) {
    val activeClass = uiState.activeClass!!.classProfile
    val rosterSize = uiState.activeClass.students.size
    val connectedCount = uiState.connectedStudents.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(activeClass.className, style = MaterialTheme.typography.headlineMedium)
        Text("Session is Active", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("STUDENTS USE THIS PIN TO JOIN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(activeClass.classPin, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text("Live Attendance: $connectedCount / $rosterSize", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onCreateAssessment,
                enabled = !uiState.isAssessmentActive,
                modifier = Modifier.weight(1f)
            ) {
                Text("New Assessment")
            }
            Button(
                onClick = onStopSession,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Stop Session")
            }
        }
    }
}

@Composable
fun ClassCard(classWithStudents: ClassWithStudents, onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Card Header: The most important info.
            Text(
                text = classWithStudents.classProfile.className,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            // Card Body: Secondary info with a helpful icon.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Group,
                    contentDescription = "Students",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${classWithStudents.students.size} students on roster",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // This weighted spacer is a powerful trick. It pushes everything
            // below it to the bottom of the Column.
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(16.dp))

            // Card Action: Neatly aligned to the end (right side).
            Button(
                onClick = onStart,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Start Session")
            }
        }
    }
}

@Composable
fun StudentRequestCard(request: ConnectionRequest, onAccept: () -> Unit, onReject: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(request.studentName, fontWeight = FontWeight.Bold)
                Text("Wants to join", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAccept,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) { Text("Accept") }
                OutlinedButton(
                    onClick = onReject,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) { Text("Reject") }
            }
        }
    }
}

@Composable
fun ConnectedStudentCard(student: ConnectedStudent, onMarkAbsent: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Check, contentDescription = "Connected", tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(student.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onMarkAbsent,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Kick")
            }
        }
    }
}

// --- DIALOGS (Redesigned) ---

@Composable
fun CreateClassDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var className by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Class") },
        text = {
            OutlinedTextField(
                value = className,
                onValueChange = { className = it },
                label = { Text("Class Name (e.g., Physics 101)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done)
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(className) }, enabled = className.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun StartSessionDialog(className: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start Session") },
        text = { Text("Are you sure you want to start a session for '$className'?") },
        confirmButton = { Button(onClick = onConfirm) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun StopSessionDialog(
    onDismiss: () -> Unit,
    onTakeAttendanceAndStop: () -> Unit,
    onStopAnyway: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End Session?") },
        text = { Text("This will disconnect all students and finalize the attendance record. Are you sure?") },
        confirmButton = {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTakeAttendanceAndStop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Take Attendance & Stop")
                }
                OutlinedButton(
                    onClick = onStopAnyway,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Stop Without Attendance")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun CreateAssessmentDialog(
    sessionId: String,
    onDismiss: () -> Unit,
    onSend: (AssessmentBlueprint) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("10") }
    val questions = remember { mutableStateListOf<AssessmentQuestion>() }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f),
        onDismissRequest = onDismiss,
        title = { Text("Create New Assessment") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Assessment Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = duration, onValueChange = { duration = it.filter { c -> c.isDigit() } }, label = { Text("Duration (minutes)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()

                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(questions, key = { _, q -> q.id }) { index, question ->
                        QuestionEditor(
                            questionNumber = index + 1,
                            question = question,
                            onQuestionChange = { newText -> questions[questions.indexOf(question)] = question.copy(text = newText) },
                            onMarkingGuideChange = { newGuide -> questions[questions.indexOf(question)] = question.copy(markingGuide = newGuide) },
                            onImageAttached = { newPath -> questions[questions.indexOf(question)] = question.copy(questionImagePath = newPath) },
                            onDelete = { questions.remove(question) }
                        )
                    }
                    item {
                        val buttonText = if (questions.isEmpty()) "Add Question" else "Add Another Question"
                        Button(onClick = { questions.add(AssessmentQuestion(text = "", type = QuestionType.TEXT_INPUT, markingGuide = "")) }, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(buttonText)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val processedQuestions = questions.map {
                        it.copy(type = if (it.questionImagePath != null) QuestionType.HANDWRITTEN_IMAGE else QuestionType.TEXT_INPUT)
                    }
                    val blueprint = AssessmentBlueprint(
                        sessionId = sessionId,
                        title = title,
                        durationInMinutes = duration.toIntOrNull() ?: 10,
                        questions = processedQuestions
                    )
                    onSend(blueprint)
                },
                enabled = title.isNotBlank() && duration.isNotBlank() && questions.isNotEmpty() && questions.all { it.text.isNotBlank() }
            ) { Text("Send to Class") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QuestionEditor(
    questionNumber: Int,
    question: AssessmentQuestion,
    onQuestionChange: (String) -> Unit,
    onMarkingGuideChange: (String) -> Unit,
    onImageAttached: (String?) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { onImageAttached(copyUriToInternalStorage(context, it, "q_${question.id}")) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempImageUri?.let { onImageAttached(copyUriToInternalStorage(context, it, "q_${question.id}")) }
        }
    }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Card(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Question #$questionNumber", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = "Delete Question", tint = MaterialTheme.colorScheme.error)
                }
            }
            OutlinedTextField(value = question.text, onValueChange = onQuestionChange, label = { Text("Question Text") }, modifier = Modifier.fillMaxWidth())

            AnimatedVisibility(visible = question.questionImagePath != null) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Image(
                        painter = rememberAsyncImagePainter(model = File(question.questionImagePath ?: "")),
                        contentDescription = "Question Image",
                        modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(onClick = { onImageAttached(null) }) {
                        Icon(Icons.Rounded.Close, "Remove Image", tint = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape))
                    }
                }
            }

            AnimatedVisibility(visible = question.questionImagePath == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { imagePickerLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Gallery")
                    }
                    OutlinedButton(onClick = {
                        if (cameraPermissionState.status.isGranted) {
                            val uri = ComposeFileProvider.getImageUri(context)
                            tempImageUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Camera")
                    }
                }
            }
            OutlinedTextField(value = question.markingGuide, onValueChange = onMarkingGuideChange, label = { Text("Marking Guide / Correct Answer") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

// --- TAB CONTENT HELPERS (for LazyColumn) ---
fun LazyListScope.liveRosterTabContent(
    uiState: TutorDashboardUiState,
    onAcceptRequest: (ConnectionRequest) -> Unit,
    onRejectRequest: (String) -> Unit,
    onAcceptAllRequests: () -> Unit,
    onMarkAbsent: (String) -> Unit
) {
    item {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Pending Requests", style = MaterialTheme.typography.titleLarge)
                if (uiState.connectionRequests.count { it.status == VerificationStatus.PIN_VERIFIED_PENDING_APPROVAL } > 1) {
                    Button(onClick = onAcceptAllRequests) { Text("Accept All") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (uiState.connectionRequests.isEmpty()) {
        item {
            Text("No pending requests.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        items(uiState.connectionRequests, key = { it.endpointId }) { request ->
            Column(Modifier.padding(horizontal = 16.dp)) {
                StudentRequestCard(request = request, onAccept = { onAcceptRequest(request) }, onReject = { onRejectRequest(request.endpointId) })
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    item {
        Column(Modifier.padding(16.dp)) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Connected Students (${uiState.connectedStudents.size})", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
        }
    }
    if (uiState.connectedStudents.isEmpty()){
        item {
            Text("No students connected yet.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        items(uiState.connectedStudents, key = { it.endpointId }) { student ->
            Column(Modifier.padding(horizontal = 16.dp)) {
                ConnectedStudentCard(student = student, onMarkAbsent = { onMarkAbsent(student.studentId) })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

fun LazyListScope.assessmentTabContent(
    uiState: TutorDashboardUiState,
    submissions: List<AssessmentSubmission>,
    onSubmissionClicked: (String) -> Unit
) {
    if (!uiState.isAssessmentActive || uiState.activeAssessment == null) {
        item {
            EmptyState(
                icon = Icons.Rounded.Add,
                headline = "No Active Assessment",
                subline = "Create a new assessment from the main controls above to send it to the class."
            )
        }
    } else {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Submissions for: ${uiState.activeAssessment.title}", style = MaterialTheme.typography.titleLarge)
                Text("${submissions.size} / ${uiState.connectedStudents.size} submitted", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
            }
        }
        if (submissions.isEmpty()) {
            item {
                Text("Waiting for students to submit their answers...", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(submissions, key = { it.submissionId }) { submission ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { onSubmissionClicked(submission.submissionId) }
                ) {
                    Text(submission.studentName, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


// --- Other Components (Unchanged logic, minor styling might be inherited) ---

@Composable
fun SettingsMenu(onSwitchRole: () -> Unit, navController: NavHostController) {
    var showMenu by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showMenu = true }) {
        Icon(Icons.Rounded.MoreVert, contentDescription = "Settings")
    }
    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
        DropdownMenuItem(
            text = { Text("Switch Role") },
            onClick = {
                showMenu = false
                showDialog = true
            }
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Switch Role?") },
            text = { Text("Your data and user ID will be preserved. You will be taken to the other dashboard.") },
            confirmButton = {
                Button(onClick = {
                    onSwitchRole()
                    navController.navigate(AptusTutorScreen.Splash.name) {
                        popUpTo(0) { inclusive = true }
                    }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}

fun copyUriToInternalStorage(context: Context, uri: Uri, newName: String): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.filesDir, "$newName.jpg")
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}