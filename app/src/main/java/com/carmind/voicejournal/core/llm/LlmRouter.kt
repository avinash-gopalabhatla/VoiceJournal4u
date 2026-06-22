package com.carmind.voicejournal.core.llm

import android.util.Log
import com.carmind.voicejournal.core.journal.EntryAnalysis
import com.carmind.voicejournal.core.journal.JournalRepository
import com.carmind.voicejournal.core.journal.UserProfileMemory
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmRouter @Inject constructor(
    private val mediaPipe: MediaPipeEngine,
    private val ollama: OllamaEngine,
    private val anthropic: AnthropicEngine,
    private val repository: JournalRepository
) : LlmEngine {

    override val name = "LlmRouter"

    private var cached: LlmEngine? = null
    private var lastCheck = 0L
    private val cacheTtlMs = 30_000L

    override suspend fun isAvailable() = true

    private suspend fun resolve(): LlmEngine {
        val now = System.currentTimeMillis()
        cached?.let { if (now - lastCheck < cacheTtlMs) return it }

        val engine = when {
            mediaPipe.isAvailable() -> {
                Log.i("LlmRouter", "Using MediaPipe (on-device)")
                mediaPipe
            }
            ollama.isAvailable() -> {
                Log.i("LlmRouter", "Using Ollama on Pi 4")
                ollama
            }
            anthropic.isAvailable() -> {
                Log.i("LlmRouter", "Using Anthropic API (online)")
                anthropic
            }
            else -> throw IllegalStateException("No LLM backend available.")
        }

        cached = engine
        lastCheck = now
        return engine
    }

    fun invalidate() { cached = null }

    override suspend fun analyzeEntry(transcript: String, context: String?): EntryAnalysis {
        val engine = resolve()
        
        // Step 1: Retrieve Unified Memory & Chronological History
        val memory = repository.getMemory()
        val recentEntries = repository.observeAll().first().take(3)
        
        val contextStrBuilder = StringBuilder()
        if (memory != null && memory.memoryText.isNotBlank()) {
            contextStrBuilder.append("USER PROFILE & LONG-TERM MEMORY:\n${memory.memoryText}\n\n")
        }
        
        if (recentEntries.isNotEmpty()) {
            contextStrBuilder.append("RECENT CHRONOLOGICAL JOURNAL ENTRIES:\n")
            recentEntries.reversed().forEach { entry ->
                contextStrBuilder.append("- Category: ${entry.category}, Summary: ${entry.summary} (Topics: ${entry.keyTopics.joinToString()})\n")
            }
        }
        
        val contextStr = contextStrBuilder.toString().trim()
        if (contextStr.isBlank() || engine.name.contains("Mock")) {
            // No context available, or using stubbed engine, run default analysis
            return engine.analyzeEntry(transcript, null)
        }

        Log.d("LlmRouter", "Analyzing with unified memory context:\n$contextStr")

        // Step 2: Final contextual analysis (single pass to save mobile hardware resources)
        return try {
            engine.analyzeEntry(transcript, contextStr)
        } catch (e: Exception) {
            Log.e("LlmRouter", "Contextual analysis failed, falling back to clean pass", e)
            engine.analyzeEntry(transcript, null)
        }
    }

    override suspend fun complete(prompt: String, systemPrompt: String?) =
        resolve().complete(prompt, systemPrompt)
}
