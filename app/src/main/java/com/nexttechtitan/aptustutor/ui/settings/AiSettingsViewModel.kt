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
import com.nexttechtitan.aptustutor.ai.GemmaAiService
import com.nexttechtitan.aptustutor.ai.ModelDownloadWorker
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

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepo: UserPreferencesRepository,
    private val workManager: WorkManager,
    private val gemmaAiService: GemmaAiService,
    private val networkUtils: NetworkUtils
) : ViewModel() {

    val modelStatus = userPreferencesRepo.aiModelStatusFlow
    val gemmaModelState = gemmaAiService.modelState

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    private val _isLoadingFromStorage = MutableStateFlow(false)
    val isLoadingFromStorage = _isLoadingFromStorage.asStateFlow()

    private val _showMeteredNetworkDialog = MutableStateFlow(false)
    val showMeteredNetworkDialog = _showMeteredNetworkDialog.asStateFlow()


    val downloadWorkInfo: StateFlow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkLiveData(ModelDownloadWorker.WORK_NAME)
            .asFlow()
            .map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun onDownloadAction() {
        viewModelScope.launch {
            if (networkUtils.isWifiConnected()) {
                // If on Wi-Fi, start immediately.
                startDownload(useMeteredNetwork = false)
                _toastEvents.emit("Download scheduled. Will begin on Wi-Fi.")
            } else {
                // If not on Wi-Fi, show the confirmation dialog.
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
            _toastEvents.emit("Download cancelled.")
        }
    }

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

    fun deleteModel() {
        viewModelScope.launch {
            val modelPath = userPreferencesRepo.aiModelPathFlow.first()
            if (modelPath!= null) {
                val file = File(modelPath)
                if (file.exists()) {
                    file.delete()
                }
            }
            // Reset preferences and release the model from memory
            userPreferencesRepo.setAiModel(ModelStatus.NOT_DOWNLOADED)
            _toastEvents.emit("Model removed from app storage.")
        }
    }
}

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