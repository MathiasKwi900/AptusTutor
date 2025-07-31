package com.nexttechtitan.aptustutor.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.nexttechtitan.aptustutor.ai.ModelDownloadWorker
import com.nexttechtitan.aptustutor.data.AiSettingsUiState
import com.nexttechtitan.aptustutor.data.ModelStatus
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import com.nexttechtitan.aptustutor.utils.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * ViewModel for the AI Settings screen.
 * Manages the lifecycle of the on-device AI model, including:
 * - Initiating downloads via WorkManager.
 * - Observing download progress.
 * - Loading a model from local device storage.
 * - Deleting the model.
 */
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepo: UserPreferencesRepository,
    private val workManager: WorkManager,
    private val networkUtils: NetworkUtils
) : ViewModel() {

    val modelStatus = userPreferencesRepo.aiModelStatusFlow

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    private val _uiState = MutableStateFlow(AiSettingsUiState())
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()


    private val _isLoadingFromStorage = MutableStateFlow(false)
    val isLoadingFromStorage = _isLoadingFromStorage.asStateFlow()

    private val _showMeteredNetworkDialog = MutableStateFlow(false)
    val showMeteredNetworkDialog = _showMeteredNetworkDialog.asStateFlow()

    /**
     * A flow that observes the state of the [ModelDownloadWorker] from WorkManager.
     * This allows the UI to reactively display download progress and status.
     */
    private val downloadWorkInfo: StateFlow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkLiveData(ModelDownloadWorker.WORK_NAME)
            .asFlow()
            .map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            downloadWorkInfo.collect { workInfo ->
                val progress = workInfo?.progress?.getInt(ModelDownloadWorker.PROGRESS, 0) ?: 0
                val state = workInfo?.state
                _uiState.value = AiSettingsUiState(
                    downloadState = state,
                    downloadProgress = progress
                )
            }
        }
    }

    /**
     * Handles the user's "Download" action. It checks the network state and either
     * starts the download (on Wi-Fi) or prompts the user for confirmation (on mobile data).
     */
    fun onDownloadAction() {
        viewModelScope.launch {
            if (networkUtils.isWifiConnected()) {
                startDownload(useMeteredNetwork = false)
                _toastEvents.emit("Download scheduled. Will begin on Wi-Fi.")
            } else {
                _showMeteredNetworkDialog.value = true
            }
        }
    }

    fun confirmMeteredDownload() {
        viewModelScope.launch {
            _showMeteredNetworkDialog.value = false
            startDownload(useMeteredNetwork = true)
            _toastEvents.emit("Download scheduled. Will use mobile data.")
        }
    }

    fun dismissMeteredDialog() {
        _showMeteredNetworkDialog.value = false
    }

    /**
     * Configures and enqueues the background download task using WorkManager, applying
     * the appropriate network constraints based on user choice.
     */
    private fun startDownload(useMeteredNetwork: Boolean) {
        val networkType = if (useMeteredNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            ModelDownloadWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            downloadRequest
        )
    }

    fun cancelDownload() {
        viewModelScope.launch {
            workManager.cancelUniqueWork(ModelDownloadWorker.WORK_NAME)
            userPreferencesRepo.setAiModel(ModelStatus.NOT_DOWNLOADED)
            _toastEvents.emit("Download cancelled.")
        }
    }

    /**
     * Loads a model from a user-selected URI. This function performs the I/O-intensive
     * task of copying the file to the app's private directory and then updates the
     * app's preferences to reflect the new model state.
     */
    fun loadModelFromUri(uri: Uri) {
        viewModelScope.launch {
            val fileName = context.contentResolver.getFileName(uri)
            if (fileName?.endsWith(".task")!= true) {
                _toastEvents.emit("Invalid file. Please select a.task model file.")
                return@launch
            }

            _isLoadingFromStorage.value = true
            try {
                withContext(Dispatchers.IO) {
                    val destinationFile = File(context.filesDir, "gemma-3n-e2b-it-int4.task")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destinationFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    userPreferencesRepo.setAiModel(ModelStatus.DOWNLOADED, destinationFile.absolutePath)
                }
                _toastEvents.emit("Model loaded successfully!")
            } catch (e: Exception) {
                _toastEvents.emit("Error: Could not load model from file.")
            } finally {
                _isLoadingFromStorage.value = false
            }
        }
    }

    /**
     * Deletes the AI model from internal storage and resets all related user preferences,
     * effectively returning the app to a pre-download state.
     */
    fun deleteModel() {
        viewModelScope.launch {
            val modelPath = userPreferencesRepo.aiModelPathFlow.first()
            if (modelPath!= null) {
                val file = File(modelPath)
                if (file.exists()) {
                    file.delete()
                }
            }
            userPreferencesRepo.setAiModel(ModelStatus.NOT_DOWNLOADED)
            _toastEvents.emit("Model removed from app storage.")
        }
    }
}

/**
 * An extension function to safely retrieve a display name from a content URI,
 * which is not directly available from the URI object itself.
 */
fun ContentResolver.getFileName(uri: Uri): String? {
    var name: String? = null
    val cursor = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            name = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
    }
    return name
}