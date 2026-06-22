package com.carmind.voicejournal.core.api

import com.carmind.voicejournal.core.journal.JournalRepository
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Named

@Singleton
class CarMindSyncApi @Inject constructor(
    private val repo: JournalRepository,
    @Named("carmind_url") private val baseUrl: String
) {
    suspend fun syncPending(): Result<Int> {
        val pending = repo.getPendingSync()
        if (pending.isEmpty()) return Result.success(0)
        // Mock sync
        return if (baseUrl.isNotBlank()) Result.success(pending.size) else Result.failure(Exception("Offline"))
    }
}
