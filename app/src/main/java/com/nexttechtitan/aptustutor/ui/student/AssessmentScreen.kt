package com.nexttechtitan.aptustutor.ui.student

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.nexttechtitan.aptustutor.data.AssessmentQuestion
import com.nexttechtitan.aptustutor.data.QuestionType
import com.nexttechtitan.aptustutor.data.StudentAssessmentQuestion
import java.io.File
import java.util.Objects

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

    // This effect starts the timer as soon as the assessment is available
    LaunchedEffect(assessment) {
        if (assessment != null) {
            viewModel.startAssessmentTimer(assessment.durationInMinutes)
        }
    }

    // This effect handles when the assessment is cleared (e.g., after submission)
    LaunchedEffect(assessment) {
        if (assessment == null) {
            onNavigateBack()
        }
    }

    BackHandler {
        showExitDialog = true
    }

    if (assessment == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
            Text("Loading Assessment...")
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(assessment.title, maxLines = 1) },
                actions = {
                    Text(
                        text = formatTime(timeLeft),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        bottomBar = {
            Button(
                onClick = { viewModel.submitAssessment() },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Submit Assessment", fontSize = 16.sp, modifier = Modifier.padding(8.dp))
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)
        ) {
            itemsIndexed(assessment.questions) { index, question ->
                QuestionCard(
                    questionNumber = index + 1,
                    question = question,
                    viewModel = viewModel
                )
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit Assessment?") },
            text = { Text("Are you sure you want to exit? Your progress will be lost and your assessment will be submitted as is.") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        viewModel.submitAssessment(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Exit & Submit") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Cancel") }
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
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                imageUri?.let { viewModel.updateImageAnswer(question.id, it) }
            }
        }
    )
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Question $questionNumber", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(question.text, style = MaterialTheme.typography.bodyLarge)
            question.questionImageFile?.let { imagePath ->
                Spacer(Modifier.height(8.dp))
                // Check if it's a full path (already downloaded) or just a filename (pending)
                val imageFile = File(imagePath)
                if (imageFile.exists()) {
                    Image(
                        painter = rememberAsyncImagePainter(model = imageFile),
                        contentDescription = "Question Image",
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // Show a loading indicator while the image downloads
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                        Text("Loading image...")
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Spacer(Modifier.height(16.dp))

            when (question.type) {
                QuestionType.TEXT_INPUT -> {
                    OutlinedTextField(
                        value = viewModel.textAnswers[question.id] ?: "",
                        onValueChange = { viewModel.updateTextAnswer(question.id, it) },
                        label = { Text("Your Answer") },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 100.dp)
                    )
                }
                QuestionType.HANDWRITTEN_IMAGE -> {
                    val capturedImageUri = viewModel.imageAnswers[question.id]
                    if (capturedImageUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(capturedImageUri),
                            contentDescription = "Captured Answer",
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (cameraPermissionState.status.isGranted) {
                                val uri = ComposeFileProvider.getImageUri(context)
                                imageUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                cameraPermissionState.launchPermissionRequest()
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(if (capturedImageUri == null) "Capture Answer" else "Retake Picture")
                    }
                }
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

// Helper object to generate a URI for the camera to save the image to
object ComposeFileProvider {
    fun getImageUri(context: Context): Uri {
        val directory = File(context.cacheDir, "images")
        directory.mkdirs()
        val file = File.createTempFile("captured_image_", ".jpg", directory)
        val authority = "${context.packageName}.provider"
        return FileProvider.getUriForFile(context, authority, file)
    }
}