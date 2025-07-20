package com.nexttechtitan.aptustutor.ui.tutor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import coil.compose.rememberAsyncImagePainter
import com.nexttechtitan.aptustutor.data.AssessmentAnswer
import com.nexttechtitan.aptustutor.data.AssessmentQuestion
import com.nexttechtitan.aptustutor.data.QuestionType
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionDetailsScreen(
    viewModel: SubmissionDetailsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    // --- BATTLE-TESTED LOGIC BLOCK (PRESERVED EXACTLY) ---
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val submission = uiState.submission
    val assessment = uiState.assessment
    val draftAnswers = uiState.draftAnswers
    var showFeedbackSentDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(key1 = true) {
        viewModel.toastEvents.collect { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val isGradingComplete = submission?.answers?.all { answer ->
        answer.score != null && answer.feedback?.isNotBlank() == true
    } ?: false
    // --- END OF PRESERVED LOGIC BLOCK ---

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            // DESIGN: The action button is removed for a cleaner, navigation-focused TopAppBar.
            CenterAlignedTopAppBar(
                title = { Text(submission?.studentName ?: "Loading...", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        // DESIGN: The primary screen action is now in a dedicated BottomAppBar, which is standard practice.
        bottomBar = {
            BottomAppBar(
                contentPadding = PaddingValues(16.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.sendFeedback()
                        showFeedbackSentDialog = true
                    },
                    enabled = isGradingComplete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Send Graded Feedback")
                }
            }
        }
    ) { paddingValues ->
        if (submission == null || assessment == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(assessment.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Submitted by ${submission.studentName}", style = MaterialTheme.typography.titleMedium)
            }
            itemsIndexed(assessment.questions, key = { _, q -> q.id }) { index, question ->
                val isSaved = uiState.savedQuestionIds.contains(question.id)
                GradedAnswerCard(
                    questionIndex = index,
                    question = question,
                    answer = draftAnswers[question.id],
                    isSaved = isSaved,
                    onScoreChange = { newScore -> viewModel.onScoreChange(question.id, newScore) },
                    onFeedbackChange = { newFeedback -> viewModel.onFeedbackChange(question.id, newFeedback) },
                    onSaveGrade = { viewModel.saveGrade(question.id) }
                )
            }
        }
    }

    if (showFeedbackSentDialog) {
        FeedbackSentDialog(onDismiss = { showFeedbackSentDialog = false })
    }
}

@Composable
fun GradedAnswerCard(
    questionIndex: Int,
    question: AssessmentQuestion,
    answer: AssessmentAnswer?,
    isSaved: Boolean,
    onScoreChange: (String) -> Unit,
    onFeedbackChange: (String) -> Unit,
    onSaveGrade: () -> Unit
) {
    val isSavable = answer?.score != null && answer.feedback.orEmpty().isNotBlank()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- 1. Question & Guide Section ---
            Column {
                Text("Question ${questionIndex + 1}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                    when (question.type) {
                        QuestionType.TEXT_INPUT -> {
                            Text(answer?.textResponse?.ifBlank { "No answer provided." } ?: "No answer provided.")
                        }
                        QuestionType.HANDWRITTEN_IMAGE -> {
                            if (answer?.imageFilePath != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(model = File(answer.imageFilePath!!)),
                                    contentDescription = "Handwritten Answer",
                                    modifier = Modifier.fillMaxWidth().height(250.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(250.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Waiting for image...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- 3. Grading & Feedback Panel ---
            Column {
                Text("Grading Panel", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = answer?.score?.toString() ?: "",
                        onValueChange = onScoreChange,
                        label = { Text("Score") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(100.dp)
                    )
                    // This weighted spacer pushes the score field and save button to opposite ends.
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onSaveGrade,
                        enabled = isSavable && !isSaved,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(if (isSaved) "Saved" else "Save")
                    }
                }

                OutlinedTextField(
                    value = answer?.feedback ?: "",
                    onValueChange = onFeedbackChange,
                    label = { Text("Feedback / Correction") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .defaultMinSize(minHeight = 100.dp)
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { /* TODO: Wire up to Gemma 3n AI grading logic */ },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Rounded.Lightbulb, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Grade with AI")
                }
            }
        }
    }
}

@Composable
fun FeedbackSentDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = "Success")},
        title = { Text("Feedback Sent") },
        text = { Text("The graded assessment has been sent back to the student.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}