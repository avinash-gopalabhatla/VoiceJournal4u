package com.carmind.voicejournal.core.llm

import com.carmind.voicejournal.core.journal.EntryAnalysis
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Named

@Singleton
class AnthropicEngine @Inject constructor(
    @Named("anthropic_key") private val apiKey: String
) : LlmEngine {
    override val name = "Anthropic Claude (online)"
    override suspend fun isAvailable() = apiKey.isNotBlank()
    override suspend fun analyzeEntry(transcript: String, context: String?): EntryAnalysis {
        // Mock implementation for fallback
        val snippet = transcript.take(30).let { if (it.length == 30) "$it..." else it }
        return EntryAnalysis(
            title = "Claude: $snippet",
            summary = "Claude's analysis of: $snippet",
            category = com.carmind.voicejournal.core.journal.EntryCategory.PERSONAL,
            mood = com.carmind.voicejournal.core.journal.EntryMood.NEUTRAL,
            tags = listOf("ClaudeMock"),
            actionItems = emptyList(),
            keyTopics = emptyList(),
            dumpSnippets = emptyList()
        )
    }
    override suspend fun complete(prompt: String, systemPrompt: String?): String = "Claude response"
}
