package com.nexttechtitan.aptustutor.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nexttechtitan.aptustutor.ai.ModelDownloadWorker
import com.nexttechtitan.aptustutor.data.ModelStatus
import com.nexttechtitan.aptustutor.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepo: UserPreferencesRepository,
    private val workManager: WorkManager
) : ViewModel() {

    val modelStatus = userPreferencesRepo.aiModelStatusFlow
    val modelPath = userPreferencesRepo.aiModelPathFlow

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    fun startCloudDownload() {
        viewModelScope.launch {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi only
                .build()

            val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniqueWork(
                ModelDownloadWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP, // Don't start a new one if it's already running
                downloadRequest
            )
            _toastEvents.emit("Model download scheduled. Will begin on Wi-Fi.")
        }
    }

    fun loadModelFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val fileName = context.contentResolver.getFileName(uri)
                if (fileName?.endsWith(".task") != true) {
                    _toastEvents.emit("Invalid file. Please select a .task model file.")
                    return@launch
                }
                val destinationFile = File(context.filesDir, "gemma-model.task")
                val inputStream = context.contentResolver.openInputStream(uri)
                val outputStream = FileOutputStream(destinationFile)

                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }

                userPreferencesRepo.setAiModel(ModelStatus.DOWNLOADED, destinationFile.absolutePath)
                _toastEvents.emit("Model loaded successfully!")
            } catch (e: Exception) {
                _toastEvents.emit("Error: Could not load model from file.")
            }
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