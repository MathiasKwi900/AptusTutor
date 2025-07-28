package com.nexttechtitan.aptustutor.ui.student

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.nexttechtitan.aptustutor.data.AssessmentAnswer
import com.nexttechtitan.aptustutor.data.AssessmentQuestion
import com.nexttechtitan.aptustutor.data.QuestionType
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionResultScreen(
    onNavigateBack: () -> Unit,
    viewModel: SubmissionResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val submissionWithAssessment = uiState.submissionWithAssessment

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
            submissionWithAssessment?.assessment != null -> {
                val assessment = submissionWithAssessment.assessment
                val submission = submissionWithAssessment.submission
                val totalScore = submission.answers?.sumOf { it.score ?: 0 } ?: 0
                val maxScore = assessment.questions.sumOf { it.maxScore }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Total Score", style = MaterialTheme.typography.titleMedium)
                                Text("$totalScore / $maxScore", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    itemsIndexed(assessment.questions, key = { _, q -> q.id }) { index, question ->
                        val answer = submission.answers?.find { it.questionId == question.id }
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
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Question $questionIndex", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${question.maxScore} marks", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(question.text)
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // --- Your Answer ---
            Text("Your Answer:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            if (question.type == QuestionType.MULTIPLE_CHOICE) {
                val answerIndex = answer?.textResponse?.toIntOrNull()
                val answerText = if (answerIndex != null && question.options != null && answerIndex in question.options.indices) {
                    "${('A' + answerIndex)}. ${question.options[answerIndex]}"
                } else {
                    "No answer provided."
                }
                Text(answerText, fontStyle = if (answerIndex == null) FontStyle.Italic else FontStyle.Normal)
            } else {
                if (!answer?.textResponse.isNullOrBlank()) {
                    Text(answer!!.textResponse)
                } else if (!answer?.imageFilePath.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(model = File(answer.imageFilePath!!)),
                        contentDescription = "Your handwritten answer",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("No answer provided.", fontStyle = FontStyle.Italic)
                }
            }

            // --- Grade & Feedback ---
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tutor Feedback:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(answer?.feedback ?: "No feedback provided.")
                }
                Spacer(Modifier.width(16.dp))
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