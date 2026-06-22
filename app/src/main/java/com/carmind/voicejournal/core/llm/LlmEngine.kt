package com.carmind.voicejournal.core.llm

import com.carmind.voicejournal.core.journal.EntryAnalysis

interface LlmEngine {
    val name: String
    suspend fun isAvailable(): Boolean
    suspend fun analyzeEntry(transcript: String, context: String? = null): EntryAnalysis
    suspend fun complete(prompt: String, systemPrompt: String? = null): String
}
