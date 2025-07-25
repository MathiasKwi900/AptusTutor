package com.nexttechtitan.aptustutor.ui.student

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.nexttechtitan.aptustutor.data.QuestionType
import com.nexttechtitan.aptustutor.data.StudentAssessmentQuestion
import com.nexttechtitan.aptustutor.utils.FileUtils
import com.nexttechtitan.aptustutor.utils.ImageUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentScreen(
    viewModel: StudentDashboardViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timeLeft by viewModel.timeLeft.collectAsStateWithLifecycle()
    val assessment = uiState.activeAssessment

    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(assessment) {
        if (assessment != null) {
            viewModel.startAssessmentTimer(assessment.durationInMinutes)
        } else {
            // If assessment becomes null (e.g., after submission), navigate back.
            onNavigateBack()
        }
    }

    BackHandler {
        showExitDialog = true
    }

    if (assessment == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // This state is brief, so a simple indicator is fine.
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            TopAppBar(
                title = { Text(assessment.title, maxLines = 1, fontWeight = FontWeight.Bold) },
                actions = {
                    Card(
                        modifier = Modifier.padding(end = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Text(
                            text = formatTime(timeLeft),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentPadding = PaddingValues(16.dp)
            ) {
                Button(
                    onClick = { viewModel.submitAssessment() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Submit Assessment", fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(assessment.questions, key = { _, q -> q.id }) { index, question ->
                when (question.type) {
                    QuestionType.MULTIPLE_CHOICE -> {
                        McqQuestionCard(
                            questionNumber = index + 1,
                            question = question,
                            viewModel = viewModel
                        )
                    }
                    else -> {
                        QuestionCard(
                            questionNumber = index + 1,
                            question = question,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    if (showExitDialog) {
        ExitConfirmationDialog(
            onDismiss = { showExitDialog = false },
            onConfirm = {
                showExitDialog = false
                viewModel.submitAssessment(true)
            }
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QuestionCard(
    questionNumber: Int,
    question: StudentAssessmentQuestion,
    viewModel: StudentDashboardViewModel
) {
    val context = LocalContext.current
    var tempImageUriHolder by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempImageUriHolder?.let { uri ->
                scope.launch {
                    val snackbarMessage = try {
                        when (val result = ImageUtils.compressImage(context, uri)) {
                            is ImageUtils.ImageCompressionResult.Success -> {
                                val submissionId = viewModel.getSubmissionId()
                                val permanentPath = FileUtils.saveAnswerImage(
                                    context = context,
                                    byteArray = result.byteArray,
                                    submissionId = submissionId,
                                    questionId = question.id
                                )

                                if (permanentPath != null) {
                                    // Pass the reliable, permanent path to the ViewModel.
                                    viewModel.updateImageAnswer(question.id, Uri.fromFile(File(permanentPath)))
                                    "Image captured."
                                } else {
                                    "Error saving image."
                                }
                            }
                            is ImageUtils.ImageCompressionResult.Error -> result.message
                        }
                    } catch (e: Exception) {
                        Log.e("ImageCapture", "Error in camera result", e)
                        "An error occurred while processing the image."
                    }
                    snackbarHostState.showSnackbar(snackbarMessage)
                }
            }
        }
    }

    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Question $questionNumber", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${question.maxScore} marks", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(12.dp))
                Text(question.text, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)

                question.questionImageFile?.let { imagePath ->
                    Spacer(Modifier.height(12.dp))
                    val imageFile = File(imagePath)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageFile.exists()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(imageFile).crossfade(true).build(),
                                contentDescription = "Question Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            // Show a loading indicator while the image is being received
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(16.dp)
            ) {
                Text("Your Answer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = viewModel.textAnswers[question.id] ?: "",
                    onValueChange = { viewModel.updateTextAnswer(question.id, it) },
                    label = { Text("Type your answer here") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 120.dp)
                )

                OrDivider()

                ImageAnswerInput(
                    capturedImageUri = viewModel.imageAnswers[question.id],
                    onLaunchCamera = {
                        if (cameraPermissionState.status.isGranted) {
                            val uri = ComposeFileProvider.getImageUri(context)
                            tempImageUriHolder = uri
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    }
                )
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun ImageAnswerInput(
    capturedImageUri: Uri?,
    onLaunchCamera: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (capturedImageUri != null) {
            Box {
                Image(
                    painter = rememberAsyncImagePainter(capturedImageUri),
                    contentDescription = "Captured Answer",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Fit
                )
            }
        }

        val buttonText = if (capturedImageUri == null) "Capture Written Answer" else "Retake Picture"
        val buttonIsPrimary = capturedImageUri == null

        if (buttonIsPrimary) {
            Button(onClick = onLaunchCamera) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(buttonText)
            }
        } else {
            OutlinedButton(onClick = onLaunchCamera) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(buttonText)
            }
        }
    }
}

@Composable
fun McqQuestionCard(
    questionNumber: Int,
    question: StudentAssessmentQuestion,
    viewModel: StudentDashboardViewModel
) {
    val selectedOptionIndex = viewModel.textAnswers[question.id]?.toIntOrNull()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Question $questionNumber", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${question.maxScore} marks", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Text(question.text, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)
            Spacer(Modifier.height(16.dp))

            // Multiple Choice Options
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options?.forEachIndexed { index, optionText ->
                    val isSelected = selectedOptionIndex == index
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.updateTextAnswer(question.id, index.toString()) },
                        shape = MaterialTheme.shapes.medium,
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.updateTextAnswer(question.id, index.toString()) }
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "${('A' + index)}. $optionText",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text("OR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun ExitConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = "Warning") },
        title = { Text("Submit Assessment?") },
        text = { Text("Are you sure you want to exit? Your assessment will be submitted as is.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Exit & Submit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

object ComposeFileProvider {
    fun getImageUri(context: Context): Uri {
        val directory = File(context.cacheDir, "images")
        directory.mkdirs()
        val file = File.createTempFile("captured_image_", ".jpg", directory)
        val authority = "${context.packageName}.provider"
        return FileProvider.getUriForFile(context, authority, file)
    }
}