package com.nexttechtitan.aptustutor.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexttechtitan.aptustutor.data.ModelStatus
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel()
) {
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle(initialValue = ModelStatus.NOT_DOWNLOADED)
    val modelPath by viewModel.modelPath.collectAsStateWithLifecycle(initialValue = null)

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
                    Text("Model Status", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))

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
                            Text("Model Installed Successfully!", color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Location: ${modelPath?.substringAfterLast('/')}",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
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