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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun TutorDashboardScreen(
    viewModel: TutorDashboardViewModel = hiltViewModel(),
    onNavigateToSubmission: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    navController: NavHostController
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val classes by viewModel.tutorClasses.collectAsStateWithLifecycle(initialValue = emptyList())
    val hasUngradedWork by viewModel.hasUngradedSubmissions.collectAsStateWithLifecycle()
    val submissions by viewModel.submissionsWithStatus.collectAsStateWithLifecycle()
    val hasPendingFeedback by viewModel.hasPendingFeedback.collectAsStateWithLifecycle()
    val hasSubmissionsToGrade by viewModel.hasSubmissionsToGrade.collectAsStateWithLifecycle()
    val isNewAssessmentAllowed by viewModel.isNewAssessmentAllowed.collectAsStateWithLifecycle()

    var showCreateClassDialog by rememberSaveable { mutableStateOf(false) }
    var showStopSessionDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateAssessmentDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateMcqAssessmentDialog by rememberSaveable { mutableStateOf(false) }
    var showAssessmentTypeDialog by rememberSaveable { mutableStateOf(false) }
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

    LaunchedEffect(key1 = Unit) {
        viewModel.navigationEvents.collect { destination ->
            navController.navigate(destination) {
                popUpTo(0) {
                    inclusive = true
                }
                launchSingleTop = true
            }
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
                Manifest.permission.BLUETOOTH_CONNECT
            )
            else -> listOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE
            )
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions = requiredPermissions)

    LaunchedEffect(key1 = Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    if (showAssessmentTypeDialog) {
        AssessmentTypeDialog(
            onDismiss = { showAssessmentTypeDialog = false },
            onTypeSelected = { type ->
                showAssessmentTypeDialog = false
                if (type == QuestionType.MULTIPLE_CHOICE) {
                    showCreateMcqAssessmentDialog = true
                } else {
                    showCreateAssessmentDialog = true
                }
            }
        )
    }

    if (showCreateMcqAssessmentDialog && uiState.activeSession?.sessionId!= null) {
        CreateMcqAssessmentDialog(
            sessionId = uiState.activeSession!!.sessionId,
            onDismiss = { showCreateMcqAssessmentDialog = false },
            onSend = { assessmentBlueprint ->
                viewModel.sendAssessment(assessmentBlueprint)
                showCreateMcqAssessmentDialog = false
            }
        )
    }

    val isSessionActive = uiState.isAdvertising && uiState.activeClass != null

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AnimatedVisibility(visible = !isSessionActive) {
                CenterAlignedTopAppBar(
                    title = { Text("Tutor Dashboard") },
                    actions = {
                        IconButton(onClick = onNavigateToHistory) {
                            Icon(Icons.Rounded.History, contentDescription = "Session History")
                        }
                        SettingsMenu(onSwitchRole = viewModel::switchUserRole, navController = navController)
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        },
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
                    onCreateAssessment = { showAssessmentTypeDialog = true },
                    onNavigateToSubmission = onNavigateToSubmission,
                    onSelectAssessment = { assessmentId -> viewModel.selectAssessmentToView(assessmentId) },
                    onGoBackToAssessmentList = { viewModel.selectAssessmentToView(null) },
                    hasPendingFeedback = hasPendingFeedback,
                    hasSubmissionsToGrade = hasSubmissionsToGrade,
                    onSendAllPending = { viewModel.sendAllPendingFeedback() },
                    isNewAssessmentAllowed = isNewAssessmentAllowed,
                )
            } else {
                ClassManagementScreen(
                    classes = classes,
                    onStartSession = { selectedClass -> selectedClassToStart = selectedClass },
                )
            }
        }
    }

    if (showStopSessionDialog) {
        StopSessionDialog(
            hasUngradedWork = hasUngradedWork,
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
                if (permissionState.allPermissionsGranted) {
                    viewModel.startSession(classToStart.classProfile.classId)
                    selectedClassToStart = null
                } else {
                    permissionState.launchMultiplePermissionRequest()
                }
            }
        )
    }
}


