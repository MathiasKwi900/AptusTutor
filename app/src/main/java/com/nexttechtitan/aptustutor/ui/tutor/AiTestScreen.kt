package com.nexttechtitan.aptustutor.ui.tutor

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.nexttechtitan.aptustutor.utils.ImageUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AiTestScreen(
    viewModel: TutorDashboardViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    var questionText by remember { mutableStateOf("") }
    var markingGuide by remember { mutableStateOf("") }
    var maxScore by remember { mutableStateOf("") }
    var studentAnswer by remember { mutableStateOf("") }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val processImageUri: (Uri?) -> Unit = { uri ->
        uri?.let {
            scope.launch {
                val snackbarMessage = when (val result = ImageUtils.compressImage(context, it)) {
                    is ImageUtils.ImageCompressionResult.Success -> {
                        imageBitmap = BitmapFactory.decodeByteArray(result.byteArray, 0, result.byteArray.size)
                        imageUri = uri // Keep original uri for display
                        "Image loaded successfully."
                    }
                    is ImageUtils.ImageCompressionResult.Error -> {
                        result.message
                    }
                }
                snackbarHostState.showSnackbar(snackbarMessage)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        processImageUri(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            processImageUri(tempCameraUri)
        }
    }

    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    fun clearImage() {
        imageUri = null
        imageBitmap?.recycle()
        imageBitmap = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AI Grading Test Tool") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = questionText,
                onValueChange = { questionText = it },
                label = { Text("Question") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = markingGuide,
                onValueChange = { markingGuide = it },
                label = { Text("Marking Guide / Correct Answer") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = maxScore,
                onValueChange = { maxScore = it.filter { char -> char.isDigit() } },
                label = { Text("Max Score") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = studentAnswer,
                onValueChange = { studentAnswer = it },
                label = { Text("Student's Typed Answer (Optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            )

            // Image Section
            if (imageUri != null) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Image(
                        painter = rememberAsyncImagePainter(imageUri),
                        contentDescription = "Selected Answer Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(onClick = { clearImage() }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove Image",
                            tint = Color.White,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .padding(4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Gallery")
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Gallery")
                }
                OutlinedButton(
                    onClick = {
                        if (cameraPermissionState.status.isGranted) {
                            val uri = AiTestComposeFileProvider.getImageUri(context)
                            tempCameraUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = "Camera")
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Camera")
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.testAiGrading(
                        questionText = questionText,
                        markingGuide = markingGuide,
                        maxScore = maxScore.toIntOrNull() ?: 0,
                        studentAnswer = studentAnswer,
                        image = imageBitmap
                    )
                },
                enabled = questionText.isNotBlank()
                        && markingGuide.isNotBlank()
                        && maxScore.isNotBlank()
                        && (studentAnswer.isNotBlank() || imageBitmap != null),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Science, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Run AI Grading Test")
            }
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