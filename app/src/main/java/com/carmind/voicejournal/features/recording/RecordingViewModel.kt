// features/recording/RecordingViewModel.kt
package com.carmind.voicejournal.features.recording

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmind.voicejournal.core.journal.JournalEntry
import com.carmind.voicejournal.core.journal.JournalRepository
import com.carmind.voicejournal.core.llm.LlmRouter
import com.carmind.voicejournal.core.stt.SttEvent
import com.carmind.voicejournal.core.stt.WhisperSttEngine
import com.carmind.voicejournal.core.stt.service.TranscriptionService
import com.carmind.voicejournal.core.settings.SettingsRepository
import com.carmind.voicejournal.core.summary.SummaryEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecordingStatus { IDLE, INITIALIZING, READY, RECORDING, REVIEWING, PROCESSING, SAVED, ERROR }

data class RecordingUiState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val recordingSeconds: Int = 0,
    val errorMessage: String? = null,
    val lastSaved: JournalEntry? = null,
)

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val stt: WhisperSttEngine,
    private val llm: LlmRouter,
    private val repo: JournalRepository,
    private val settings: SettingsRepository,
    private val summaryEngine: SummaryEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(RecordingUiState())
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()

    private val transcriptBuffer = StringBuilder()
    private var durationJob: kotlinx.coroutines.Job? = null
    private var currentAudioFile: java.io.File? = null

    init {
        viewModelScope.launch {
            // Watch for model path OR timestamp changes in settings
            combine(
                settings.whisperModelPath,
                settings.whisperUpdateTimestamp
            ) { path, ts -> path to ts }.collect {
                initializeStt()
            }
        }

        // Single collector for STT events
        viewModelScope.launch {
            stt.events.collect { event ->
                Log.d("RecordingViewModel", "Received STT Event: ${event.javaClass.simpleName}")
                when (event) {
                    is SttEvent.Partial -> _state.update {
                        it.copy(partialTranscript = event.text)
                    }
                    is SttEvent.Final -> {
                        Log.i("RecordingViewModel", "Final Transcript: '${event.text}'")
                        TranscriptionService.stop(stt.context)
                        transcriptBuffer.append("${event.text} ")
                        _state.update { it.copy(
                            status = RecordingStatus.REVIEWING,
                            finalTranscript = transcriptBuffer.toString().trim(),
                            partialTranscript = "",
                        )}
                    }
                    is SttEvent.Error -> {
                        Log.e("RecordingViewModel", "STT Error: ${event.message}")
                        TranscriptionService.stop(stt.context)
                        _state.update { it.copy(
                            status = RecordingStatus.ERROR,
                            errorMessage = event.message,
                        )}
                    }
                }
            }
        }
    }

    private fun initializeStt() {
        _state.update { it.copy(status = RecordingStatus.INITIALIZING, errorMessage = null) }
        viewModelScope.launch {
            try {
                stt.initialize()
                _state.update { it.copy(status = RecordingStatus.READY) }
            } catch (e: Throwable) {
                Log.e("RecordingViewModel", "STT init failed", e)
                val msg = when (e) {
                    is UnsatisfiedLinkError -> "Native library error. Is your phone 64-bit (arm64-v8a)?"
                    else -> e.message ?: "Unknown error"
                }
                _state.update { it.copy(
                    status = RecordingStatus.ERROR,
                    errorMessage = "STT init failed: $msg"
                )}
            }
        }
    }

    fun startRecording() {
        if (_state.value.status !in listOf(RecordingStatus.READY, RecordingStatus.SAVED, RecordingStatus.REVIEWING)) return

        currentAudioFile = stt.createAudioFile()
        
        transcriptBuffer.clear()
        _state.update { it.copy(
            status = RecordingStatus.RECORDING,
            partialTranscript = "",
            finalTranscript = "",
            recordingSeconds = 0,
            errorMessage = null,
        )}

        durationJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                _state.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
            }
        }

        stt.startListening(viewModelScope, currentAudioFile)
    }

    fun stopRecording() {
        if (_state.value.status != RecordingStatus.RECORDING) return

        durationJob?.cancel()
        _state.update { it.copy(status = RecordingStatus.PROCESSING) }
        TranscriptionService.start(stt.context)
        stt.stopListening()
    }

    fun startManualEntry() {
        if (_state.value.status !in listOf(RecordingStatus.READY, RecordingStatus.SAVED, RecordingStatus.REVIEWING)) return

        transcriptBuffer.clear()
        _state.update { it.copy(
            status = RecordingStatus.REVIEWING,
            partialTranscript = "",
            finalTranscript = "",
            recordingSeconds = 0,
            errorMessage = null,
        )}
    }

    fun setManualTranscript(text: String) {
        transcriptBuffer.clear()
        transcriptBuffer.append(text)
        _state.update { it.copy(finalTranscript = text) }
    }

    fun updateTranscript(newText: String) {
        transcriptBuffer.clear()
        transcriptBuffer.append(newText)
        _state.update { it.copy(finalTranscript = newText) }
    }

    fun analyzeTranscript() {
        if (_state.value.status != RecordingStatus.REVIEWING) return

        val transcript = _state.value.finalTranscript.trim()
        val duration = _state.value.recordingSeconds.toFloat()

        if (transcript.length < 5) {
            _state.update { it.copy(status = RecordingStatus.READY, errorMessage = "Too short — try again") }
            return
        }

        _state.update { it.copy(status = RecordingStatus.PROCESSING) }

        viewModelScope.launch {
            try {
                val analysis = llm.analyzeEntry(transcript)
                val entry = JournalEntry(
                    rawTranscript = transcript,
                    title = analysis.title,
                    summary = analysis.summary,
                    category = analysis.category,
                    mood = analysis.mood,
                    tags = analysis.tags,
                    actionItems = analysis.actionItems,
                    keyTopics = analysis.keyTopics,
                    dumpSnippets = analysis.dumpSnippets,
                    durationSeconds = duration,
                    audioPath = currentAudioFile?.absolutePath
                )
                repo.save(entry)
                _state.update { it.copy(
                    status = RecordingStatus.SAVED,
                    lastSaved = entry,
                )}
                
                // Trigger memory and summaries updates in background
                viewModelScope.launch {
                    try {
                        updateUserMemory(entry)
                    } catch (e: Exception) {
                        Log.e("RecordingViewModel", "Failed to update user memory", e)
                    }
                    try {
                        summaryEngine.updateAllSummaries()
                    } catch (e: Exception) {
                        Log.e("RecordingViewModel", "Failed to update summaries", e)
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(
                    status = RecordingStatus.ERROR,
                    errorMessage = e.message,
                )}
            }
        }
    }

    private suspend fun updateUserMemory(entry: JournalEntry) {
        val currentMemory = repo.getMemory()?.memoryText ?: "No profile created yet."
        val prompt = """
            You are a unified memory manager. Update the user's running profile with information from the new journal entry.
            Keep track of key projects, recurring topics, ongoing goals, names of family/colleagues, and active habits.
            Do not lose existing information unless it has changed or is resolved.
            Keep the updated profile concise (under 250 words) and structured.
            
            CURRENT USER PROFILE:
            $currentMemory
            
            NEW JOURNAL ENTRY:
            Category: ${entry.category}
            Title: ${entry.title}
            Summary: ${entry.summary}
            Topics: ${entry.keyTopics.joinToString()}
            Action Items: ${entry.actionItems.joinToString()}
            Transcript: "${entry.rawTranscript}"
            
            OUTPUT: Return ONLY the updated plain text user profile.
        """.trimIndent()
        
        val updatedText = llm.complete(prompt, "You are a master of personal context synthesis.")
        if (updatedText.isNotBlank() && !updatedText.contains("Error") && !updatedText.contains("Mock")) {
            repo.saveMemory(com.carmind.voicejournal.core.journal.UserProfileMemory(memoryText = updatedText.trim()))
            Log.d("RecordingViewModel", "Unified user memory updated successfully.")
        }
    }

    fun cancelRecording() {
        durationJob?.cancel()
        TranscriptionService.stop(stt.context)
        stt.stopListening()
        
        // Cleanup the unused audio file if it exists
        currentAudioFile?.let { 
            if (it.exists()) {
                Log.d("RecordingViewModel", "Deleting discarded audio file: ${it.absolutePath}")
                it.delete()
            }
        }
        currentAudioFile = null

        _state.update { RecordingUiState(status = RecordingStatus.READY) }
    }

    fun resetToReady() {
        _state.update { it.copy(
            status = RecordingStatus.READY,
            errorMessage = null,
            lastSaved = null,
        )}
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            stt.release()
        }
    }
}
