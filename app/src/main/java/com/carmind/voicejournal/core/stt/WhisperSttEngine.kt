package com.carmind.voicejournal.core.stt

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import com.carmind.voicejournal.core.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import com.whispercpp.whisper.WhisperContext

@Singleton
class WhisperSttEngine @Inject constructor(
    @ApplicationContext val context: Context,
    private val settings: SettingsRepository,
) {
    companion object {
        private const val TAG = "WhisperSTT"
        private const val SAMPLE_RATE = 16000
        private const val DEFAULT_MODEL_ASSET = "models/whisper/tiny.en.bin"
    }

    private var whisperContext: WhisperContext? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var currentModelPath: String? = null
    private var currentModelTimestamp: String? = null

    private val _events = MutableSharedFlow<SttEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SttEvent> = _events.asSharedFlow()

    var isInitialized = false
        private set

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting Whisper initialization...")
            val userPath = settings.whisperModelPath.first()
            val updateTs = settings.whisperUpdateTimestamp.first()
            
            Log.d(TAG, "Current user model path: '$userPath', TS: $updateTs")

            val targetPath = if (!userPath.isNullOrBlank()) {
                val userFile = File(userPath)
                if (userFile.exists() && userFile.length() > 1_000_000) {
                    userPath
                } else {
                    Log.w(TAG, "Custom model path '$userPath' not found or too small, falling back to default.")
                    // Clear the invalid path from settings
                    settings.saveWhisperModelPath("", "Default (Tiny)")
                    null
                }
            } else {
                null
            }

            val finalPath = if (targetPath != null) {
                targetPath
            } else {
                val internalFile = File(context.filesDir, "whisper_model.bin")
                if (!internalFile.exists()) {
                    Log.i(TAG, "Copying default Whisper model from assets...")
                    context.assets.open(DEFAULT_MODEL_ASSET).use { input ->
                        internalFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                internalFile.absolutePath
            }

            if (isInitialized && currentModelPath == finalPath && currentModelTimestamp == updateTs) {
                Log.d(TAG, "Already initialized with $finalPath (up to date)")
                return@withContext
            }

            val modelFile = File(finalPath)
            if (!modelFile.exists()) {
                throw IllegalStateException("Whisper model file not found at $finalPath")
            }
            if (modelFile.length() == 0L) {
                throw IllegalStateException("Whisper model file is empty at $finalPath")
            }

            // If we are already initialized, release the old one
            if (whisperContext != null) {
                Log.i(TAG, "Re-initializing Whisper: releasing old context")
                whisperContext?.release()
                whisperContext = null
            }

            Log.i(TAG, "Creating Whisper context from $finalPath (Size: ${modelFile.length()})")
            
            if (modelFile.length() < 1_000_000) {
                 if (modelFile.length() > 0 && modelFile.absolutePath.contains(context.filesDir.absolutePath)) {
                     // If it's a corrupted download in our filesDir, delete it so we can try again
                     Log.w(TAG, "Deleting corrupted model file: ${modelFile.absolutePath}")
                     modelFile.delete()
                 }
                 throw IllegalStateException("The Whisper model file is too small or corrupted.")
            }

            whisperContext = WhisperContext.createContextFromFile(finalPath)
            currentModelPath = finalPath
            currentModelTimestamp = updateTs
            isInitialized = true
            Log.i(TAG, "Whisper initialized successfully")
        } catch (e: Throwable) {
            Log.e(TAG, "Whisper init failed", e)
            isInitialized = false
            currentModelPath = null
            currentModelTimestamp = null
            throw e
        }
    }

    fun startListening(scope: CoroutineScope, audioFile: File? = null) {
        if (!isInitialized) {
            _events.tryEmit(SttEvent.Error("Call initialize() first"))
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE * 2)

        // Try VOICE_RECOGNITION first, fallback to MIC if it fails or produces silence
        var source = MediaRecorder.AudioSource.VOICE_RECOGNITION
        
        audioRecord = AudioRecord(
            source,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "VOICE_RECOGNITION failed, trying MIC source")
            source = MediaRecorder.AudioSource.MIC
            audioRecord = AudioRecord(
                source,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed on all sources")
            _events.tryEmit(SttEvent.Error("Microphone initialization failed. Please check permissions."))
            return
        }

        audioRecord?.startRecording()
        
        if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            Log.e(TAG, "AudioRecord failed to start recording")
            _events.tryEmit(SttEvent.Error("Could not start recording"))
            return
        }

        _events.tryEmit(SttEvent.Partial("Listening..."))
        
        recordingJob = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize / 2)
            val fos = audioFile?.let { FileOutputStream(it) }
            
            try {
                if (fos != null) writeWavHeader(fos, 0)
                var totalAudioLen = 0L
                var silenceCount = 0

                while (isActive) {
                    val currentRecord = audioRecord
                    if (currentRecord == null) break
                    
                    val read = currentRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var maxAmp = 0
                        val bytes = ByteArray(read * 2)
                        for (i in 0 until read) {
                            val sample = buffer[i].toInt()
                            val absSample = if (sample < 0) -sample else sample
                            if (absSample > maxAmp) maxAmp = absSample
                            
                            bytes[i * 2] = (sample and 0xff).toByte()
                            bytes[i * 2 + 1] = (sample shr 8).toByte()
                        }
                        
                        if (maxAmp < 10) {
                            silenceCount++
                            if (silenceCount % 100 == 0) Log.w(TAG, "Warning: Recording very low amplitude ($maxAmp)")
                        } else {
                            silenceCount = 0
                        }

                        fos?.write(bytes)
                        totalAudioLen += bytes.size
                        
                        if (totalAudioLen % (SAMPLE_RATE * 2) == 0L) {
                            _events.emit(SttEvent.Partial("Listening... (${totalAudioLen / (SAMPLE_RATE * 2)}s)"))
                        }
                    } else if (read < 0) {
                        Log.e(TAG, "AudioRecord read error: $read")
                        break
                    } else {
                        if (currentRecord.recordingState == AudioRecord.RECORDSTATE_STOPPED) break
                        yield()
                    }
                }

                if (audioFile != null && totalAudioLen > 0) {
                    fos?.flush()
                    fos?.close()
                    updateWavHeader(audioFile, totalAudioLen)
                    
                    Log.d(TAG, "Recording finished. Size: ${audioFile.length()}. Starting segmented transcription...")
                    transcribeFile(file = audioFile)
                } else {
                    fos?.close()
                    _events.emit(SttEvent.Error("No audio was recorded"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in recording loop", e)
                fos?.close()
            } finally {
                recordingJob = null
            }
        }
    }

    private suspend fun transcribeFile(file: File) = withContext(Dispatchers.Default) {
        try {
            Log.i(TAG, "Starting fast full file transcription for ${file.absolutePath}")
            _events.emit(SttEvent.Partial("Preparing AI engine..."))

            val fileSize = file.length()
            if (fileSize <= 44) {
                _events.emit(SttEvent.Error("Audio file is empty"))
                return@withContext
            }

            val totalSamples = ((fileSize - 44) / 2).toInt()
            if (totalSamples <= 0) {
                _events.emit(SttEvent.Error("Audio file is empty"))
                return@withContext
            }

            Log.d(TAG, "Reading $totalSamples samples from WAV file...")
            _events.emit(SttEvent.Partial("Transcribing audio..."))

            val byteBuffer = ByteArray(totalSamples * 2)
            val raf = java.io.RandomAccessFile(file, "r")
            try {
                raf.seek(44L)
                raf.readFully(byteBuffer)
            } finally {
                raf.close()
            }

            // Convert to FloatArray (Mono 16-bit PCM to Float normalized between -1.0f and 1.0f)
            val floatData = FloatArray(totalSamples)
            for (j in 0 until totalSamples) {
                val b1 = byteBuffer[j * 2].toInt() and 0xFF
                val b2 = byteBuffer[j * 2 + 1].toInt() shl 8
                floatData[j] = (b1 or b2).toShort() / 32768.0f
            }

            // Process full audio with a generous timeout
            val result = try {
                withTimeoutOrNull(300000) {
                    whisperContext?.transcribeData(floatData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in transcription JNI call", e)
                null
            }

            val finalResult = result?.trim() ?: ""
            if (finalResult.isNotEmpty()) {
                _events.emit(SttEvent.Final(finalResult))
            } else {
                _events.emit(SttEvent.Error("AI could not understand the audio. Please speak louder or check your mic."))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Transcription fatal error", e)
            _events.emit(SttEvent.Error("Transcription failed: ${e.localizedMessage}"))
        }
    }

    fun stopListening() {
        // Don't cancel the job immediately, let it finish processing the remaining audio
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        // We can cancel the job after a short delay if it hasn't finished, 
        // but it's better to let the loop exit naturally when read() returns <= 0
    }

    private fun writeWavHeader(out: FileOutputStream, totalAudioLen: Long) {
        val totalDataLen = totalAudioLen + 36
        val sampleRate = SAMPLE_RATE.toLong()
        val channels = 1
        val byteRate = 16 * SAMPLE_RATE * channels / 8

        val header = ByteArray(44)
        header[0] = 'R'.toByte()
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        header[12] = 'f'.toByte()
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (1 * 16 / 8).toByte()
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        out.write(header, 0, 44)
    }

    private fun updateWavHeader(file: File, totalAudioLen: Long) {
        val totalDataLen = totalAudioLen + 36
        val raf = java.io.RandomAccessFile(file, "rw")
        raf.seek(4)
        raf.write(
            byteArrayOf(
                (totalDataLen and 0xff).toByte(),
                (totalDataLen shr 8 and 0xff).toByte(),
                (totalDataLen shr 16 and 0xff).toByte(),
                (totalDataLen shr 24 and 0xff).toByte()
            )
        )
        raf.seek(40)
        raf.write(
            byteArrayOf(
                (totalAudioLen and 0xff).toByte(),
                (totalAudioLen shr 8 and 0xff).toByte(),
                (totalAudioLen shr 16 and 0xff).toByte(),
                (totalAudioLen shr 24 and 0xff).toByte()
            )
        )
        raf.close()
    }

    suspend fun release() {
        stopListening()
        whisperContext?.release()
        whisperContext = null
        isInitialized = false
    }

    fun createAudioFile(): File {
        val dir = File(context.filesDir, "recordings")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "whisper_entry_${System.currentTimeMillis()}.wav")
    }
}
