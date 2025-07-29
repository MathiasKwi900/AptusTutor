package com.nexttechtitan.aptustutor.ui.tutor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexttechtitan.aptustutor.data.AptusTutorRepository
import com.nexttechtitan.aptustutor.data.AssessmentSubmission
import com.nexttechtitan.aptustutor.data.FeedbackStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * ViewModel for the [SubmissionsListScreen].
 * It fetches the title of a historic assessment and a live list of all student
 * submissions for that specific assessment.
 */
@HiltViewModel
class SubmissionsListViewModel @Inject constructor(
    repository: AptusTutorRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val assessmentId: String = savedStateHandle.get("assessmentId")!!
    val submissions = repository.getSubmissionsFlowForAssessment(assessmentId)

    val assessmentTitle = repository.getAssessmentById(assessmentId)
        .map { it?.title ?: "Submissions" }
}


/**
 * A screen that displays a list of all submissions for a single past assessment.
 * Tapping a submission navigates the tutor to the details screen for grading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionsListScreen(
    viewModel: SubmissionsListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onSubmissionClick: (String) -> Unit
) {
    val submissions by viewModel.submissions.collectAsStateWithLifecycle(initialValue = emptyList())
    val title by viewModel.assessmentTitle.collectAsStateWithLifecycle(initialValue = "Submissions")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        if (submissions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Waiting for Submissions", style = MaterialTheme.typography.titleLarge)
                    Text("Submissions from students will appear here live.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(submissions, key = { it.submissionId }) { submission ->
                    SubmissionCard(
                        submission = submission,
                        onClick = { onSubmissionClick(submission.submissionId) }
                    )
                }
            }
        }
    }
}

/** A card representing a single student's submission in a list. */
@Composable
private fun SubmissionCard(submission: AssessmentSubmission, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(submission.studentName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            SubmissionStatusChip(status = submission.feedbackStatus)
        }
    }
}

/** A small, colored chip that indicates the feedback status of a submission. */
@Composable
private fun SubmissionStatusChip(status: FeedbackStatus) {
    val (text, color) = when (status) {
        FeedbackStatus.PENDING_SEND -> "Ready to Grade" to MaterialTheme.colorScheme.secondary
        FeedbackStatus.SENT_PENDING_ACK -> "Feedback Sent" to MaterialTheme.colorScheme.tertiary
        FeedbackStatus.DELIVERED -> "Delivered" to Color(0xFF34A853)
    }
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f), contentColor = color)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}