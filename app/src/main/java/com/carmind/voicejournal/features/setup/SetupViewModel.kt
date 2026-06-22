package com.carmind.voicejournal.features.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmind.voicejournal.core.setup.DownloadState
import com.carmind.voicejournal.core.setup.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val modelManager: ModelManager
) : ViewModel() {

    val downloadState: StateFlow<DownloadState> = modelManager.downloadState

    fun startDownload() {
        viewModelScope.launch {
            modelManager.downloadModels()
        }
    }
}
