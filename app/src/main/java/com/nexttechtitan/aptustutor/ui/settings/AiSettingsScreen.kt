package com.nexttechtitan.aptustutor.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Downloading
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.nexttechtitan.aptustutor.ai.GemmaAiService
import com.nexttechtitan.aptustutor.ai.ModelDownloadWorker
import com.nexttechtitan.aptustutor.data.ModelStatus
import com.nexttechtitan.aptustutor.ui.student.OrDivider
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel()
) {
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle(initialValue = ModelStatus.NOT_DOWNLOADED)
    val gemmaModelState by viewModel.gemmaModelState.collectAsStateWithLifecycle()
    val downloadWorkInfo by viewModel.downloadWorkInfo.collectAsStateWithLifecycle()
    val showMeteredDialog by viewModel.showMeteredNetworkDialog.collectAsStateWithLifecycle()
    val isLoadingFromStorage by viewModel.isLoadingFromStorage.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.loadModelFromUri(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.deleteModel()
                showDeleteDialog = false
            }
        )
    }

    if (showMeteredDialog) {
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
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                ModelStatusCard(
                    modelStatus = modelStatus,
                    gemmaModelState = gemmaModelState,
                    workInfo = downloadWorkInfo
                )

                GetModelCard(
                    modelStatus = modelStatus,
                    onDownload = { viewModel.onDownloadAction() },
                    onLoadFromFile = { filePickerLauncher.launch("*/*") }
                )

                ManageModelCard(
                    modelStatus = modelStatus,
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

@Composable
private fun ModelStatusCard(
    modelStatus: ModelStatus,
    gemmaModelState: GemmaAiService.ModelState,
    workInfo: WorkInfo?
) {
    val progress = workInfo?.progress?.getInt(ModelDownloadWorker.PROGRESS, 0)?: 0
    val animatedProgress by animateFloatAsState(targetValue = progress / 100f, label = "downloadProgress")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val (icon, text, color) = when (modelStatus) {
                ModelStatus.NOT_DOWNLOADED -> Triple(Icons.Rounded.Error, "Model Not Installed", MaterialTheme.colorScheme.error)
                ModelStatus.DOWNLOADING -> Triple(Icons.Rounded.Downloading, "Downloading Model...", MaterialTheme.colorScheme.primary)
                ModelStatus.DOWNLOADED -> Triple(Icons.Rounded.CheckCircle, "Model Ready for Use", Color(0xFF34A853))
            }

            Icon(imageVector = icon, contentDescription = text, tint = color, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.titleLarge, color = color)

            if (modelStatus == ModelStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("$progress%", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun GetModelCard(
    modelStatus: ModelStatus,
    onDownload: () -> Unit,
    onLoadFromFile: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Get Model", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            Button(
                onClick = onDownload,
                enabled = modelStatus == ModelStatus.NOT_DOWNLOADED,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Download from Cloud")
            }
            Text(
                "Download is managed by your device and will continue in the background, even if you close the app. You will see progress in your system notifications.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onLoadFromFile,
                enabled = modelStatus == ModelStatus.NOT_DOWNLOADED,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Load from Device Storage")
            }
        }
    }
}

@Composable
private fun ManageModelCard(
    modelStatus: ModelStatus,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Manage Model", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            // Shareable AI Info Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = "Shareable AI", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    "Shareable AI: After downloading, the model file is also saved to your device's 'Downloads' folder. You can share this file with other tutors to save them data!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Button(
                onClick = onDelete,
                enabled = modelStatus == ModelStatus.DOWNLOADED,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Delete Model from App")
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

// Add this new composable at the bottom of AiSettingsScreen.kt

@Composable
private fun ModelManagerCard(
    modifier: Modifier = Modifier,
    modelStatus: ModelStatus,
    workInfo: WorkInfo?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onLoadFromFile: () -> Unit,
    onDelete: () -> Unit
) {
    val downloadState = workInfo?.state
    val isDownloading = modelStatus == ModelStatus.DOWNLOADING ||
            downloadState == WorkInfo.State.ENQUEUED ||
            downloadState == WorkInfo.State.RUNNING

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Title ---
            Text(
                "AptusTutor AI Model",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                "Gemma 3N-E2B (INT4 Quantized)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            // --- State-Driven Content ---
            when {
                // STATE 1: DOWNLOADING
                isDownloading -> {
                    val progress = workInfo?.progress?.getInt(ModelDownloadWorker.PROGRESS, 0) ?: 0
                    val animatedProgress by animateFloatAsState(targetValue = progress / 100f, label = "downloadProgress")

                    Text("Downloading Model...", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text("$progress%", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
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
                        "Download the model (approx. 3.14 GB) to enable offline AI features.",
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