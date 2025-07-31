package com.nexttechtitan.aptustutor.ui.tutor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexttechtitan.aptustutor.data.SessionWithClassDetails
import com.nexttechtitan.aptustutor.ui.student.AssessmentsDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * A screen that displays a list of a tutor's previously conducted sessions.
 * Tapping a session allows the tutor to view and grade any assessments from that session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorHistoryScreen(
    viewModel: TutorHistoryViewModel = hiltViewModel(),
    onNavigateToSubmissionsList: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var sessionForDialog by remember { mutableStateOf<SessionWithClassDetails?>(null) }

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
                        onCardClicked = { sessionForDialog = session }
                    )
                }
            }
        }
    }

    // When a history card is tapped, this state is set, which triggers the AssessmentsDialog.
    sessionForDialog?.let { item ->
        val assessments by viewModel.getAssessmentsForSession(item.session.sessionId)
            .collectAsStateWithLifecycle(initialValue = emptyList())

        // This dialog lists all assessments from the selected historical session.
        AssessmentsDialog(
            assessments = assessments,
            onDismiss = { sessionForDialog = null },
            onAssessmentSelected = { assessmentId ->
                sessionForDialog = null
                onNavigateToSubmissionsList(assessmentId)
            }
        )
    }
}

/**
 * A card that displays summary information for a single past session.
 */
@Composable
fun HistorySessionCard(
    sessionDetails: SessionWithClassDetails,
    onCardClicked: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("EEE, d MMM yyyy 'at' hh:mm a", Locale.getDefault()) }
    val session = sessionDetails.session

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onCardClicked,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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