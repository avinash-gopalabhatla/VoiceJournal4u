package com.carmind.voicejournal.core.journal

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class EntryCategory(val label: String, val colorHex: String) {
    WORK("Work", "#4A9EFF"),
    HEALTH("Health", "#4ADBA2"),
    PERSONAL("Personal", "#B57BFF"),
    IDEAS("Ideas", "#FFD166"),
    TASKS("Tasks", "#FF6B6B"),
    FINANCE("Finance", "#4ADBD1"),
    LEARNING("Learning", "#FF9F43"),
    RELATIONSHIPS("Relationships", "#F78FB3");

    companion object {
        fun from(name: String): EntryCategory = entries.find { it.name == name } ?: PERSONAL
    }
}

enum class EntryMood(val label: String, val emoji: String) {
    ENERGIZED("Energized", "⚡"),
    FOCUSED("Focused", "🎯"),
    NEUTRAL("Neutral", "😐"),
    STRESSED("Stressed", "😫"),
    REFLECTIVE("Reflective", "🧘"),
    EXCITED("Excited", "🤩"),
    TIRED("Tired", "😴");

    companion object {
        fun from(name: String): EntryMood = entries.find { it.name == name } ?: NEUTRAL
    }
}

@Entity(tableName = "entries")
data class JournalEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val rawTranscript: String,
    val title: String,
    val summary: String,
    val category: EntryCategory,
    val mood: EntryMood,
    val tags: List<String>,
    val actionItems: List<String>,
    val keyTopics: List<String>,
    val dumpSnippets: List<String> = emptyList(),
    val source: String = "Mobile",
    val durationSeconds: Float? = null,
    val syncedToPi: Boolean = false,
    val audioPath: String? = null,
)

@Entity(tableName = "summaries")
data class TimeSummary(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val period: SummaryPeriod,
    val startDate: Long,
    val endDate: Long,
    val content: String,
    val keyAchievements: List<String>,
    val topTags: List<String>,
)

enum class SummaryPeriod { WEEK, MONTH, QUARTER, HALF_YEAR, YEAR }

data class EntryAnalysis(
    val title: String,
    val summary: String,
    val category: EntryCategory,
    val mood: EntryMood,
    val tags: List<String>,
    val actionItems: List<String>,
    val keyTopics: List<String>,
    val dumpSnippets: List<String> = emptyList(),
)

@Entity(tableName = "user_memory")
data class UserProfileMemory(
    @PrimaryKey val id: String = "singleton_memory",
    val lastUpdated: Long = System.currentTimeMillis(),
    val memoryText: String
)

@Entity(tableName = "coach_sessions")
data class CoachSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val promptText: String,
    val responseText: String,
    val clarityScore: Int,
    val vocabularyTable: String, // JSON string for replacement list
    val grammarFixes: String,    // JSON string for corrections
    val feedback: String
)
