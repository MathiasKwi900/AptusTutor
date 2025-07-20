package com.nexttechtitan.aptustutor.ui.student

import android.Manifest
import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.nexttechtitan.aptustutor.data.StudentAssessmentQuestion
import com.nexttechtitan.aptustutor.utils.ImageUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentScreen(
    viewModel: StudentDashboardViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    // --- BATTLE-TESTED LOGIC BLOCK (PRESERVED EXACTLY) ---
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timeLeft by viewModel.timeLeft.collectAsStateWithLifecycle()
    val assessment = uiState.activeAssessment

    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(assessment) {
        if (assessment != null) {
            viewModel.startAssessmentTimer(assessment.durationInMinutes)
        }
    }

    LaunchedEffect(assessment) {
        if (assessment == null) {
            onNavigateBack()
        }
    }

    BackHandler {
        showExitDialog = true
    }
    // --- END OF PRESERVED LOGIC BLOCK ---

    if (assessment == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
            Text("Loading Assessment...")
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, // DESIGN: A neutral, paper-like background.
        topBar = {
            TopAppBar(
                title = { Text(assessment.title, maxLines = 1, fontWeight = FontWeight.Bold) },
                actions = {
                    // DESIGN: The timer is given a distinct visual container to draw attention to it.
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
            // DESIGN: A dedicated BottomAppBar provides a clear, elevated space for the final submission action.
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
                QuestionCard(
                    questionNumber = index + 1,
                    question = question,
                    viewModel = viewModel
                )
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

// DESIGN: The Question Card is now structured to create a clear separation between question and answer.
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QuestionCard(
    questionNumber: Int,
    question: StudentAssessmentQuestion,
    viewModel: StudentDashboardViewModel
) {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            imageUri?.let { uri ->
                scope.launch {
                    when (val result = ImageUtils.compressImage(context, uri)) {
                        is ImageUtils.ImageCompressionResult.Success -> {
                            val compressedFile = File.createTempFile("answer_img_", ".jpg", context.cacheDir)
                            compressedFile.writeBytes(result.byteArray)
                            viewModel.updateImageAnswer(question.id, Uri.fromFile(compressedFile))
                        }
                        is ImageUtils.ImageCompressionResult.Error -> snackbarHostState.showSnackbar(result.message)
                    }
                }
            }
        }
    }
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            // --- QUESTION ZONE ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Question $questionNumber", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            // --- ANSWER ZONE ---
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
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 120.dp)
                )

                OrDivider()

                ImageAnswerInput(
                    capturedImageUri = viewModel.imageAnswers[question.id],
                    onLaunchCamera = {
                        if (cameraPermissionState.status.isGranted) {
                            val uri = ComposeFileProvider.getImageUri(context)
                            imageUri = uri
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

// DESIGN: This component now correctly handles the Capture/Retake logic without a "remove" option.
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
        // Show the captured image if it exists.
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

        // The button text and style changes depending on whether an image has already been taken.
        val buttonText = if (capturedImageUri == null) "Capture Written Answer" else "Retake Picture"
        val button: @Composable () -> Unit = {
            Button(onClick = onLaunchCamera) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(buttonText)
            }
        }

        if (capturedImageUri == null) {
            button()
        } else {
            // Use an OutlinedButton for the secondary "Retake" action.
            OutlinedButton(onClick = onLaunchCamera) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(buttonText)
            }
        }
    }
}

// DESIGN: A reusable, styled divider.
@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text("OR", style = MaterialTheme.typography.labelSmall)
        HorizontalDivider(Modifier.weight(1f))
    }
}

// DESIGN: A styled confirmation dialog.
@Composable
private fun ExitConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = "Warning") },
        title = { Text("Exit Assessment?") },
        text = { Text("Are you sure you want to exit? Your progress will be lost and your assessment will be submitted as is.") },
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


// --- HELPER FUNCTIONS (Unchanged) ---

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