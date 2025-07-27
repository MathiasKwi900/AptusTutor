package com.nexttechtitan.aptustutor.ui.tutor

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.nexttechtitan.aptustutor.ai.GemmaAiService
import com.nexttechtitan.aptustutor.data.ModelStatus
import com.nexttechtitan.aptustutor.ui.AptusTutorScreen
import com.nexttechtitan.aptustutor.utils.ImageUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AptusHubScreen(
    viewModel: AptusHubViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    navController: NavHostController
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val gemmaModelState by viewModel.gemmaModelState.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("AI Sandbox", "Student Analytics")
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val isAiReadyForGrading by remember(modelStatus) {
        derivedStateOf {
            modelStatus == ModelStatus.DOWNLOADED
        }
    }

    LaunchedEffect(key1 = Unit) {
        viewModel.toastEvents.collect { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Aptus Hub") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (isAiReadyForGrading) {
                            viewModel.runSandboxGrading()
                        } else {
                            scope.launch {
                                val message = when {
                                    modelStatus!= ModelStatus.DOWNLOADED -> "AI model is not downloaded."
                                    else -> "AI engine is still preparing."
                                }
                                val result = snackbarHostState.showSnackbar(
                                    message = message,
                                    actionLabel = "Settings",
                                    duration = SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    navController.navigate(AptusTutorScreen.AiSettings.name)
                                }
                            }
                        }
                    },
                    icon = { Icon(Icons.Rounded.Science, contentDescription = null) },
                    text = { Text("Grade All with AI") },
                    expanded =!uiState.isGrading,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(paddingValues)) {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) },
                            icon = {
                                when (index) {
                                    0 -> Icon(Icons.Rounded.Biotech, contentDescription = null)
                                    1 -> Icon(Icons.Rounded.BarChart, contentDescription = null)
                                }
                            }
                        )
                    }
                }
                when (selectedTabIndex) {
                    0 -> AiSandboxTab(viewModel = viewModel)
                    1 -> StudentAnalyticsTab(viewModel = viewModel) // This remains unchanged
                }
            }

            if (uiState.isGrading) {
                GradingProgressOverlay(progressText = uiState.gradingProgressText)
            }
        }
    }
}

@Composable
fun AiSandboxTab(viewModel: AptusHubViewModel) {
    val questions = viewModel.sandboxQuestions

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI Sandbox", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Test the 'One Engine per Question' caching strategy. The first student for each question will take longer as the AI 'warms up' a dedicated engine. Subsequent students for the same question will be graded much faster.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(questions, key = { it.questionId }) { question ->
            SandboxQuestionCard(
                question = question,
                onQuestionChange = { updatedQuestion ->
                    val index = viewModel.sandboxQuestions.indexOf(question)
                    if (index!= -1) {
                        viewModel.sandboxQuestions[index] = updatedQuestion
                    }
                },
                onRemove = { viewModel.removeQuestion(question) },
                canRemove = questions.size > 1,
                onAddStudent = { viewModel.addStudentAnswer(question) },
                onStudentAnswerChange = { studentAnswer, updatedAnswer ->
                    val qIndex = viewModel.sandboxQuestions.indexOf(question)
                    if (qIndex!= -1) {
                        val aIndex = viewModel.sandboxQuestions[qIndex].studentAnswers.indexOf(studentAnswer)
                        if (aIndex!= -1) {
                            viewModel.sandboxQuestions[qIndex].studentAnswers[aIndex] = updatedAnswer
                        }
                    }
                },
                onRemoveStudent = { studentAnswer ->
                    viewModel.removeStudentAnswer(question, studentAnswer)
                }
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(onClick = { viewModel.addQuestion() }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Add Another Question")
                }
            }
            Spacer(Modifier.height(80.dp)) // Spacer for the FAB
        }
    }
}

