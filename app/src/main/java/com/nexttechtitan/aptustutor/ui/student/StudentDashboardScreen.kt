package com.nexttechtitan.aptustutor.ui.student

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.nexttechtitan.aptustutor.data.Assessment
import com.nexttechtitan.aptustutor.data.DiscoveredSession
import com.nexttechtitan.aptustutor.data.SessionHistoryItem
import com.nexttechtitan.aptustutor.data.StudentDashboardUiState
import com.nexttechtitan.aptustutor.ui.AptusTutorScreen
import com.nexttechtitan.aptustutor.ui.tutor.SettingsMenu
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardScreen(
    viewModel: StudentDashboardViewModel = hiltViewModel(),
    navController: NavHostController
) {
    Log.d("AptusTutorDebug", "==> BRACKET LOG: Recomposing StudentDashboardScreen.")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.sessionHistory.collectAsStateWithLifecycle()
    var sessionToJoin by remember { mutableStateOf<DiscoveredSession?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showLeaveDialog by remember { mutableStateOf(false) }
    var userWantsToDiscover by remember { mutableStateOf(false) }
    var sessionForDialog by remember { mutableStateOf<SessionHistoryItem?>(null) }

    val requiredPermissions = remember {
        when {
            // Android 13 (API 33) and above
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
            // Android 12 (API 31 & 32)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            else -> listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
            )
        }
    }

    LaunchedEffect(key1 = Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }
    LaunchedEffect(key1 = Unit) {
        viewModel.navigationEvents.collect { destination ->
            navController.navigate(destination) {
                popUpTo(0) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    val permissionState = rememberMultiplePermissionsState(
        permissions = requiredPermissions
    ) { permissionsResult ->
        // This callback runs AFTER the user responds to the permission dialog
        Log.d("PermissionStatus", "Permission results received:")
        permissionsResult.forEach { (permission, isGranted) ->
            Log.d("PermissionStatus", "  ${permission}: ${if (isGranted) "GRANTED" else "DENIED"}")
        }

        val allPermissionsGranted = permissionsResult.all { it.value }
        Log.d("PermissionStatus", "All permissions granted: $allPermissionsGranted")

        if (allPermissionsGranted) {
            if (userWantsToDiscover) {
                Log.d("PermissionStatus", "All permissions granted and user wants to discover. Starting discovery.")
                viewModel.startDiscovery()
            } else {
                Log.d("PermissionStatus", "All permissions granted, but user does not currently want to discover.")
            }
        } else {
            Log.d("PermissionStatus", "Not all permissions granted. Updating UI and showing Snackbar.")
            // User denied permissions, update state and show the Snackbar
            userWantsToDiscover = false
            scope.launch {
                snackbarHostState
                    .showSnackbar("Nearby features require Precise Location. If you granted *Approximate*, please look for this App in your phone Settings to grant *Precise*.",
                        duration = SnackbarDuration.Long)

            }
        }
    }

    if (uiState.isDiscovering) {
        LaunchedEffect(Unit) {
            delay(120_000L)
            if (uiState.isDiscovering) {
                viewModel.stopDiscovery()
                userWantsToDiscover = false
                snackbarHostState.showSnackbar("Search timed out. Please try again.")
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            userWantsToDiscover = false
            snackbarHostState.showSnackbar(message)
            viewModel.errorShown()
        }
    }
    // --- END OF PRESERVED LOGIC BLOCK ---

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Student Dashboard") },
                actions = { SettingsMenu(onSwitchRole = viewModel::switchUserRole, navController = navController) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                val isConnected = uiState.connectionStatus == "Connected" && uiState.connectedSession != null
                LaunchedEffect(isConnected) {
                    if (isConnected) {
                        userWantsToDiscover = false
                        viewModel.stopDiscovery()
                    }
                }

                if (isConnected) {
                    ConnectedInfoCard(
                        session = uiState.connectedSession!!,
                        onLeaveClicked = { showLeaveDialog = true }
                    )
                } else {
                    DiscoveryCard(
                        isDiscovering = uiState.isDiscovering || userWantsToDiscover,
                        onToggleDiscovery = { isChecked ->
                            if (isChecked) {
                                userWantsToDiscover = true
                                if (permissionState.allPermissionsGranted) {
                                    viewModel.startDiscovery()
                                } else {
                                    permissionState.launchMultiplePermissionRequest()
                                }
                            } else {
                                userWantsToDiscover = false
                                viewModel.stopDiscovery()
                            }
                        }
                    )
                }
            }

            // --- NEARBY CLASSES SECTION ---
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text("Nearby Classes", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (uiState.discoveredSessions.isEmpty()) {
                item {
                    EmptyState(
                        icon = if (uiState.isDiscovering) Icons.Rounded.SignalWifiOff else Icons.Rounded.SignalWifiOff,
                        headline = if (uiState.isDiscovering) "Searching..." else "No Classes Found",
                        subline = if (uiState.isDiscovering) "Looking for tutors in your area. This may take a moment." else "Once a tutor starts a session nearby, it will appear here."
                    )
                }
            } else {
                items(uiState.discoveredSessions, key = { it.sessionId }) { session ->
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SessionCard(
                            session = session,
                            uiState = uiState,
                            onJoin = { sessionToJoin = session }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }


            // --- ATTENDANCE HISTORY SECTION ---
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text("My Attendance History", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (history.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.HistoryEdu,
                        headline = "No History Yet",
                        subline = "Your attended classes and assessment results will appear here."
                    )
                }
            } else {
                items(history, key = { it.sessionWithDetails.session.sessionId }) { sessionHistoryItem ->
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        HistoryCard(
                            sessionHistoryItem = sessionHistoryItem,
                            onViewResultsClicked = {
                                sessionForDialog = sessionHistoryItem
                                //navController.navigate("${AptusTutorScreen.SubmissionResult.name}/${sessionHistoryItem.sessionWithDetails.session.sessionId}")
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // --- DIALOGS (Wired to original logic) ---
        sessionToJoin?.let { session ->
            JoinSessionDialog(
                session = session,
                onDismiss = { sessionToJoin = null },
                onConfirm = { pin ->
                    userWantsToDiscover = false
                    viewModel.joinSession(session, pin)
                    sessionToJoin = null
                }
            )
        }

        sessionForDialog?.let { item ->
            val assessments by viewModel.getAssessmentsForSession(item.sessionWithDetails.session.sessionId)
                .collectAsStateWithLifecycle(initialValue = emptyList())

            AssessmentsDialog(
                assessments = assessments,
                onDismiss = { sessionForDialog = null },
                onAssessmentSelected = { assessmentId ->
                    sessionForDialog = null
                    navController.navigate(
                        "${AptusTutorScreen.SubmissionResult.name}/${item.sessionWithDetails.session.sessionId}/$assessmentId"
                    )
                }
            )
        }

        if (showLeaveDialog) {
            LeaveSessionDialog(
                onDismiss = { showLeaveDialog = false },
                onConfirm = {
                    viewModel.leaveSession()
                    showLeaveDialog = false
                }
            )
        }
    }
}


@Composable
private fun ConnectedInfoCard(session: DiscoveredSession, onLeaveClicked: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // ENHANCEMENT: Reduced vertical padding to make the card more compact.
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            // ENHANCEMENT: Rebalanced padding and added Arrangement.spacedBy for consistent, centered spacing.
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // UX FIX: The checkmark icon has been removed to avoid user confusion.
            Text(
                "Connected to Session",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                // The class name is now the primary headline for clear focus.
                text = session.className,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Text(
                "Tutor: ${session.tutorName}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            // A small spacer adds breathing room before the action button.
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onLeaveClicked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("Leave Session")
            }
        }
    }
}

@Composable
private fun DiscoveryCard(isDiscovering: Boolean, onToggleDiscovery: (Boolean) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Find a Class", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isDiscovering) "Searching for nearby tutors..." else "Discovery is off",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // ENHANCEMENT: Themed Switch colors for better brand consistency and visual feedback.
            Switch(
                checked = isDiscovering,
                onCheckedChange = onToggleDiscovery,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    }
}

@Composable
fun SessionCard(session: DiscoveredSession, uiState: StudentDashboardUiState, onJoin: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isJoiningThisSession = uiState.joiningSessionId == session.endpointId
            val isWaitingForApproval = uiState.connectionStatus == "Verifying PIN..." && uiState.connectedSession?.endpointId == session.endpointId
            val isFullyConnected = uiState.connectionStatus == "Connected" && uiState.connectedSession?.endpointId == session.endpointId
            val displaySession = uiState.connectedSession?.takeIf { it.endpointId == session.endpointId }?: session

            Column(modifier = Modifier.weight(1f)) {
                if (isWaitingForApproval) {
                    Text("${displaySession.tutorName}'s Class", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                } else {
                    Text(displaySession.className, style = MaterialTheme.typography.titleMedium)
                    Text("Tutor: ${displaySession.tutorName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(
                onClick = onJoin,
                enabled =!isJoiningThisSession &&!isWaitingForApproval &&!isFullyConnected
            ) {
                when {
                    isFullyConnected -> Text("Joined")
                    isWaitingForApproval -> Text("Waiting...")
                    isJoiningThisSession -> Text("Joining...")
                    else -> Text("Join")
                }
            }
        }
    }
}

@Composable
fun HistoryCard(sessionHistoryItem: SessionHistoryItem, onViewResultsClicked: () -> Unit) {
    val formatter = remember { SimpleDateFormat("EEE, d MMM yyyy 'at' hh:mm a", Locale.getDefault()) }
    val sessionDetails = sessionHistoryItem.sessionWithDetails
    if (sessionDetails.classProfile == null) {
        Log.e("AptusTutorDebug", "ClassProfile is null for session: ${sessionDetails.session.sessionId}")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = sessionDetails.classProfile?.className ?: "Class Name",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Attended: ${formatter.format(Date(sessionDetails.session.sessionTimestamp))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (sessionDetails.session.endTime != null) {
                Text(
                    text = "Duration: ${calculateDuration(sessionDetails.session.sessionTimestamp, sessionDetails.session.endTime!!)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (sessionHistoryItem.hasSubmission) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onViewResultsClicked,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("View Results")
                }
            }
        }
    }
}

@Composable
fun JoinSessionDialog(session: DiscoveredSession, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    val dialogTitle = if (session.className.isBlank() || session.className == "Connecting...") {
        "Join ${session.tutorName}'s class"
    } else {
        "Join '${session.className}'"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Link, contentDescription = null) },
        title = { Text(dialogTitle) },
        text = {
            Column {
                Text("Your tutor will provide a 4-digit PIN to join the session.")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) pin = it.filter { c -> c.isDigit() } },
                    label = { Text("Class PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(pin) }, enabled = pin.length == 4) { Text("Join") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun LeaveSessionDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave Session?") },
        text = { Text("Are you sure you want to disconnect from the current session?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Leave")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AssessmentsDialog(
    assessments: List<Assessment>,
    onDismiss: () -> Unit,
    onAssessmentSelected: (assessmentId: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Assessment") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (assessments.isEmpty()) {
                    item { Text("Loading assessments...") }
                }
                items(assessments, key = { it.id }) { assessment ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onAssessmentSelected(assessment.id) }
                    ) {
                        Text(
                            text = assessment.title,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EmptyState(icon: ImageVector, headline: String, subline: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = headline,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Text(text = headline, style = MaterialTheme.typography.titleLarge)
        Text(
            text = subline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

private fun calculateDuration(start: Long, end: Long): String {
    val durationMillis = end - start
    val minutes = (durationMillis / 1000) / 60
    return if (minutes < 1) "Less than a minute" else "$minutes min"
}