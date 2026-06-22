package com.carmind.voicejournal.features.summaries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmind.voicejournal.core.journal.JournalEntry
import com.carmind.voicejournal.core.journal.JournalRepository
import com.carmind.voicejournal.core.journal.TimeSummary
import com.carmind.voicejournal.core.summary.SummaryEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummariesUiState(
    val summaries: List<TimeSummary> = emptyList(),
    val dumpItems: List<String> = emptyList(),
    val usageDays: Int = 0,
    val isRefreshing: Boolean = false,
    val lastRefreshTime: Long = 0
)

@HiltViewModel
class SummariesViewModel @Inject constructor(
    private val repository: JournalRepository,
    private val summaryEngine: SummaryEngine
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _lastRefreshTime = MutableStateFlow(0L)

    val state: StateFlow<SummariesUiState> = combine(
        repository.observeSummaries(),
        repository.observeAll(),
        _isRefreshing,
        _lastRefreshTime
    ) { summaries, entries, refreshing, lastRefresh ->
        // Group by period and take only the latest one for each, as requested
        val consolidatedSummaries = summaries
            .groupBy { it.period }
            .map { (_, periodSummaries) -> 
                periodSummaries.maxBy { it.timestamp } 
            }
            .sortedBy { it.period.ordinal }

        val dump = entries.flatMap { it.dumpSnippets }.filter { it.isNotBlank() }
        val days = entries.map { 
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = it.timestamp
            cal.get(java.util.Calendar.DAY_OF_YEAR).toString() + cal.get(java.util.Calendar.YEAR)
        }.distinct().size

        SummariesUiState(
            summaries = consolidatedSummaries,
            dumpItems = dump,
            usageDays = days,
            isRefreshing = refreshing,
            lastRefreshTime = lastRefresh
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SummariesUiState())

    fun refreshSummaries() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                summaryEngine.updateAllSummaries()
                _lastRefreshTime.value = System.currentTimeMillis()
            } catch (e: Exception) {
                android.util.Log.e("SummariesViewModel", "Manual refresh failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
