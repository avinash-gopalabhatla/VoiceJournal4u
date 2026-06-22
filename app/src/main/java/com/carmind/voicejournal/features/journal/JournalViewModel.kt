// features/journal/JournalViewModel.kt
package com.carmind.voicejournal.features.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmind.voicejournal.core.api.CarMindSyncApi
import com.carmind.voicejournal.core.journal.EntryCategory
import com.carmind.voicejournal.core.journal.JournalEntry
import com.carmind.voicejournal.core.journal.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JournalUiState(
    val entries: List<JournalEntry> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: EntryCategory? = null,
    val isLoading: Boolean = false,
    val syncStatus: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val repo: JournalRepository,
    private val syncApi: CarMindSyncApi,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<EntryCategory?>(null)
    private val _syncStatus = MutableStateFlow<String?>(null)

    val state: StateFlow<JournalUiState> = combine(
        repo.observeAll(),
        _searchQuery,
        _selectedCategory,
        _syncStatus,
    ) { allEntries, query, category, syncStatus ->
        val filtered = allEntries
            .filter { category == null || it.category == category }
            .filter { query.isBlank() || it.matchesQuery(query) }
        JournalUiState(
            entries = filtered,
            searchQuery = query,
            selectedCategory = category,
            syncStatus = syncStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JournalUiState(isLoading = true))

    fun setSearch(q: String) = _searchQuery.update { q }
    fun setCategory(cat: EntryCategory?) = _selectedCategory.update { cat }

    fun delete(entry: JournalEntry) = viewModelScope.launch {
        repo.delete(entry)
    }

    fun updateEntry(entry: JournalEntry) = viewModelScope.launch {
        repo.save(entry)
    }

    fun syncToCarMind() = viewModelScope.launch {
        _syncStatus.update { "Syncing…" }
        syncApi.syncPending().fold(
            onSuccess = { count -> _syncStatus.update { "Synced $count entries ✓" } },
            onFailure = { _syncStatus.update { "Pi 4 unreachable" } },
        )
        kotlinx.coroutines.delay(3000)
        _syncStatus.update { null }
    }
}

fun JournalEntry.matchesQuery(q: String): Boolean {
    if (q.isBlank()) return true
    val lower = q.lowercase()
    return rawTranscript.lowercase().contains(lower) ||
            title.lowercase().contains(lower) ||
            summary.lowercase().contains(lower) ||
            tags.any { it.lowercase().contains(lower) } ||
            keyTopics.any { it.lowercase().contains(lower) }
}
