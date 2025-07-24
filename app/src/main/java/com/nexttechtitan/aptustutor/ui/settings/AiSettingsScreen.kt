package com.nexttechtitan.aptustutor.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexttechtitan.aptustutor.ai.GemmaAiService
import com.nexttechtitan.aptustutor.data.ModelStatus
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel()
) {
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle(initialValue = ModelStatus.NOT_DOWNLOADED)
    val modelInitialized by viewModel.modelInitialized.collectAsStateWithLifecycle(initialValue = false)
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

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

    LaunchedEffect(modelStatus) {
        if (modelStatus == ModelStatus.DOWNLOADED) {
            viewModel.ensureModelLoaded()
        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (modelStatus) {
                        ModelStatus.NOT_DOWNLOADED -> {
                            Text("The Gemma 3n model is not installed.", color = MaterialTheme.colorScheme.error)
                            Text(
                                "Download it from the cloud or load it from your device storage to enable AI features.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                        ModelStatus.DOWNLOADING -> {
                            Text("Downloading model...", color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            CircularProgressIndicator()
                            Text(
                                "Download will proceed in the background on Wi-Fi.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        ModelStatus.DOWNLOADED -> {
                            if (!modelInitialized) {
                                Spacer(Modifier.height(16.dp))
                                InitializationCard(
                                    modelState = modelState,
                                    onInitialize = viewModel::runAiInitialization
                                )
                            } else {
                                ReadyCardContent()
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "For Development/Testing",
                style = MaterialTheme.typography.labelSmall
            )
            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Load Model From Device")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                "For Production Use",
                style = MaterialTheme.typography.labelSmall
            )
            Button(
                onClick = { viewModel.startCloudDownload() },
                modifier = Modifier.fillMaxWidth(),
                enabled = false
                //enabled = modelStatus != ModelStatus.DOWNLOADING
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Download From Cloud (Wi-Fi)")
            }
            Text(
                "Cloud download functionality is under development.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InitializationCard(
    modelState: GemmaAiService.ModelState,
    onInitialize: () -> Unit
) {
    val isWorking = modelState is GemmaAiService.ModelState.LoadingModel ||
            modelState is GemmaAiService.ModelState.WarmingUp ||
            modelState is GemmaAiService.ModelState.Busy

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (modelState) {
                is GemmaAiService.ModelState.ModelReadyCold -> {
                    Icon(
                        Icons.Rounded.RocketLaunch,
                        contentDescription = "Initializing",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Final Step: Initialize AI Engine", style = MaterialTheme.typography.headlineSmall)
                    Text("Run a one-time process to make AI grading significantly faster. This may take several minutes. Please keep the app open.", textAlign = TextAlign.Center)
                    Button(onClick = onInitialize, modifier = Modifier.fillMaxWidth()) {
                        Text("Start Initialization")
                    }
                }
                is GemmaAiService.ModelState.LoadingModel -> {
                    CircularProgressIndicator()
                    Text("Loading AI Model...", style = MaterialTheme.typography.titleMedium)
                }
                is GemmaAiService.ModelState.WarmingUp -> {
                    CircularProgressIndicator()
                    Text("Warming Up: ${modelState.step}", style = MaterialTheme.typography.titleMedium)
                    Text("This is the most crucial step and will take some time. Please be patient.", textAlign = TextAlign.Center)
                }
                is GemmaAiService.ModelState.Ready -> {
                    ReadyCardContent()
                }
                is GemmaAiService.ModelState.Failed -> {
                    Icon(Icons.Rounded.Error,
                        contentDescription = "Error",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Initialization Failed", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                    Text(modelState.error.message?: "An unknown error occurred.", textAlign = TextAlign.Center)
                }
                else -> {
                    CircularProgressIndicator()
                    Text("Please wait...", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ReadyCardContent() {
    Icon(
        Icons.Rounded.Verified,
        contentDescription = "Ready",
        modifier = Modifier.size(48.dp),
        tint = Color(0xFF34A853) // Google Green
    )
    Text("AI Engine is Ready!", style = MaterialTheme.typography.headlineSmall)
    Text(
        "AptusTutor is now optimized for the fastest possible on-device grading.",
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium
    )
}