package com.nexttechtitan.aptustutor.ui.main

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.nexttechtitan.aptustutor.data.ModelStatus
import com.nexttechtitan.aptustutor.ui.AptusTutorScreen
import com.nexttechtitan.aptustutor.utils.CapabilityResult
import com.nexttechtitan.aptustutor.utils.DeviceCapability
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
    val isAiReady = modelStatus == ModelStatus.DOWNLOADED

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    // RENAMED TABS
    val tabs = listOf("AI Sandbox", "Class Analytics")
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                                    // UPDATED ICONS
                                    0 -> Icon(Icons.Rounded.Science, contentDescription = null)
                                    1 -> Icon(Icons.Rounded.Insights, contentDescription = null)
                                }
                            }
                        )
                    }
                }
                when (selectedTabIndex) {
                    0 -> AiSandboxTab( // RENAMED
                        uiState = uiState,
                        isAiReady = isAiReady,
                        viewModel = viewModel,
                        onGradeClick = {
                            if (isAiReady) {
                                viewModel.runGrading()
                            } else {
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "AI model not downloaded. On-device AI features require this model.",
                                        actionLabel = "Settings",
                                        duration = SnackbarDuration.Long
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        navController.navigate(AptusTutorScreen.AiSettings.name)
                                    }
                                }
                            }
                        }
                    )
                    1 -> StudentAnalyticsTab(viewModel = viewModel)
                }
            }

            if (uiState.isGrading) {
                GradingProgressOverlay(
                    statusText = uiState.gradingStatus,
                    health = uiState.deviceHealth
                )
            }
        }
    }
}