@Composable
fun ClassManagementScreen(
    classes: List<ClassWithStudents>,
    onStartSession: (ClassWithStudents) -> Unit,
) {
    if (classes.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.School,
            headline = "No Classes Yet",
            subline = "Tap the '+' button to create your first class. Once created, you can start a session to allow students to join and build your roster."
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    uiState: TutorDashboardUiState,
    submissions: List<SubmissionWithStatus>,
    onStopSession: () -> Unit,
    onAcceptRequest: (ConnectionRequest) -> Unit,
    onRejectRequest: (String) -> Unit,
    onAcceptAllRequests: () -> Unit,
    onMarkAbsent: (String) -> Unit,
    onCreateAssessment: () -> Unit,
    onNavigateToSubmission: (String) -> Unit,
    onSelectAssessment: (String) -> Unit,
    onGoBackToAssessmentList: () -> Unit,
    hasPendingFeedback: Boolean,
    hasSubmissionsToGrade: Boolean,
    onSendAllPending: () -> Unit,
    isNewAssessmentAllowed: Boolean,
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Live Roster", "Assessments")
    val assessmentCount = uiState.sentAssessments.size

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SessionStatusHeader(
                uiState = uiState,
                onStopSession = onStopSession,
                onCreateAssessment = onCreateAssessment,
                isNewAssessmentAllowed = isNewAssessmentAllowed
            )
        }

        item {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            if (index == 1 && assessmentCount > 0) {
                                BadgedBox(badge = {
                                    Badge { Text("$assessmentCount") }
                                }) {
                                    Text(title, modifier = Modifier.padding(bottom = 8.dp))
                                }
                            } else {
                                Text(title)
                            }
                        }
                    )
                }
            }
        }

        when (selectedTabIndex) {
            0 -> liveRosterTabContent(uiState, onAcceptRequest, onRejectRequest, onAcceptAllRequests, onMarkAbsent)
            1 -> assessmentTabContent(
                uiState,
                submissions,
                onNavigateToSubmission,
                onSelectAssessment = onSelectAssessment,
                onGoBackToAssessmentList = onGoBackToAssessmentList,
                hasPendingFeedback = hasPendingFeedback,
                hasSubmissionsToGrade = hasSubmissionsToGrade,
                onSendAll = onSendAllPending,
            )
        }
    }
}

