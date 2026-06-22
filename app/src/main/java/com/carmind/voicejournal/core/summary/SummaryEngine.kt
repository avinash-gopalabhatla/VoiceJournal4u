package com.carmind.voicejournal.core.summary

import com.carmind.voicejournal.core.journal.*
import com.carmind.voicejournal.core.llm.LlmRouter
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryEngine @Inject constructor(
    private val repository: JournalRepository,
    private val llm: LlmRouter
) {
    suspend fun updateAllSummaries() {
        val entries = repository.getAllEntries()
        android.util.Log.d("SummaryEngine", "Updating summaries with ${entries.size} total entries")
        if (entries.isEmpty()) return

        updatePeriodSummary(SummaryPeriod.WEEK, entries)
        updatePeriodSummary(SummaryPeriod.MONTH, entries)
    }

    private suspend fun updatePeriodSummary(period: SummaryPeriod, entries: List<JournalEntry>) {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        
        // Normalize calendar to start of day
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val startTime = when (period) {
            SummaryPeriod.WEEK -> {
                // Robust way to get the start of the current week (Sunday)
                val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
                calendar.add(Calendar.DAY_OF_YEAR, -(currentDay - calendar.firstDayOfWeek))
                calendar.timeInMillis
            }
            SummaryPeriod.MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.timeInMillis
            }
            else -> 0L
        }

        val periodEntries = entries.filter { it.timestamp >= startTime }.sortedBy { it.timestamp }
        android.util.Log.d("SummaryEngine", "Found ${periodEntries.size} entries for period ${period.name} since $startTime")
        
        if (periodEntries.isEmpty()) {
            android.util.Log.w("SummaryEngine", "No entries found for ${period.name}, skipping.")
            return
        }

        val entriesText = periodEntries.joinToString("\n\n") { entry ->
            val date = java.text.SimpleDateFormat("EEE, MMM d HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
            "ENTRY [$date]:\nTitle: ${entry.title}\nSummary: ${entry.summary}\nTopics: ${entry.keyTopics.joinToString()}"
        }
        
        val prompt = """
            You are creating a ${period.name} TIME CAPSULE summary. 
            There are ${periodEntries.size} total entries in this period. 
            You MUST synthesize ALL of them into a cohesive narrative.
            
            INPUT ENTRIES:
            $entriesText
            
            INSTRUCTIONS:
            1. Write a 3-4 sentence narrative 'content' that connects the dots between these entries.
            2. Identify the top 3-5 'key_achievements' across all entries.
            3. Pick the 3 most representative 'top_tags'.
            
            FORMAT: Return ONLY valid JSON.
            {
              "content": "...",
              "key_achievements": ["...", "..."],
              "top_tags": ["...", "..."]
            }
        """.trimIndent()

        try {
            val response = llm.complete(prompt, "You are a master life synthesizer.")
            
            val jsonStr = if (response.contains("```json")) {
                response.substringAfter("```json").substringBefore("```").trim()
            } else if (response.contains("{")) {
                response.substring(response.indexOf("{"), response.lastIndexOf("}") + 1)
            } else {
                response
            }

            val json = JSONObject(jsonStr)
            val summaryId = "${period.name}_$startTime"
            
            // Explicitly delete old one first to force Room to emit a new list
            repository.deleteSummaryById(summaryId)
            
            val summary = TimeSummary(
                id = summaryId,
                timestamp = System.currentTimeMillis(),
                period = period,
                startDate = startTime,
                endDate = now,
                content = json.optString("content", "Summary processing..."),
                keyAchievements = json.optJSONArray("key_achievements")?.let { arr -> List(arr.length()) { arr.getString(it) } } ?: emptyList(),
                topTags = json.optJSONArray("top_tags")?.let { arr -> List(arr.length()) { arr.getString(it) } } ?: emptyList()
            )
            
            repository.saveSummary(summary)
            android.util.Log.i("SummaryEngine", "Successfully updated ${period.name} summary ($summaryId) with ${periodEntries.size} entries.")
        } catch (e: Exception) {
            android.util.Log.e("SummaryEngine", "Failed to update ${period.name} summary", e)
        }
    }
}
