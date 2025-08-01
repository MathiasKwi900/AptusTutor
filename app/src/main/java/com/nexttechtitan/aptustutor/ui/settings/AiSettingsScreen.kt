package com.nexttechtitan.aptustutor.ui.settings

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.nexttechtitan.aptustutor.data.AiSettingsUiState
import com.nexttechtitan.aptustutor.data.ModelStatus
import com.nexttechtitan.aptustutor.ui.student.OrDivider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The user interface for managing the on-device AI model. It allows users to
 * download the model, load it from a file, see its status, and delete it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel()
) {
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle(initialValue = ModelStatus.NOT_DOWNLOADED)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showMeteredDialog by viewModel.showMeteredNetworkDialog.collectAsStateWithLifecycle()
    val isLoadingFromStorage by viewModel.isLoadingFromStorage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val postNotificationsPermissionLauncher = rememberLauncherForActivityResult(
        contract = RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.onDownloadAction()
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("Permission denied. Download will proceed without showing download progress notifications.")
                    viewModel.onDownloadAction()
                }
            }
        }
    )
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = context.contentResolver.getFileName(it)
            if (fileName?.endsWith(".task") == true) {
                viewModel.loadModelFromUri(it)
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("Invalid file. Please select a .task model file.")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (showDeleteDialog) {
        // Confirmation dialog to prevent accidental model deletion.
        DeleteConfirmationDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.deleteModel()
                showDeleteDialog = false
            }
        )
    }

    if (showMeteredDialog) {
        // Confirmation dialog to warn the user before using mobile data for a large download.
        MeteredNetworkDialog(
            onDismiss = { viewModel.dismissMeteredDialog() },
            onConfirm = { viewModel.confirmMeteredDownload() }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AI Model Management") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ModelManagerCard(
                    modelStatus = modelStatus,
                    uiState = uiState,
                    onDownload = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val permission = Manifest.permission.POST_NOTIFICATIONS
                            val isPermissionGranted = ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (isPermissionGranted) {
                                viewModel.onDownloadAction()
                            } else {
                                postNotificationsPermissionLauncher.launch(permission)
                            }
                        } else {
                            viewModel.onDownloadAction()
                        }
                    },
                    onCancel = { viewModel.cancelDownload() },
                    onLoadFromFile = { filePickerLauncher.launch("application/octet-stream") },
                    onDelete = { showDeleteDialog = true }
                )
            }
            if (isLoadingFromStorage) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f))
                        .clickable(enabled = false, onClick = {}),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Text(
                            "Copying model to app storage...",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * A central, state-driven card that dynamically changes its content based on the
 * AI model's status (e.g., shows download buttons if not downloaded, progress if
 * downloading, and delete options if already downloaded). This simplifies the UI logic.
 */
@Composable
private fun ModelManagerCard(
    modifier: Modifier = Modifier,
    modelStatus: ModelStatus,
    uiState: AiSettingsUiState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onLoadFromFile: () -> Unit,
    onDelete: () -> Unit
) {
    val downloadState = uiState.downloadState
    val progress = uiState.downloadProgress
    val isDownloading = modelStatus == ModelStatus.DOWNLOADING ||
            downloadState == WorkInfo.State.ENQUEUED ||
            downloadState == WorkInfo.State.RUNNING

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "AptusTutor AI Model",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Gemma 3n-E2B (INT4 Quantized)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            // The core of the UI. This 'when' block determines which set of controls to display
            // based on the model's current state, creating a clear user flow.
            when {
                // STATE 1: DOWNLOADING
                isDownloading -> {
                    if (downloadState == WorkInfo.State.ENQUEUED) {
                        Text("Download Queued", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(
                                "Waiting for conditions (e.g., network)...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = if (progress > 0) "Downloading: $progress%" else "Connecting. Please wait...",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        if (progress > 0) {
                            val animatedProgress by animateFloatAsState(
                                targetValue = progress / 100f,
                                label = "downloadProgress"
                            )
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxWidth(),
                                strokeCap = StrokeCap.Round,
                                gapSize = 0.dp,
                                drawStopIndicator = {}
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Error, contentDescription = "Cancel")
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Cancel Download")
                    }
                }

                // STATE 2: DOWNLOADED
                modelStatus == ModelStatus.DOWNLOADED -> {
                    Icon(
                        Icons.Rounded.Verified,
                        contentDescription = "Model Ready",
                        tint = Color(0xFF34A853),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Model Ready", style = MaterialTheme.typography.titleMedium, color = Color(0xFF34A853))
                    Text("AI features are now enabled.", style = MaterialTheme.typography.bodySmall)

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Shareable AI", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            "Shareable AI: After downloading, the model file is also saved to your device's 'Downloads' folder. You can share this file with others to save them data! Once shared, they will simply click Load from Device Storage to load the model.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = "Delete")
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Delete Model from App")
                    }
                }

                // STATE 3: NOT DOWNLOADED (Initial State)
                else -> {
                    Icon(
                        Icons.Rounded.CloudDownload,
                        contentDescription = "Download Needed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Download Required", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Download the model (approx. 3.14 GB) to enable offline AI features. This is a one-time download",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(20.dp))

                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.RocketLaunch, contentDescription = null)
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Download from Cloud")
                    }
                    OrDivider()
                    OutlinedButton(onClick = onLoadFromFile, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Load from Device Storage")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = "Warning") },
        title = { Text("Delete Model?") },
        text = {
            Text(
                "This will remove the AI model from this app's internal storage, disabling AI features until it's loaded again.\n\n" +
                        "Note: If you downloaded from the cloud, a copy of the model will remain in your device's 'Downloads' folder for sharing."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun MeteredNetworkDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SignalCellularAlt, contentDescription = "Warning") },
        title = { Text("No Wi-Fi Connection") },
        text = {
            Text("You are not connected to Wi-Fi. Downloading the AI model (approx. 3.14 GB) will use your mobile data. Do you want to continue?")
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Use Mobile Data") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Wait for Wi-Fi") }
        }
    )
}