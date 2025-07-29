package com.nexttechtitan.aptustutor.ui.tutor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.nexttechtitan.aptustutor.ui.main.GradingProgressOverlay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * The primary grading screen where a tutor reviews a single student's submission.
 * This UI allows the tutor to:
 * - View each question, the marking guide, and the student's answer (text or image).
 * - Manually input scores and feedback.
 * - Trigger on-device AI to grade the submission.
 * - Save grading progress or send the final feedback to the student.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionDetailsScreen(
    viewModel: SubmissionDetailsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    navController: NavHostController
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val submission = uiState.submission
    val assessment = uiState.assessment
    val draftAnswers = uiState.draftAnswers
    var showSendConfirmationDialog by rememberSaveable { mutableStateOf(false) }

    // This derived state recalculates only when the assessment data changes.
    // It's an optimization to avoid checking the list on every recomposition.
    val containsMcq by remember(assessment) {
        derivedStateOf { assessment?.questions?.any { it.type == QuestionType.MULTIPLE_CHOICE }?: false }
    }

    // A derived state that simplifies checking if any AI-related process is active.
    val isAiBusy = uiState.isGradingEntireSubmission ||
            modelState is GemmaAiService.ModelState.Busy ||
            modelState is GemmaAiService.ModelState.LoadingModel

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // This effect handles one-off navigation events from the ViewModel, like navigating back
    // automatically after saving or sending feedback.
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
                        isModelDownloaded = modelStatus == ModelStatus.DOWNLOADED,
                        isAiBusy = isAiBusy,
                        isFeedbackSent = uiState.feedbackSent,
                        onAutoGradeMcq = { viewModel.autoGradeMcqQuestions() },
                        onGradeWithAi = {
                            if (modelStatus == ModelStatus.DOWNLOADED) {
                                viewModel.gradeEntireSubmission()
                            } else {
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = if (modelStatus == ModelStatus.NOT_DOWNLOADED) "AI model is not downloaded." else "Model is still downloading.",
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
                    when (question.type) {
                        QuestionType.MULTIPLE_CHOICE -> {
                            GradedMcqAnswerCard(
                                questionIndex = index + 1,
                                question = question,
                                answer = draftAnswers[question.id],
                                isFeedbackSent = uiState.feedbackSent,
                                onScoreChange = { newScore -> viewModel.onScoreChange(question.id, newScore, question.maxScore) },
                                onFeedbackChange = { newFeedback -> viewModel.onFeedbackChange(question.id, newFeedback) }
                            )
                        }
                        else -> {
                            GradedAnswerCard(
                                questionIndex = index + 1,
                                question = question,
                                answer = draftAnswers[question.id],
                                isFeedbackSent = uiState.feedbackSent,
                                onScoreChange = { newScore -> viewModel.onScoreChange(question.id, newScore, question.maxScore) },
                                onFeedbackChange = { newFeedback -> viewModel.onFeedbackChange(question.id, newFeedback) }
                            )
                        }
                    }
                }
            }
            if (uiState.isGradingEntireSubmission) {
                GradingProgressOverlay(
                    statusText = uiState.gradingProgressText ?: "AI Grading in progress...",
                    health = uiState.deviceHealth
                )
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

/**
 * A dropdown menu in the TopAppBar containing actions for the submission,
 * such as auto-grading MCQs or grading the entire submission with AI.
 * The options are dynamically enabled based on the AI model's status and availability.
 */
@Composable
fun SubmissionActionsMenu(
    containsMcq: Boolean,
    isModelDownloaded: Boolean,
    isAiBusy: Boolean,
    isFeedbackSent: Boolean,
    onAutoGradeMcq: () -> Unit,
    onGradeWithAi: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    val isAiGradingEnabled = isModelDownloaded &&
            !isAiBusy && !isFeedbackSent

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
                enabled = isAiGradingEnabled
            )
        }
    }
}

/**
 * A confirmation dialog to prevent the tutor from accidentally sending finalized feedback.
 */
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

/**
 * A card for grading a standard written-response or image-based answer.
 * It displays the question, guide, student's answer, and provides input fields
 * for the score and feedback, which become read-only after feedback is sent.
 */
@Composable
fun GradedMcqAnswerCard(
    questionIndex: Int,
    question: AssessmentQuestion,
    answer: AssessmentAnswer?,
    isFeedbackSent: Boolean,
    onScoreChange: (String) -> Unit,
    onFeedbackChange: (String) -> Unit
) {
    val isReadOnly = isFeedbackSent

    fun getOptionText(indexStr: String?): String {
        val index = indexStr?.toIntOrNull() ?: return "[Not answered]"
        if (question.options == null || index !in question.options.indices) {
            return "[Invalid Answer]"
        }
        return "${('A' + index)}. ${question.options[index]}"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- 1. Question & Guide Section ---
            Column {
                Text("Question $questionIndex (Multiple Choice)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(question.text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp)
                ) {
                    Column {
                        Text("Correct Answer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(getOptionText(question.markingGuide), fontStyle = FontStyle.Italic)
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
                    Text(getOptionText(answer?.textResponse))
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
                    modifier = Modifier.fillMaxWidth(0.5f).align(Alignment.End)
                )
            }
        }
    }
}

/**
 * A card for grading a standard written-response or image-based answer.
 * It displays the question, guide, student's answer, and provides input fields
 * for the score and feedback, which become read-only after feedback is sent.
 */
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