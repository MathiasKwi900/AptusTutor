package com.nexttechtitan.aptustutor.ui.tutor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexttechtitan.aptustutor.data.SessionWithClassDetails
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorHistoryScreen(
    viewModel: TutorHistoryViewModel = hiltViewModel(),
    onNavigateToSubmission: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.sessions.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.History,
                headline = "No History Yet",
                subline = "Completed sessions with assessments will appear here."
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.sessions, key = { it.session.sessionId }) { session ->
                    HistorySessionCard(
                        sessionDetails = session,
                        isSelected = uiState.selectedSessionId == session.session.sessionId,
                        submissions = uiState.submissionsForSelectedSession,
                        onCardClicked = {
                            if (uiState.selectedSessionId == session.session.sessionId) {
                                viewModel.clearSelectedSession()
                            } else {
                                viewModel.selectSession(session.session.sessionId)
                            }
                        },
                        onSubmissionClicked = onNavigateToSubmission
                    )
                }
            }
        }
    }
}

@Composable
fun HistorySessionCard(
    sessionDetails: SessionWithClassDetails,
    isSelected: Boolean,
    submissions: List<SubmissionWithStatus>,
    onCardClicked: () -> Unit,
    onSubmissionClicked: (String) -> Unit
) {
    val formatter = remember { SimpleDateFormat("EEE, d MMM yyyy 'at' hh:mm a", Locale.getDefault()) }
    val session = sessionDetails.session

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onCardClicked,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = sessionDetails.classProfile?.className ?: "Class Name", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Conducted on: ${formatter.format(Date(session.sessionTimestamp))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            session.endTime?.let {
                val duration = calculateDuration(session.sessionTimestamp, it)
                Text(
                    "Duration: $duration",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isSelected) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text("Submissions", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (submissions.isEmpty()) {
                        Text("No submissions were made for this session's assessment.")
                    } else {
                        submissions.forEach { item ->
                            SubmissionItemRow(item, onSubmissionClicked)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmissionItemRow(
    item: SubmissionWithStatus,
    onSubmissionClicked: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSubmissionClicked(item.submission.submissionId) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(item.submission.studentName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(item.statusText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun calculateDuration(start: Long, end: Long): String {
    val durationMillis = end - start
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
    return when {
        minutes < 1 -> "Less than a minute"
        minutes == 1L -> "1 minute"
        else -> "$minutes minutes"
    }
}