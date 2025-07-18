package com.nexttechtitan.aptustutor.ui.student

import android.Manifest
import android.os.Build
import android.util.Log
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
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardScreen(
    viewModel: StudentDashboardViewModel = hiltViewModel(),
    navController: NavHostController
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.sessionHistory.collectAsStateWithLifecycle()
    var sessionToJoin by remember { mutableStateOf<DiscoveredSession?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var userWantsToDiscover by remember { mutableStateOf(false) }
    val requiredPermissions = remember {
        when {
            // Android 13 (API 33) and above
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )

            // Android 12 (API 31 & 32)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )

            else -> listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
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
                snackbarHostState.showSnackbar("Permissions are required to find nearby classes.")
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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
            DiscoveryCard(
                isDiscovering = uiState.isDiscovering || userWantsToDiscover,
                onToggleDiscovery = { isChecked ->
                    if (isChecked) {
                        userWantsToDiscover = true
                        Log.d("AptusTutorDebug", "[STUDENT UI] Discovery toggled ON. Checking permissions...")
                        if (permissionState.allPermissionsGranted) {
                            Log.d("AptusTutorDebug", "[STUDENT UI] Permissions are granted. Starting discovery.")
                            viewModel.startDiscovery()
                        } else {
                            Log.d("AptusTutorDebug", "[STUDENT UI] Permissions not granted. Launching request dialog.")
                            permissionState.launchMultiplePermissionRequest()
                        }
                    } else {
                        Log.d("AptusTutorDebug", "[STUDENT UI] Discovery toggled OFF. Stopping discovery.")
                        userWantsToDiscover = false
                        viewModel.stopDiscovery()
                    }
                }
            )
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