@Composable
fun SandboxQuestionCard(
    question: SandboxQuestion,
    onQuestionChange: (SandboxQuestion) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    onAddStudent: () -> Unit,
    onStudentAnswerChange: (SandboxStudentAnswer, SandboxStudentAnswer) -> Unit,
    onRemoveStudent: (SandboxStudentAnswer) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Question Details", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Remove Question")
                    }
                }
            }
            OutlinedTextField(value = question.questionText, onValueChange = { onQuestionChange(question.copy(questionText = it)) }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = question.markingGuide, onValueChange = { onQuestionChange(question.copy(markingGuide = it)) }, label = { Text("Marking Guide") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = question.maxScore, onValueChange = { onQuestionChange(question.copy(maxScore = it.filter(Char::isDigit))) }, label = { Text("Max Score") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text("Student Answers", style = MaterialTheme.typography.titleLarge)
            question.studentAnswers.forEach { studentAnswer ->
                StudentAnswerInput(
                    answer = studentAnswer,
                    onAnswerChange = { onStudentAnswerChange(studentAnswer, it) },
                    onRemove = { onRemoveStudent(studentAnswer) },
                    canRemove = question.studentAnswers.size > 1
                )
            }
            Button(onClick = onAddStudent, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Add Student Answer")
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StudentAnswerInput(
    answer: SandboxStudentAnswer,
    onAnswerChange: (SandboxStudentAnswer) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val processImageUri: (Uri?) -> Unit = { uri ->
        uri?.let {
            scope.launch {
                when (val result = ImageUtils.compressImage(context, it)) {
                    is ImageUtils.ImageCompressionResult.Success -> {
                        val bitmap = BitmapFactory.decodeByteArray(result.byteArray, 0, result.byteArray.size)
                        onAnswerChange(answer.copy(answerImage = bitmap))
                    }
                    is ImageUtils.ImageCompressionResult.Error -> { /* Handle error */ }
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> processImageUri(uri) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success) processImageUri(tempCameraUri) }
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Student #${answer.studentId.take(4)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (canRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove Student Answer")
                    }
                }
            }
            OutlinedTextField(value = answer.answerText, onValueChange = { onAnswerChange(answer.copy(answerText = it)) }, label = { Text("Typed Answer") }, modifier = Modifier.fillMaxWidth())

            if (answer.answerImage!= null) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Image(painter = rememberAsyncImagePainter(answer.answerImage), contentDescription = "Answer Image", modifier = Modifier.fillMaxWidth().height(150.dp).clip(MaterialTheme.shapes.medium).border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium), contentScale = ContentScale.Fit)
                    IconButton(onClick = { onAnswerChange(answer.copy(answerImage = null)) }) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Remove Image", tint = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(4.dp))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("Gallery") }
                OutlinedButton(onClick = {
                    if (cameraPermissionState.status.isGranted) {
                        val uri = AiTestComposeFileProvider.getImageUri(context)
                        tempCameraUri = uri
                        cameraLauncher.launch(uri)
                    } else {
                        cameraPermissionState.launchPermissionRequest()
                    }
                }, modifier = Modifier.weight(1f)) { Text("Camera") }
            }

            AnimatedVisibility(visible = answer.result!= null || answer.isGrading) {
            Column {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (answer.isGrading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = "Graded", tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(answer.statusText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                answer.result?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(value = it.score?.toString()?: "N/A", onValueChange = {}, readOnly = true, label = { Text("Score") }, modifier = Modifier.width(100.dp))
                        OutlinedTextField(value = it.feedback?: "Error", onValueChange = {}, readOnly = true, label = { Text("Feedback") }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        }
    }
}

@Composable
fun StudentAnalyticsTab(viewModel: AptusHubViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Class Analytics", style = MaterialTheme.typography.headlineSmall)
            Text(
                "AI-powered insights into your class's performance.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            ClassHealthCard(onCardClick = { viewModel.showComingSoonToast() })
        }

        item {
            AiInsightsCard(onInsightClick = { viewModel.showComingSoonToast() })
        }

        item {
            StudentRosterSection(onStudentClick = { viewModel.showComingSoonToast() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassHealthCard(onCardClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onCardClick
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Health Snapshot", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                HealthMetric(icon = Icons.Rounded.Star, label = "Avg. Score", value = "82%")
                HealthMetric(icon = Icons.Rounded.CoPresent, label = "Attendance", value = "95%")
                HealthMetric(icon = Icons.Rounded.Warning, label = "At-Risk Students", value = "3")
            }
        }
    }
}

@Composable
fun HealthMetric(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiInsightsCard(onInsightClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onInsightClick
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("AI-Powered Insights", style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider()
            InsightItem(
                icon = Icons.Rounded.PersonSearch,
                title = "Students to Watch",
                subtitle = "AI has flagged 3 students with declining performance or attendance."
            )
            InsightItem(
                icon = Icons.Rounded.Biotech,
                title = "Commonly Missed Topics",
                subtitle = "75% of students struggled with questions related to 'Thermodynamics'."
            )
        }
    }
}

@Composable
fun InsightItem(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.secondary)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentRosterSection(onStudentClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            label = { Text("Search student roster...") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            readOnly = true,
            interactionSource = remember { MutableInteractionSource() }
                .also { interactionSource ->
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect {
                            if (it is PressInteraction.Release) {
                                onStudentClick()
                            }
                        }
                    }
                }
        )
        repeat(3) {
            Card(modifier = Modifier.fillMaxWidth(), onClick = onStudentClick) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Rounded.AccountCircle, contentDescription = "Student", modifier = Modifier.size(40.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Student Name ${it + 1}", style = MaterialTheme.typography.titleMedium)
                        Text("Avg. Score: ${80 - it * 5}%", style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "View Details")
                }
            }
        }
    }
}

@Composable
fun GradingProgressOverlay(progressText: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f))
            .clickable(enabled = false, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.surface
            )
            Text(
                text = progressText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.surface,
                textAlign = TextAlign.Center
            )
        }
    }
}

private object AiTestComposeFileProvider {
    fun getImageUri(context: Context): Uri {
        val directory = File(context.cacheDir, "images")
        directory.mkdirs()
        val file = File.createTempFile("test_captured_image_", ".jpg", directory)
        val authority = "${context.packageName}.provider"
        return FileProvider.getUriForFile(context, authority, file)
    }
}