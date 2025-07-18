// File: ui/tutor/TutorDashboardScreen.kt
package com.nexttechtitan.aptustutor.ui.tutor

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.nexttechtitan.aptustutor.data.AssessmentBlueprint
import com.nexttechtitan.aptustutor.data.AssessmentQuestion
import com.nexttechtitan.aptustutor.data.AssessmentSubmission
import com.nexttechtitan.aptustutor.data.ClassWithStudents
import com.nexttechtitan.aptustutor.data.ConnectedStudent
import com.nexttechtitan.aptustutor.data.ConnectionRequest
import com.nexttechtitan.aptustutor.data.QuestionType
import com.nexttechtitan.aptustutor.data.TutorDashboardUiState
import com.nexttechtitan.aptustutor.data.VerificationStatus
import com.nexttechtitan.aptustutor.ui.AptusTutorScreen
import com.nexttechtitan.aptustutor.ui.student.ComposeFileProvider
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TutorDashboardScreen(
    viewModel: TutorDashboardViewModel = hiltViewModel(),
    onNavigateToSubmission: (String) -> Unit,
    navController: NavHostController
    ) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val classes by viewModel.tutorClasses.collectAsStateWithLifecycle(initialValue = emptyList())
    val submissions by viewModel.assessmentSubmissions.collectAsStateWithLifecycle()

    // State for dialogs
    var showCreateClassDialog by remember { mutableStateOf(false) }
    var showStopSessionDialog by remember { mutableStateOf(false) }
    var showCreateAssessmentDialog by remember { mutableStateOf(false) }
    var selectedClassToStart by remember { mutableStateOf<ClassWithStudents?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(key1 = true) {
        viewModel.toastEvents.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionState = rememberMultiplePermissionsState(permissions = requiredPermissions)

    LaunchedEffect(key1 = Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
        ) {
            if (uiState.isAdvertising && uiState.activeClass != null) {
                // --- ACTIVE SESSION VIEW ---
                ActiveSessionScreen(
                    uiState = uiState,
                    onStopSession = { showStopSessionDialog = true },
                    onAccept = { request -> viewModel.acceptStudent(request) },
                    onReject = { endpointId -> viewModel.rejectStudent(endpointId) },
                    onAcceptAll = { viewModel.acceptAll() },
                    onMarkAbsent = { studentId -> viewModel.markStudentAbsent(studentId) },
                    onCreateAssessment = { showCreateAssessmentDialog = true },
                    submissions = submissions,
                    onNavigateToSubmission = onNavigateToSubmission
                )
            } else {
                // --- CLASS MANAGEMENT VIEW ---
                ClassManagementScreen(
                    classes = classes,
                    onShowCreateClassDialog = { showCreateClassDialog = true },
                    onStartSession = { selectedClass -> selectedClassToStart = selectedClass },
                    viewModel = viewModel,
                    navController = navController
                )
            }
        }

        if (showStopSessionDialog) {
            StopSessionDialog(
                onDismiss = { showStopSessionDialog = false },
                onTakeAttendanceAndStop = { // Renamed for clarity
                    viewModel.takeAttendanceAndStop()
                    showStopSessionDialog = false // The session will stop, so UI will change anyway
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

        if (showCreateAssessmentDialog) {
            val activeSessionId = uiState.activeSession?.sessionId
            if (activeSessionId != null) {
                CreateAssessmentDialog(
                    sessionId = activeSessionId,
                    onDismiss = { showCreateAssessmentDialog = false },
                    onSend = { assessmentBlueprint ->
                        viewModel.sendAssessment(assessmentBlueprint)
                        showCreateAssessmentDialog = false
                    }
                )
            }
        }

        // Confirmation to start a session
        selectedClassToStart?.let { classToStart ->
            AlertDialog(
                onDismissRequest = { selectedClassToStart = null },
                title = { Text("Start Session") },
                text = { Text("Are you sure you want to start a session for '${classToStart.classProfile.className}'?") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.startSession(classToStart.classProfile.classId)
                        selectedClassToStart = null
                    }) { Text("Start") }
                },
                dismissButton = {
                    TextButton(onClick = { selectedClassToStart = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassManagementScreen(
    classes: List<ClassWithStudents>,
    onShowCreateClassDialog: () -> Unit,
    onStartSession: (ClassWithStudents) -> Unit,
    viewModel: TutorDashboardViewModel,
    navController: NavHostController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tutor Dashboard") },
                actions = { SettingsMenu(onSwitchRole = viewModel::switchUserRole, navController = navController) }
            )
        }
    ) { paddingValues ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onShowCreateClassDialog) {
                Text("Create New Class")
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            LazyColumn {
                item {
                    Text(
                        "My Classes",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                if (classes.isEmpty()) {
                    item { Text("No classes created yet. Click the button to create your first class.") }
                } else {
                    items(classes) { classWithStudents ->
                        ClassCard(
                            classWithStudents,
                            onStart = { onStartSession(classWithStudents) })
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveSessionScreen(
    uiState: TutorDashboardUiState,
    onStopSession: () -> Unit,
    onAccept: (ConnectionRequest) -> Unit,
    onReject: (String) -> Unit,
    onAcceptAll: () -> Unit,
    onMarkAbsent: (String) -> Unit,
    onCreateAssessment: () -> Unit,
    submissions: List<AssessmentSubmission>,
    onNavigateToSubmission: (String) -> Unit
) {
    val activeClass = uiState.activeClass!!.classProfile
    val rosterSize = uiState.activeClass!!.students.size
    val connectedCount = uiState.connectedStudents.size

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(activeClass.className, style = MaterialTheme.typography.headlineMedium)
        Text("Session is Active", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = onCreateAssessment, enabled = !uiState.isAssessmentActive) {
                Text("New Assessment")
            }
            Button(onClick = onStopSession, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Stop Session")
            }
        }
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Class PIN", style = MaterialTheme.typography.labelSmall)
                Text(activeClass.classPin, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Attendance: $connectedCount / $rosterSize", style = MaterialTheme.typography.titleMedium)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        var selectedTabIndex by remember { mutableStateOf(0) }
        val tabs = listOf("Live Roster", "Assessment")
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> LiveRosterTab(uiState, onAccept, onReject, onAcceptAll, onMarkAbsent)
            1 -> AssessmentTab(uiState, submissions, onSubmissionClicked = onNavigateToSubmission)
        }
    }
}

@Composable
fun LiveRosterTab(
    uiState: TutorDashboardUiState,
    onAccept: (ConnectionRequest) -> Unit,
    onReject: (String) -> Unit,
    onAcceptAll: () -> Unit,
    onMarkAbsent: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Pending Requests", style = MaterialTheme.typography.titleLarge)
                if (uiState.connectionRequests.any { it.status == VerificationStatus.PIN_VERIFIED_PENDING_APPROVAL }) {
                    Button(onClick = onAcceptAll) { Text("Accept All") }
                }
            }
        }
        items(uiState.connectionRequests) { request ->
            StudentRequestCard(request = request, onAccept = { onAccept(request) }, onReject = { onReject(request.endpointId) })
        }

        item { Text("Connected Students (${uiState.connectedStudents.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp)) }
        items(uiState.connectedStudents) { student ->
            ConnectedStudentCard(student = student, onMarkAbsent = { onMarkAbsent(student.studentId) })
        }
    }
}

@Composable
fun AssessmentTab(
    uiState: TutorDashboardUiState,
    submissions: List<AssessmentSubmission>,
    onSubmissionClicked: (String) -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        if (!uiState.isAssessmentActive || uiState.activeAssessment == null) {
            Text("No active assessment. Create one from the main controls.", modifier = Modifier.padding(16.dp))
        } else {
            Text("Submissions for: ${uiState.activeAssessment.title}", style = MaterialTheme.typography.titleLarge)
            Text("${submissions.size} / ${uiState.connectedStudents.size} submitted", style = MaterialTheme.typography.titleSmall)


            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(submissions) { submission ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            onSubmissionClicked(submission.submissionId)
                        }
                    ) {
                        Text(submission.studentName, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
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
        onDismissRequest = onDismiss,
        title = { Text("Create New Assessment") },
        text = {
            Column(modifier = Modifier.fillMaxHeight(0.8f)) { // Limit height
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Assessment Title") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = duration, onValueChange = { duration = it.filter { c -> c.isDigit() } }, label = { Text("Duration (minutes)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Text("Questions", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(questions) { question ->
                        QuestionEditor(
                            question = question,
                            onQuestionChange = { newText ->
                                val index = questions.indexOf(question)
                                if (index != -1) questions[index] = question.copy(text = newText)
                            },
                            onTypeChange = { newType ->
                                val index = questions.indexOf(question)
                                if (index != -1) questions[index] = question.copy(type = newType)
                            },
                            onMarkingGuideChange = { newGuide ->
                                val index = questions.indexOf(question)
                                if (index != -1) questions[index] = question.copy(markingGuide = newGuide)
                            },
                            onImageAttached = { newPath ->
                                val index = questions.indexOf(question)
                                if (index != -1) questions[index] = question.copy(questionImagePath = newPath)
                            },
                            onDelete = { questions.remove(question) }
                        )
                    }
                    item {
                        Button(onClick = { questions.add(AssessmentQuestion(text = "", type = QuestionType.TEXT_INPUT, markingGuide = "")) }, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Add Question")
                            Spacer(Modifier.width(4.dp))
                            Text("Add Question")
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
                        durationInMinutes = duration.toIntOrNull() ?: 10,
                        questions = questions.toList()
                    )
                    onSend(blueprint)
                },
                enabled = title.isNotBlank() && duration.isNotBlank() && questions.isNotEmpty()
            ) {
                Text("Send to Class")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun QuestionEditor(
    question: AssessmentQuestion,
    onQuestionChange: (String) -> Unit,
    onTypeChange: (QuestionType) -> Unit,
    onMarkingGuideChange: (String) -> Unit,
    onImageAttached: (String?) -> Unit,
    onDelete: () -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                val localPath = copyUriToInternalStorage(context, it, "q_${question.id}")
                onImageAttached(localPath)
            }
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                tempImageUri?.let {
                    val localPath = copyUriToInternalStorage(context, it, "q_${question.id}")
                    onImageAttached(localPath)
                }
            }
        }
    )

    Card(modifier = Modifier.padding(vertical = 8.dp).border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Question", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Question", tint = MaterialTheme.colorScheme.error)
                }
            }
            OutlinedTextField(value = question.text, onValueChange = onQuestionChange, label = { Text("Question Text") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            if (question.questionImagePath != null) {
                Box {
                    Image(
                        painter = rememberAsyncImagePainter(model = File(question.questionImagePath!!)),
                        contentDescription = "Question Image",
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(onClick = { onImageAttached(null) }, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove Image", tint = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape))
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { imagePickerLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                        Text("Pick Image")
                    }
                    OutlinedButton(onClick = {
                        val uri = ComposeFileProvider.getImageUri(context)
                        tempImageUri = uri
                        cameraLauncher.launch(uri)
                    }, modifier = Modifier.weight(1f)) {
                        Text("Take Photo")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Column {
                OutlinedTextField(
                    value = question.type.name.replace("_", " "),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Answer Type") },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Dropdown") },
                    modifier = Modifier.fillMaxWidth().clickable { dropdownExpanded = true }
                )
                DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                    QuestionType.values().forEach { type ->
                        DropdownMenuItem(text = { Text(type.name.replace("_", " ")) }, onClick = { onTypeChange(type); dropdownExpanded = false })
                    }
                }
                OutlinedTextField(
                    value = question.markingGuide,
                    onValueChange = onMarkingGuideChange,
                    label = { Text("Marking Guide / Correct Answer") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun ConnectedStudentCard(
    student: ConnectedStudent,
    onMarkAbsent: () -> Unit
) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(student.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = onMarkAbsent,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Mark Absent")
            }
        }
    }
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
        text = { Text("Have you taken attendance for this session?") },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onTakeAttendanceAndStop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Take Attendance & Stop")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onStopAnyway,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Stop Anyway")
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}

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
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(className) }, enabled = className.isNotBlank()) {
                Text("Create")
            }
        }
    )
}

@Composable
fun ClassCard(classWithStudents: ClassWithStudents, onStart: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(classWithStudents.classProfile.className, fontWeight = FontWeight.Bold)
                Text("${classWithStudents.students.size} students on roster")
            }
            Button(onClick = onStart) {
                Text("Start Session")
            }
        }
    }
}
@Composable
fun StudentRequestCard(request: ConnectionRequest, onAccept: () -> Unit, onReject: () -> Unit) {
    val statusText = if (request.status == VerificationStatus.PIN_VERIFIED_PENDING_APPROVAL) " (New Student)" else ""
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(request.studentName, fontWeight = FontWeight.Bold)
                Text("Wants to join$statusText", style = MaterialTheme.typography.bodySmall)
            }
            Row {
                Button(onClick = onAccept) { Text("Accept") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onReject) { Text("Reject") }
            }
        }
    }
}

@Composable
fun SettingsMenu(onSwitchRole: () -> Unit, navController: NavHostController) {
    var showMenu by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showMenu = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "Settings")
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
                    // Navigate to splash to force a refresh of the start destination
                    navController.navigate(AptusTutorScreen.Splash.name) {
                        popUpTo(0) { inclusive = true } // Clear the entire back stack
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