@Composable
fun SessionStatusHeader(
    uiState: TutorDashboardUiState,
    onStopSession: () -> Unit,
    onCreateAssessment: () -> Unit,
    isNewAssessmentAllowed: Boolean
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
                enabled = isNewAssessmentAllowed,
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
            Text(
                text = classWithStudents.classProfile.className,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(16.dp))
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
            Icon(Icons.Rounded.CheckCircle, contentDescription = "Connected", tint = MaterialTheme.colorScheme.primary)
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

@Composable
fun CreateClassDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var className by rememberSaveable { mutableStateOf("") }
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
        icon = { Icon(Icons.Rounded.Podcasts, contentDescription = null) },
        title = { Text("Start Session for '$className'?") },
        text = { Text("Students will be able to discover and join this session using a 4-digit PIN.") },
        confirmButton = { Button(onClick = onConfirm) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun StopSessionDialog(
    hasUngradedWork: Boolean,
    onDismiss: () -> Unit,
    onTakeAttendanceAndStop: () -> Unit,
    onStopAnyway: () -> Unit
) {
    val text = if (hasUngradedWork) {
        "This session has ungraded submissions. You can end the session now and grade them later from your Session History. Are you sure you want to stop?"
    } else {
        "This will disconnect all students and finalize the attendance record. Are you sure?"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
        title = { Text("End Session?") },
        text = { Text(text) },
        confirmButton = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTakeAttendanceAndStop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Finalize Attendance & Stop")
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
    var title by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("10") }
    val questions = remember { mutableStateListOf<AssessmentQuestion>() }

    // Add a default first question
    LaunchedEffect(Unit) {
        if (questions.isEmpty()) {
            questions.add(AssessmentQuestion(text = "", type = QuestionType.TEXT_INPUT, markingGuide = "", maxScore = 10))
        }
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f),
        onDismissRequest = onDismiss,
        title = { Text("Create New Assessment") },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
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
                            onQuestionChange = { questions[index] = question.copy(text = it) },
                            onMarkingGuideChange = { questions[index] = question.copy(markingGuide = it) },
                            onMaxScoreChange = { newScore -> questions[index] = question.copy(maxScore = newScore) },
                            onImageAttached = { newPath -> questions[index] = question.copy(questionImagePath = newPath) },
                            onDelete = { questions.remove(question) }
                        )
                    }
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                            Button(onClick = { questions.add(AssessmentQuestion(text = "", type = QuestionType.TEXT_INPUT, markingGuide = "", maxScore = 10)) }) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Add Another Question")
                            }
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
                enabled = title.isNotBlank() && duration.isNotBlank() && questions.isNotEmpty() && questions.all { it.text.isNotBlank() && it.markingGuide.isNotBlank() }
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
    onMaxScoreChange: (Int) -> Unit,
    onImageAttached: (String?) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    Card(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Question #$questionNumber", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, enabled = questionNumber > 1) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = "Delete Question", tint = if (questionNumber > 1) MaterialTheme.colorScheme.error else Color.Gray)
                }
            }
            OutlinedTextField(value = question.text, onValueChange = onQuestionChange, label = { Text("Question Text") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = question.markingGuide, onValueChange = onMarkingGuideChange, label = { Text("Marking Guide / Correct Answer") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = question.maxScore.toString(),
                onValueChange = { onMaxScoreChange(it.toIntOrNull() ?: 0) },
                label = { Text("Max Score") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

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
            Text("No pending requests.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
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
            Text("No students connected yet.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
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
    submissions: List<SubmissionWithStatus>,
    onSubmissionClicked: (String) -> Unit,
    onSelectAssessment: (String) -> Unit,
    onGoBackToAssessmentList: () -> Unit,
    hasPendingFeedback: Boolean,
    hasSubmissionsToGrade: Boolean,
    onSendAll: () -> Unit
) {
    if (uiState.viewingAssessmentId == null) {
        if (uiState.sentAssessments.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.FactCheck,
                    headline = "No Active Assessment",
                    subline = "Create a new assessment from the main controls above to send it to the class."
                )
            }
        } else {
            item {
                Text(
                    "Sent Assessments",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(uiState.sentAssessments, key = { it.id }) { assessment ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onSelectAssessment(assessment.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(assessment.title, fontWeight = FontWeight.Bold)
                        Text("${assessment.questions.size} questions", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    } else {
        val viewingAssessment = uiState.sentAssessments.find { it.id == uiState.viewingAssessmentId }

        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ADD a back button to return to the list
                IconButton(onClick = onGoBackToAssessmentList) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to assessments")
                }
                Column {
                    Text("Submissions for: ${viewingAssessment?.title}", style = MaterialTheme.typography.titleLarge)
                    Text("${submissions.size} / ${uiState.connectedStudents.size} submitted", style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        if (submissions.isEmpty()) {
            item {
                Text(
                    "Waiting for students to submit their answers...",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            items(submissions, key = { it.submission.submissionId }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onSubmissionClicked(item.submission.submissionId) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            item.submission.studentName,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            item.statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                if (submissions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(
                            16.dp,
                            Alignment.CenterHorizontally
                        )
                    ) {
                        // This is the new button
                        Button(
                            onClick = { /* Will Grade using Gemma 3n */ },
                            enabled = hasSubmissionsToGrade
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Grade All with AI")
                        }

                        // This is your existing button
                        Button(
                            onClick = onSendAll,
                            enabled = hasPendingFeedback
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Send All Pending")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsMenu(onSwitchRole: () -> Unit, navController: NavHostController) {
    var showMenu by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showMenu = true }) {
        Icon(Icons.Rounded.MoreVert, contentDescription = "Settings")
    }
    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
        DropdownMenuItem(
            text = { Text("Aptus Hub") },
            leadingIcon = { Icon(Icons.Rounded.Hub, null) },
            onClick = {
                showMenu = false
                navController.navigate(AptusTutorScreen.AptusHubScreen.name)
            }
        )
        DropdownMenuItem(
            text = { Text("AI Model Settings") },
            leadingIcon = { Icon(Icons.Rounded.Lightbulb, null) },
            onClick = {
                showMenu = false
                navController.navigate(AptusTutorScreen.AiSettings.name)
            }
        )
        DropdownMenuItem(
            text = { Text("Switch Role") },
            leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) },
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
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, headline: String, subline: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = headline,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Text(text = headline, style = MaterialTheme.typography.titleLarge)
        Text(
            text = subline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentTypeDialog(
    onDismiss: () -> Unit,
    onTypeSelected: (QuestionType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Quiz, contentDescription = "Choose Assessment Type") },
        title = { Text("Choose Assessment Type") },
        text = {
            Column {
                Text("Select the type of assessment you want to create.")
                Spacer(Modifier.height(16.dp))
                Card(
                    onClick = { onTypeSelected(QuestionType.TEXT_INPUT) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Written Response", style = MaterialTheme.typography.titleMedium)
                            Text("Students type or write answers.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Card(
                    onClick = { onTypeSelected(QuestionType.MULTIPLE_CHOICE) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Checklist, contentDescription = null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Multiple Choice", style = MaterialTheme.typography.titleMedium)
                            Text("Students select from a list of options.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMcqAssessmentDialog(
    sessionId: String,
    onDismiss: () -> Unit,
    onSend: (AssessmentBlueprint) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("10") }
    val questions = remember { mutableStateListOf<AssessmentQuestion>() }

    // Add a default first question
    LaunchedEffect(Unit) {
        if (questions.isEmpty()) {
            questions.add(
                AssessmentQuestion(
                    text = "",
                    type = QuestionType.MULTIPLE_CHOICE,
                    markingGuide = "-1", // -1 indicates no correct option selected yet
                    maxScore = 10,
                    options = listOf("", "") // Start with two empty options
                )
            )
        }
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f),
        onDismissRequest = onDismiss,
        title = { Text("Create Multiple Choice Quiz") },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Quiz Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = duration, onValueChange = { duration = it.filter { c -> c.isDigit() } }, label = { Text("Duration (minutes)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()

                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(questions, key = { _, q -> q.id }) { index, question ->
                        McqQuestionEditor(
                            questionNumber = index + 1,
                            question = question,
                            onQuestionChange = { updatedQuestion ->
                                questions[index] = updatedQuestion
                            },
                            onDelete = { questions.remove(question) }
                        )
                    }
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                            Button(onClick = {
                                questions.add(
                                    AssessmentQuestion(
                                        text = "",
                                        type = QuestionType.MULTIPLE_CHOICE,
                                        markingGuide = "-1",
                                        maxScore = 10,
                                        options = listOf("", "")
                                    )
                                )
                            }) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Add Another Question")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val blueprint = AssessmentBlueprint(
                        sessionId = sessionId,
                        title = title,
                        durationInMinutes = duration.toIntOrNull()?: 10,
                        questions = questions.toList()
                    )
                    onSend(blueprint)
                },
                enabled = title.isNotBlank() && duration.isNotBlank() && questions.isNotEmpty() &&
                        questions.all {
                            it.text.isNotBlank() &&
                                    it.markingGuide.toInt() >= 0 &&
                                    it.options?.all { opt -> opt.isNotBlank() }?: false
                        }
            ) { Text("Send to Class") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McqQuestionEditor(
    questionNumber: Int,
    question: AssessmentQuestion,
    onQuestionChange: (AssessmentQuestion) -> Unit,
    onDelete: () -> Unit
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }
    val options = remember { mutableStateListOf<String>().also { it.addAll(question.options?: emptyList()) } }
    val correctOptionIndex = question.markingGuide.toIntOrNull()?: -1

    Card(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Question #$questionNumber", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, enabled = questionNumber > 1) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = "Delete Question", tint = if (questionNumber > 1) MaterialTheme.colorScheme.error else Color.Gray)
                }
            }
            OutlinedTextField(
                value = question.text,
                onValueChange = { onQuestionChange(question.copy(text = it)) },
                label = { Text("Question Text") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            Text("Answer Options", style = MaterialTheme.typography.titleSmall)

            options.forEachIndexed { index, option ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = option,
                        onValueChange = {
                            options[index] = it
                            onQuestionChange(question.copy(options = options.toList()))
                        },
                        label = { Text("Option ${('A' + index)}") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        options.removeAt(index)
                        // If the removed option was the correct one, reset the selection
                        val newCorrectIndex = if (correctOptionIndex == index) -1 else if (correctOptionIndex > index) correctOptionIndex - 1 else correctOptionIndex
                        onQuestionChange(question.copy(options = options.toList(), markingGuide = newCorrectIndex.toString()))
                    }, enabled = options.size > 2) {
                        Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Remove Option")
                    }
                }
            }

            TextButton(onClick = {
                options.add("")
                onQuestionChange(question.copy(options = options.toList()))
            }, modifier = Modifier.align(Alignment.End)) {
                Text("Add Option")
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { isDropdownExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = if (correctOptionIndex in options.indices) "Option ${('A' + correctOptionIndex)}: ${options[correctOptionIndex]}" else "Select Correct Answer",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Correct Answer") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        options.forEachIndexed { index, optionText ->
                            if (optionText.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text("Option ${('A' + index)}: $optionText") },
                                    onClick = {
                                        onQuestionChange(question.copy(markingGuide = index.toString()))
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = question.maxScore.toString(),
                    onValueChange = { onQuestionChange(question.copy(maxScore = it.toIntOrNull()?: 0)) },
                    label = { Text("Score") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(100.dp)
                )
            }
        }
    }
}

/*
 // V2 FEATURE: Image-based questions from the tutor are temporarily disabled.
// The underlying data model still supports `questionImagePath`, but the UI
// for attaching images is commented out to simplify the initial release.
// This can be re-enabled when the two-step AI processing for image questions is implemented.
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QuestionEditor(
    questionNumber: Int,
    question: AssessmentQuestion,
    onQuestionChange: (String) -> Unit,
    onMarkingGuideChange: (String) -> Unit,
    onMaxScoreChange: (Int) -> Unit,
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
                IconButton(onClick = onDelete, enabled = questionNumber > 1) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = "Delete Question", tint = if (questionNumber > 1) MaterialTheme.colorScheme.error else Color.Gray)
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
            OutlinedTextField(
                value = question.maxScore.toString(),
                onValueChange = { onMaxScoreChange(it.toIntOrNull() ?: 0) },
                label = { Text("Max Score") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.align(Alignment.End)
            )
        }
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
 */