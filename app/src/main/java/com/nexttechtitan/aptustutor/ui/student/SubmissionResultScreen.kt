package com.nexttechtitan.aptustutor.ui.student

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.nexttechtitan.aptustutor.data.AssessmentAnswer
import com.nexttechtitan.aptustutor.data.AssessmentQuestion
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionResultScreen(
    onNavigateBack: () -> Unit,
    viewModel: SubmissionResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.submissionWithAssessment?.assessment?.title ?: "Assessment Results") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.submissionWithAssessment != null -> {
                val assessment = uiState.submissionWithAssessment!!.assessment
                val submission = uiState.submissionWithAssessment!!.submission

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(assessment.questions) { index, question ->
                        val answer = submission.answers.find { it.questionId == question.id }
                        ResultAnswerCard(
                            questionIndex = index + 1,
                            question = question,
                            answer = answer
                        )
                    }
                }
            }
            else -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(uiState.error ?: "An unknown error occurred.")
                }
            }
        }
    }
}

@Composable
fun ResultAnswerCard(
    questionIndex: Int,
    question: AssessmentQuestion,
    answer: AssessmentAnswer?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            // --- Question ---
            Text("Question $questionIndex", style = MaterialTheme.typography.titleMedium)
            Text(question.text, fontWeight = FontWeight.Bold)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // --- Your Answer ---
            Text("Your Answer:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (answer?.textResponse != null) {
                Text(answer.textResponse)
            } else if (answer?.imageFilePath != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = File(answer.imageFilePath!!)),
                    contentDescription = "Your handwritten answer",
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("No answer provided.", fontStyle = FontStyle.Italic)
            }

            // --- Grade & Feedback ---
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tutor Feedback:", style = MaterialTheme.typography.titleSmall)
                    Text(answer?.feedback ?: "No feedback provided.")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Score", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = answer?.score?.toString() ?: "-",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}