// RENAMED Composable
@Composable
fun AiSandboxTab(
    uiState: AptusHubUiState,
    isAiReady: Boolean,
    viewModel: AptusHubViewModel,
    onGradeClick: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("On-Device AI Sandbox", style = MaterialTheme.typography.headlineSmall)
                ExpandableInfoCard(
                    title = "How this works",
                    content = "Test the on-device AI grading capabilities by providing a question, marking guide, and a hypothetical student's answer. Performance depends on your device's capabilities and may be slower on low-end devices. However, improvements are underway."
                )
            }
        }

        item {
            OutlinedCard(
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("1. Assessment Details", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = uiState.questionText,
                        onValueChange = viewModel::onQuestionTextChanged,
                        label = { Text("Question (e.g., Explain photosynthesis)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.markingGuide,
                        onValueChange = viewModel::onMarkingGuideChanged,
                        label = { Text("Marking Guide / Ideal Answer") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.maxScore,
                        onValueChange = viewModel::onMaxScoreChanged,
                        label = { Text("Max Score") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .align(Alignment.End)
                    )
                }
            }
        }

        item {
            StudentAnswerInput(
                answerText = uiState.studentAnswerText,
                onAnswerTextChange = viewModel::onStudentAnswerTextChanged,
                answerImage = uiState.studentAnswerImage,
                onAnswerImageChange = viewModel::onStudentAnswerImageChanged
            )
        }

        item {
            Button(
                onClick = onGradeClick,
                enabled = isAiReady && !uiState.isGrading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Rounded.ModelTraining, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Grade with AI", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (uiState.gradingHistory.isNotEmpty()) {
            item {
                Text(
                    "Grading History",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            items(uiState.gradingHistory, key = { it.id }) { item ->
                GradedItemCard(item = item)
            }
        }
    }
}

@Composable
fun StudentAnswerInput(
    answerText: String,
    onAnswerTextChange: (String) -> Unit,
    answerImage: Bitmap?,
    onAnswerImageChange: (Bitmap?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val processImageUri: (Uri?) -> Unit = { uri ->
        uri?.let {
            scope.launch {
                when (val result = ImageUtils.compressImage(context, it)) {
                    is ImageUtils.ImageCompressionResult.Success -> {
                        val bitmap = BitmapFactory.decodeByteArray(result.byteArray, 0, result.byteArray.size)
                        onAnswerImageChange(bitmap)
                    }
                    is ImageUtils.ImageCompressionResult.Error -> {
                        snackbarHostState.showSnackbar(result.message)
                    }
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> processImageUri(uri) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success) processImageUri(tempCameraUri) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            tempCameraUri?.let { uri -> cameraLauncher.launch(uri) }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Camera permission is required.") }
        }
    }

    OutlinedCard(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("2. Student's Submission", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = answerText,
                onValueChange = onAnswerTextChange,
                label = { Text("Typed Answer") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            AnimatedVisibility(visible = answerImage != null) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Image(
                        painter = rememberAsyncImagePainter(answerImage),
                        contentDescription = "Answer Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(onClick = { onAnswerImageChange(null) }) {
                        Icon(
                            imageVector = Icons.Rounded.Cancel,
                            contentDescription = "Remove Image",
                            tint = Color.White,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .padding(4.dp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Gallery")
                }
                OutlinedButton(
                    onClick = {
                        val permission = Manifest.permission.CAMERA
                        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                            val uri = AiTestComposeFileProvider.getImageUri(context)
                            tempCameraUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            val uri = AiTestComposeFileProvider.getImageUri(context)
                            tempCameraUri = uri
                            permissionLauncher.launch(permission)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Camera")
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

private data class AnalyticsFeature(
    val title: String,
    val icon: ImageVector,
    val message: String
)

@Composable
fun StudentAnalyticsTab(viewModel: AptusHubViewModel) {
    val features = remember {
        listOf(
            AnalyticsFeature(
                title = "Performance Insights",
                icon = Icons.AutoMirrored.Rounded.TrendingUp,
                message = "Coming soon: This feature will empower a tutor to see beyond single test scores. Gemma 3n will analyze submission trends to provide a dashboard of each student's academic trajectory, flagging both excellence and potential struggles."
            ),
            AnalyticsFeature(
                title = "Topic Mastery",
                icon = Icons.Rounded.Science,
                message = "Coming soon: This tool will transform a teacher into a curriculum strategist. Gemma 3n will semantically analyze all answers to pinpoint concepts the entire class finds challenging, allowing for targeted group-level intervention."
            ),
            AnalyticsFeature(
                title = "Personalized Practice",
                icon = Icons.Rounded.PsychologyAlt,
                message = "Coming soon: This will enable a true one-on-one tutoring at scale. Gemma 3n will create bespoke practice problems that address a student's individual learning gaps, turning every device into a personal study partner."
            ),
            AnalyticsFeature(
                title = "Attendance Patterns",
                icon = Icons.Rounded.EventBusy,
                message = "Coming soon: A proactive tool to look beyond a simple roster. Gemma 3n will help identify patterns in attendance, helping a tutor to address potential non-academic issues and foster a more supportive learning environment."
            ),
            AnalyticsFeature(
                title = "Concept Explainer",
                icon = Icons.Rounded.Lightbulb,
                message = "Coming soon: This allows a tutor to instantly differentiate instruction. With Gemma 3n, a complex topic can be re-explained in multiple, simpler ways, ensuring no student is left behind due to a single teaching style."
            ),
            AnalyticsFeature(
                title = "Question Generation",
                icon = Icons.Rounded.Quiz,
                message = "Coming soon: An intelligent assistant for educators. Gemma 3n will help create a diverse bank of assessment questions, reducing preparation time and preventing question fatigue."
            ),
            AnalyticsFeature(
                title = "Sentiment Analysis",
                icon = Icons.Rounded.SentimentSatisfied,
                message = "Coming soon: This will provide a deeper, empathetic understanding of the learning experience. Gemma 3n will analyze written responses to help a tutor understand not just what a student knows, but how they feel about it."
            ),
            AnalyticsFeature(
                title = "Smart Grouping",
                icon = Icons.Rounded.Groups,
                message = "Coming soon: This feature will turn class management into a strategic activity. Gemma 3n will suggest optimal student groups that balance skills or pair students for effective peer-to-peer learning."
            )
        )
    }

    var selectedFeature by remember { mutableStateOf<String?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("AI-Powered Classroom Insights", style = MaterialTheme.typography.headlineSmall)
                ExpandableInfoCard(
                    title = "About this vision",
                    content = "The following capabilities demonstrate how on-device AI will empower an educator, turning classroom-wide data into personalized student insights."
                )
            }
        }
        items(features) { feature ->
            FeatureCard(
                title = feature.title,
                icon = feature.icon,
                isSelected = selectedFeature == feature.title,
                onClick = {
                    selectedFeature = feature.title
                    viewModel.onFeatureCardTapped(feature.message)
                }
            )
        }
    }
}

@Composable
private fun ExpandableInfoCard(
    title: String,
    content: String,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
            .clickable { isExpanded = !isExpanded }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (isExpanded) "Show less" else "Show more",
                tint = MaterialTheme.colorScheme.secondary
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureCard(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val targetContainerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val targetContentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val targetElevation by animateDpAsState(if (isSelected) 8.dp else 2.dp, tween(300), label = "elevation")
    val targetScale by animateFloatAsState(if (isSelected) 1.05f else 1.0f, tween(300), label = "scale")

    val animatedContainerColor by animateColorAsState(targetContainerColor, tween(300), label = "containerColor")
    val animatedContentColor by animateColorAsState(targetContentColor, tween(300), label = "contentColor")

    Card(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(1f) // Makes the card a square
            .scale(targetScale),
        colors = CardDefaults.cardColors(
            containerColor = animatedContainerColor,
            contentColor = animatedContentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = targetElevation)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun GradedItemCard(item: GradedItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Question and Answer Section
            Text("Submission Details", style = MaterialTheme.typography.titleMedium)
            TextItem(icon = Icons.AutoMirrored.Rounded.HelpOutline, label = "Question", value = item.questionText)
            if (item.studentAnswerText.isNotBlank()) {
                TextItem(icon = Icons.AutoMirrored.Filled.Notes, label = "Student's Answer", value = item.studentAnswerText)
            }
            item.studentAnswerImage?.let {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Image, contentDescription = "Image Answer", tint = MaterialTheme.colorScheme.secondary)
                        Text("Image Answer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                    Image(
                        painter = rememberAsyncImagePainter(it),
                        contentDescription = "Graded Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // AI Result Section
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Graded", tint = MaterialTheme.colorScheme.primary)
                Text("AI Grade & Feedback", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ScoreIndicator(score = item.result.score?: 0)
                Text(
                    text = item.result.feedback?: "No feedback provided.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun TextItem(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 2.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ScoreIndicator(score: Int) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.size(50.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            strokeWidth = 4.dp,
        )
        Text(
            text = score.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun GradingProgressOverlay(statusText: String, health: CapabilityResult?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.9f))
            .clickable(
                enabled = false,
                onClick = {},
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.surface,
                strokeWidth = 6.dp,
                strokeCap = StrokeCap.Round
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.surface,
                textAlign = TextAlign.Center
            )
            AnimatedVisibility(visible = health!= null) {
                DeviceHealthMonitor(health = health)
            }
        }
    }
}

@Composable
fun DeviceHealthMonitor(health: CapabilityResult?) {
    if (health == null) return

    val statusColor = when (health.capability) {
        DeviceCapability.CAPABLE -> Color(0xFF388E3C) // Green
        DeviceCapability.LIMITED -> Color(0xFFF57C00) // Orange
        DeviceCapability.UNSUPPORTED -> Color(0xFFD32F2F) // Red
    }

    val animatedColor by animateColorAsState(targetValue = statusColor, animationSpec = tween(500), label = "statusColor")

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, animatedColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = when (health.capability) {
                        DeviceCapability.CAPABLE -> Icons.Rounded.CheckCircle
                        DeviceCapability.LIMITED -> Icons.Rounded.Warning
                        DeviceCapability.UNSUPPORTED -> Icons.Rounded.Error
                    },
                    contentDescription = "Device Status",
                    tint = animatedColor
                )
                Text(
                    "Live Device Status",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.3f))

            // Display the primary reason for the status
            Text(
                text = health.message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Display detailed metrics
            HealthMetricRow(
                icon = Icons.Rounded.Memory,
                label = "Available RAM",
                value = "${health.availableRamMb} MB"
            )
            HealthMetricRow(
                icon = Icons.Rounded.Thermostat,
                label = "Thermal Headroom",
                value = health.thermalHeadroom?.let { "${(it * 100).toInt()}%" }?: "N/A"
            )
        }
    }
}

@Composable
fun HealthMetricRow(icon: ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
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