package com.nexttechtitan.aptustutor.ui.tutor

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.nexttechtitan.aptustutor.ai.GemmaAiService
import com.nexttechtitan.aptustutor.data.AssessmentAnswer
import com.nexttechtitan.aptustutor.data.AssessmentQuestion
import com.nexttechtitan.aptustutor.data.ModelStatus
import com.nexttechtitan.aptustutor.data.QuestionType
import com.nexttechtitan.aptustutor.ui.AptusTutorScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionDetailsScreen(
    viewModel: SubmissionDetailsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    navController: NavHostController
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    val modelInitialized by viewModel.modelInitialized.collectAsStateWithLifecycle(initialValue = false)
    val submission = uiState.submission
    val assessment = uiState.assessment
    val draftAnswers = uiState.draftAnswers
    var showSendConfirmationDialog by rememberSaveable { mutableStateOf(false) }

    val isAiReadyForGrading by remember {
        derivedStateOf { modelState is GemmaAiService.ModelState.ModelReadyCold || modelState is GemmaAiService.ModelState.Ready }
    }

    val containsMcq by remember(assessment) {
        derivedStateOf { assessment?.questions?.any { it.type == QuestionType.MULTIPLE_CHOICE }?: false }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(key1 = Unit) {
        viewModel.navigationEvents.collectLatest {
            onNavigateBack()
        }
    }

    LaunchedEffect(key1 = true) {
        viewModel.toastEvents.collectLatest { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val isGradingComplete = remember(draftAnswers) {
        assessment?.questions?.all { q ->
            val answer = draftAnswers[q.id]
            answer?.score != null && !answer.feedback.isNullOrBlank()
        } ?: false
    }

    val totalPossibleScore = remember(assessment) {
        assessment?.questions?.sumOf { it.maxScore } ?: 0
    }
    val studentTotalScore = remember(draftAnswers) {
        draftAnswers.values.sumOf { it.score ?: 0 }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(submission?.studentName ?: "Loading...", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (assessment != null && submission != null) {
                        Text(
                            text = "Score: $studentTotalScore / $totalPossibleScore",
                            modifier = Modifier.padding(end = 16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    SubmissionActionsMenu(
                        containsMcq = containsMcq,
                        isAiReady = isAiReadyForGrading && modelInitialized,
                        isAiBusy = uiState.isGradingEntireSubmission,
                        isFeedbackSent = uiState.feedbackSent,
                        onAutoGradeMcq = { viewModel.autoGradeMcqQuestions() },
                        onGradeWithAi = {
                            if (isAiReadyForGrading && modelInitialized) {
                                viewModel.gradeEntireSubmission()
                            } else {
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "AI Engine needs a one-time setup. Please go to settings to initialize.",
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
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.saveAllDrafts() },
                        modifier = Modifier.weight(1f),
                        enabled =!uiState.feedbackSent
                    ) {
                        Text("Save Draft")
                    }
                    Button(
                        onClick = { showSendConfirmationDialog = true },
                        enabled = isGradingComplete &&!uiState.feedbackSent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(if (uiState.feedbackSent) "Sent" else "Send Feedback")
                    }
                }
            }
        }
    ) { paddingValues ->
        if (submission == null || assessment == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        assessment.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Submitted by ${submission.studentName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                itemsIndexed(assessment.questions, key = { _, q -> q.id }) { index, question ->
                    GradedAnswerCard(
                        questionIndex = index + 1,
                        question = question,
                        answer = draftAnswers[question.id],
                        isFeedbackSent = uiState.feedbackSent,
                        onScoreChange = { newScore ->
                            viewModel.onScoreChange(
                                question.id,
                                newScore,
                                question.maxScore
                            )
                        },
                        onFeedbackChange = { newFeedback ->
                            viewModel.onFeedbackChange(
                                question.id,
                                newFeedback
                            )
                        }
                    )
                }
            }
            if (uiState.isGradingEntireSubmission) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                        .clickable(enabled = false, onClick = {}),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = uiState.gradingProgressText ?: "AI Grading in progress...",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    if (showSendConfirmationDialog) {
        SendConfirmationDialog(
            onDismiss = { showSendConfirmationDialog = false },
            onConfirm = {
                showSendConfirmationDialog = false
                viewModel.sendFeedback()
            }
        )
    }
}

@Composable
fun SubmissionActionsMenu(
    containsMcq: Boolean,
    isAiReady: Boolean,
    isAiBusy: Boolean,
    isFeedbackSent: Boolean,
    onAutoGradeMcq: () -> Unit,
    onGradeWithAi: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More Actions")
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (containsMcq) {
                DropdownMenuItem(
                    text = { Text("Auto-Grade MCQs") },
                    onClick = {
                        onAutoGradeMcq()
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Rounded.Checklist, contentDescription = null) },
                    enabled =!isFeedbackSent
                )
            }
            DropdownMenuItem(
                text = { Text("Grade All with AI") },
                onClick = {
                    onGradeWithAi()
                    showMenu = false
                },
                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                enabled = isAiReady &&!isAiBusy &&!isFeedbackSent
            )
        }
    }
}

@Composable
fun AiGradingConfirmationDialog(
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.RocketLaunch, contentDescription = null) },
        title = { Text("AI Is Not Yet Initialized!") },
        text = {
            Text("The AI model is ready but must be initialized before usage. To do so, we recommend you visit settings")
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Go to Settings")
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun SendConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
        title = { Text("Send Feedback?") },
        text = { Text("This action is final and cannot be undone. The student will be notified and the grading fields will become read-only.") },
        confirmButton = { Button(onClick = onConfirm) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun GradedAnswerCard(
    questionIndex: Int,
    question: AssessmentQuestion,
    answer: AssessmentAnswer?,
    isFeedbackSent: Boolean,
    onScoreChange: (String) -> Unit,
    onFeedbackChange: (String) -> Unit
) {
    val isGraded = answer?.score!= null &&!answer.feedback.orEmpty().isBlank()
    val isReadOnly = isFeedbackSent

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- 1. Question & Guide Section ---
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isGraded) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "Graded",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text("Question $questionIndex", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(question.text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(12.dp)
                ) {
                    Column {
                        Text("Marking Guide", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(question.markingGuide, fontStyle = FontStyle.Italic)
                    }
                }
            }

            // --- 2. Student's Answer Section ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Student's Answer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    val hasImage = !answer?.imageFilePath.isNullOrBlank()
                    val hasText = !answer?.textResponse.isNullOrBlank()

                    if (hasImage) {
                        Image(
                            painter = rememberAsyncImagePainter(model = File(answer!!.imageFilePath!!)),
                            contentDescription = "Handwritten Answer",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    if (hasText) {
                        if (hasImage) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(answer!!.textResponse!!)
                    }
                    if (!hasImage && !hasText) {
                        Text(
                            "[No answer provided]",
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }

            // --- 3. Grading & Feedback Panel ---
            Column {
                Text("Grading Panel", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = answer?.feedback ?: "",
                    onValueChange = onFeedbackChange,
                    label = { Text("Feedback / Correction") },
                    readOnly = isReadOnly,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = answer?.score?.toString() ?: "",
                    onValueChange = onScoreChange,
                    label = { Text("Score") },
                    suffix = { Text("/ ${question.maxScore}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    readOnly = isReadOnly,
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .align(Alignment.End)
                )
            }
        }
    }
}