package com.carmind.voicejournal.core.journal

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepository @Inject constructor(
    private val dao: JournalDao
) {
    fun observeAll() = dao.observeAll()
    suspend fun getAllEntries() = dao.getAllEntries()
    suspend fun save(entry: JournalEntry) = dao.insert(entry)
    suspend fun delete(entry: JournalEntry) = dao.delete(entry)
    suspend fun getPendingSync() = dao.getPendingSync()

    fun observeSummaries() = dao.observeSummaries()
    suspend fun saveSummary(summary: TimeSummary) = dao.insertSummary(summary)
    suspend fun deleteSummaryById(id: String) = dao.deleteSummaryById(id)

    suspend fun getMemory() = dao.getMemory()
    suspend fun saveMemory(memory: UserProfileMemory) = dao.insertMemory(memory)

    fun observeCoachSessions() = dao.observeCoachSessions()
    suspend fun getAllCoachSessions() = dao.getAllCoachSessions()
    suspend fun saveCoachSession(session: CoachSession) = dao.insertCoachSession(session)
}
