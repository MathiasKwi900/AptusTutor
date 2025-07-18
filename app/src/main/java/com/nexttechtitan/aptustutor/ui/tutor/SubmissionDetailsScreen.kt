package com.nexttechtitan.aptustutor.ui.tutor

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionDetailsScreen(
    viewModel: SubmissionDetailsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val submission = uiState.submission
    val assessment = uiState.assessment
    var showFeedbackSentDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(submission?.studentName ?: "Loading...") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        viewModel.sendFeedback()
                        showFeedbackSentDialog = true
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Feedback to Student")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (submission == null || assessment == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(assessment.title, style = MaterialTheme.typography.headlineSmall)
            }
            itemsIndexed(assessment.questions) { index, question ->
                val answer = submission.answers.find { it.questionId == question.id }
                GradedAnswerCard(
                    questionIndex = index,
                    question = question,
                    answer = answer,
                    onSaveGrade = viewModel::saveGrade
                )
            }
        }
    }

    if (showFeedbackSentDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackSentDialog = false },
            title = { Text("Feedback Sent") },
            text = { Text("The graded assessment has been sent back to the student.") },
            confirmButton = { TextButton(onClick = { showFeedbackSentDialog = false }) { Text("OK") } }
        )
    }
}

@Composable
fun GradedAnswerCard(
    questionIndex: Int,
    question: AssessmentQuestion,
    answer: AssessmentAnswer?,
    onSaveGrade: (questionId: String, score: Int, feedback: String) -> Unit
) {
    var score by remember { mutableStateOf(answer?.score?.toString() ?: "") }
    var feedback by remember { mutableStateOf(answer?.feedback ?: "") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            // --- Question & Guide Section ---
            Text("Question ${questionIndex + 1}", style = MaterialTheme.typography.titleMedium)
            Text(question.text, fontWeight = FontWeight.Bold)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Marking Guide", style = MaterialTheme.typography.titleSmall)
            Text(question.markingGuide, fontStyle = FontStyle.Italic, color = Color.Gray)
            Spacer(Modifier.height(16.dp))

            // --- Student Answer Section ---
            Text("Student's Answer", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            when (question.type) {
                QuestionType.TEXT_INPUT -> {
                    Text(answer?.textResponse ?: "No answer provided.")
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
                        Text("No image provided.")
                    }
                }
            }

            // --- Grading Section ---
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Grading", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = score,
                    onValueChange = { score = it.filter { c -> c.isDigit() } },
                    label = { Text("Score") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSaveGrade(question.id, score.toIntOrNull() ?: 0, feedback) },
                    enabled = score.isNotBlank() || feedback.isNotBlank()
                ) {
                    Text("Save")
                }
            }
            OutlinedTextField(
                value = feedback,
                onValueChange = { feedback = it },
                label = { Text("Feedback / Correction") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}