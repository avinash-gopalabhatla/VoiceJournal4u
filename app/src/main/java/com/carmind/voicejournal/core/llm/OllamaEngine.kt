package com.carmind.voicejournal.core.llm

import com.carmind.voicejournal.core.journal.EntryAnalysis
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Named

@Singleton
class OllamaEngine @Inject constructor(
    @Named("ollama_url") private val baseUrl: String
) : LlmEngine {
    override val name = "Ollama Pi 4 (local)"
    override suspend fun isAvailable() = false // Default to false for now
    override suspend fun analyzeEntry(transcript: String, context: String?): EntryAnalysis {
        val snippet = transcript.take(30).let { if (it.length == 30) "$it..." else it }
        return EntryAnalysis(
            title = "Ollama: $snippet",
            summary = "Pi 4 local analysis of: $snippet",
            category = com.carmind.voicejournal.core.journal.EntryCategory.PERSONAL,
            mood = com.carmind.voicejournal.core.journal.EntryMood.NEUTRAL,
            tags = listOf("OllamaMock"),
            actionItems = emptyList(),
            keyTopics = emptyList(),
            dumpSnippets = emptyList()
        )
    }
    override suspend fun complete(prompt: String, systemPrompt: String?): String = "Ollama response"
}
