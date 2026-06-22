package com.whispercpp.whisper

import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

private const val LOG_TAG = "LibWhisper"

class WhisperContext private constructor(private var ptr: Long) {
    // Meet Whisper C++ constraint: Don't access from more than one thread at a time.
    private val scope: CoroutineScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    suspend fun transcribeData(data: FloatArray, printTimestamp: Boolean = false): String =
        withContext(scope.coroutineContext) {
            if (ptr == 0L) {
                Log.e(LOG_TAG, "Context pointer is null")
                return@withContext ""
            }
            val numThreads = WhisperCpuConfig.preferredThreadCount
            Log.d(LOG_TAG, "Transcribing ${data.size} samples with $numThreads threads. Model ptr: $ptr")
            WhisperLib.fullTranscribe(ptr, numThreads, data)
            val textCount = WhisperLib.getTextSegmentCount(ptr)
            Log.d(LOG_TAG, "Transcribed $textCount segments")
            return@withContext buildString {
                for (i in 0 until textCount) {
                    if (printTimestamp) {
                        val textTimestamp =
                            "[${toTimestamp(WhisperLib.getTextSegmentT0(ptr, i))} --> ${
                                toTimestamp(WhisperLib.getTextSegmentT1(ptr, i))
                            }]"
                        val textSegment = WhisperLib.getTextSegment(ptr, i)
                        append("$textTimestamp: $textSegment\n")
                    } else {
                        append(WhisperLib.getTextSegment(ptr, i))
                    }
                }
            }
        }

    suspend fun transcribeFile(path: String): String = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext "File not found"
        
        // Skip WAV header (44 bytes) and read PCM data
        val bytes = file.readBytes()
        if (bytes.size <= 44) return@withContext "Empty audio file"
        
        val pcmData = ShortArray((bytes.size - 44) / 2)
        for (i in pcmData.indices) {
            val b1 = bytes[44 + i * 2].toInt() and 0xFF
            val b2 = bytes[44 + i * 2 + 1].toInt() shl 8
            pcmData[i] = (b1 or b2).toShort()
        }
        
        // Convert ShortArray to FloatArray (-1.0f to 1.0f)
        val floatData = FloatArray(pcmData.size)
        for (i in pcmData.indices) {
            floatData[i] = pcmData[i] / 32768.0f
        }
        
        return@withContext transcribeData(floatData)
    }

    suspend fun benchMemory(nthreads: Int): String = withContext(scope.coroutineContext) {
        return@withContext WhisperLib.benchMemcpy(nthreads)
    }

    suspend fun benchGgmlMulMat(nthreads: Int): String = withContext(scope.coroutineContext) {
        return@withContext WhisperLib.benchGgmlMulMat(nthreads)
    }

    suspend fun release() = withContext(scope.coroutineContext) {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0
        }
    }

    protected fun finalize() {
        runBlocking {
            release()
        }
    }

    companion object {
        fun createContextFromFile(filePath: String): WhisperContext {
            val ptr = WhisperLib.initContext(filePath)
            if (ptr == 0L) {
                throw java.lang.RuntimeException("Couldn't create context with path $filePath")
            }
            return WhisperContext(ptr)
        }

        fun createContextFromInputStream(stream: InputStream): WhisperContext {
            val ptr = WhisperLib.initContextFromInputStream(stream)
            if (ptr == 0L) {
                throw java.lang.RuntimeException("Couldn't create context from input stream")
            }
            return WhisperContext(ptr)
        }

        fun createContextFromAsset(assetManager: AssetManager, assetPath: String): WhisperContext {
            val ptr = WhisperLib.initContextFromAsset(assetManager, assetPath)
            if (ptr == 0L) {
                throw java.lang.RuntimeException("Couldn't create context from asset $assetPath")
            }
            return WhisperContext(ptr)
        }

        fun getSystemInfo(): String {
            return WhisperLib.getSystemInfo()
        }
    }
}

internal class WhisperLib {
    companion object {
        var isLoaded = false
            private set
        var loadError: String? = null
            private set

        init {
            try {
                Log.d(LOG_TAG, "Loading libwhisper.so")
                System.loadLibrary("whisper")
                isLoaded = true
                Log.d(LOG_TAG, "libwhisper.so loaded successfully")
            } catch (e: Throwable) {
                Log.e(LOG_TAG, "Failed to load libwhisper.so", e)
                loadError = e.message
            }
        }
        
        fun checkLoaded() {
            if (!isLoaded) {
                throw UnsatisfiedLinkError("Native library 'whisper' not loaded: $loadError")
            }
        }

        // JNI methods
        external fun initContextFromInputStream(inputStream: InputStream): Long
        external fun initContextFromAsset(assetManager: AssetManager, assetPath: String): Long
        external fun initContext(modelPath: String): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray)
        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
        external fun getTextSegmentT0(contextPtr: Long, index: Int): Long
        external fun getTextSegmentT1(contextPtr: Long, index: Int): Long
        external fun getSystemInfo(): String
        external fun benchMemcpy(nthread: Int): String
        external fun benchGgmlMulMat(nthread: Int): String
    }
}

private fun toTimestamp(t: Long, comma: Boolean = false): String {
    var msec = t * 10
    val hr = msec / (1000 * 60 * 60)
    msec -= hr * (1000 * 60 * 60)
    val min = msec / (1000 * 60)
    msec -= min * (1000 * 60)
    val sec = msec / 1000
    msec -= sec * 1000
    val delimiter = if (comma) "," else "."
    return String.format("%02d:%02d:%02d%s%03d", hr, min, sec, delimiter, msec)
}
