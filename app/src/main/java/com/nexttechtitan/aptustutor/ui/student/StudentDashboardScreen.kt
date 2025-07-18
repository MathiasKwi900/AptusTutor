package com.nexttechtitan.aptustutor.ui.student

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.nexttechtitan.aptustutor.data.DiscoveredSession
import com.nexttechtitan.aptustutor.data.SessionWithClassDetails
import com.nexttechtitan.aptustutor.ui.tutor.SettingsMenu
import kotlinx.coroutines.delay

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardScreen(
    viewModel: StudentDashboardViewModel = hiltViewModel(),
    navController: NavHostController
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.sessionHistory.collectAsStateWithLifecycle()
    var sessionToJoin by remember { mutableStateOf<DiscoveredSession?>(null) }

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionState = rememberMultiplePermissionsState(permissions = requiredPermissions)

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    if (uiState.isDiscovering) {
        LaunchedEffect(Unit) {
            // Wait for 2 minutes
            delay(120_000L)
            // If still discovering after 2 minutes, stop it.
            if (uiState.isDiscovering) {
                viewModel.stopDiscovery()
                // Here you could also show a snackbar message: "Search timed out."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Student Dashboard") },
                actions = { SettingsMenu(onSwitchRole = viewModel::switchUserRole, navController = navController) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (permissionState.allPermissionsGranted) {
                DiscoveryCard(
                    isDiscovering = uiState.isDiscovering,
                    onToggleDiscovery = { isChecked ->
                        if (isChecked) viewModel.startDiscovery() else viewModel.stopDiscovery()
                    })
                Spacer(Modifier.height(16.dp))
                Text(
                    "Status: ${uiState.connectionStatus}",
                    style = MaterialTheme.typography.titleMedium
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text("Nearby Classes", style = MaterialTheme.typography.titleLarge)
                    }
                    if (uiState.discoveredSessions.isEmpty() && uiState.isDiscovering) {
                        item {
                            Text(
                                "Searching for classes...",
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                    items(uiState.discoveredSessions) { session ->
                        SessionCard(session = session, onJoin = { sessionToJoin = session })
                    }
                    item {
                        Text(
                            "My Attendance History",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                        )
                    }
                    if (history.isEmpty()) {
                        item {
                            Text("Your past sessions will appear here once you've been marked present in a class.")
                        }
                    } else {
                        items(history) { sessionDetails ->
                            HistoryCard(sessionDetails = sessionDetails)
                        }
                    }
                }

            } else {
                PermissionsNotGrantedCard {
                    permissionState.launchMultiplePermissionRequest()
                }
            }
        }

        sessionToJoin?.let { session ->
            JoinSessionDialog(
                session = session,
                onDismiss = { sessionToJoin = null },
                onConfirm = { pin ->
                    viewModel.joinSession(session, pin)
                    sessionToJoin = null
                }
            )
        }
    }
}

@Composable
fun JoinSessionDialog(
    session: DiscoveredSession,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join '${session.className}'") },
        text = {
            Column {
                Text("Your tutor will provide a 4-digit PIN to join the session.")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("Class PIN") })
            }
        },
        confirmButton = { Button(onClick = { onConfirm(pin) }, enabled = pin.length == 4) { Text("Join") } }
    )
}

@Composable
fun SessionCard(session: DiscoveredSession, onJoin: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(session.className, fontWeight = FontWeight.Bold)
                Text("Tutor: ${session.tutorName}", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onJoin) { Text("Join") }
        }
    }
}

@Composable
private fun DiscoveryCard(isDiscovering: Boolean, onToggleDiscovery: (Boolean) -> Unit) {
    Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Find a Class", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isDiscovering) "Searching for nearby tutors..." else "Discovery is off.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = isDiscovering, onCheckedChange = onToggleDiscovery)
        }
    }
}

@Composable
private fun PermissionsNotGrantedCard(onRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Permissions Required",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Please grant Bluetooth and Location permissions to find tutors.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRequest) {
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
fun HistoryCard(sessionDetails: SessionWithClassDetails) {
    val formatter = remember {
        SimpleDateFormat("EEE, d MMM yyyy 'at' hh:mm a", Locale.getDefault())
    }

    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            // We can now access the class name!
            Text(
                text = sessionDetails.classProfile.className,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Attended: ${formatter.format(Date(sessionDetails.session.sessionTimestamp))}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (sessionDetails.session.endTime != null) {
                Text(
                    text = "Duration: ${calculateDuration(sessionDetails.session.sessionTimestamp, sessionDetails.session.endTime!!)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun calculateDuration(start: Long, end: Long): String {
    val durationMillis = end - start
    val minutes = (durationMillis / 1000) / 60
    return if (minutes < 1) "Less than a minute" else "$minutes min"
}

