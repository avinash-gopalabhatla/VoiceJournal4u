package com.carmind.voicejournal.features.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmind.voicejournal.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val whisperPath: String? = null,
    val whisperName: String? = null,
    val llmPath: String? = null,
    val llmName: String? = null,
    val isProcessing: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _localStatus = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    val state: StateFlow<SettingsUiState> = combine(
        repository.whisperModelPath,
        repository.whisperModelName,
        repository.llmModelPath,
        repository.llmModelName,
        _localStatus
    ) { whisperPath, whisperName, llmPath, llmName, status ->
        SettingsUiState(
            whisperPath = whisperPath,
            whisperName = whisperName,
            llmPath = llmPath,
            llmName = llmName,
            statusMessage = status.first,
            isError = status.second,
            isProcessing = status.first != null && !status.second && !status.first!!.contains("Success") && !status.first!!.contains("Reset")
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun importWhisperModel(uri: Uri) {
        viewModelScope.launch {
            val fileName = getFileName(uri)
            _localStatus.value = "Importing $fileName..." to false
            
            val path = copyAndVerify(uri, "whisper_custom.bin", isWhisper = true)
            if (path != null) {
                repository.saveWhisperModelPath(path, fileName)
                _localStatus.value = "Successfully imported $fileName" to false
            } else {
                _localStatus.value = "Failed to import. Invalid Whisper model file." to true
            }
            kotlinx.coroutines.delay(3000)
            _localStatus.value = null to false
        }
    }

    fun importLlmModel(uri: Uri) {
        viewModelScope.launch {
            val fileName = getFileName(uri)
            _localStatus.value = "Importing $fileName..." to false
            
            val path = copyAndVerify(uri, "llm_custom.bin", isWhisper = false)
            if (path != null) {
                repository.saveLlmModelPath(path, fileName)
                _localStatus.value = "Successfully imported $fileName" to false
            } else {
                _localStatus.value = "Failed to import. Invalid LLM model file." to true
            }
            kotlinx.coroutines.delay(3000)
            _localStatus.value = null to false
        }
    }

    private suspend fun copyAndVerify(uri: Uri, destName: String, isWhisper: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            val destFile = File(context.filesDir, destName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (!destFile.exists() || destFile.length() < 1_000_000) return@withContext null

            // Magic Byte Verification
            val isValid = destFile.inputStream().use { input ->
                val header = ByteArray(4)
                val bytesRead = input.read(header)
                if (bytesRead < 4) return@use false
                
                val magicHex = header.joinToString("") { "%02x".format(it) }
                Log.d("SettingsViewModel", "Magic bytes for verification: $magicHex")
                
                if (isWhisper) {
                    // Check common Whisper GGML headers
                    // 67676d6c = "ggml", 67676a74 = "ggjt", 67676c61 = "ggla", 47475546 = "GGUF"
                    val isSignatureMatch = magicHex == "67676d6c" || magicHex == "67676a74" || magicHex == "67676c61" || magicHex == "47475546"
                    // If signature doesn't match, let's at least trust the user if the size is significant (> 10MB)
                    isSignatureMatch || destFile.length() > 10_000_000
                } else {
                    // LLM: Accept large files as LLM models if they aren't Whisper files.
                    destFile.length() > 50_000_000 && magicHex != "67676d6c"
                }
            }

            if (isValid) destFile.absolutePath else {
                destFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Import failed", e)
            null
        }
    }

    private fun getFileName(uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) name = cursor.getString(index)
                }
            }
        }
        return name ?: uri.path?.substringAfterLast('/') ?: "custom_model.bin"
    }

    fun clearWhisperModel() {
        viewModelScope.launch {
            repository.saveWhisperModelPath("", "Default (Tiny)")
            _localStatus.value = "Reset to default Whisper model" to false
            kotlinx.coroutines.delay(2000)
            _localStatus.value = null to false
        }
    }

    fun clearLlmModel() {
        viewModelScope.launch {
            repository.saveLlmModelPath("", "Default (Gemma)")
            _localStatus.value = "Reset to default LLM model" to false
            kotlinx.coroutines.delay(2000)
            _localStatus.value = null to false
        }
    }
}
