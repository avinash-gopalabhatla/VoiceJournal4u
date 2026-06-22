package com.carmind.voicejournal.features.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmind.voicejournal.core.journal.EntryCategory
import com.carmind.voicejournal.core.journal.JournalEntry
import com.carmind.voicejournal.core.journal.JournalRepository
import com.carmind.voicejournal.core.llm.LlmRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InsightsUiState(
    val selectedCategory: EntryCategory? = null,
    val selectedTags: Set<String> = emptySet(),
    val allTags: List<String> = emptyList(),
    val filteredEntries: List<JournalEntry> = emptyList(),
    val generatedInsight: String? = null,
    val isGenerating: Boolean = false
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repo: JournalRepository,
    private val llm: LlmRouter
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<EntryCategory?>(null)
    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val _generatedInsight = MutableStateFlow<String?>(null)
    private val _isGenerating = MutableStateFlow(false)

    val state: StateFlow<InsightsUiState> = combine(
        repo.observeAll(),
        _selectedCategory,
        _selectedTags,
        _generatedInsight,
        _isGenerating
    ) { entries, category, tags, insight, generating ->
        val allTags = entries.flatMap { it.tags }.distinct().sorted()
        val filtered = entries.filter { entry ->
            (category == null || entry.category == category) &&
            (tags.isEmpty() || entry.tags.any { it in tags })
        }
        
        InsightsUiState(
            selectedCategory = category,
            selectedTags = tags,
            allTags = allTags,
            filteredEntries = filtered,
            generatedInsight = insight,
            isGenerating = generating
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsUiState())

    fun toggleCategory(category: EntryCategory?) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
        _generatedInsight.value = null
    }

    fun toggleTag(tag: String) {
        val current = _selectedTags.value.toMutableSet()
        if (tag in current) current.remove(tag) else current.add(tag)
        _selectedTags.value = current
        _generatedInsight.value = null
    }

    fun generateInsight() {
        val currentEntries = state.value.filteredEntries
        if (currentEntries.isEmpty()) return

        _isGenerating.value = true
        viewModelScope.launch {
            try {
                // Group stats to make it easier for the LLM to spot trends
                val moodStats = currentEntries.groupingBy { it.mood.label }.eachCount()
                val categoryStats = currentEntries.groupingBy { it.category.label }.eachCount()
                val statsSummary = StringBuilder().apply {
                    append("Mood breakdown: ")
                    moodStats.forEach { (mood, count) -> append("$mood ($count times), ") }
                    append("\nCategory breakdown: ")
                    categoryStats.forEach { (cat, count) -> append("$cat ($count times), ") }
                }.toString()

                // Limit entries to last 10 to protect context size
                val entriesToProcess = currentEntries.take(10)
                val context = entriesToProcess.joinToString("\n---\n") { 
                    "Date: ${java.text.SimpleDateFormat("MMM d", java.util.Locale.US).format(java.util.Date(it.timestamp))}\nTitle: ${it.title}\nCategory: ${it.category.label}\nMood: ${it.mood.label} (${it.mood.emoji})\nSummary: ${it.summary}\nTopics: ${it.keyTopics.joinToString()}"
                }
                
                val prompt = """
                    You are analyzing a set of personal journal entries to detect patterns and offer deep, growth-focused insights.
                    
                    AGGREGATED METRICS:
                    $statsSummary
                    
                    RELEVANT ENTRIES (Last ${entriesToProcess.size}):
                    $context
                    
                    INSTRUCTIONS:
                    Provide a highly structured personal development analysis. Structure your output EXACTLY as follows:
                    
                    ### 📈 Mood & Habit Patterns
                    Identify 1-2 major emotional or behavioral patterns observed in these entries. Explain why they seem to happen.
                    
                    ### ⚠️ Core Friction Points
                    Highlight any recurring obstacles, negative self-talk, or sources of anxiety/stress.
                    
                    ### 💡 Coaching Recommendations
                    Offer 2-3 specific, actionable steps the user can take this week based on your observations. Keep it encouraging but direct.
                """.trimIndent()

                val result = llm.complete(prompt, "You are a wisdom-focused personal growth coach analyzing journal entries.")
                _generatedInsight.value = result
            } catch (e: Exception) {
                _generatedInsight.value = "Failed to generate insight: ${e.message}"
            } finally {
                _isGenerating.value = false
            }
        }
    }
}
