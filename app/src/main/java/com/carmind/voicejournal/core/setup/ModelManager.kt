package com.carmind.voicejournal.core.setup

import android.content.Context
import android.util.Log
import com.carmind.voicejournal.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val currentFile: String) : DownloadState()
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository
) {
    private val cookieJar = object : CookieJar {
        private val cookieStore = mutableMapOf<String, List<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: listOf()
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    companion object {
        const val LLM_URL = "https://www.dropbox.com/scl/fi/x4f861apu1zqaqpofsn1w/gemma-2b-it-gpu-int4.bin?rlkey=mz2ihz4y7j1k1whg6vqdf1cut&st=1ahcxarc&dl=1"
        const val WHISPER_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin"
        
        const val WHISPER_FILENAME = "whisper_model.bin"
        const val LLM_FILENAME = "gemma_model.bin"
        
        const val WHISPER_MIN_SIZE = 30_000_000L // ~30MB+ (allows tiny and quantized models)
        const val LLM_MIN_SIZE = 1_000_000_000L // ~1.3GB
    }

    fun areModelsDownloaded(): Boolean {
        val whisperFile = File(context.filesDir, WHISPER_FILENAME)
        val llmFile = File(context.filesDir, "llm/$LLM_FILENAME")
        return whisperFile.exists() && whisperFile.length() > WHISPER_MIN_SIZE &&
               llmFile.exists() && llmFile.length() > LLM_MIN_SIZE
    }

    suspend fun downloadModels() = withContext(Dispatchers.IO) {
        try {
            _downloadState.value = DownloadState.Downloading(0f, "Checking local files...")

            // 1. Download Whisper if missing or small
            val whisperFile = File(context.filesDir, WHISPER_FILENAME)
            val modelName = settings.whisperModelName.first()
            if (modelName?.contains("Base") == true && whisperFile.exists()) {
                Log.i("ModelManager", "Deleting old Base Whisper model to upgrade to Tiny...")
                whisperFile.delete()
            }

            if (!whisperFile.exists() || whisperFile.length() < WHISPER_MIN_SIZE) {
                downloadFile(WHISPER_URL, WHISPER_FILENAME, "Whisper STT Model")
            } else {
                Log.d("ModelManager", "Whisper already exists, skipping.")
            }
            settings.saveWhisperModelPath(whisperFile.absolutePath, "Whisper Tiny (EN)")

            // 2. Download LLM if missing or small
            val llmDir = File(context.filesDir, "llm")
            if (!llmDir.exists()) llmDir.mkdirs()
            val llmFile = File(llmDir, LLM_FILENAME)
            if (!llmFile.exists() || llmFile.length() < LLM_MIN_SIZE) {
                downloadFile(LLM_URL, "llm/$LLM_FILENAME", "Gemma LLM Model")
            } else {
                Log.d("ModelManager", "LLM already exists, skipping.")
            }
            settings.saveLlmModelPath(llmFile.absolutePath, "Gemma 2B (Snapdragon Optimized)")

            _downloadState.value = DownloadState.Completed
        } catch (e: Exception) {
            Log.e("ModelManager", "Download failed", e)
            _downloadState.value = DownloadState.Error(e.message ?: "Connection lost. Please retry.")
        }
    }

    private suspend fun downloadFile(downloadUrl: String, fileName: String, displayName: String) {
        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw Exception("Dropbox error $code. Please check the link.")
        }

        val body = response.body ?: throw Exception("Empty response body from server")
        val totalSize = body.contentLength()
        Log.d("ModelManager", "Download started for $displayName. Total size: $totalSize")
        
        val targetFile = File(context.filesDir, fileName)
        val tempFile = File(context.filesDir, "$fileName.tmp")
        targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        
        try {
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Update progress every 2MB
                        if (totalBytesRead % (1024 * 1024 * 2) < (128 * 1024)) {
                            val progress = if (totalSize > 0) totalBytesRead.toFloat() / totalSize else 0f
                            val mb = totalBytesRead.toFloat() / (1024 * 1024)
                            val statusText = if (totalSize > 0 && totalSize > totalBytesRead) {
                                "$displayName (${String.format(Locale.US, "%.0f", progress * 100)}%)"
                            } else {
                                "$displayName (${String.format(Locale.US, "%.1f", mb)} MB)"
                            }
                            _downloadState.value = DownloadState.Downloading(progress, statusText)
                        }
                    }
                }
            }
            
            // Move temp file to actual file only if successful
            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }
        } finally {
            response.close()
            if (tempFile.exists()) tempFile.delete()
        }
        
        // Final verification for Dropbox
        if (targetFile.length() < 1024 * 1024) { 
            targetFile.delete()
            throw Exception("Download failed: Received file is too small.")
        }
    }
}
